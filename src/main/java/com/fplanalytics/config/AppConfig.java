package com.fplanalytics.config;

public class AppConfig {

  private ServerConfig server = new ServerConfig();
  private MongoConfig mongo = new MongoConfig();
  private KafkaConfig kafka = new KafkaConfig();
  private ZipkinConfig zipkin = new ZipkinConfig();
  private OllamaConfig ollama = new OllamaConfig();
  private FplConfig fpl = new FplConfig();
  private RefreshConfig refresh = new RefreshConfig();

  // ---- Nested config classes ----

  public static class ServerConfig {
    private int port = 8080;
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
  }

  public static class MongoConfig {
    private String uri = "mongodb://fpl:fpl_secret@localhost:27017/fpl_analytics?authSource=admin";
    private String database = "fpl_analytics";
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
  }

  public static class KafkaConfig {
    private String bootstrap = "localhost:9092";
    private ConsumerConfig consumer = new ConsumerConfig();
    private ProducerConfig producer = new ProducerConfig();

    public String getBootstrap() { return bootstrap; }
    public void setBootstrap(String bootstrap) { this.bootstrap = bootstrap; }
    public ConsumerConfig getConsumer() { return consumer; }
    public ProducerConfig getProducer() { return producer; }

    public static class ConsumerConfig {
      private String group = "fpl-analytics-consumers";
      private String autoOffsetReset = "earliest";
      private String enableAutoCommit = "true";
      public String getGroup() { return group; }
      public void setGroup(String group) { this.group = group; }
      public String getAutoOffsetReset() { return autoOffsetReset; }
      public void setAutoOffsetReset(String v) { this.autoOffsetReset = v; }
      public String getEnableAutoCommit() { return enableAutoCommit; }
      public void setEnableAutoCommit(String v) { this.enableAutoCommit = v; }
    }

    public static class ProducerConfig {
      private String acks = "1";
      public String getAcks() { return acks; }
      public void setAcks(String acks) { this.acks = acks; }
    }
  }

  public static class ZipkinConfig {
    private String endpoint = "http://localhost:9411/api/v2/spans";
    private String serviceName = "fpl-analytics";
    private float samplingRate = 1.0f;
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public float getSamplingRate() { return samplingRate; }
    public void setSamplingRate(float samplingRate) { this.samplingRate = samplingRate; }
  }

  public static class OllamaConfig {
    private String url = "http://localhost:11434";
    private String model = "mistral:7b-instruct-v0.2";
    private float temperature = 0.3f;
    private int numPredict = 600;
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    public int getNumPredict() { return numPredict; }
    public void setNumPredict(int numPredict) { this.numPredict = numPredict; }
  }

  public static class FplConfig {
    private String apiBase = "https://fantasy.premierleague.com/api";
    private int rateLimitPerMinute = 60;
    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
  }

  public static class RefreshConfig {
    private long bootstrapIntervalMs = 7_200_000L;
    private long fixturesIntervalMs = 86_400_000L;
    private long leagueStandingsIntervalMs = 1_800_000L;
    private long liveScoresIntervalMs = 60_000L;
    private int analyticsCacheTtlMinutes = 15;
    public long getBootstrapIntervalMs() { return bootstrapIntervalMs; }
    public void setBootstrapIntervalMs(long v) { this.bootstrapIntervalMs = v; }
    public long getFixturesIntervalMs() { return fixturesIntervalMs; }
    public void setFixturesIntervalMs(long v) { this.fixturesIntervalMs = v; }
    public long getLeagueStandingsIntervalMs() { return leagueStandingsIntervalMs; }
    public void setLeagueStandingsIntervalMs(long v) { this.leagueStandingsIntervalMs = v; }
    public long getLiveScoresIntervalMs() { return liveScoresIntervalMs; }
    public void setLiveScoresIntervalMs(long v) { this.liveScoresIntervalMs = v; }
    public int getAnalyticsCacheTtlMinutes() { return analyticsCacheTtlMinutes; }
    public void setAnalyticsCacheTtlMinutes(int v) { this.analyticsCacheTtlMinutes = v; }
  }

  // ---- Root getters/setters ----

  public ServerConfig getServer() { return server; }
  public void setServer(ServerConfig server) { this.server = server; }
  public MongoConfig getMongo() { return mongo; }
  public void setMongo(MongoConfig mongo) { this.mongo = mongo; }
  public KafkaConfig getKafka() { return kafka; }
  public void setKafka(KafkaConfig kafka) { this.kafka = kafka; }
  public ZipkinConfig getZipkin() { return zipkin; }
  public void setZipkin(ZipkinConfig zipkin) { this.zipkin = zipkin; }
  public OllamaConfig getOllama() { return ollama; }
  public void setOllama(OllamaConfig ollama) { this.ollama = ollama; }
  public FplConfig getFpl() { return fpl; }
  public void setFpl(FplConfig fpl) { this.fpl = fpl; }
  public RefreshConfig getRefresh() { return refresh; }
  public void setRefresh(RefreshConfig refresh) { this.refresh = refresh; }
}
