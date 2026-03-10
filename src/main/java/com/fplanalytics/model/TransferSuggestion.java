package com.fplanalytics.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferSuggestion {

  private PlayerRef transferOut;
  private PlayerRef transferIn;
  private double evScore;
  private double evGain;
  private String reasoning;
  private boolean withinBudget;

  public static class PlayerRef {
    private int playerId;
    private String webName;
    private String teamShortName;
    private int cost; // in 10ths of millions (e.g. 130 = £13.0m)
    private double formScore;
    private double fdrScore;

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public String getWebName() { return webName; }
    public void setWebName(String webName) { this.webName = webName; }
    public String getTeamShortName() { return teamShortName; }
    public void setTeamShortName(String teamShortName) { this.teamShortName = teamShortName; }
    public int getCost() { return cost; }
    public void setCost(int cost) { this.cost = cost; }
    public double getFormScore() { return formScore; }
    public void setFormScore(double formScore) { this.formScore = formScore; }
    public double getFdrScore() { return fdrScore; }
    public void setFdrScore(double fdrScore) { this.fdrScore = fdrScore; }
  }

  public PlayerRef getTransferOut() { return transferOut; }
  public void setTransferOut(PlayerRef transferOut) { this.transferOut = transferOut; }
  public PlayerRef getTransferIn() { return transferIn; }
  public void setTransferIn(PlayerRef transferIn) { this.transferIn = transferIn; }
  public double getEvScore() { return evScore; }
  public void setEvScore(double evScore) { this.evScore = evScore; }
  public double getEvGain() { return evGain; }
  public void setEvGain(double evGain) { this.evGain = evGain; }
  public String getReasoning() { return reasoning; }
  public void setReasoning(String reasoning) { this.reasoning = reasoning; }
  public boolean isWithinBudget() { return withinBudget; }
  public void setWithinBudget(boolean withinBudget) { this.withinBudget = withinBudget; }
}
