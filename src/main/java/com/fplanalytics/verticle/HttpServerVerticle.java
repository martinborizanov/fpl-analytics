package com.fplanalytics.verticle;

import brave.Tracing;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.handler.*;
import com.fplanalytics.tracing.TracingHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the HTTP server, route definitions, middleware, and static file serving.
 */
public class HttpServerVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(HttpServerVerticle.class);

  private HttpServer server;

  @Override
  public void start(Promise<Void> startPromise) {
    AppConfig config = (AppConfig) vertx.sharedData().getLocalMap("fpl.shared").get("config");
    Tracing tracing = (Tracing) vertx.sharedData().getLocalMap("fpl.shared").get("tracing");

    Router router = Router.router(vertx);

    // ---- Middleware ----
    router.route().handler(CorsHandler.create()
      .addOrigin("*")
      .allowedMethod(io.vertx.core.http.HttpMethod.GET)
      .allowedMethod(io.vertx.core.http.HttpMethod.POST)
      .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
      .allowedHeader("Content-Type")
      .allowedHeader("Accept"));

    router.route().handler(new TracingHandler(tracing));
    router.route("/api/*").handler(BodyHandler.create());

    // ---- Handlers ----
    HealthHandler healthHandler = new HealthHandler(vertx);
    RefreshHandler refreshHandler = new RefreshHandler(vertx);
    AnalyticsHandler analyticsHandler = new AnalyticsHandler(vertx, config);
    LeagueHandler leagueHandler = new LeagueHandler(vertx);
    AdviceHandler adviceHandler = new AdviceHandler(vertx);
    UserHandler userHandler = new UserHandler(vertx);

    // ---- API Routes ----
    // System
    router.get("/api/v1/health").handler(healthHandler::handle);
    router.get("/api/v1/metrics").handler(healthHandler::metrics);

    // Refresh control
    router.post("/api/v1/refresh/bootstrap").handler(refreshHandler::refreshBootstrap);
    router.post("/api/v1/refresh/fixtures").handler(refreshHandler::refreshFixtures);
    router.post("/api/v1/refresh/player/:playerId").handler(refreshHandler::refreshPlayer);
    router.post("/api/v1/refresh/league/:leagueId").handler(refreshHandler::refreshLeague);
    router.get("/api/v1/refresh/status").handler(refreshHandler::status);

    // User & team
    router.get("/api/v1/fpl/user/:userId").handler(userHandler::getUser);
    router.get("/api/v1/fpl/user/:userId/team/:gameweekId").handler(userHandler::getTeam);
    router.get("/api/v1/fpl/leagues/:userId").handler(userHandler::getLeagues);

    // Analytics
    router.get("/api/v1/analytics/:userId/gameweek/:gameweekId").handler(analyticsHandler::getReport);
    router.get("/api/v1/analytics/:userId/form").handler(analyticsHandler::getForm);
    router.get("/api/v1/analytics/:userId/transfers").handler(analyticsHandler::getTransfers);
    router.get("/api/v1/analytics/:userId/chips").handler(analyticsHandler::getChips);
    router.get("/api/v1/analytics/fixtures/difficulty").handler(analyticsHandler::getFixtureDifficulty);
    router.get("/api/v1/analytics/:userId/history/:gameweekId").handler(analyticsHandler::getHistory);

    // League
    router.get("/api/v1/league/:leagueId/standings").handler(leagueHandler::getStandings);
    router.get("/api/v1/league/:leagueId/comparison/:userId").handler(leagueHandler::compareTeams);
    router.get("/api/v1/league/:leagueId/history").handler(leagueHandler::getHistory);

    // AI Advice
    router.post("/api/v1/advice/:userId").handler(adviceHandler::triggerAdvice);
    router.get("/api/v1/advice/:userId/latest").handler(adviceHandler::getLatest);
    router.get("/api/v1/advice/:userId/stream/:requestId").handler(adviceHandler::streamAdvice);

    // ---- Static frontend ----
    router.route("/*").handler(StaticHandler.create("webroot")
      .setIndexPage("index.html")
      .setCachingEnabled(false));

    // ---- Error handler ----
    router.errorHandler(500, ctx -> {
      log.error("Unhandled server error on {}", ctx.request().path(), ctx.failure());
      if (!ctx.response().ended()) {
        ctx.response().setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end(new io.vertx.core.json.JsonObject()
            .put("error", "Internal server error")
            .put("path", ctx.request().path())
            .encode());
      }
    });

    // ---- Start server ----
    server = vertx.createHttpServer();
    server.requestHandler(router)
      .listen(config.getServer().getPort())
      .onSuccess(s -> {
        log.info("HTTP server started on port {}", config.getServer().getPort());
        startPromise.complete();
      })
      .onFailure(err -> {
        log.error("Failed to start HTTP server", err);
        startPromise.fail(err);
      });
  }

  @Override
  public void stop() {
    if (server != null) server.close();
  }
}
