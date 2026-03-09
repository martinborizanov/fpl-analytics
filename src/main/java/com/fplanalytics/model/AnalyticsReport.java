package com.fplanalytics.model;

import java.time.Instant;
import java.util.List;

public class AnalyticsReport {

  private int userId;
  private int gameweekId;
  private Instant computedAt;
  private Instant ttlExpiresAt;
  private List<FormScore> formScores;
  private List<TransferSuggestion> transferSuggestions;
  private ChipTimingReport chipTiming;
  private List<FixtureDifficulty> fixtureDifficulty;

  public static class FixtureDifficulty {
    private int teamId;
    private String teamName;
    private String teamShortName;
    private List<FixtureEntry> next5Fixtures;
    private double fdrScore; // average difficulty

    public static class FixtureEntry {
      private int gameweek;
      private String opponent;
      private int difficulty;
      private boolean isHome;
      public int getGameweek() { return gameweek; }
      public void setGameweek(int gameweek) { this.gameweek = gameweek; }
      public String getOpponent() { return opponent; }
      public void setOpponent(String opponent) { this.opponent = opponent; }
      public int getDifficulty() { return difficulty; }
      public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
      public boolean isHome() { return isHome; }
      public void setHome(boolean home) { isHome = home; }
    }

    public int getTeamId() { return teamId; }
    public void setTeamId(int teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getTeamShortName() { return teamShortName; }
    public void setTeamShortName(String teamShortName) { this.teamShortName = teamShortName; }
    public List<FixtureEntry> getNext5Fixtures() { return next5Fixtures; }
    public void setNext5Fixtures(List<FixtureEntry> next5Fixtures) { this.next5Fixtures = next5Fixtures; }
    public double getFdrScore() { return fdrScore; }
    public void setFdrScore(double fdrScore) { this.fdrScore = fdrScore; }
  }

  public int getUserId() { return userId; }
  public void setUserId(int userId) { this.userId = userId; }
  public int getGameweekId() { return gameweekId; }
  public void setGameweekId(int gameweekId) { this.gameweekId = gameweekId; }
  public Instant getComputedAt() { return computedAt; }
  public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
  public Instant getTtlExpiresAt() { return ttlExpiresAt; }
  public void setTtlExpiresAt(Instant ttlExpiresAt) { this.ttlExpiresAt = ttlExpiresAt; }
  public List<FormScore> getFormScores() { return formScores; }
  public void setFormScores(List<FormScore> formScores) { this.formScores = formScores; }
  public List<TransferSuggestion> getTransferSuggestions() { return transferSuggestions; }
  public void setTransferSuggestions(List<TransferSuggestion> transferSuggestions) { this.transferSuggestions = transferSuggestions; }
  public ChipTimingReport getChipTiming() { return chipTiming; }
  public void setChipTiming(ChipTimingReport chipTiming) { this.chipTiming = chipTiming; }
  public List<FixtureDifficulty> getFixtureDifficulty() { return fixtureDifficulty; }
  public void setFixtureDifficulty(List<FixtureDifficulty> fixtureDifficulty) { this.fixtureDifficulty = fixtureDifficulty; }
}
