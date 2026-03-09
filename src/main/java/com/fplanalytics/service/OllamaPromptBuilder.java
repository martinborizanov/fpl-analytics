package com.fplanalytics.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.fplanalytics.model.AnalyticsReport;
import com.fplanalytics.model.ChipTimingReport;
import com.fplanalytics.model.FormScore;
import com.fplanalytics.model.TransferSuggestion;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OllamaPromptBuilder {

  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory("prompts");
  private final String systemPrompt;

  public OllamaPromptBuilder() {
    this.systemPrompt = loadResource("prompts/system-prompt.txt");
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  /**
   * Builds a user prompt based on the focus type and analytics report.
   */
  public String buildPrompt(String focus, AnalyticsReport report) {
    String templateName = switch (focus.toLowerCase()) {
      case "transfers" -> "transfers-prompt.mustache";
      case "chips" -> "chips-prompt.mustache";
      default -> "general-prompt.mustache";
    };

    Map<String, Object> scope = buildScope(focus, report);
    return render(templateName, scope);
  }

  private Map<String, Object> buildScope(String focus, AnalyticsReport report) {
    Map<String, Object> scope = new HashMap<>();
    scope.put("gameweekId", report.getGameweekId());

    // Form scores
    List<Map<String, Object>> formRows = new ArrayList<>();
    List<FormScore> formScores = report.getFormScores() != null ? report.getFormScores() : List.of();
    for (FormScore fs : formScores) {
      Map<String, Object> row = new HashMap<>();
      row.put("webName", fs.getWebName());
      row.put("teamShortName", fs.getTeamShortName());
      row.put("formScore", String.format("%.1f", fs.getFormScore()));
      row.put("trend", fs.getTrend());
      row.put("last5Points", fs.getLast5Points() != null ? fs.getLast5Points().toString() : "[]");
      formRows.add(row);
    }
    scope.put("formScores", formRows);

    // Transfer suggestions
    List<Map<String, Object>> transferRows = new ArrayList<>();
    List<TransferSuggestion> suggestions = report.getTransferSuggestions() != null
      ? report.getTransferSuggestions() : List.of();
    for (int i = 0; i < suggestions.size(); i++) {
      TransferSuggestion ts = suggestions.get(i);
      Map<String, Object> row = new HashMap<>();
      row.put("rank", i + 1);
      Map<String, Object> out = new HashMap<>();
      if (ts.getTransferOut() != null) {
        out.put("webName", ts.getTransferOut().getWebName());
        out.put("formScore", String.format("%.1f", ts.getTransferOut().getFormScore()));
        out.put("fdrScore", String.format("%.1f", ts.getTransferOut().getFdrScore()));
      }
      row.put("transferOut", out);
      Map<String, Object> in = new HashMap<>();
      if (ts.getTransferIn() != null) {
        in.put("webName", ts.getTransferIn().getWebName());
        in.put("formScore", String.format("%.1f", ts.getTransferIn().getFormScore()));
        in.put("fdrScore", String.format("%.1f", ts.getTransferIn().getFdrScore()));
      }
      row.put("transferIn", in);
      row.put("evGain", String.format("%.1f", ts.getEvGain()));
      transferRows.add(row);
    }
    scope.put("transferSuggestions", transferRows);
    scope.put("bankInMillions", "0.5"); // Simplified
    scope.put("transfersAvailable", "1");
    scope.put("transferCostNote", "-4pt per additional transfer");

    // Chips
    ChipTimingReport chipReport = report.getChipTiming();
    List<Map<String, Object>> chips = new ArrayList<>();
    if (chipReport != null) {
      addChip(chips, chipReport.getWildcard());
      addChip(chips, chipReport.getFreeHit());
      addChip(chips, chipReport.getTripleCaptain());
      addChip(chips, chipReport.getBenchBoost());
    }
    scope.put("chips", chips);
    scope.put("wildcardStatus", chipReport != null && chipReport.getWildcard() != null
      ? chipReport.getWildcard().getRecommendation() : "Unknown");
    scope.put("freeHitStatus", chipReport != null && chipReport.getFreeHit() != null
      ? chipReport.getFreeHit().getRecommendation() : "Unknown");
    scope.put("tripleCaptainStatus", chipReport != null && chipReport.getTripleCaptain() != null
      ? chipReport.getTripleCaptain().getRecommendation() : "Unknown");
    scope.put("benchBoostStatus", chipReport != null && chipReport.getBenchBoost() != null
      ? chipReport.getBenchBoost().getRecommendation() : "Unknown");

    // Fixture difficulty
    List<Map<String, Object>> fdrRows = new ArrayList<>();
    if (report.getFixtureDifficulty() != null) {
      for (AnalyticsReport.FixtureDifficulty fd : report.getFixtureDifficulty()) {
        Map<String, Object> row = new HashMap<>();
        row.put("teamName", fd.getTeamName());
        row.put("fdrScore", String.format("%.1f", fd.getFdrScore()));
        fdrRows.add(row);
      }
    }
    scope.put("fixtureDifficulty", fdrRows);
    scope.put("hasDoubleGw", false); // Would be computed in full impl

    return scope;
  }

  private void addChip(List<Map<String, Object>> chips, ChipTimingReport.ChipAdvice advice) {
    if (advice == null) return;
    Map<String, Object> row = new HashMap<>();
    row.put("chipName", advice.getChipName());
    row.put("score", advice.getScore());
    row.put("recommendation", advice.getRecommendation());
    row.put("rationale", advice.getRationale());
    chips.add(row);
  }

  private String render(String templateName, Map<String, Object> scope) {
    try {
      Mustache mustache = mustacheFactory.compile(templateName);
      StringWriter writer = new StringWriter();
      mustache.execute(writer, scope).flush();
      return writer.toString();
    } catch (IOException e) {
      throw new RuntimeException("Failed to render prompt template: " + templateName, e);
    }
  }

  private String loadResource(String path) {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
      if (is == null) return "You are an expert FPL analyst.";
      return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      return "You are an expert FPL analyst.";
    }
  }
}
