package ru.iopump.qa.allure.service;

/**
 * Raised when an admin attempts to perform a destructive action on themselves
 * (delete, demote, block) or on the bootstrap main-admin account. Guards against
 * the "admin locks themselves out" footgun.
 */
public class SelfProtectionException extends RuntimeException {

    public SelfProtectionException(String message) {
        super(message);
    }
}
