package com.fplanalytics.handler;

import com.fplanalytics.mongo.CollectionRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.UUID;

public class AdviceHandler {

  private final Vertx vertx;

  public AdviceHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * POST /api/v1/advice/:userId
   * Triggers AI advice generation. Returns 202 with requestId.
   */
  public void triggerAdvice(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    if (userId < 0) return;

    JsonObject body = ctx.body().asJsonObject();
    if (body == null) body = new JsonObject();

    int gwId = body.getInteger("gameweekId", 0);
    String focus = body.getString("focus", "general");
    String requestId = UUID.randomUUID().toString();

    // Load latest analytics report and trigger Ollama
    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.ANALYTICS_CACHE)
          .put("query", new JsonObject().put("userId", userId)))
      .onSuccess(msg -> {
        JsonObject report = msg.body().getJsonObject("result", new JsonObject());
        vertx.eventBus().send("fpl.ollama.generate",
          new JsonObject()
            .put("requestId", requestId)
            .put("userId", userId)
            .put("gameweekId", gwId)
            .put("focus", focus)
            .put("analyticsReport", report));

        ctx.response().setStatusCode(202)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject()
            .put("requestId", requestId)
            .put("status", "generating")
            .put("focus", focus)
            .put("userId", userId)
            .encode());
      })
      .onFailure(err -> serverError(ctx, err));
  }

  /**
   * GET /api/v1/advice/:userId/latest
   * Returns the most recently generated advice.
   */
  public void getLatest(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    if (userId < 0) return;

    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.AI_ADVICE)
          .put("query", new JsonObject().put("userId", userId)))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          ctx.response().setStatusCode(404).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "No advice found. Trigger generation first.").encode());
        } else {
          json(ctx, result);
        }
      })
      .onFailure(err -> serverError(ctx, err));
  }

  /**
   * GET /api/v1/advice/:userId/stream/:requestId
   * SSE endpoint. Streams tokens as Ollama generates them.
   */
  public void streamAdvice(RoutingContext ctx) {
    String requestId = ctx.pathParam("requestId");
    if (requestId == null || requestId.isBlank()) {
      ctx.response().setStatusCode(400).end("Missing requestId");
      return;
    }

    ctx.response()
      .putHeader("Content-Type", "text/event-stream")
      .putHeader("Cache-Control", "no-cache")
      .putHeader("Connection", "keep-alive")
      .setChunked(true);

    // Subscribe to token stream for this requestId
    var consumer = vertx.eventBus().<JsonObject>localConsumer("fpl.advice.token." + requestId, msg -> {
      JsonObject event = msg.body();
      if (ctx.response().ended()) return;

      if (Boolean.TRUE.equals(event.getBoolean("done"))) {
        ctx.response().write("event: complete\ndata: {\"done\":true}\n\n");
        ctx.response().end();
      } else if (event.containsKey("error")) {
        ctx.response().write("event: error\ndata: " + event.encode() + "\n\n");
        ctx.response().end();
      } else {
        ctx.response().write("data: " + event.encode() + "\n\n");
      }
    });

    // Clean up consumer when client disconnects
    ctx.request().connection().closeHandler(v -> consumer.unregister());

    // Timeout after 90 seconds
    vertx.setTimer(90_000, id -> {
      consumer.unregister();
      if (!ctx.response().ended()) {
        ctx.response().write("event: error\ndata: {\"error\":\"timeout\"}\n\n");
        ctx.response().end();
      }
    });
  }

  private int parseIntParam(RoutingContext ctx, String name) {
    try { return Integer.parseInt(ctx.pathParam(name)); }
    catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", "Invalid " + name).encode());
      return -1;
    }
  }

  private void json(RoutingContext ctx, JsonObject body) {
    ctx.response().putHeader("Content-Type", "application/json").end(body.encode());
  }

  private void serverError(RoutingContext ctx, Throwable err) {
    ctx.response().setStatusCode(500).putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", err.getMessage()).encode());
  }
}
