package com.fplanalytics.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChipTimingReport {

  private ChipAdvice wildcard;
  private ChipAdvice freeHit;
  private ChipAdvice tripleCaptain;
  private ChipAdvice benchBoost;

  public static class ChipAdvice {
    private String chipName;
    private boolean available;
    private int score; // 0-10
    private String recommendation; // "Play now", "Hold", "Consider GW{n}"
    private String rationale;

    public String getChipName() { return chipName; }
    public void setChipName(String chipName) { this.chipName = chipName; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
  }

  public ChipAdvice getWildcard() { return wildcard; }
  public void setWildcard(ChipAdvice wildcard) { this.wildcard = wildcard; }
  public ChipAdvice getFreeHit() { return freeHit; }
  public void setFreeHit(ChipAdvice freeHit) { this.freeHit = freeHit; }
  public ChipAdvice getTripleCaptain() { return tripleCaptain; }
  public void setTripleCaptain(ChipAdvice tripleCaptain) { this.tripleCaptain = tripleCaptain; }
  public ChipAdvice getBenchBoost() { return benchBoost; }
  public void setBenchBoost(ChipAdvice benchBoost) { this.benchBoost = benchBoost; }
}
