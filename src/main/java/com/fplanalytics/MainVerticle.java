package com.fplanalytics;

import brave.Tracing;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.config.ConfigLoader;
import com.fplanalytics.tracing.TracingConfig;
import com.fplanalytics.verticle.*;
import io.vertx.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point. Loads config, initialises Zipkin tracing,
 * and deploys all verticles in the correct dependency order.
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle())
      .onSuccess(id -> log.info("FPL Analytics started successfully (deployment: {})", id))
      .onFailure(err -> {
        log.error("Failed to start FPL Analytics", err);
        vertx.close();
      });
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ConfigLoader.load(vertx)
      .compose(config -> {
        // Initialise Zipkin tracing — this must happen before any verticle that traces
        Tracing tracing = TracingConfig.create(config);
        log.info("Zipkin tracing initialised → {}", config.getZipkin().getEndpoint());

        // Store shared objects in vertx context so verticles can retrieve them
        vertx.sharedData().getLocalMap("fpl.shared")
          .put("config", config)
          .put("tracing", tracing);

        return deployAll(config);
      })
      .onSuccess(v -> {
        log.info("All verticles deployed successfully");
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  private Future<Void> deployAll(AppConfig config) {
    // 1. MongoVerticle first — others depend on it
    return deploy(new MongoVerticle(), null)
      // 2. HttpServerVerticle
      .compose(v -> deploy(new HttpServerVerticle(), null))
      // 3. FplApiClientVerticle — 2 worker instances
      .compose(v -> deploy(new FplApiClientVerticle(),
        new DeploymentOptions().setWorker(true).setInstances(2)))
      // 4. DataRefreshVerticle — starts timers
      .compose(v -> deploy(new DataRefreshVerticle(), null))
      // 5. KafkaConsumerVerticle — 2 worker instances
      .compose(v -> deploy(new KafkaConsumerVerticle(),
        new DeploymentOptions().setWorker(true).setInstances(2)))
      // 6. AnalyticsVerticle
      .compose(v -> deploy(new AnalyticsVerticle(), null))
      // 7. OllamaVerticle — worker (LLM calls can be slow)
      .compose(v -> deploy(new OllamaVerticle(),
        new DeploymentOptions().setWorker(true).setInstances(1)))
      .mapEmpty();
  }

  private Future<String> deploy(Verticle verticle, DeploymentOptions options) {
    String name = verticle.getClass().getSimpleName();
    log.info("Deploying {}", name);
    Future<String> future = options != null
      ? vertx.deployVerticle(verticle, options)
      : vertx.deployVerticle(verticle);
    return future
      .onSuccess(id -> log.info("{} deployed (id={})", name, id))
      .onFailure(err -> log.error("Failed to deploy {}", name, err));
  }
}
