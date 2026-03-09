package com.fplanalytics.service;

import com.fplanalytics.model.AnalyticsReport;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FixtureDifficultyService {

  /**
   * Builds fixture difficulty entries for the given team IDs over the next N gameweeks.
   *
   * @param teamIds       Set of team IDs to analyse
   * @param fixtures      All fixture documents from MongoDB
   * @param teamInfo      Map of teamId → team info (name, shortName)
   * @param currentGw     Current gameweek number
   * @param lookAheadGws  How many gameweeks ahead to analyse
   */
  public List<AnalyticsReport.FixtureDifficulty> compute(
      List<Integer> teamIds,
      List<JsonObject> fixtures,
      Map<Integer, JsonObject> teamInfo,
      int currentGw,
      int lookAheadGws) {

    List<AnalyticsReport.FixtureDifficulty> results = new ArrayList<>();

    for (int teamId : teamIds) {
      AnalyticsReport.FixtureDifficulty fd = new AnalyticsReport.FixtureDifficulty();
      JsonObject info = teamInfo.getOrDefault(teamId, new JsonObject());
      fd.setTeamId(teamId);
      fd.setTeamName(info.getString("name", "Unknown"));
      fd.setTeamShortName(info.getString("short_name", "???"));

      List<AnalyticsReport.FixtureDifficulty.FixtureEntry> upcoming = fixtures.stream()
        .filter(f -> {
          int gw = f.getInteger("eventId", 0);
          return gw > currentGw && gw <= currentGw + lookAheadGws
            && (f.getInteger("homeTeamId") == teamId || f.getInteger("awayTeamId") == teamId);
        })
        .limit(lookAheadGws)
        .map(f -> {
          boolean isHome = f.getInteger("homeTeamId") == teamId;
          int opponentId = isHome ? f.getInteger("awayTeamId") : f.getInteger("homeTeamId");
          int difficulty = isHome ? f.getInteger("teamHDifficulty", 3) : f.getInteger("teamADifficulty", 3);
          JsonObject oppInfo = teamInfo.getOrDefault(opponentId, new JsonObject());

          AnalyticsReport.FixtureDifficulty.FixtureEntry entry = new AnalyticsReport.FixtureDifficulty.FixtureEntry();
          entry.setGameweek(f.getInteger("eventId", 0));
          entry.setOpponent(oppInfo.getString("short_name", "???"));
          entry.setDifficulty(difficulty);
          entry.setHome(isHome);
          return entry;
        })
        .collect(Collectors.toList());

      fd.setNext5Fixtures(upcoming);

      double avgFdr = upcoming.isEmpty() ? 3.0 :
        upcoming.stream().mapToInt(AnalyticsReport.FixtureDifficulty.FixtureEntry::getDifficulty).average().orElse(3.0);
      fd.setFdrScore(Math.round(avgFdr * 100.0) / 100.0);

      results.add(fd);
    }

    // Sort by FDR ascending (easiest fixtures first)
    results.sort((a, b) -> Double.compare(a.getFdrScore(), b.getFdrScore()));
    return results;
  }
}
