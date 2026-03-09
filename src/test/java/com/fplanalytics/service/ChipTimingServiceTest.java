package com.fplanalytics.service;

import com.fplanalytics.model.ChipTimingReport;
import com.fplanalytics.model.FormScore;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChipTimingServiceTest {

  private ChipTimingService service;

  @BeforeEach
  void setUp() {
    service = new ChipTimingService();
  }

  @Test
  void shouldRecommendHoldForAllChipsInNormalCircumstances() {
    List<FormScore> formScores = List.of(makeForm("Salah", 7.0, "STABLE"));
    ChipTimingReport report = service.compute(
      null, Set.of(), new JsonObject(), formScores, 25, false, 0);

    assertThat(report.getWildcard().getRecommendation()).contains("Hold");
    assertThat(report.getFreeHit().getRecommendation()).contains("Hold");
    assertThat(report.getBenchBoost().getRecommendation()).contains("Hold");
  }

  @Test
  void shouldRecommendFreeHitOnDoubleGameweek() {
    List<FormScore> formScores = List.of(makeForm("Haaland", 9.0, "UP"));
    ChipTimingReport report = service.compute(
      null, Set.of(), new JsonObject(), formScores, 28, true, 0);

    assertThat(report.getFreeHit().getScore()).isGreaterThanOrEqualTo(7);
    assertThat(report.getFreeHit().getRecommendation()).containsIgnoringCase("double");
  }

  @Test
  void shouldMarkUsedChipsAsUnavailable() {
    List<FormScore> formScores = List.of(makeForm("Salah", 8.0, "UP"));
    Set<String> usedChips = Set.of("wildcard", "freehit", "3xc", "bboost");
    ChipTimingReport report = service.compute(
      null, usedChips, new JsonObject(), formScores, 25, false, 0);

    assertThat(report.getWildcard().isAvailable()).isFalse();
    assertThat(report.getFreeHit().isAvailable()).isFalse();
    assertThat(report.getTripleCaptain().isAvailable()).isFalse();
    assertThat(report.getBenchBoost().isAvailable()).isFalse();
    assertThat(report.getWildcard().getScore()).isZero();
  }

  @Test
  void shouldRecommendWildcardWithManyInjuries() {
    List<FormScore> formScores = List.of(makeForm("Salah", 4.0, "DOWN"));
    ChipTimingReport report = service.compute(
      null, Set.of(), new JsonObject(), formScores, 22, false, 4);

    assertThat(report.getWildcard().getScore()).isGreaterThanOrEqualTo(4);
  }

  private FormScore makeForm(String name, double score, String trend) {
    FormScore fs = new FormScore();
    fs.setWebName(name);
    fs.setFormScore(score);
    fs.setTrend(trend);
    return fs;
  }
}
