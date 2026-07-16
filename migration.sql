-- MANUAL / reference-only operator script. NOT auto-applied at boot: the app has no
-- spring.sql.init wiring and no Flyway/Liquibase, so nothing executes this file
-- automatically. Run the relevant statements by hand against the target database when
-- performing the noted version upgrades. (Versioned migrations via Flyway are a
-- separate, deferred task.)

-- version 2.10.0
-- New column
ALTER TABLE REPORT_ENTITY
    ADD COLUMN BUILD_URL varchar(255) NOT NULL DEFAULT ''

-- version 2.11.0
-- Auth tables (app_user, app_api_token, app_system_settings). Idempotent so it
-- coexists with ddl-auto:update. DEBT: replace ddl-auto:update with a versioned
-- Flyway migration (see .claude/rules/avoid.md #11).
CREATE TABLE IF NOT EXISTS app_user (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    username varchar(128) NOT NULL,
    display_name varchar(128) NOT NULL,
    role varchar(16) NOT NULL,
    created_at timestamp NOT NULL,
    password_hash varchar(128),
    password_temporary boolean NOT NULL DEFAULT false,
    blocked boolean NOT NULL DEFAULT false,
    main_admin boolean NOT NULL DEFAULT false,
    last_login_at timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_username ON app_user (username);

CREATE TABLE IF NOT EXISTS app_api_token (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    user_id uuid NOT NULL REFERENCES app_user (id),
    name varchar(128) NOT NULL,
    token_hash varchar(64) NOT NULL,
    token_lookup varchar(16) NOT NULL,
    created_at timestamp NOT NULL,
    expires_at timestamp,
    last_used_at timestamp,
    revoked_at timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_api_token_hash ON app_api_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_api_token_lookup ON app_api_token (token_lookup);

CREATE TABLE IF NOT EXISTS app_system_settings (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    require_api_auth boolean NOT NULL DEFAULT false,
    updated_at timestamp NOT NULL,
    updated_by_username varchar(128)
);