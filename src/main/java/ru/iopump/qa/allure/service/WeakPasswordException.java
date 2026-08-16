package ru.iopump.qa.allure.service;

/**
 * Raised when a new password fails the minimum length policy.
 */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
