package ru.iopump.qa.allure.entity;

/**
 * Role attached to a {@link UserEntity}. Used for display and coarse access
 * gating in the server-rendered UI. No full RBAC — this is a flag, not a
 * permission system.
 */
public enum UserRole {
    GUEST,
    USER,
    ADMIN
}
