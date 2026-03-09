package com.fplanalytics.kafka;

public final class TopicRegistry {

  // Raw FPL API data
  public static final String RAW_BOOTSTRAP = "fpl.raw.bootstrap";
  public static final String RAW_FIXTURES = "fpl.raw.fixtures";
  public static final String RAW_PLAYER_HISTORY = "fpl.raw.player.history";
  public static final String RAW_LEAGUE_STANDINGS = "fpl.raw.league.standings";
  public static final String RAW_TEAM = "fpl.raw.team";
  public static final String RAW_LIVE = "fpl.raw.live";

  // Analytics pipeline
  public static final String ANALYTICS_REQUEST = "fpl.analytics.request";
  public static final String ANALYTICS_COMPUTED = "fpl.analytics.computed";

  // AI advice pipeline
  public static final String OLLAMA_REQUEST = "fpl.ollama.request";
  public static final String OLLAMA_RESPONSE = "fpl.ollama.response";

  private TopicRegistry() {}
}
