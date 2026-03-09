package com.fplanalytics.verticle;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.kafka.KafkaProducerFactory;
import com.fplanalytics.kafka.TopicRegistry;
import com.fplanalytics.model.AnalyticsReport;
import com.fplanalytics.service.OllamaPromptBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Worker verticle that receives AI advice requests, builds Mustache prompts,
 * calls the Ollama streaming API, and streams tokens back via the event bus
 * for SSE delivery. Full text is also published to Kafka for persistence.
 */
public class OllamaVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(OllamaVerticle.class);

  private AppConfig config;
  private WebClient webClient;
  private KafkaProducer<String, String> producer;
  private final OllamaPromptBuilder promptBuilder = new OllamaPromptBuilder();
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private Tracer tracer;

  @Override
  public void start(Promise<Void> startPromise) {
    config = (AppConfig) vertx.sharedData().getLocalMap("fpl.shared").get("config");
    Tracing tracing = (Tracing) vertx.sharedData().getLocalMap("fpl.shared").get("tracing");
    tracer = tracing.tracer();

    String ollamaUrl = config.getOllama().getUrl();
    URI uri = URI.create(ollamaUrl);

    webClient = WebClient.create(vertx, new WebClientOptions()
      .setDefaultHost(uri.getHost())
      .setDefaultPort(uri.getPort() > 0 ? uri.getPort() : 11434)
      .setConnectTimeout(30_000));

    producer = KafkaProducerFactory.create(vertx, config);

    // Subscribe to ollama.request topic via event bus (KafkaConsumerVerticle relays messages)
    vertx.eventBus().<JsonObject>localConsumer("fpl.ollama.generate", msg -> {
      JsonObject body = msg.body();
      String requestId = body.getString("requestId");
      String focus = body.getString("focus", "general");
      JsonObject reportJson = body.getJsonObject("analyticsReport", new JsonObject());

      try {
        AnalyticsReport report = mapper.convertValue(reportJson.getMap(), AnalyticsReport.class);
        generateAdvice(requestId, body.getInteger("userId", 0), focus, report);
      } catch (Exception e) {
        log.error("Failed to deserialise analytics report for request {}", requestId, e);
      }
    });

    startPromise.complete();
    log.info("OllamaVerticle ready → {}", config.getOllama().getUrl());
  }

  private void generateAdvice(String requestId, int userId, String focus, AnalyticsReport report) {
    Span span = tracer.nextSpan()
      .name("ollama.generate")
      .tag("model.name", config.getOllama().getModel())
      .tag("focus.type", focus)
      .tag("request.id", requestId)
      .start();

    String systemPrompt = promptBuilder.getSystemPrompt();
    String userPrompt = promptBuilder.buildPrompt(focus, report);

    JsonObject requestBody = new JsonObject()
      .put("model", config.getOllama().getModel())
      .put("system", systemPrompt)
      .put("prompt", userPrompt)
      .put("stream", true)
      .put("options", new JsonObject()
        .put("temperature", config.getOllama().getTemperature())
        .put("num_predict", config.getOllama().getNumPredict()));

    AtomicReference<StringBuilder> fullText = new AtomicReference<>(new StringBuilder());

    span.annotate("generation.started");

    webClient.post("/api/generate")
      .putHeader("Content-Type", "application/json")
      .sendBuffer(Buffer.buffer(requestBody.encode()))
      .onSuccess(response -> {
        // Parse streaming response: each line is a JSON object
        String[] lines = response.bodyAsString().split("\n");
        for (String line : lines) {
          if (line.isBlank()) continue;
          try {
            JsonObject chunk = new JsonObject(line);
            String token = chunk.getString("response", "");
            if (!token.isEmpty()) {
              fullText.get().append(token);
              // Publish token to SSE stream
              vertx.eventBus().publish("fpl.advice.token." + requestId,
                new JsonObject().put("token", token).put("requestId", requestId));
            }
            if (chunk.getBoolean("done", false)) {
              span.annotate("generation.complete");
              span.finish();
              publishResponse(requestId, userId, report.getGameweekId(), fullText.get().toString());
            }
          } catch (Exception e) {
            log.warn("Failed to parse Ollama chunk: {}", line);
          }
        }
      })
      .onFailure(err -> {
        span.error(err);
        span.finish();
        log.error("Ollama request failed for requestId {}: {}", requestId, err.getMessage());
        // Publish error to SSE stream
        vertx.eventBus().publish("fpl.advice.token." + requestId,
          new JsonObject().put("error", err.getMessage()).put("requestId", requestId));
      });
  }

  private void publishResponse(String requestId, int userId, int gwId, String adviceText) {
    JsonObject response = new JsonObject()
      .put("requestId", requestId)
      .put("userId", userId)
      .put("gameweekId", gwId)
      .put("generatedAt", Instant.now().toString())
      .put("model", config.getOllama().getModel())
      .put("adviceText", adviceText);

    KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(
      TopicRegistry.OLLAMA_RESPONSE, requestId, response.encode());
    producer.send(record)
      .onSuccess(meta -> log.info("Ollama response published for requestId={}", requestId))
      .onFailure(err -> log.error("Failed to publish Ollama response", err));

    // Signal completion to SSE stream
    vertx.eventBus().publish("fpl.advice.token." + requestId,
      new JsonObject().put("done", true).put("requestId", requestId));
  }

  @Override
  public void stop() {
    if (webClient != null) webClient.close();
    if (producer != null) producer.close();
  }
}
