package com.adaptivereadinggame.model;

/** Identifies which Java-validated path selected a chapter's next difficulty. */
public enum DifficultyDecisionSource {
    JAVA_RULES,
    MODEL,
    JAVA_FALLBACK,
    JAVA_GUARDRAIL
}
