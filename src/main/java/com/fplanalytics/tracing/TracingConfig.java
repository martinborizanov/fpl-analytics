package com.fplanalytics.tracing;

import brave.Tracing;
import brave.sampler.Sampler;
import com.fplanalytics.config.AppConfig;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class TracingConfig {

  private TracingConfig() {}

  public static Tracing create(AppConfig config) {
    AppConfig.ZipkinConfig zipkinConfig = config.getZipkin();

    OkHttpSender sender = OkHttpSender.create(zipkinConfig.getEndpoint());
    AsyncReporter<zipkin2.Span> reporter = AsyncReporter.create(sender);

    return Tracing.newBuilder()
      .localServiceName(zipkinConfig.getServiceName())
      .spanReporter(reporter)
      .sampler(Sampler.create(zipkinConfig.getSamplingRate()))
      .build();
  }
}
