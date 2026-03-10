package com.fplanalytics;

import brave.Tracing;
import com.fplanalytics.config.AppConfig;

/**
 * Static holder for application-wide singletons (config, tracing).
 * Avoids Vert.x LocalMap's Shareable restriction while keeping a
 * single initialisation point in MainVerticle.
 */
public final class SharedContext {

  private SharedContext() {}

  private static volatile AppConfig config;
  private static volatile Tracing tracing;

  public static AppConfig getConfig() { return config; }
  public static void setConfig(AppConfig c) { config = c; }

  public static Tracing getTracing() { return tracing; }
  public static void setTracing(Tracing t) { tracing = t; }
}
