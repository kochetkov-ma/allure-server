package ru.iopump.qa.allure.service;

/**
 * Specialised {@link SelfProtectionException} raised when an administrative action
 * targets a reserved system account that the application's invariants depend on —
 * currently the seeded {@code guest} row that backs anonymous access
 * (see {@link ru.iopump.qa.allure.security.CurrentUserProvider#GUEST_USERNAME}).
 * <p>
 * Such accounts must never be deleted, blocked, promoted to administrator, or have
 * their password reset, because the anonymous profile/token flow depends on their
 * exact shape (exists, role {@code GUEST}, no local password).
 */
public class SystemAccountProtectionException extends SelfProtectionException {

    public SystemAccountProtectionException(String message) {
        super(message);
    }
}
