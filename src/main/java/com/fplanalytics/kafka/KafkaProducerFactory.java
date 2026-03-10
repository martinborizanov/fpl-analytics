package com.fplanalytics.kafka;

import com.fplanalytics.config.AppConfig;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;

import java.util.HashMap;
import java.util.Map;

public class KafkaProducerFactory {

  private KafkaProducerFactory() {}

  public static KafkaProducer<String, String> create(Vertx vertx, AppConfig config) {
    Map<String, String> producerConfig = new HashMap<>();
    producerConfig.put("bootstrap.servers", config.getKafka().getBootstrap());
    producerConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerConfig.put("acks", config.getKafka().getProducer().getAcks());
    producerConfig.put("enable.idempotence", "false");
    producerConfig.put("retries", "3");
    producerConfig.put("max.block.ms", "10000");
    producerConfig.put("max.request.size", "10485760");   // 10MB — matches broker KAFKA_MESSAGE_MAX_BYTES
    producerConfig.put("buffer.memory", "33554432");      // 32MB send buffer
    return KafkaProducer.create(vertx, producerConfig);
  }
}
