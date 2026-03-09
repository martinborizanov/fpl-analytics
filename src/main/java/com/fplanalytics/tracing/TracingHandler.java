package com.fplanalytics.tracing;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vert.x Web handler that starts a server span for every inbound HTTP request
 * and stores the active Span in the RoutingContext for downstream use.
 */
public class TracingHandler implements Handler<RoutingContext> {

  private static final Logger log = LoggerFactory.getLogger(TracingHandler.class);
  public static final String SPAN_KEY = "fpl.trace.span";

  private final Tracer tracer;
  private final brave.propagation.Propagation.Getter<io.vertx.core.MultiMap, String> getter =
    (carrier, key) -> carrier.get(key);

  public TracingHandler(Tracing tracing) {
    this.tracer = tracing.tracer();
  }

  @Override
  public void handle(RoutingContext ctx) {
    try {
      brave.propagation.Extractor<io.vertx.core.MultiMap> extractor =
        tracing().propagation().extractor(getter);

      TraceContextOrSamplingFlags extracted = extractor.extract(ctx.request().headers());

      Span span = tracer.nextSpan(extracted)
        .name("http.server " + ctx.request().method().name() + " " + ctx.normalizedPath())
        .tag("http.method", ctx.request().method().name())
        .tag("http.url", ctx.request().absoluteURI())
        .kind(Span.Kind.SERVER)
        .start();

      ctx.put(SPAN_KEY, span);

      ctx.addEndHandler(v -> {
        span.tag("http.status_code", String.valueOf(ctx.response().getStatusCode()));
        if (ctx.failure() != null) {
          span.error(ctx.failure());
        }
        span.finish();
      });
    } catch (Exception e) {
      log.warn("Failed to create trace span for request {}", ctx.request().path(), e);
    }
    ctx.next();
  }

  private Tracing tracing() {
    return Tracing.current();
  }
}
