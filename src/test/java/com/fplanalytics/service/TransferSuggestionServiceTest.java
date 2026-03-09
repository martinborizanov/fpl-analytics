package com.fplanalytics.service;

import com.fplanalytics.model.FormScore;
import com.fplanalytics.model.TransferSuggestion;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSuggestionServiceTest {

  private TransferSuggestionService service;

  @BeforeEach
  void setUp() {
    service = new TransferSuggestionService();
  }

  @Test
  void shouldReturnEmptyListWithNoSquad() {
    List<TransferSuggestion> suggestions = service.generate(
      List.of(), List.of(), Map.of(), Map.of(), 15, 1, 5);
    assertThat(suggestions).isEmpty();
  }

  @Test
  void shouldSuggestHighFormPlayerOverLowFormPlayer() {
    // Squad has player 1 (low form, mid cost)
    JsonObject lowFormPlayer = new JsonObject()
      .put("id", 1).put("web_name", "LowForm")
      .put("team", 5).put("element_type", 3)
      .put("now_cost", 65).put("form", "2.5")
      .put("status", "a").put("chance_of_playing_next_round", 100);

    // Available player 2 (high form, similar cost)
    JsonObject highFormPlayer = new JsonObject()
      .put("id", 2).put("web_name", "HighForm")
      .put("team", 14).put("element_type", 3)
      .put("now_cost", 70).put("form", "9.0")
      .put("status", "a").put("chance_of_playing_next_round", 100);

    FormScore lowFs = new FormScore(); lowFs.setPlayerId(1); lowFs.setFormScore(2.5); lowFs.setWebName("LowForm");
    FormScore highFs = new FormScore(); highFs.setPlayerId(2); highFs.setFormScore(9.0); highFs.setWebName("HighForm");

    Map<Integer, Double> fdrByTeam = Map.of(5, 4.0, 14, 2.0); // Liverpool has easy fixtures
    Map<Integer, JsonObject> allPlayers = Map.of(1, lowFormPlayer, 2, highFormPlayer);

    List<TransferSuggestion> suggestions = service.generate(
      List.of(1), List.of(lowFs, highFs), fdrByTeam, allPlayers, 15, 1, 5);

    // Should suggest transferring out player 1 for player 2
    assertThat(suggestions).isNotEmpty();
    assertThat(suggestions.get(0).getTransferOut().getWebName()).isEqualTo("LowForm");
    assertThat(suggestions.get(0).getTransferIn().getWebName()).isEqualTo("HighForm");
    assertThat(suggestions.get(0).getEvGain()).isPositive();
  }

  @Test
  void shouldNotSuggestTransferOutOfBudget() {
    JsonObject cheapSquadPlayer = new JsonObject()
      .put("id", 1).put("web_name", "Cheap")
      .put("team", 5).put("element_type", 3)
      .put("now_cost", 45).put("form", "2.0")
      .put("status", "a").put("chance_of_playing_next_round", 100);

    JsonObject expensiveTarget = new JsonObject()
      .put("id", 2).put("web_name", "Expensive")
      .put("team", 14).put("element_type", 3)
      .put("now_cost", 130).put("form", "9.5") // £13m — way over budget
      .put("status", "a").put("chance_of_playing_next_round", 100);

    FormScore cheapFs = new FormScore(); cheapFs.setPlayerId(1); cheapFs.setFormScore(2.0);
    FormScore expFs = new FormScore(); expFs.setPlayerId(2); expFs.setFormScore(9.5);

    Map<Integer, JsonObject> allPlayers = Map.of(1, cheapSquadPlayer, 2, expensiveTarget);

    List<TransferSuggestion> suggestions = service.generate(
      List.of(1), List.of(cheapFs, expFs), Map.of(5, 3.0, 14, 2.0),
      allPlayers, 0, 1, 5); // bank = £0

    // Player 2 costs £13m but squad player costs £4.5m + 0 bank = can't afford
    assertThat(suggestions).isEmpty();
  }
}
