package com.fplanalytics.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class RefreshHandler {

  private final Vertx vertx;

  public RefreshHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  public void refreshBootstrap(RoutingContext ctx) {
    vertx.eventBus().send("fpl.cmd.refresh.bootstrap",
      new JsonObject().put("type", "bootstrap").put("triggeredAt", Instant.now().toString()));
    accepted(ctx, "bootstrap");
  }

  public void refreshFixtures(RoutingContext ctx) {
    vertx.eventBus().send("fpl.cmd.refresh.fixtures",
      new JsonObject().put("type", "fixtures").put("triggeredAt", Instant.now().toString()));
    accepted(ctx, "fixtures");
  }

  public void refreshPlayer(RoutingContext ctx) {
    int playerId;
    try { playerId = Integer.parseInt(ctx.pathParam("playerId")); }
    catch (NumberFormatException e) { badRequest(ctx, "Invalid playerId"); return; }
    vertx.eventBus().send("fpl.cmd.refresh.playerHistory",
      new JsonObject().put("playerId", playerId).put("triggeredAt", Instant.now().toString()));
    accepted(ctx, "playerHistory:" + playerId);
  }

  public void refreshLeague(RoutingContext ctx) {
    int leagueId;
    try { leagueId = Integer.parseInt(ctx.pathParam("leagueId")); }
    catch (NumberFormatException e) { badRequest(ctx, "Invalid leagueId"); return; }
    vertx.eventBus().send("fpl.cmd.refresh.leagueStandings",
      new JsonObject().put("leagueId", leagueId).put("triggeredAt", Instant.now().toString()));
    accepted(ctx, "leagueStandings:" + leagueId);
  }

  public void status(RoutingContext ctx) {
    vertx.eventBus().<JsonObject>request("mongo.find",
        new JsonObject()
          .put("collection", "refresh_audit")
          .put("query", new JsonObject()))
      .onSuccess(msg -> {
        ctx.response()
          .putHeader("Content-Type", "application/json")
          .end(msg.body().encode());
      })
      .onFailure(err -> serverError(ctx, err));
  }

  private void accepted(RoutingContext ctx, String jobType) {
    ctx.response()
      .setStatusCode(202)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject()
        .put("status", "accepted")
        .put("jobType", jobType)
        .put("triggeredAt", Instant.now().toString())
        .encode());
  }

  private void badRequest(RoutingContext ctx, String message) {
    ctx.response().setStatusCode(400)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }

  private void serverError(RoutingContext ctx, Throwable err) {
    ctx.response().setStatusCode(500)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", err.getMessage()).encode());
  }
}
