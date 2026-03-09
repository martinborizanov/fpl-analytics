package com.fplanalytics.handler;

import com.fplanalytics.mongo.CollectionRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class LeagueHandler {

  private final Vertx vertx;

  public LeagueHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  public void getStandings(RoutingContext ctx) {
    int leagueId = parseIntParam(ctx, "leagueId");
    if (leagueId < 0) return;

    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.LEAGUE_STANDINGS)
          .put("query", new JsonObject().put("leagueId", leagueId)))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          // Trigger refresh and return 202
          vertx.eventBus().send("fpl.cmd.refresh.leagueStandings",
            new JsonObject().put("leagueId", leagueId));
          ctx.response().setStatusCode(202).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("status", "fetching").put("leagueId", leagueId).encode());
        } else {
          json(ctx, result);
        }
      })
      .onFailure(err -> serverError(ctx, err));
  }

  public void compareTeams(RoutingContext ctx) {
    int leagueId = parseIntParam(ctx, "leagueId");
    int userId = parseIntParam(ctx, "userId");
    if (leagueId < 0 || userId < 0) return;

    // Return league standings with user highlighted
    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.LEAGUE_STANDINGS)
          .put("query", new JsonObject().put("leagueId", leagueId)))
      .onSuccess(msg -> {
        JsonObject result = msg.body().getJsonObject("result");
        if (result == null) {
          ctx.response().setStatusCode(404).putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "League not found. Trigger refresh first.").encode());
          return;
        }
        // Annotate the standings with the requesting user
        result.put("requestingUserId", userId);
        json(ctx, result);
      })
      .onFailure(err -> serverError(ctx, err));
  }

  public void getHistory(RoutingContext ctx) {
    int leagueId = parseIntParam(ctx, "leagueId");
    if (leagueId < 0) return;

    vertx.eventBus().<JsonObject>request("mongo.find",
        new JsonObject()
          .put("collection", CollectionRegistry.ANALYTICS_CACHE)
          .put("query", new JsonObject()))
      .onSuccess(msg -> json(ctx, msg.body()))
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
