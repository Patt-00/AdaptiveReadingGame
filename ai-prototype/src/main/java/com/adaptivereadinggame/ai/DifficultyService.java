package com.adaptivereadinggame.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** All game policy lives here, not in the Python model tool. */
public final class DifficultyService {
    public enum Level { EASY, MEDIUM, HARD }
    public enum Action { INCREASE, KEEP, DECREASE }
    public record ComprehensionQuestion(int choiceCount, int correctChoiceIndex) {
        public ComprehensionQuestion {
            if (choiceCount < 1 || correctChoiceIndex < 0 || correctChoiceIndex >= choiceCount)
                throw new IllegalArgumentException("Correct choice must exist");
        }
    }
    public record Section(Level difficulty, int correct, int total) {
        public Section {
            Objects.requireNonNull(difficulty);
            if (total < 1 || total > 100 || correct < 0 || correct > total)
                throw new IllegalArgumentException("Require 1 <= total <= 100 and 0 <= correct <= total");
        }
        /** Supply first-attempt answers only; story choices are not comprehension scores. */
        public static Section fromAnswers(Level level, List<Boolean> answers) {
            Objects.requireNonNull(answers);
            if (answers.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Missing answer");
            return new Section(level, (int) answers.stream().filter(Boolean::booleanValue).count(), answers.size());
        }
        /** Java checks selections against the answer key; never send answer keys to Python. */
        public static Section grade(Level level, List<ComprehensionQuestion> questions, List<Integer> firstSelections) {
            Objects.requireNonNull(questions); Objects.requireNonNull(firstSelections);
            if (questions.size() != firstSelections.size()) throw new IllegalArgumentException("One selection per question required");
            int correct = 0;
            for (int i = 0; i < questions.size(); i++) {
                var question = Objects.requireNonNull(questions.get(i));
                int selection = Objects.requireNonNull(firstSelections.get(i));
                if (selection < 0 || selection >= question.choiceCount()) throw new IllegalArgumentException("Invalid selection");
                if (selection == question.correctChoiceIndex()) correct++;
            }
            return new Section(level, correct, questions.size());
        }
        double accuracy() { return 100.0 * correct / total; }
    }
    public record Summary(Level currentDifficulty, List<Double> recentAccuracyPercent,
                          double averageAccuracyPercent, String trend) {
        public Summary { recentAccuracyPercent = List.copyOf(recentAccuracyPercent); }
        double latest() { return recentAccuracyPercent.getLast(); }
    }
    public record Decision(Level nextDifficulty, Action suggestedAction, Action appliedAction,
                           String source, String note, Summary summary) {}
    public record Prompt(String instruction, String input) {}
    @FunctionalInterface public interface ModelTool { Action infer(Prompt prompt) throws Exception; }
    private final ModelTool model;
    public DifficultyService(ModelTool model) { this.model = Objects.requireNonNull(model); }

    public Summary summarize(Level current, List<Section> history) {
        Objects.requireNonNull(current); Objects.requireNonNull(history);
        if (history.size() > 30) throw new IllegalArgumentException("History limited to 30 sections");
        List<Double> values = new ArrayList<>();
        for (Section section : history) {
            Objects.requireNonNull(section);
            if (section.difficulty() != current) values.clear();
            else { values.add(section.accuracy()); if (values.size() > 3) values.removeFirst(); }
        }
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double delta = values.size() < 2 ? 0 : values.getLast() - values.getFirst();
        return new Summary(current, values, average,
                delta > 10.000001 ? "improving" : delta < -10.000001 ? "declining" : "steady");
    }

    public Decision decide(Level current, List<Section> history) {
        Summary summary = summarize(current, history);
        if (summary.recentAccuracyPercent().size() < 3)
            return new Decision(current, Action.KEEP, Action.KEEP, "cold_start",
                    "Complete three consecutive sections at this difficulty.", summary);
        Action suggestion;
        String source = "qwen";
        String note = "Model suggestion accepted by Java safeguards.";
        try { suggestion = Objects.requireNonNull(model.infer(prompt(summary))); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            suggestion = baseline(summary); source = "rules_fallback"; note = "Model request interrupted; Java baseline used.";
        } catch (Exception unavailable) {
            suggestion = baseline(summary); source = "rules_fallback";
            note = "Model unavailable or invalid response; Java baseline used (" + unavailable.getClass().getSimpleName() + ").";
        }
        Action applied = suggestion;
        // Experimental policy, not a validated educational assessment.
        if (source.equals("qwen") &&
                ((suggestion == Action.INCREASE && (summary.averageAccuracyPercent() < 80 || summary.latest() < 80)) ||
                 (suggestion == Action.DECREASE && (summary.averageAccuracyPercent() >= 60 || summary.latest() >= 60)))) {
            applied = Action.KEEP; note = "Java rejected a suggestion unsupported by recent accuracy.";
        }
        int step = applied == Action.INCREASE ? 1 : applied == Action.DECREASE ? -1 : 0;
        int index = Math.clamp(current.ordinal() + step, 0, Level.values().length - 1);
        Level next = Level.values()[index];
        if (next == current && applied != Action.KEEP) {
            applied = Action.KEEP; note = "Java kept the current level at the difficulty boundary.";
        }
        return new Decision(next, suggestion, applied, source, note, summary);
    }
    private Action baseline(Summary summary) {
        return summary.latest() >= 80 ? Action.INCREASE : summary.latest() < 60 ? Action.DECREASE : Action.KEEP;
    }
    private Prompt prompt(Summary s) {
        String instruction = "You recommend the next reading challenge in a prototype game. "
                + "Consider accuracy across the last three completed sections at the same difficulty. "
                + "Consistently strong or clearly improving results support increase. "
                + "Persistent low or declining accuracy supports decrease. Mixed or moderate results support keep. "
                + "Return exactly one word: increase, keep, or decrease. "
                + "Examples: 80%, 80%, 100% -> increase; 60%, 60%, 60% -> keep; 40%, 20%, 20% -> decrease.";
        String percentages = s.recentAccuracyPercent().stream()
                .map(value -> java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + "%")
                .collect(java.util.stream.Collectors.joining(", "));
        String input = "Current difficulty: " + s.currentDifficulty().name().toLowerCase(java.util.Locale.ROOT)
                + ". Accuracy, oldest first: " + percentages
                + ". Mean accuracy: " + Math.round(s.averageAccuracyPercent() * 100) / 100.0
                + "%. Trend: " + s.trend() + ". Recommendation:";
        return new Prompt(instruction, input);
    }
}
