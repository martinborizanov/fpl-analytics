package com.fplanalytics.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthHandler {

  private static final Logger log = LoggerFactory.getLogger(HealthHandler.class);
  private final Vertx vertx;

  public HealthHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  public void handle(RoutingContext ctx) {
    // Check MongoDB by pinging it
    vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", "refresh_audit")
          .put("query", new JsonObject()))
      .onSuccess(msg -> {
        JsonObject response = new JsonObject()
          .put("status", "UP")
          .put("components", new JsonObject()
            .put("mongo", "UP")
            .put("kafka", "UP") // Simplified; would check Kafka consumer lag in full impl
            .put("ollama", "UP"));
        ctx.response()
          .putHeader("Content-Type", "application/json")
          .setStatusCode(200)
          .end(response.encode());
      })
      .onFailure(err -> {
        JsonObject response = new JsonObject()
          .put("status", "DOWN")
          .put("components", new JsonObject()
            .put("mongo", "DOWN")
            .put("error", err.getMessage()));
        ctx.response()
          .putHeader("Content-Type", "application/json")
          .setStatusCode(503)
          .end(response.encode());
      });
  }

  public void metrics(RoutingContext ctx) {
    JsonObject metrics = new JsonObject()
      .put("description", "Metrics endpoint — extend with Micrometer in future")
      .put("status", "ok");
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(metrics.encode());
  }
}
