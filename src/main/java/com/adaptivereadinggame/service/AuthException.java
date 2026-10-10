package com.adaptivereadinggame.service;

/** A safe authentication error that a controller can turn into a validation message. */
public final class AuthException extends RuntimeException {
    public enum Code { VALIDATION, USERNAME_UNAVAILABLE, INVALID_CREDENTIALS, ALREADY_SIGNED_IN }

    private final Code code;

    public AuthException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    public Code code() { return code; }
}
