package com.fplanalytics.verticle;

import com.fplanalytics.config.AppConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns all periodic data refresh timers.
 * Sends commands to FplApiClientVerticle via the event bus.
 */
public class DataRefreshVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(DataRefreshVerticle.class);

  private final List<Long> timerIds = new ArrayList<>();
  private AppConfig config;

  @Override
  public void start(Promise<Void> startPromise) {
    config = (AppConfig) vertx.sharedData().getLocalMap("fpl.shared").get("config");

    // Trigger an immediate refresh on startup
    triggerBootstrap();
    triggerFixtures();

    // Bootstrap-static every 2 hours
    timerIds.add(vertx.setPeriodic(config.getRefresh().getBootstrapIntervalMs(),
      id -> triggerBootstrap()));

    // Fixtures every 24 hours
    timerIds.add(vertx.setPeriodic(config.getRefresh().getFixturesIntervalMs(),
      id -> triggerFixtures()));

    log.info("DataRefreshVerticle timers started");
    startPromise.complete();
  }

  private void triggerBootstrap() {
    JsonObject cmd = new JsonObject()
      .put("type", "bootstrap")
      .put("triggeredAt", Instant.now().toString());
    vertx.eventBus().send("fpl.cmd.refresh.bootstrap", cmd);
    log.debug("Triggered bootstrap refresh");
  }

  private void triggerFixtures() {
    JsonObject cmd = new JsonObject()
      .put("type", "fixtures")
      .put("triggeredAt", Instant.now().toString());
    vertx.eventBus().send("fpl.cmd.refresh.fixtures", cmd);
    log.debug("Triggered fixtures refresh");
  }

  public void triggerPlayerHistory(int playerId) {
    JsonObject cmd = new JsonObject()
      .put("type", "playerHistory")
      .put("playerId", playerId)
      .put("triggeredAt", Instant.now().toString());
    vertx.eventBus().send("fpl.cmd.refresh.playerHistory", cmd);
  }

  public void triggerLeagueStandings(int leagueId) {
    JsonObject cmd = new JsonObject()
      .put("type", "leagueStandings")
      .put("leagueId", leagueId)
      .put("triggeredAt", Instant.now().toString());
    vertx.eventBus().send("fpl.cmd.refresh.leagueStandings", cmd);
  }

  public void triggerTeamPicks(int userId, int gameweekId) {
    JsonObject cmd = new JsonObject()
      .put("type", "teamPicks")
      .put("userId", userId)
      .put("gameweekId", gameweekId)
      .put("triggeredAt", Instant.now().toString());
    vertx.eventBus().send("fpl.cmd.refresh.teamPicks", cmd);
  }

  @Override
  public void stop() {
    timerIds.forEach(vertx::cancelTimer);
    timerIds.clear();
  }
}
