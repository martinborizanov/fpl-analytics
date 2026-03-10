package com.fplanalytics.service;

import com.fplanalytics.model.FormScore;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FormScoreService {

  /**
   * Computes form scores for a list of player IDs using their history records.
   *
   * @param playerIds     Player IDs in the user's squad
   * @param historyByPlayer Map of playerId → raw history JsonObject from MongoDB
   * @param playerInfo    Map of playerId → player info from bootstrap
   * @return List of FormScore sorted descending by formScore
   */
  public List<FormScore> compute(
      List<Integer> playerIds,
      Map<Integer, JsonObject> historyByPlayer,
      Map<Integer, JsonObject> playerInfo) {

    List<FormScore> results = new ArrayList<>();

    for (int playerId : playerIds) {
      JsonObject history = historyByPlayer.get(playerId);
      JsonObject info = playerInfo.getOrDefault(playerId, new JsonObject());

      FormScore fs = new FormScore();
      fs.setPlayerId(playerId);
      fs.setWebName(info.getString("web_name", "Unknown"));
      fs.setTeamShortName(info.getString("team_short_name", ""));

      if (history == null) {
        // Fall back to the FPL API's own form field when no history doc is available
        double fplForm = parseDouble(info.getString("form", "0"));
        fs.setFormScore(fplForm);
        fs.setLast5Points(List.of());
        fs.setRollingAvg(0);
        fs.setTrend("STABLE");
        results.add(fs);
        continue;
      }

      JsonArray historyArray = extractHistoryArray(history);
      List<Integer> last5 = extractLast5Points(historyArray);

      double rollingAvg = last5.isEmpty() ? 0 :
        last5.stream().mapToInt(Integer::intValue).average().orElse(0);

      // FPL API form field (last 30 days average)
      double fplForm = parseDouble(info.getString("form", "0"));
      // Blend FPL's own form score with our rolling average
      double blendedForm = (fplForm * 0.6) + (rollingAvg * 0.4);

      fs.setFormScore(Math.round(blendedForm * 100.0) / 100.0);
      fs.setLast5Points(last5);
      fs.setRollingAvg(Math.round(rollingAvg * 100.0) / 100.0);
      fs.setTrend(computeTrend(last5));

      results.add(fs);
    }

    results.sort((a, b) -> Double.compare(b.getFormScore(), a.getFormScore()));
    return results;
  }

  private JsonArray extractHistoryArray(JsonObject history) {
    String raw = history.getString("raw", "{}");
    try {
      JsonObject parsed = new JsonObject(raw);
      return parsed.getJsonArray("history", new JsonArray());
    } catch (Exception e) {
      return new JsonArray();
    }
  }

  private List<Integer> extractLast5Points(JsonArray historyArray) {
    int size = historyArray.size();
    int start = Math.max(0, size - 5);
    List<Integer> last5 = new ArrayList<>();
    for (int i = start; i < size; i++) {
      JsonObject gw = historyArray.getJsonObject(i);
      last5.add(gw.getInteger("total_points", 0));
    }
    return last5;
  }

  private String computeTrend(List<Integer> last5) {
    if (last5.size() < 3) return "STABLE";
    int recent = last5.subList(Math.max(0, last5.size() - 2), last5.size())
      .stream().mapToInt(i -> i).sum();
    int older = last5.subList(0, Math.min(3, last5.size()))
      .stream().mapToInt(i -> i).sum();
    double recentAvg = recent / 2.0;
    double olderAvg = older / 3.0;
    if (recentAvg > olderAvg + 2) return "UP";
    if (recentAvg < olderAvg - 2) return "DOWN";
    return "STABLE";
  }

  private double parseDouble(String s) {
    try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
  }
}
