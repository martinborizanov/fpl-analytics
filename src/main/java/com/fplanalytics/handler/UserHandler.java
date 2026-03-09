package com.fplanalytics.handler;

import com.fplanalytics.mongo.CollectionRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class UserHandler {

  private final Vertx vertx;

  public UserHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  public void getUser(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    if (userId < 0) return;

    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.TEAM_PICKS)
          .put("query", new JsonObject().put("userId", userId)))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          // Trigger data fetch
          vertx.eventBus().send("fpl.cmd.refresh.teamPicks",
            new JsonObject().put("userId", userId).put("gameweekId", 0));
          ctx.response().setStatusCode(202).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("status", "fetching").put("userId", userId).encode());
        } else {
          json(ctx, result);
        }
      })
      .onFailure(err -> serverError(ctx, err));
  }

  public void getTeam(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    int gwId = parseIntParam(ctx, "gameweekId");
    if (userId < 0 || gwId < 0) return;

    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.TEAM_PICKS)
          .put("query", new JsonObject().put("userId", userId).put("gameweekId", gwId)))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          vertx.eventBus().send("fpl.cmd.refresh.teamPicks",
            new JsonObject().put("userId", userId).put("gameweekId", gwId));
          ctx.response().setStatusCode(202).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("status", "fetching").encode());
        } else {
          json(ctx, result);
        }
      })
      .onFailure(err -> serverError(ctx, err));
  }

  public void getLeagues(RoutingContext ctx) {
    int userId = parseIntParam(ctx, "userId");
    if (userId < 0) return;

    // Bootstrap contains league info; simplified response
    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.BOOTSTRAP_SNAPSHOTS)
          .put("query", new JsonObject()))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          ctx.response().setStatusCode(404).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "No bootstrap data. Trigger refresh first.").encode());
        } else {
          json(ctx, new JsonObject().put("userId", userId).put("message",
            "Fetch your FPL entry data to see your leagues. Leagues are user-specific and require authentication."));
        }
      })
      .onFailure(err -> serverError(ctx, err));
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
