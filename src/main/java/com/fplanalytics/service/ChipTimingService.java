package com.fplanalytics.service;

import com.fplanalytics.model.ChipTimingReport;
import com.fplanalytics.model.FormScore;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ChipTimingService {

  /**
   * Scores each chip 0-10 and generates a recommendation.
   *
   * @param activeChip     Currently active chip name (null if none)
   * @param usedChips      Set of already-used chip names
   * @param teamPicks      Current team picks JsonObject
   * @param formScores     Form scores for all squad players
   * @param currentGw      Current gameweek number
   * @param hasDoubleGw    Whether any of the upcoming 2 GWs is a double gameweek
   * @param injuryCount    Number of players in squad with injury/doubtful status
   */
  public ChipTimingReport compute(
      String activeChip,
      Set<String> usedChips,
      JsonObject teamPicks,
      List<FormScore> formScores,
      int currentGw,
      boolean hasDoubleGw,
      int injuryCount) {

    ChipTimingReport report = new ChipTimingReport();

    report.setWildcard(computeWildcard(usedChips, formScores, injuryCount, currentGw));
    report.setFreeHit(computeFreeHit(usedChips, hasDoubleGw, currentGw));
    report.setTripleCaptain(computeTripleCaptain(usedChips, hasDoubleGw, formScores));
    report.setBenchBoost(computeBenchBoost(usedChips, hasDoubleGw, teamPicks));

    return report;
  }

  private ChipTimingReport.ChipAdvice computeWildcard(Set<String> usedChips, List<FormScore> formScores,
                                                       int injuryCount, int currentGw) {
    ChipTimingReport.ChipAdvice advice = new ChipTimingReport.ChipAdvice();
    advice.setChipName("Wildcard");

    boolean available = !usedChips.contains("wildcard");
    advice.setAvailable(available);

    if (!available) {
      advice.setScore(0);
      advice.setRecommendation("Already used");
      advice.setRationale("Wildcard has been used this half of the season.");
      return advice;
    }

    // Score factors: injuries, poor form players, budget flexibility
    long poorFormCount = formScores.stream().filter(f -> f.getFormScore() < 3.0).count();
    int score = 0;
    if (injuryCount >= 3) score += 4;
    else if (injuryCount >= 2) score += 2;
    if (poorFormCount >= 4) score += 3;
    else if (poorFormCount >= 2) score += 2;
    if (currentGw >= 20 && currentGw <= 22) score += 2; // Second wildcard window opening
    score = Math.min(score, 10);

    advice.setScore(score);
    if (score >= 7) {
      advice.setRecommendation("Play now");
      advice.setRationale(String.format(
        "Strong case: %d injuries, %d players in poor form. Wildcard value is high.",
        injuryCount, poorFormCount));
    } else if (score >= 4) {
      advice.setRecommendation("Consider soon");
      advice.setRationale(String.format(
        "Moderate case: %d injuries, %d poor-form players. Monitor over next 2 GWs.",
        injuryCount, poorFormCount));
    } else {
      advice.setRecommendation("Hold");
      advice.setRationale("Squad is in reasonable shape. Save for a more critical moment.");
    }
    return advice;
  }

  private ChipTimingReport.ChipAdvice computeFreeHit(Set<String> usedChips, boolean hasDoubleGw, int currentGw) {
    ChipTimingReport.ChipAdvice advice = new ChipTimingReport.ChipAdvice();
    advice.setChipName("Free Hit");
    boolean available = !usedChips.contains("freehit");
    advice.setAvailable(available);

    if (!available) {
      advice.setScore(0);
      advice.setRecommendation("Already used");
      advice.setRationale("Free Hit has already been played this season.");
      return advice;
    }

    int score = hasDoubleGw ? 8 : 1;
    advice.setScore(score);
    if (hasDoubleGw) {
      advice.setRecommendation("Play now — Double Gameweek");
      advice.setRationale("A Double Gameweek is upcoming. Free Hit is ideal here to field 11 players from DGW teams.");
    } else {
      advice.setRecommendation("Hold");
      advice.setRationale("Free Hit is best saved for a Double Gameweek or a Blank Gameweek. No DGW detected soon.");
    }
    return advice;
  }

  private ChipTimingReport.ChipAdvice computeTripleCaptain(Set<String> usedChips, boolean hasDoubleGw,
                                                            List<FormScore> formScores) {
    ChipTimingReport.ChipAdvice advice = new ChipTimingReport.ChipAdvice();
    advice.setChipName("Triple Captain");
    boolean available = !usedChips.contains("3xc");
    advice.setAvailable(available);

    if (!available) {
      advice.setScore(0);
      advice.setRecommendation("Already used");
      advice.setRationale("Triple Captain has already been played this season.");
      return advice;
    }

    // Best captain candidate: highest form + easy fixture
    FormScore topPlayer = formScores.isEmpty() ? null : formScores.get(0);
    int score = 0;
    if (hasDoubleGw) score += 5;
    if (topPlayer != null && topPlayer.getFormScore() > 8) score += 3;
    if (topPlayer != null && "UP".equals(topPlayer.getTrend())) score += 2;
    score = Math.min(score, 10);

    advice.setScore(score);
    String captainName = topPlayer != null ? topPlayer.getWebName() : "your best player";
    if (score >= 7) {
      advice.setRecommendation("Play now");
      advice.setRationale(String.format(
        "Excellent conditions: DGW detected and %s is in outstanding form (%.1f). High upside for TC.",
        captainName, topPlayer != null ? topPlayer.getFormScore() : 0));
    } else if (score >= 4) {
      advice.setRecommendation("Consider soon");
      advice.setRationale(String.format("%s is in good form. Wait for a DGW if possible.", captainName));
    } else {
      advice.setRecommendation("Hold");
      advice.setRationale("Save Triple Captain for a Double Gameweek with a premium in great form.");
    }
    return advice;
  }

  private ChipTimingReport.ChipAdvice computeBenchBoost(Set<String> usedChips, boolean hasDoubleGw,
                                                         JsonObject teamPicks) {
    ChipTimingReport.ChipAdvice advice = new ChipTimingReport.ChipAdvice();
    advice.setChipName("Bench Boost");
    boolean available = !usedChips.contains("bboost");
    advice.setAvailable(available);

    if (!available) {
      advice.setScore(0);
      advice.setRecommendation("Already used");
      advice.setRationale("Bench Boost has already been played this season.");
      return advice;
    }

    int score = hasDoubleGw ? 6 : 1;
    advice.setScore(score);
    if (hasDoubleGw) {
      advice.setRecommendation("Consider — Double Gameweek");
      advice.setRationale("DGW is upcoming. Ensure bench players also have double fixtures before activating.");
    } else {
      advice.setRecommendation("Hold");
      advice.setRationale("Bench Boost is most powerful in Double Gameweeks when bench players also have two fixtures.");
    }
    return advice;
  }
}
