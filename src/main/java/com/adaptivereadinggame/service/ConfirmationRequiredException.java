package com.adaptivereadinggame.service;

/** The UI must obtain an explicit decision, then retry the same action if confirmed. */
public final class ConfirmationRequiredException extends IllegalStateException {
    public ConfirmationRequiredException(String message) { super(message); }
}
