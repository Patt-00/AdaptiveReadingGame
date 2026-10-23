package com.adaptivereadinggame.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Java owns scoring, history selection, validation, safeguards, and final difficulty. */
public final class DifficultyService {
    public enum Level { EASY, MEDIUM, HARD }
    public enum Action { INCREASE, KEEP, DECREASE }

    public record Section(Level difficulty, int correct, int total) {
        public Section {
            Objects.requireNonNull(difficulty);
            if (total < 1 || total > 100 || correct < 0 || correct > total)
                throw new IllegalArgumentException("Require 1 <= total <= 100 and 0 <= correct <= total");
        }

        double accuracyPercent() { return 100.0 * correct / total; }
    }

    public record Summary(Level currentDifficulty, List<Double> recentAccuracyPercent,
                          double averageAccuracyPercent, String trend) {
        public Summary { recentAccuracyPercent = List.copyOf(recentAccuracyPercent); }
    }

    public record Decision(Level nextDifficulty, Action suggestedAction, Action appliedAction,
                           String source, String note, Summary summary) {}

    public record Prompt(String instruction, String input) {}

    @FunctionalInterface
    public interface ModelTool {
        Action infer(Prompt prompt) throws Exception;
    }

    private final ModelTool model;

    public DifficultyService(ModelTool model) { this.model = Objects.requireNonNull(model); }

    public Summary summarize(Level current, List<Section> chronologicalHistory) {
        Objects.requireNonNull(current);
        Objects.requireNonNull(chronologicalHistory);
        if (chronologicalHistory.size() > 30)
            throw new IllegalArgumentException("History is limited to 30 sections");
        List<Double> recent = new ArrayList<>();
        for (Section section : chronologicalHistory) {
            Objects.requireNonNull(section);
            if (section.difficulty() != current) {
                recent.clear();
            } else {
                recent.add(section.accuracyPercent());
                if (recent.size() > 3) recent.removeFirst();
            }
        }
        double mean = recent.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double change = recent.size() < 2 ? 0 : recent.getLast() - recent.getFirst();
        String trend = change > 10.000001 ? "improving"
                : change < -10.000001 ? "declining" : "steady";
        return new Summary(current, recent, mean, trend);
    }

    public Decision decide(Level current, List<Section> chronologicalHistory) {
        Summary summary = summarize(current, chronologicalHistory);
        if (summary.recentAccuracyPercent().size() < 3)
            return new Decision(current, Action.KEEP, Action.KEEP, "cold_start",
                    "Complete three consecutive sections at this difficulty.", summary);

        Action suggested;
        String source = "qwen";
        String note = "Model suggestion accepted by Java safeguards.";
        try {
            suggested = Objects.requireNonNull(model.infer(prompt(summary)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            suggested = baseline(summary);
            source = "rules_fallback";
            note = "Model request interrupted; Java baseline used.";
        } catch (Exception unavailable) {
            suggested = baseline(summary);
            source = "rules_fallback";
            note = "Model unavailable or invalid response; Java baseline used ("
                    + unavailable.getClass().getSimpleName() + ").";
        }

        Action applied = suggested;
        if (source.equals("qwen")
                && ((suggested == Action.INCREASE
                    && (summary.averageAccuracyPercent() < 80 || latest(summary) < 80))
                || (suggested == Action.DECREASE
                    && (summary.averageAccuracyPercent() >= 60 || latest(summary) >= 60)))) {
            applied = Action.KEEP;
            note = "Java rejected a suggestion unsupported by recent accuracy.";
        }

        int step = applied == Action.INCREASE ? 1 : applied == Action.DECREASE ? -1 : 0;
        int index = Math.clamp(current.ordinal() + step, 0, Level.values().length - 1);
        Level next = Level.values()[index];
        if (next == current && applied != Action.KEEP) {
            applied = Action.KEEP;
            note = "Java kept the current level at the difficulty boundary.";
        }
        return new Decision(next, suggested, applied, source, note, summary);
    }

    private static double latest(Summary summary) { return summary.recentAccuracyPercent().getLast(); }

    private static Action baseline(Summary summary) {
        double latest = latest(summary);
        return latest >= 80 ? Action.INCREASE : latest < 60 ? Action.DECREASE : Action.KEEP;
    }

    private static Prompt prompt(Summary summary) {
        String instruction = "You recommend the next reading challenge in a prototype game. "
                + "Consider accuracy across the last three completed sections at the same difficulty. "
                + "Consistently strong or clearly improving results support increase. "
                + "Persistent low or declining accuracy supports decrease. Mixed or moderate results support keep. "
                + "Return exactly one word: increase, keep, or decrease. "
                + "Examples: 80%, 80%, 100% -> increase; 60%, 60%, 60% -> keep; 40%, 20%, 20% -> decrease.";
        String percentages = summary.recentAccuracyPercent().stream()
                .map(value -> java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + "%")
                .collect(java.util.stream.Collectors.joining(", "));
        String input = "Current difficulty: " + summary.currentDifficulty().name().toLowerCase(java.util.Locale.ROOT)
                + ". Accuracy, oldest first: " + percentages
                + ". Mean accuracy: " + Math.round(summary.averageAccuracyPercent() * 100) / 100.0
                + "%. Trend: " + summary.trend() + ". Recommendation:";
        return new Prompt(instruction, input);
    }
}
