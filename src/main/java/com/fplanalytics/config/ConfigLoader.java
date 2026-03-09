package com.fplanalytics.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class ConfigLoader {

  private static final ObjectMapper MAPPER = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private ConfigLoader() {}

  public static Future<AppConfig> load(Vertx vertx) {
    ConfigStoreOptions fileStore = new ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(new JsonObject().put("path", "config/application.yaml"));

    // Environment variables override YAML (12-factor)
    ConfigStoreOptions envStore = new ConfigStoreOptions()
      .setType("env")
      .setConfig(new JsonObject().put("raw-data", true));

    ConfigRetrieverOptions options = new ConfigRetrieverOptions()
      .addStore(fileStore)
      .addStore(envStore);

    ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

    return retriever.getConfig()
      .map(json -> applyEnvOverrides(json))
      .map(json -> {
        try {
          return MAPPER.convertValue(json.getMap(), AppConfig.class);
        } catch (Exception e) {
          throw new RuntimeException("Failed to deserialise application config", e);
        }
      });
  }

  private static JsonObject applyEnvOverrides(JsonObject config) {
    // Allow flat env vars to override nested config paths
    String mongoUri = System.getenv("MONGO_URI");
    if (mongoUri != null) {
      config.put("mongo", config.getJsonObject("mongo", new JsonObject()).put("uri", mongoUri));
    }
    String kafkaBootstrap = System.getenv("KAFKA_BOOTSTRAP");
    if (kafkaBootstrap != null) {
      config.put("kafka", config.getJsonObject("kafka", new JsonObject()).put("bootstrap", kafkaBootstrap));
    }
    String zipkinEndpoint = System.getenv("ZIPKIN_ENDPOINT");
    if (zipkinEndpoint != null) {
      config.put("zipkin", config.getJsonObject("zipkin", new JsonObject()).put("endpoint", zipkinEndpoint));
    }
    String ollamaUrl = System.getenv("OLLAMA_URL");
    if (ollamaUrl != null) {
      config.put("ollama", config.getJsonObject("ollama", new JsonObject()).put("url", ollamaUrl));
    }
    String ollamaModel = System.getenv("OLLAMA_MODEL");
    if (ollamaModel != null) {
      config.put("ollama", config.getJsonObject("ollama", new JsonObject()).put("model", ollamaModel));
    }
    String serverPort = System.getenv("SERVER_PORT");
    if (serverPort != null) {
      config.put("server", config.getJsonObject("server", new JsonObject()).put("port", Integer.parseInt(serverPort)));
    }
    return config;
  }
}
