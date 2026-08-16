package ru.iopump.qa.allure.service;

/**
 * Specialised {@link SelfProtectionException} raised when an action is forbidden
 * against the bootstrap main-admin row (delete, revoke admin, reset-password by
 * another actor).
 */
public class MainAdminProtectionException extends SelfProtectionException {

    public MainAdminProtectionException(String message) {
        super(message);
    }
}
