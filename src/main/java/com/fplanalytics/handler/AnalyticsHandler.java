package com.fplanalytics.handler;

import com.fplanalytics.config.AppConfig;
import com.fplanalytics.mongo.CollectionRegistry;
import com.fplanalytics.verticle.AnalyticsVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class AnalyticsHandler {

  private final Vertx vertx;
  private final AppConfig config;

  public AnalyticsHandler(Vertx vertx, AppConfig config) {
    this.vertx = vertx;
    this.config = config;
  }

  /**
   * GET /api/v1/analytics/:userId/gameweek/:gameweekId
   * Returns full analytics report; triggers computation if cache is stale.
   */
  public void getReport(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    int gwId = parseIntParam(ctx, "gameweekId");
    if (userId < 0 || gwId < 0) return;

    boolean force = "true".equalsIgnoreCase(ctx.queryParam("force").stream().findFirst().orElse("false"));

    // Try cache first
    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.ANALYTICS_CACHE)
          .put("query", new JsonObject().put("userId", userId).put("gameweekId", gwId)))
      .onSuccess(msg -> {
        JsonObject cached = msg.body().getJsonObject("result");
        if (cached != null && !force && !isCacheExpired(cached)) {
          json(ctx, cached);
        } else {
          // Trigger recomputation asynchronously and wait up to 15s
          triggerAndWait(ctx, userId, gwId);
        }
      })
      .onFailure(err -> triggerAndWait(ctx, userId, gwId));
  }

  private void triggerAndWait(RoutingContext ctx, int userId, int gwId) {
    vertx.eventBus().send("fpl.analytics.compute",
      new JsonObject().put("userId", userId).put("gameweekId", gwId));

    // Wait for completion signal (up to 15 seconds)
    long timerId = vertx.setTimer(15_000, id -> {
      // Timeout: return whatever is cached
      vertx.eventBus().<JsonObject>request("mongo.findOne",
          new JsonObject()
            .put("collection", CollectionRegistry.ANALYTICS_CACHE)
            .put("query", new JsonObject().put("userId", userId).put("gameweekId", gwId)))
        .onSuccess(msg -> {
          JsonObject cached = msg.body().getJsonObject("result");
          if (cached != null) json(ctx, cached);
          else ctx.response().setStatusCode(202)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("status", "computing").put("message", "Analytics computation in progress").encode());
        })
        .onFailure(err -> serverError(ctx, err));
    });

    vertx.eventBus().<JsonObject>localConsumer("fpl.analytics.ready." + userId + "." + gwId, ready -> {
      vertx.cancelTimer(timerId);
      vertx.eventBus().<JsonObject>request("mongo.findOne",
          new JsonObject()
            .put("collection", CollectionRegistry.ANALYTICS_CACHE)
            .put("query", new JsonObject().put("userId", userId).put("gameweekId", gwId)))
        .onSuccess(msg -> json(ctx, msg.body().getJsonObject("result", new JsonObject())))
        .onFailure(err -> serverError(ctx, err));
    });
  }

  public void getForm(RoutingContext ctx) {
    getFromReport(ctx, "formScores");
  }

  public void getTransfers(RoutingContext ctx) {
    getFromReport(ctx, "transferSuggestions");
  }

  public void getChips(RoutingContext ctx) {
    getFromReport(ctx, "chipTiming");
  }

  public void getFixtureDifficulty(RoutingContext ctx) {
    vertx.eventBus().<JsonObject>request("mongo.find",
        new JsonObject().put("collection", CollectionRegistry.FIXTURES).put("query", new JsonObject()))
      .onSuccess(msg -> json(ctx, msg.body()))
      .onFailure(err -> serverError(ctx, err));
  }

  public void getHistory(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    int gwId = parseIntParam(ctx, "gameweekId");
    if (userId < 0 || gwId < 0) return;
    // Returns last N gameweek reports for the user
    vertx.eventBus().<JsonObject>request("mongo.find",
        new JsonObject()
          .put("collection", CollectionRegistry.ANALYTICS_CACHE)
          .put("query", new JsonObject().put("userId", userId)))
      .onSuccess(msg -> json(ctx, msg.body()))
      .onFailure(err -> serverError(ctx, err));
  }

  private void getFromReport(RoutingContext ctx, String field) {
    int userId = parseIntParam(ctx, "userId");
    if (userId < 0) return;
    int gwId = parseIntParamOrDefault(ctx, "gameweekId", 0);

    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.ANALYTICS_CACHE)
          .put("query", new JsonObject().put("userId", userId).put("gameweekId", gwId > 0 ? gwId : new JsonObject())))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          ctx.response().setStatusCode(404).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "Analytics not yet computed. Trigger refresh first.").encode());
        } else {
          Object value = result.getValue(field);
          if (value instanceof JsonArray arr) {
            ctx.response().putHeader("Content-Type", "application/json").end(arr.encode());
          } else if (value instanceof JsonObject obj) {
            json(ctx, obj);
          } else {
            ctx.response().putHeader("Content-Type", "application/json").end("[]");
          }
        }
      })
      .onFailure(err -> serverError(ctx, err));
  }

  private boolean isCacheExpired(JsonObject cached) {
    String ttl = cached.getString("ttlExpiresAt");
    if (ttl == null) return true;
    try { return Instant.parse(ttl).isBefore(Instant.now()); }
    catch (Exception e) { return true; }
  }

  private int parseIntParam(RoutingContext ctx, String name) {
    try { return Integer.parseInt(ctx.pathParam(name)); }
    catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", "Invalid " + name).encode());
      return -1;
    }
  }

  private int parseIntParamOrDefault(RoutingContext ctx, String name, int def) {
    String v = ctx.pathParam(name);
    if (v == null) return def;
    try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
  }

  private void json(RoutingContext ctx, JsonObject body) {
    ctx.response().putHeader("Content-Type", "application/json").end(body.encode());
  }

  private void serverError(RoutingContext ctx, Throwable err) {
    ctx.response().setStatusCode(500).putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", err.getMessage()).encode());
  }
}
