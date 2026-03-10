package com.fplanalytics.verticle;

import com.fplanalytics.SharedContext;
import com.fplanalytics.config.AppConfig;
import com.fplanalytics.SharedContext;
import com.fplanalytics.mongo.CollectionRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import io.vertx.ext.mongo.UpdateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wraps the MongoDB client. All database operations go through this verticle
 * via event bus request/reply, keeping the client lifecycle in one place.
 *
 * Event bus addresses:
 *  mongo.upsert       → { collection, filter, document } → { result: "ok" }
 *  mongo.find         → { collection, query }            → { results: [...] }
 *  mongo.findOne      → { collection, query }            → { result: {...} }
 *  mongo.delete       → { collection, filter }           → { result: "ok" }
 */
public class MongoVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(MongoVerticle.class);

  private MongoClient mongoClient;

  @Override
  public void start(Promise<Void> startPromise) {
    AppConfig config = SharedContext.getConfig();

    JsonObject mongoConfig = new JsonObject()
      .put("connection_string", config.getMongo().getUri())
      .put("db_name", config.getMongo().getDatabase());

    mongoClient = MongoClient.createShared(vertx, mongoConfig);

    // Verify connection
    mongoClient.runCommand("ping", new JsonObject().put("ping", 1))
      .onSuccess(result -> {
        log.info("MongoDB connected to {}", config.getMongo().getUri());
        registerConsumers();
        startPromise.complete();
      })
      .onFailure(err -> {
        log.error("MongoDB connection failed", err);
        startPromise.fail(err);
      });
  }

  private void registerConsumers() {
    // Upsert: replace document matching filter, insert if not found
    vertx.eventBus().<JsonObject>localConsumer("mongo.upsert", msg -> {
      JsonObject body = msg.body();
      String collection = body.getString("collection");
      JsonObject filter = body.getJsonObject("filter");
      JsonObject document = body.getJsonObject("document");

      UpdateOptions opts = new UpdateOptions().setUpsert(true);
      mongoClient.replaceDocumentsWithOptions(collection, filter, document, opts)
        .onSuccess(res -> msg.reply(new JsonObject().put("result", "ok")
          .put("matched", res.getDocMatched())
          .put("modified", res.getDocModified())))
        .onFailure(err -> {
          log.error("mongo.upsert failed on {}: {}", collection, err.getMessage());
          msg.fail(500, err.getMessage());
        });
    });

    // Find all matching documents
    vertx.eventBus().<JsonObject>localConsumer("mongo.find", msg -> {
      JsonObject body = msg.body();
      String collection = body.getString("collection");
      JsonObject query = body.getJsonObject("query", new JsonObject());

      mongoClient.find(collection, query)
        .onSuccess(results -> {
          io.vertx.core.json.JsonArray arr = new io.vertx.core.json.JsonArray(results);
          msg.reply(new JsonObject().put("results", arr));
        })
        .onFailure(err -> {
          log.error("mongo.find failed on {}: {}", collection, err.getMessage());
          msg.fail(500, err.getMessage());
        });
    });

    // Find one document
    vertx.eventBus().<JsonObject>localConsumer("mongo.findOne", msg -> {
      JsonObject body = msg.body();
      String collection = body.getString("collection");
      JsonObject query = body.getJsonObject("query", new JsonObject());

      mongoClient.findOne(collection, query, null)
        .onSuccess(result -> {
          JsonObject reply = new JsonObject();
          if (result != null) reply.put("result", result);
          msg.reply(reply);
        })
        .onFailure(err -> {
          log.error("mongo.findOne failed on {}: {}", collection, err.getMessage());
          msg.fail(500, err.getMessage());
        });
    });

    // Delete documents
    vertx.eventBus().<JsonObject>localConsumer("mongo.delete", msg -> {
      JsonObject body = msg.body();
      String collection = body.getString("collection");
      JsonObject filter = body.getJsonObject("filter", new JsonObject());

      mongoClient.removeDocuments(collection, filter)
        .onSuccess(res -> msg.reply(new JsonObject().put("result", "ok")))
        .onFailure(err -> {
          log.error("mongo.delete failed on {}: {}", collection, err.getMessage());
          msg.fail(500, err.getMessage());
        });
    });

    log.info("MongoVerticle event bus consumers registered");
  }

  @Override
  public void stop() {
    if (mongoClient != null) {
      mongoClient.close();
    }
  }
}
