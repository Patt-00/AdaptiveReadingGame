package com.adaptivereadinggame.repository.mysql;

/** Storage failure without exposing connection settings or SQL parameters to a screen. */
public final class RepositoryException extends RuntimeException {
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
