package com.fplanalytics.verticle;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fplanalytics.SharedContext;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.kafka.KafkaProducerFactory;
import com.fplanalytics.kafka.TopicRegistry;
import com.fplanalytics.model.AnalyticsReport;
import com.fplanalytics.model.FormScore;
import com.fplanalytics.mongo.CollectionRegistry;
import com.fplanalytics.service.ChipTimingService;
import com.fplanalytics.service.FixtureDifficultyService;
import com.fplanalytics.service.FormScoreService;
import com.fplanalytics.service.TransferSuggestionService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes analytics reports from persisted FPL data and publishes results.
 * Listens on event bus for analytics requests and cache invalidation events.
 */
public class AnalyticsVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsVerticle.class);

  private final FormScoreService formScoreService = new FormScoreService();
  private final FixtureDifficultyService fdrService = new FixtureDifficultyService();
  private final TransferSuggestionService transferService = new TransferSuggestionService();
  private final ChipTimingService chipService = new ChipTimingService();
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private AppConfig config;
  private KafkaProducer<String, String> producer;
  private Tracer tracer;

  @Override
  public void start(Promise<Void> startPromise) {
    config = SharedContext.getConfig();
    Tracing tracing = SharedContext.getTracing();
    tracer = tracing.tracer();
    producer = KafkaProducerFactory.create(vertx, config);

    // Listen for analytics compute requests from Kafka (via KafkaConsumerVerticle via event bus)
    vertx.eventBus().<JsonObject>localConsumer("fpl.analytics.compute", msg -> {
      int userId = msg.body().getInteger("userId");
      int gwId = msg.body().getInteger("gameweekId");
      computeAndPublish(userId, gwId);
    });

    // Cache invalidation: mark all analytics cache as stale
    vertx.eventBus().<JsonObject>localConsumer("fpl.cache.invalidate", msg -> {
      log.debug("Cache invalidation triggered by: {}", msg.body().getString("dataType", "unknown"));
      // TTL-based expiry handles this naturally; here we just log
    });

    startPromise.complete();
    log.info("AnalyticsVerticle ready");
  }

  public void computeAndPublish(int userId, int gwId) {
    Span span = tracer.nextSpan()
      .name("analytics.compute")
      .tag("user.id", String.valueOf(userId))
      .tag("gameweek.id", String.valueOf(gwId))
      .start();

    loadBootstrap()
      .compose(bootstrap -> loadTeamPicks(userId, gwId)
        .compose(teamPicks -> loadFixtures()
          .compose(fixtures -> {
            try {
              AnalyticsReport report = buildReport(userId, gwId, bootstrap, teamPicks, fixtures, span);
              String reportJson = mapper.writeValueAsString(report);

              // Publish to Kafka (KafkaConsumerVerticle will persist it)
              KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(
                TopicRegistry.ANALYTICS_COMPUTED, String.valueOf(userId), reportJson);
              return producer.send(record).mapEmpty();
            } catch (Exception e) {
              return Future.failedFuture(e);
            }
          })))
      .onSuccess(v -> {
        span.finish();
        log.info("Analytics computed and published for user={} gw={}", userId, gwId);
      })
      .onFailure(err -> {
        span.error(err);
        span.finish();
        log.error("Analytics computation failed for user={} gw={}", userId, gwId, err);
      });
  }

  private AnalyticsReport buildReport(int userId, int gwId,
                                       JsonObject bootstrap, JsonObject teamPicks,
                                       List<JsonObject> fixtures, Span parentSpan) {
    // Parse bootstrap players and teams
    JsonObject bootstrapData = parseRaw(bootstrap);
    JsonArray players = bootstrapData.getJsonArray("elements", new JsonArray());
    JsonArray teams = bootstrapData.getJsonArray("teams", new JsonArray());
    JsonArray events = bootstrapData.getJsonArray("events", new JsonArray());

    Map<Integer, JsonObject> playerInfo = new HashMap<>();
    players.forEach(o -> {
      JsonObject p = (JsonObject) o;
      int pid = p.getInteger("id", -1);
      if (pid > 0) playerInfo.put(pid, p);
    });

    Map<Integer, JsonObject> teamInfo = new HashMap<>();
    teams.forEach(o -> {
      JsonObject t = (JsonObject) o;
      int tid = t.getInteger("id", -1);
      if (tid > 0) teamInfo.put(tid, t);
    });

    // Current GW — prefer the event flagged is_current:true by the FPL API;
    // fall back to minimum unfinished event if bootstrap hasn't been enriched yet.
    int currentGw = gwId > 0 ? gwId :
      events.stream()
        .map(o -> (JsonObject) o)
        .filter(e -> Boolean.TRUE.equals(e.getBoolean("is_current")))
        .mapToInt(e -> e.getInteger("id", 0))
        .findFirst()
        .orElseGet(() ->
          events.stream()
            .map(o -> (JsonObject) o)
            .filter(e -> !e.getBoolean("finished", false))
            .mapToInt(e -> e.getInteger("id", 0))
            .min().orElse(1));

    // Squad player IDs
    List<Integer> squadIds = new ArrayList<>();
    if (teamPicks != null) {
      JsonObject raw = parseRaw(teamPicks);
      JsonArray picks = raw.getJsonArray("picks", new JsonArray());
      picks.forEach(o -> {
        int el = ((JsonObject) o).getInteger("element", -1);
        if (el > 0) squadIds.add(el);
      });
    }

    // Form scores for squad players
    List<FormScore> formScores = formScoreService.compute(
      squadIds, Map.of(), playerInfo); // history map empty; simplified

    // FDR for squad teams
    Set<Integer> squadTeamIds = squadIds.stream()
      .map(id -> playerInfo.getOrDefault(id, new JsonObject()).getInteger("team", 0))
      .filter(id -> id > 0)
      .collect(Collectors.toSet());

    Map<Integer, Double> fdrByTeam = new HashMap<>();
    for (int teamId : squadTeamIds) {
      List<JsonObject> teamFixtures = fixtures.stream()
        .filter(f -> teamId == f.getInteger("homeTeamId", 0) || teamId == f.getInteger("awayTeamId", 0))
        .filter(f -> f.getInteger("eventId", 0) > currentGw)
        .limit(5)
        .collect(Collectors.toList());
      double avgFdr = teamFixtures.isEmpty() ? 3.0 :
        teamFixtures.stream().mapToInt(f ->
          teamId == f.getInteger("homeTeamId", 0)
            ? f.getInteger("teamHDifficulty", 3)
            : f.getInteger("teamADifficulty", 3)
        ).average().orElse(3.0);
      fdrByTeam.put(teamId, avgFdr);
    }

    // Transfer suggestions
    int bankInTenths = teamPicks != null
      ? parseRaw(teamPicks).getJsonObject("entry_history", new JsonObject()).getInteger("bank", 0)
      : 0;
    int transfersAvail = teamPicks != null
      ? parseRaw(teamPicks).getJsonObject("transfers", new JsonObject()).getInteger("limit", 1)
      : 1;

    List<com.fplanalytics.model.TransferSuggestion> transfers = transferService.generate(
      squadIds, formScores, fdrByTeam, playerInfo, bankInTenths, transfersAvail, 5);

    // FDR table for squad teams
    List<AnalyticsReport.FixtureDifficulty> fdrList = fdrService.compute(
      new ArrayList<>(squadTeamIds), fixtures, teamInfo, currentGw, 5);

    // Chip timing
    String activeChip = teamPicks != null
      ? parseRaw(teamPicks).getString("active_chip", null)
      : null;
    Set<String> usedChips = new HashSet<>(); // Would be populated from entry history in full impl
    boolean hasDgw = checkForDgw(fixtures, currentGw);
    int injuries = (int) squadIds.stream()
      .filter(id -> {
        JsonObject p = playerInfo.get(id);
        return p != null && !"a".equals(p.getString("status", "a"));
      }).count();

    com.fplanalytics.model.ChipTimingReport chipReport = chipService.compute(
      activeChip, usedChips, teamPicks != null ? parseRaw(teamPicks) : new JsonObject(),
      formScores, currentGw, hasDgw, injuries);

    // Assemble report
    AnalyticsReport report = new AnalyticsReport();
    report.setUserId(userId);
    report.setGameweekId(currentGw);
    report.setComputedAt(Instant.now());
    report.setTtlExpiresAt(Instant.now().plus(config.getRefresh().getAnalyticsCacheTtlMinutes(), ChronoUnit.MINUTES));
    report.setFormScores(formScores);
    report.setTransferSuggestions(transfers);
    report.setChipTiming(chipReport);
    report.setFixtureDifficulty(fdrList);

    return report;
  }

  private Future<JsonObject> loadBootstrap() {
    return vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.BOOTSTRAP_SNAPSHOTS)
          .put("query", new JsonObject()))
      .map(msg -> msg.body().getJsonObject("result", new JsonObject()));
  }

  private Future<JsonObject> loadTeamPicks(int userId, int gwId) {
    return vertx.eventBus().<JsonObject>request("mongo.findOne",
        new JsonObject()
          .put("collection", CollectionRegistry.TEAM_PICKS)
          .put("query", new JsonObject().put("userId", userId).put("gameweekId", gwId)))
      .map(msg -> msg.body().getJsonObject("result"));
  }

  private Future<List<JsonObject>> loadFixtures() {
    return vertx.eventBus().<JsonObject>request("mongo.find",
        new JsonObject()
          .put("collection", CollectionRegistry.FIXTURES)
          .put("query", new JsonObject()))
      .map(msg -> {
        JsonArray arr = msg.body().getJsonArray("results", new JsonArray());
        List<JsonObject> list = new ArrayList<>();
        arr.forEach(o -> list.add((JsonObject) o));
        return list;
      });
  }

  private JsonObject parseRaw(JsonObject doc) {
    if (doc == null) return new JsonObject();
    String raw = doc.getString("raw");
    if (raw == null) return doc;
    try { return new JsonObject(raw); } catch (Exception e) { return new JsonObject(); }
  }

  private boolean checkForDgw(List<JsonObject> fixtures, int currentGw) {
    // Simplified: check if any team appears twice in next 2 GWs
    Map<Integer, Long> teamCount = new HashMap<>();
    fixtures.stream()
      .filter(f -> f.getInteger("eventId") != null
               && f.getInteger("homeTeamId") != null
               && f.getInteger("awayTeamId") != null)
      .filter(f -> {
        int gw = f.getInteger("eventId", 0);
        return gw > currentGw && gw <= currentGw + 2;
      })
      .forEach(f -> {
        teamCount.merge(f.getInteger("homeTeamId", 0), 1L, Long::sum);
        teamCount.merge(f.getInteger("awayTeamId", 0), 1L, Long::sum);
      });
    return teamCount.values().stream().anyMatch(c -> c >= 2);
  }

  @Override
  public void stop() {
    if (producer != null) producer.close();
  }
}
