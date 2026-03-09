package com.fplanalytics.kafka;

import com.fplanalytics.config.AppConfig;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;

import java.util.HashMap;
import java.util.Map;

public class KafkaConsumerFactory {

  private KafkaConsumerFactory() {}

  public static KafkaConsumer<String, String> create(Vertx vertx, AppConfig config) {
    Map<String, String> consumerConfig = new HashMap<>();
    consumerConfig.put("bootstrap.servers", config.getKafka().getBootstrap());
    consumerConfig.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    consumerConfig.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    consumerConfig.put("group.id", config.getKafka().getConsumer().getGroup());
    consumerConfig.put("auto.offset.reset", config.getKafka().getConsumer().getAutoOffsetReset());
    consumerConfig.put("enable.auto.commit", config.getKafka().getConsumer().getEnableAutoCommit());
    consumerConfig.put("session.timeout.ms", "30000");
    consumerConfig.put("max.poll.records", "100");
    return KafkaConsumer.create(vertx, consumerConfig);
  }
}
