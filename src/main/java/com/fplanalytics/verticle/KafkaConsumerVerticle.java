package com.fplanalytics.verticle;

import com.fplanalytics.SharedContext;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.SharedContext;
import com.fplanalytics.kafka.KafkaConsumerFactory;
import com.fplanalytics.SharedContext;
import com.fplanalytics.kafka.TopicRegistry;
import com.fplanalytics.SharedContext;
import com.fplanalytics.mongo.CollectionRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;

/**
 * Worker verticle that consumes all raw FPL data topics from Kafka
 * and persists them into MongoDB via MongoVerticle.
 */
public class KafkaConsumerVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(KafkaConsumerVerticle.class);

  private KafkaConsumer<String, String> consumer;

  @Override
  public void start(Promise<Void> startPromise) {
    AppConfig config = SharedContext.getConfig();
    consumer = KafkaConsumerFactory.create(vertx, config);

    consumer.exceptionHandler(err -> log.error("Kafka consumer error", err));

    consumer.handler(record -> {
      log.debug("Consumed from {} key={}", record.topic(), record.key());
      routeRecord(record.topic(), record.key(), record.value());
    });

    Set<String> topics = Set.of(
      TopicRegistry.RAW_BOOTSTRAP,
      TopicRegistry.RAW_FIXTURES,
      TopicRegistry.RAW_PLAYER_HISTORY,
      TopicRegistry.RAW_LEAGUE_STANDINGS,
      TopicRegistry.RAW_TEAM,
      TopicRegistry.RAW_LIVE,
      TopicRegistry.ANALYTICS_COMPUTED,
      TopicRegistry.OLLAMA_RESPONSE
    );

    consumer.subscribe(topics)
      .onSuccess(v -> {
        log.info("KafkaConsumerVerticle subscribed to {} topics", topics.size());
        startPromise.complete();
      })
      .onFailure(err -> {
        log.error("Failed to subscribe to Kafka topics", err);
        startPromise.fail(err);
      });
  }

  private void routeRecord(String topic, String key, String value) {
    switch (topic) {
      case TopicRegistry.RAW_BOOTSTRAP -> handleBootstrap(value);
      case TopicRegistry.RAW_FIXTURES -> handleFixtures(value);
      case TopicRegistry.RAW_PLAYER_HISTORY -> handlePlayerHistory(key, value);
      case TopicRegistry.RAW_LEAGUE_STANDINGS -> handleLeagueStandings(key, value);
      case TopicRegistry.RAW_TEAM -> handleTeamPicks(key, value);
      case TopicRegistry.ANALYTICS_COMPUTED -> handleAnalyticsComputed(value);
      case TopicRegistry.OLLAMA_RESPONSE -> handleOllamaResponse(value);
      default -> log.warn("No handler for topic: {}", topic);
    }
  }

  private void handleBootstrap(String value) {
    JsonObject doc = new JsonObject()
      .put("fetchedAt", Instant.now().toString())
      .put("raw", value); // Store raw JSON; parsed by AnalyticsVerticle on demand

    upsert(CollectionRegistry.BOOTSTRAP_SNAPSHOTS,
      new JsonObject().put("fetchedAt", doc.getString("fetchedAt")),
      doc)
      .onSuccess(v -> invalidateAnalyticsCache("bootstrap"));
  }

  private void handleFixtures(String value) {
    // Parse fixtures array and upsert each by eventId+homeTeamId+awayTeamId
    try {
      JsonArray fixtures = new JsonArray(value);
      fixtures.forEach(o -> {
        JsonObject fixture = (JsonObject) o;
        // Skip unscheduled fixtures (FPL returns null for event/team when not yet assigned)
        Integer eventId = fixture.getInteger("event");
        Integer homeTeamId = fixture.getInteger("team_h");
        Integer awayTeamId = fixture.getInteger("team_a");
        if (eventId == null || homeTeamId == null || awayTeamId == null) return;
        JsonObject filter = new JsonObject()
          .put("eventId", eventId)
          .put("homeTeamId", homeTeamId)
          .put("awayTeamId", awayTeamId);
        JsonObject doc = new JsonObject()
          .put("eventId", eventId)
          .put("homeTeamId", homeTeamId)
          .put("awayTeamId", awayTeamId)
          .put("kickoffTime", fixture.getString("kickoff_time"))
          .put("teamHDifficulty", fixture.getInteger("team_h_difficulty", 3))
          .put("teamADifficulty", fixture.getInteger("team_a_difficulty", 3))
          .put("finished", fixture.getBoolean("finished", false));
        upsert(CollectionRegistry.FIXTURES, filter, doc);
      });
      invalidateAnalyticsCache("fixtures");
    } catch (Exception e) {
      log.error("Failed to parse fixtures payload", e);
    }
  }

  private void handlePlayerHistory(String playerId, String value) {
    JsonObject doc = new JsonObject()
      .put("playerId", Integer.parseInt(playerId))
      .put("fetchedAt", Instant.now().toString())
      .put("raw", value);
    upsert(CollectionRegistry.PLAYER_HISTORIES,
      new JsonObject().put("playerId", Integer.parseInt(playerId)),
      doc);
  }

  private void handleLeagueStandings(String leagueId, String value) {
    JsonObject doc = new JsonObject()
      .put("leagueId", Integer.parseInt(leagueId))
      .put("fetchedAt", Instant.now().toString())
      .put("raw", value);
    upsert(CollectionRegistry.LEAGUE_STANDINGS,
      new JsonObject().put("leagueId", Integer.parseInt(leagueId)),
      doc);
  }

  private void handleTeamPicks(String userId, String value) {
    try {
      JsonObject picks = new JsonObject(value);
      int gwId = picks.getJsonObject("entry_history", new JsonObject()).getInteger("event", 0);
      JsonObject doc = new JsonObject()
        .put("userId", Integer.parseInt(userId))
        .put("gameweekId", gwId)
        .put("fetchedAt", Instant.now().toString())
        .put("raw", value);
      upsert(CollectionRegistry.TEAM_PICKS,
        new JsonObject().put("userId", Integer.parseInt(userId)).put("gameweekId", gwId),
        doc);
    } catch (Exception e) {
      log.error("Failed to parse team picks for user {}", userId, e);
    }
  }

  private void handleAnalyticsComputed(String value) {
    try {
      JsonObject report = new JsonObject(value);
      int userId = report.getInteger("userId");
      int gwId = report.getInteger("gameweekId");
      upsert(CollectionRegistry.ANALYTICS_CACHE,
        new JsonObject().put("userId", userId).put("gameweekId", gwId),
        report);
      // Notify waiting HTTP connections
      vertx.eventBus().publish("fpl.analytics.ready." + userId + "." + gwId,
        new JsonObject().put("ready", true));
    } catch (Exception e) {
      log.error("Failed to store analytics computed", e);
    }
  }

  private void handleOllamaResponse(String value) {
    try {
      JsonObject resp = new JsonObject(value);
      upsert(CollectionRegistry.AI_ADVICE,
        new JsonObject().put("requestId", resp.getString("requestId")),
        resp);
      // Notify waiting SSE connection
      vertx.eventBus().publish("fpl.advice.ready." + resp.getString("requestId"),
        new JsonObject().put("ready", true));
    } catch (Exception e) {
      log.error("Failed to store Ollama response", e);
    }
  }

  private Future<Void> upsert(String collection, JsonObject filter, JsonObject doc) {
    return vertx.eventBus().<JsonObject>request("mongo.upsert", new JsonObject()
        .put("collection", collection)
        .put("filter", filter)
        .put("document", doc))
      .onFailure(err -> log.error("Upsert failed on {}: {}", collection, err.getMessage()))
      .mapEmpty();
  }

  private void invalidateAnalyticsCache(String dataType) {
    // Publish invalidation event; AnalyticsVerticle listens and resets TTL
    vertx.eventBus().publish("fpl.cache.invalidate", new JsonObject().put("dataType", dataType));
  }

  @Override
  public void stop() {
    if (consumer != null) {
      consumer.close();
    }
  }
}
