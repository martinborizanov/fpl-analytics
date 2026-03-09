package com.fplanalytics.kafka;

import io.vertx.core.json.JsonObject;

/**
 * Wrapper for Kafka messages that includes B3 tracing headers.
 */
public class MessageEnvelope {

  private String traceId;
  private String spanId;
  private String parentSpanId;
  private String payload;

  public MessageEnvelope() {}

  public MessageEnvelope(String payload, String traceId, String spanId, String parentSpanId) {
    this.payload = payload;
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
  }

  public static MessageEnvelope of(String payload) {
    return new MessageEnvelope(payload, null, null, null);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put("payload", payload);
    if (traceId != null) json.put("traceId", traceId);
    if (spanId != null) json.put("spanId", spanId);
    if (parentSpanId != null) json.put("parentSpanId", parentSpanId);
    return json;
  }

  public static MessageEnvelope fromJson(JsonObject json) {
    MessageEnvelope env = new MessageEnvelope();
    env.setPayload(json.getString("payload"));
    env.setTraceId(json.getString("traceId"));
    env.setSpanId(json.getString("spanId"));
    env.setParentSpanId(json.getString("parentSpanId"));
    return env;
  }

  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public String getSpanId() { return spanId; }
  public void setSpanId(String spanId) { this.spanId = spanId; }
  public String getParentSpanId() { return parentSpanId; }
  public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
}
