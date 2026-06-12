package ru.iopump.qa.allure.service;

/**
 * Raised when an admin mutation (delete, block, unblock, grant/revoke admin,
 * reset password) targets a user id that no longer resolves to a persisted row —
 * e.g. a stale id from a concurrently deleted user. Carries a human-safe message
 * so the web layer can surface it directly as a flash error without leaking
 * internal state. Replaces the former bare {@link IllegalArgumentException} so the
 * web exception advice can translate not-found distinctly from genuine 500s.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
