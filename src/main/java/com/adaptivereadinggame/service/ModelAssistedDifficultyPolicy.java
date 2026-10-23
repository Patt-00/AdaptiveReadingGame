package com.adaptivereadinggame.service;

import com.adaptivereadinggame.ai.DifficultyService;
import com.adaptivereadinggame.ai.QwenClient;
import com.adaptivereadinggame.model.AssessmentResult;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.DifficultyDecisionSource;
import com.adaptivereadinggame.repository.AssessmentRepository;

import java.util.ArrayList;
import java.util.Objects;

/** Supplies chronological numeric history to the model helper and returns Java's final choice. */
public final class ModelAssistedDifficultyPolicy implements DifficultyPolicy {
    private final AssessmentRepository assessments;
    private final DifficultyService recommendations;

    public ModelAssistedDifficultyPolicy(AssessmentRepository assessments, DifficultyService.ModelTool model) {
        this.assessments = Objects.requireNonNull(assessments);
        this.recommendations = new DifficultyService(Objects.requireNonNull(model));
    }

    public static ModelAssistedDifficultyPolicy local(AssessmentRepository assessments) {
        return new ModelAssistedDifficultyPolicy(assessments, QwenClient.fromEnvironment());
    }

    @Override public Difficulty nextDifficulty(Context context) {
        return recommend(context).nextDifficulty();
    }

    @Override public Recommendation recommend(Context context) {
        Objects.requireNonNull(context);
        var history = new ArrayList<DifficultyService.Section>();
        for (AssessmentResult result : assessments.historyFor(context.studentId())) {
            history.add(section(result.assessedDifficulty(), result.attempt().correctAnswers(),
                    result.attempt().totalQuestions()));
        }
        history.add(section(context.current(), context.correct(), context.total()));
        DifficultyService.Level current = DifficultyService.Level.valueOf(context.current().name());
        DifficultyService.Decision decision = recommendations.decide(current, history);
        DifficultyDecisionSource source = switch (decision.source()) {
            case "qwen" -> decision.suggestedAction() == decision.appliedAction()
                    ? DifficultyDecisionSource.MODEL : DifficultyDecisionSource.JAVA_GUARDRAIL;
            case "rules_fallback" -> DifficultyDecisionSource.JAVA_FALLBACK;
            default -> DifficultyDecisionSource.JAVA_RULES;
        };
        return new Recommendation(Difficulty.valueOf(decision.nextDifficulty().name()), source);
    }

    private static DifficultyService.Section section(Difficulty difficulty, int correct, int total) {
        return new DifficultyService.Section(DifficultyService.Level.valueOf(difficulty.name()), correct, total);
    }
}
