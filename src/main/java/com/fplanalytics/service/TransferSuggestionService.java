package com.fplanalytics.service;

import com.fplanalytics.model.FormScore;
import com.fplanalytics.model.TransferSuggestion;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

public class TransferSuggestionService {

  private static final int TRANSFER_COST_POINTS = 4;

  /**
   * Generates EV-based transfer suggestions.
   *
   * EV formula: formScore × (6 - avgFDR) × minutesRisk
   *
   * @param squadPlayerIds  Player IDs in the current squad
   * @param formScores      Form scores for all players (squad + candidates)
   * @param fdrByTeam       Map teamId → average FDR score for next 5 GWs
   * @param playerInfo      Map playerId → player bootstrap info
   * @param bankInTenths    Bank value in tenths of millions (e.g. 15 = £1.5m)
   * @param transfersAvail  Number of free transfers available
   * @param limit           Max suggestions to return
   */
  public List<TransferSuggestion> generate(
      List<Integer> squadPlayerIds,
      List<FormScore> formScores,
      Map<Integer, Double> fdrByTeam,
      Map<Integer, JsonObject> playerInfo,
      int bankInTenths,
      int transfersAvail,
      int limit) {

    Map<Integer, FormScore> formByPlayerId = formScores.stream()
      .collect(Collectors.toMap(FormScore::getPlayerId, f -> f));

    // Compute EV for all players in bootstrap
    Map<Integer, Double> evByPlayer = playerInfo.entrySet().stream()
      .filter(e -> isActive(e.getValue()))
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> computeEv(e.getValue(), formByPlayerId, fdrByTeam)
      ));

    List<TransferSuggestion> suggestions = new ArrayList<>();

    // For each player in squad, find the best replacement with positive EV gain
    for (int outId : squadPlayerIds) {
      JsonObject outInfo = playerInfo.get(outId);
      if (outInfo == null) continue;

      int outCost = outInfo.getInteger("now_cost", 0);
      double outEv = evByPlayer.getOrDefault(outId, 0.0);
      int elementType = outInfo.getInteger("element_type", 0);

      // Budget: selling price (simplified as now_cost) + bank
      int maxSpend = outCost + bankInTenths;

      // Find best in-player of same position within budget with highest EV
      playerInfo.entrySet().stream()
        .filter(e -> !squadPlayerIds.contains(e.getKey()))
        .filter(e -> e.getValue().getInteger("element_type", 0) == elementType)
        .filter(e -> e.getValue().getInteger("now_cost", 0) <= maxSpend)
        .filter(e -> isActive(e.getValue()))
        .max(Comparator.comparingDouble(e -> evByPlayer.getOrDefault(e.getKey(), 0.0)))
        .ifPresent(best -> {
          int inId = best.getKey();
          double inEv = evByPlayer.getOrDefault(inId, 0.0);
          double evGain = inEv - outEv - (transfersAvail > 0 ? 0 : TRANSFER_COST_POINTS);

          if (evGain > 0.5) { // Only suggest if meaningful gain
            TransferSuggestion ts = new TransferSuggestion();

            TransferSuggestion.PlayerRef out = buildRef(outId, outInfo, outEv, fdrByTeam, playerInfo);
            TransferSuggestion.PlayerRef in = buildRef(inId, best.getValue(), inEv, fdrByTeam, playerInfo);

            ts.setTransferOut(out);
            ts.setTransferIn(in);
            ts.setEvScore(Math.round(inEv * 100.0) / 100.0);
            ts.setEvGain(Math.round(evGain * 100.0) / 100.0);
            ts.setWithinBudget(best.getValue().getInteger("now_cost", 0) <= maxSpend);
            ts.setReasoning(buildReasoning(out, in, evGain, transfersAvail));

            suggestions.add(ts);
          }
        });
    }

    return suggestions.stream()
      .sorted(Comparator.comparingDouble(TransferSuggestion::getEvGain).reversed())
      .limit(limit)
      .collect(Collectors.toList());
  }

  private double computeEv(JsonObject player, Map<Integer, FormScore> formByPlayerId, Map<Integer, Double> fdrByTeam) {
    int playerId = player.getInteger("id", 0);
    int teamId = player.getInteger("team", 0);

    FormScore form = formByPlayerId.get(playerId);
    double formScore = form != null ? form.getFormScore() :
      parseDouble(player.getString("form", "0"));

    double fdr = fdrByTeam.getOrDefault(teamId, 3.0);
    double minutesRisk = player.getInteger("chance_of_playing_next_round", 100) / 100.0;

    // EV = formScore × (6 - avgFDR) × minutesRisk
    // (6 - fdr) makes easy fixtures more valuable; fdr range is 2-5
    return formScore * (6.0 - fdr) * minutesRisk;
  }

  private boolean isActive(JsonObject player) {
    String status = player.getString("status", "a");
    int chanceOfPlaying = player.getInteger("chance_of_playing_next_round", 100);
    return ("a".equals(status) || "d".equals(status)) && chanceOfPlaying > 25;
  }

  private TransferSuggestion.PlayerRef buildRef(
      int playerId, JsonObject info, double ev,
      Map<Integer, Double> fdrByTeam, Map<Integer, JsonObject> allInfo) {

    TransferSuggestion.PlayerRef ref = new TransferSuggestion.PlayerRef();
    ref.setPlayerId(playerId);
    ref.setWebName(info.getString("web_name", "Unknown"));
    ref.setCost(info.getInteger("now_cost", 0));
    ref.setFormScore(parseDouble(info.getString("form", "0")));
    ref.setFdrScore(fdrByTeam.getOrDefault(info.getInteger("team", 0), 3.0));

    // Team short name from team info if available
    ref.setTeamShortName(info.getString("team_short_name", ""));
    return ref;
  }

  private String buildReasoning(TransferSuggestion.PlayerRef out, TransferSuggestion.PlayerRef in,
                                double evGain, int transfersAvail) {
    String costStr = transfersAvail > 0 ? "free transfer" : "(-4pt cost)";
    return String.format("%s → %s (%s): EV gain %.1f. Form %.1f→%.1f, FDR %.1f→%.1f",
      out.getWebName(), in.getWebName(), costStr,
      evGain, out.getFormScore(), in.getFormScore(),
      out.getFdrScore(), in.getFdrScore());
  }

  private double parseDouble(String s) {
    try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
  }
}
