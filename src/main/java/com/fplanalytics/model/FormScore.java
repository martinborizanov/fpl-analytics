package com.fplanalytics.model;

import java.util.List;

public class FormScore {
  private int playerId;
  private String webName;
  private String teamShortName;
  private double formScore;
  private List<Integer> last5Points;
  private double rollingAvg;
  private String trend; // "UP", "DOWN", "STABLE"

  public FormScore() {}

  public int getPlayerId() { return playerId; }
  public void setPlayerId(int playerId) { this.playerId = playerId; }
  public String getWebName() { return webName; }
  public void setWebName(String webName) { this.webName = webName; }
  public String getTeamShortName() { return teamShortName; }
  public void setTeamShortName(String teamShortName) { this.teamShortName = teamShortName; }
  public double getFormScore() { return formScore; }
  public void setFormScore(double formScore) { this.formScore = formScore; }
  public List<Integer> getLast5Points() { return last5Points; }
  public void setLast5Points(List<Integer> last5Points) { this.last5Points = last5Points; }
  public double getRollingAvg() { return rollingAvg; }
  public void setRollingAvg(double rollingAvg) { this.rollingAvg = rollingAvg; }
  public String getTrend() { return trend; }
  public void setTrend(String trend) { this.trend = trend; }
}
