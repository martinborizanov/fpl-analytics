package com.fplanalytics.verticle;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.kafka.KafkaProducerFactory;
import com.fplanalytics.kafka.TopicRegistry;
import com.fplanalytics.mongo.CollectionRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker verticle that calls the FPL public API and publishes raw JSON
 * payloads to Kafka topics. Implements a simple token-bucket rate limiter.
 */
public class FplApiClientVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(FplApiClientVerticle.class);
  private static final int MAX_RETRIES = 3;

  private AppConfig config;
  private WebClient webClient;
  private KafkaProducer<String, String> producer;
  private Tracer tracer;

  // Simple token counter for rate limiting (tokens refilled by a periodic timer)
  private final AtomicInteger tokens = new AtomicInteger(60);

  @Override
  public void start(Promise<Void> startPromise) {
    config = (AppConfig) vertx.sharedData().getLocalMap("fpl.shared").get("config");
    Tracing tracing = (Tracing) vertx.sharedData().getLocalMap("fpl.shared").get("tracing");
    tracer = tracing.tracer();

    webClient = WebClient.create(vertx, new WebClientOptions()
      .setConnectTimeout(10_000)
      .setIdleTimeout(30)
      .setSsl(true)
      .setTrustAll(false)
      .setUserAgent("FPL-Analytics/1.0"));

    producer = KafkaProducerFactory.create(vertx, config);

    // Refill token bucket every minute
    vertx.setPeriodic(60_000, id -> tokens.set(config.getFpl().getRateLimitPerMinute()));

    registerCommandHandlers();
    startPromise.complete();
    log.info("FplApiClientVerticle ready");
  }

  private void registerCommandHandlers() {
    vertx.eventBus().<JsonObject>localConsumer("fpl.cmd.refresh.bootstrap", msg ->
      fetchAndPublish("/bootstrap-static/", TopicRegistry.RAW_BOOTSTRAP, "bootstrap", null, 0));

    vertx.eventBus().<JsonObject>localConsumer("fpl.cmd.refresh.fixtures", msg ->
      fetchAndPublish("/fixtures/", TopicRegistry.RAW_FIXTURES, "fixtures", null, 0));

    vertx.eventBus().<JsonObject>localConsumer("fpl.cmd.refresh.playerHistory", msg -> {
      int playerId = msg.body().getInteger("playerId");
      fetchAndPublish("/element-summary/" + playerId + "/",
        TopicRegistry.RAW_PLAYER_HISTORY, String.valueOf(playerId), null, 0);
    });

    vertx.eventBus().<JsonObject>localConsumer("fpl.cmd.refresh.leagueStandings", msg -> {
      int leagueId = msg.body().getInteger("leagueId");
      fetchAndPublish("/leagues-classic/" + leagueId + "/standings/",
        TopicRegistry.RAW_LEAGUE_STANDINGS, String.valueOf(leagueId), null, 0);
    });

    vertx.eventBus().<JsonObject>localConsumer("fpl.cmd.refresh.teamPicks", msg -> {
      int userId = msg.body().getInteger("userId");
      int gwId = msg.body().getInteger("gameweekId");
      fetchAndPublish("/entry/" + userId + "/event/" + gwId + "/picks/",
        TopicRegistry.RAW_TEAM, String.valueOf(userId), null, 0);
    });
  }

  private void fetchAndPublish(String path, String topic, String key, String spanName, int attempt) {
    if (tokens.decrementAndGet() < 0) {
      tokens.incrementAndGet();
      log.warn("Rate limit reached, delaying fetch of {}", path);
      vertx.setTimer(5_000, id -> fetchAndPublish(path, topic, key, spanName, attempt));
      return;
    }

    String apiBase = config.getFpl().getApiBase();
    Span span = tracer.nextSpan()
      .name("fpl.api.fetch " + path)
      .tag("fpl.endpoint", path)
      .tag("kafka.topic", topic)
      .kind(Span.Kind.CLIENT)
      .start();

    String host = apiBase.replace("https://", "").replace("http://", "").split("/")[0];

    webClient.get(443, host, "/api" + path)
      .ssl(true)
      .timeout(30_000)
      .send()
      .onSuccess(response -> {
        span.tag("http.status_code", String.valueOf(response.statusCode()));

        if (response.statusCode() == 200) {
          String body = response.bodyAsString();
          span.tag("response.size.bytes", String.valueOf(body.length()));
          span.finish();

          publishToKafka(topic, key, body);
          writeAudit(topic, true, null);
          // Notify data updated
          vertx.eventBus().publish("fpl.data.updated." + topic.replace(".", "_"), new JsonObject());

        } else if (response.statusCode() == 429) {
          span.annotate("rate.limit.hit");
          span.finish();
          log.warn("FPL API rate limited on {}, retry in 60s", path);
          vertx.setTimer(60_000, id -> {
            if (attempt < MAX_RETRIES) fetchAndPublish(path, topic, key, spanName, attempt + 1);
          });

        } else {
          span.tag("http.error", "status " + response.statusCode());
          span.finish();
          log.error("Unexpected status {} fetching {}", response.statusCode(), path);
          retryWithBackoff(path, topic, key, attempt, null);
        }
      })
      .onFailure(err -> {
        span.error(err);
        span.finish();
        log.error("HTTP error fetching {}: {}", path, err.getMessage());
        retryWithBackoff(path, topic, key, attempt, err.getMessage());
      });
  }

  private void retryWithBackoff(String path, String topic, String key, int attempt, String errorMsg) {
    if (attempt < MAX_RETRIES) {
      long delay = (long) Math.pow(2, attempt) * 5_000L;
      log.info("Retrying {} in {}ms (attempt {})", path, delay, attempt + 1);
      vertx.setTimer(delay, id -> fetchAndPublish(path, topic, key, null, attempt + 1));
    } else {
      log.error("Max retries exceeded for {}", path);
      writeAudit(topic, false, errorMsg);
    }
  }

  private void publishToKafka(String topic, String key, String value) {
    KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(topic, key, value);
    producer.send(record)
      .onSuccess(meta -> log.debug("Published to {} partition={} offset={}", topic, meta.getPartition(), meta.getOffset()))
      .onFailure(err -> log.error("Failed to publish to {}: {}", topic, err.getMessage()));
  }

  private void writeAudit(String topic, boolean success, String errorMsg) {
    JsonObject audit = new JsonObject()
      .put("topic", topic)
      .put("triggeredAt", Instant.now().toString())
      .put("success", success);
    if (errorMsg != null) audit.put("errorMessage", errorMsg);

    vertx.eventBus().send("mongo.upsert", new JsonObject()
      .put("collection", CollectionRegistry.REFRESH_AUDIT)
      .put("filter", new JsonObject().put("topic", topic).put("triggeredAt", audit.getString("triggeredAt")))
      .put("document", audit));
  }

  @Override
  public void stop() {
    if (webClient != null) webClient.close();
    if (producer != null) producer.close();
  }
}
