package com.fplanalytics.service;

import com.fplanalytics.model.FormScore;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormScoreServiceTest {

  private FormScoreService service;

  @BeforeEach
  void setUp() {
    service = new FormScoreService();
  }

  @Test
  void shouldReturnEmptyListForNoPlayers() {
    List<FormScore> scores = service.compute(List.of(), Map.of(), Map.of());
    assertThat(scores).isEmpty();
  }

  @Test
  void shouldComputeFormScoreFromFplApiForm() {
    JsonObject playerInfo = new JsonObject()
      .put("web_name", "Salah")
      .put("form", "9.5")
      .put("team", 14);

    List<FormScore> scores = service.compute(
      List.of(302),
      Map.of(), // no history
      Map.of(302, playerInfo)
    );

    assertThat(scores).hasSize(1);
    FormScore fs = scores.get(0);
    assertThat(fs.getWebName()).isEqualTo("Salah");
    assertThat(fs.getFormScore()).isGreaterThan(0);
  }

  @Test
  void shouldDetectUpwardTrend() {
    JsonObject playerInfo = new JsonObject()
      .put("web_name", "Haaland")
      .put("form", "8.0")
      .put("team", 11);

    // History with improving last 2 GWs
    JsonArray history = new JsonArray()
      .add(new JsonObject().put("total_points", 3).put("round", 23))
      .add(new JsonObject().put("total_points", 4).put("round", 24))
      .add(new JsonObject().put("total_points", 5).put("round", 25))
      .add(new JsonObject().put("total_points", 12).put("round", 26))
      .add(new JsonObject().put("total_points", 14).put("round", 27));

    JsonObject historyDoc = new JsonObject()
      .put("raw", new JsonObject().put("history", history).encode());

    List<FormScore> scores = service.compute(
      List.of(355),
      Map.of(355, historyDoc),
      Map.of(355, playerInfo)
    );

    assertThat(scores).hasSize(1);
    assertThat(scores.get(0).getTrend()).isEqualTo("UP");
    assertThat(scores.get(0).getLast5Points()).containsExactly(3, 4, 5, 12, 14);
  }

  @Test
  void shouldSortByFormScoreDescending() {
    JsonObject playerA = new JsonObject().put("web_name", "PlayerA").put("form", "3.0").put("team", 1);
    JsonObject playerB = new JsonObject().put("web_name", "PlayerB").put("form", "9.0").put("team", 2);
    JsonObject playerC = new JsonObject().put("web_name", "PlayerC").put("form", "6.0").put("team", 3);

    List<FormScore> scores = service.compute(
      List.of(1, 2, 3),
      Map.of(),
      Map.of(1, playerA, 2, playerB, 3, playerC)
    );

    assertThat(scores.get(0).getWebName()).isEqualTo("PlayerB");
    assertThat(scores.get(scores.size() - 1).getWebName()).isEqualTo("PlayerA");
  }
}
