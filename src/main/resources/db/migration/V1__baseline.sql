-- Lab Timesheet & Project Management System
-- Review baseline for PostgreSQL 18.4.
--
-- REVIEW REQUIRED — IMPLEMENTATION NOT AUTHORIZED.
-- After approval, the platform owner may promote this file to the initial
-- Flyway migration. It is intentionally not an application migration yet.

BEGIN;

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 1. Singleton installation/bootstrap state.
CREATE TABLE system_state (
    singleton_id smallint PRIMARY KEY DEFAULT 1,
    initialized boolean NOT NULL DEFAULT false,
    initialized_at timestamptz,
    bootstrap_admin_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_state_singleton CHECK (singleton_id = 1),
    CONSTRAINT ck_system_state_initialization CHECK (
        (initialized = false AND initialized_at IS NULL AND bootstrap_admin_id IS NULL)
        OR
        (initialized = true AND initialized_at IS NOT NULL AND bootstrap_admin_id IS NOT NULL)
    ),
    CONSTRAINT ck_system_state_version CHECK (version >= 0)
);

INSERT INTO system_state (singleton_id) VALUES (1);

-- 2. Authentication identity and immutable global role.
CREATE TABLE app_users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email varchar(320) NOT NULL,
    display_name varchar(120) NOT NULL,
    password_hash varchar(255),
    global_role varchar(16) NOT NULL,
    account_status varchar(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    activated_at timestamptz,
    locked_at timestamptz,
    deactivated_at timestamptz,
    last_login_at timestamptz,
    created_by_user_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_app_users_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_app_users_email CHECK (length(btrim(email)) > 3),
    CONSTRAINT ck_app_users_display_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_app_users_global_role CHECK (global_role IN ('ADMIN', 'MENTOR', 'INTERN')),
    CONSTRAINT ck_app_users_account_status CHECK (
        account_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'DEACTIVATED')
    ),
    CONSTRAINT ck_app_users_pending_password CHECK (
        (account_status = 'PENDING_ACTIVATION' AND password_hash IS NULL)
        OR
        (account_status <> 'PENDING_ACTIVATION' AND length(btrim(password_hash)) > 0)
    ),
    CONSTRAINT ck_app_users_activated_state CHECK (
        (account_status = 'PENDING_ACTIVATION' AND activated_at IS NULL)
        OR
        (account_status <> 'PENDING_ACTIVATION' AND activated_at IS NOT NULL)
    ),
    CONSTRAINT ck_app_users_lock_timestamp CHECK (
        (account_status = 'LOCKED' AND locked_at IS NOT NULL)
        OR
        (account_status <> 'LOCKED' AND locked_at IS NULL)
    ),
    CONSTRAINT ck_app_users_deactivation_timestamp CHECK (
        (account_status = 'DEACTIVATED' AND deactivated_at IS NOT NULL)
        OR
        (account_status <> 'DEACTIVATED' AND deactivated_at IS NULL)
    ),
    CONSTRAINT ck_app_users_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_app_users_email_ci ON app_users (lower(btrim(email)));
CREATE INDEX ix_app_users_created_by ON app_users (created_by_user_id);
CREATE INDEX ix_app_users_role_status ON app_users (global_role, account_status);

ALTER TABLE system_state
    ADD CONSTRAINT fk_system_state_bootstrap_admin
    FOREIGN KEY (bootstrap_admin_id) REFERENCES app_users (id) ON DELETE RESTRICT;

CREATE INDEX ix_system_state_bootstrap_admin ON system_state (bootstrap_admin_id);

CREATE FUNCTION prevent_app_user_role_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.global_role IS DISTINCT FROM OLD.global_role THEN
        RAISE EXCEPTION 'global_role is immutable for app_user %', OLD.id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_app_users_immutable_role
BEFORE UPDATE OF global_role ON app_users
FOR EACH ROW
EXECUTE FUNCTION prevent_app_user_role_change();

-- 3. Hashed, single-use account activation and password-reset tokens.
CREATE TABLE user_action_tokens (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL,
    purpose varchar(24) NOT NULL,
    token_hash bytea NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    invalidated_at timestamptz,
    issued_by_user_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_user_action_tokens_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_action_tokens_issuer
        FOREIGN KEY (issued_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_action_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_action_tokens_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_user_action_tokens_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_user_action_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_user_action_tokens_terminal_state CHECK (
        NOT (used_at IS NOT NULL AND invalidated_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_user_action_tokens_one_live
    ON user_action_tokens (user_id, purpose)
    WHERE used_at IS NULL AND invalidated_at IS NULL;
CREATE INDEX ix_user_action_tokens_user ON user_action_tokens (user_id, created_at DESC);
CREATE INDEX ix_user_action_tokens_issuer ON user_action_tokens (issued_by_user_id);
CREATE INDEX ix_user_action_tokens_expiry
    ON user_action_tokens (expires_at)
    WHERE used_at IS NULL AND invalidated_at IS NULL;

-- 4. Intern-only profile and internship lifecycle.
CREATE TABLE intern_profiles (
    user_id bigint PRIMARY KEY,
    student_code varchar(64) NOT NULL,
    department varchar(120),
    phone varchar(32),
    internship_start_date date NOT NULL,
    internship_end_date date NOT NULL,
    internship_status varchar(24) NOT NULL DEFAULT 'NOT_STARTED',
    activated_at timestamptz,
    completed_at timestamptz,
    withdrawn_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_intern_profiles_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_intern_profiles_student_code CHECK (length(btrim(student_code)) > 0),
    CONSTRAINT ck_intern_profiles_dates CHECK (internship_end_date >= internship_start_date),
    CONSTRAINT ck_intern_profiles_status CHECK (
        internship_status IN ('NOT_STARTED', 'ACTIVE', 'COMPLETED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_intern_profiles_active_timestamp CHECK (
        internship_status NOT IN ('ACTIVE', 'COMPLETED') OR activated_at IS NOT NULL
    ),
    CONSTRAINT ck_intern_profiles_completed_timestamp CHECK (
        (internship_status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR
        (internship_status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_intern_profiles_withdrawn_timestamp CHECK (
        (internship_status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
        OR
        (internship_status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
    ),
    CONSTRAINT ck_intern_profiles_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_intern_profiles_student_code_ci
    ON intern_profiles (lower(btrim(student_code)));
CREATE INDEX ix_intern_profiles_status_dates
    ON intern_profiles (internship_status, internship_start_date, internship_end_date);

-- 5. Versioned SMTP settings. Secrets are AES-256-GCM envelopes.
CREATE TABLE smtp_configurations (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    host varchar(255) NOT NULL,
    port integer NOT NULL,
    security_mode varchar(16) NOT NULL,
    username varchar(320),
    password_ciphertext bytea,
    password_nonce bytea,
    secret_key_version integer,
    from_address varchar(320) NOT NULL,
    from_name varchar(120) NOT NULL,
    tested_at timestamptz,
    tested_by_user_id bigint,
    activated_at timestamptz,
    activated_by_user_id bigint,
    retired_at timestamptz,
    retired_by_user_id bigint,
    created_by_user_id bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_smtp_configurations_tested_by
        FOREIGN KEY (tested_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_smtp_configurations_activated_by
        FOREIGN KEY (activated_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_smtp_configurations_retired_by
        FOREIGN KEY (retired_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_smtp_configurations_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_smtp_configurations_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_smtp_configurations_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_smtp_configurations_security CHECK (security_mode IN ('NONE', 'STARTTLS', 'TLS')),
    CONSTRAINT ck_smtp_configurations_host CHECK (length(btrim(host)) > 0),
    CONSTRAINT ck_smtp_configurations_from CHECK (length(btrim(from_address)) > 3),
    CONSTRAINT ck_smtp_configurations_from_name CHECK (length(btrim(from_name)) > 0),
    CONSTRAINT ck_smtp_configurations_secret_pair CHECK (
        (username IS NULL AND password_ciphertext IS NULL
            AND password_nonce IS NULL AND secret_key_version IS NULL)
        OR
        (username IS NOT NULL AND length(btrim(username)) > 0
            AND password_ciphertext IS NOT NULL
            AND octet_length(password_ciphertext) > 16
            AND password_nonce IS NOT NULL
            AND octet_length(password_nonce) = 12
            AND secret_key_version > 0)
    ),
    CONSTRAINT ck_smtp_configurations_test_actor CHECK (
        (tested_at IS NULL AND tested_by_user_id IS NULL)
        OR
        (tested_at IS NOT NULL AND tested_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_smtp_configurations_activation CHECK (
        (status = 'DRAFT' AND activated_at IS NULL AND activated_by_user_id IS NULL)
        OR
        (status IN ('ACTIVE', 'RETIRED')
            AND tested_at IS NOT NULL
            AND tested_by_user_id IS NOT NULL
            AND activated_at IS NOT NULL
            AND activated_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_smtp_configurations_retirement CHECK (
        (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_by_user_id IS NOT NULL)
        OR
        (status <> 'RETIRED' AND retired_at IS NULL AND retired_by_user_id IS NULL)
    ),
    CONSTRAINT ck_smtp_configurations_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_smtp_configurations_one_active
    ON smtp_configurations ((1)) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_smtp_configurations_one_draft
    ON smtp_configurations ((1)) WHERE status = 'DRAFT';
CREATE INDEX ix_smtp_configurations_created_by ON smtp_configurations (created_by_user_id);
CREATE INDEX ix_smtp_configurations_tested_by ON smtp_configurations (tested_by_user_id);
CREATE INDEX ix_smtp_configurations_activated_by ON smtp_configurations (activated_by_user_id);
CREATE INDEX ix_smtp_configurations_retired_by ON smtp_configurations (retired_by_user_id);

-- 6. Versioned HolidayAPI credentials; country is intentionally fixed to VN.
CREATE TABLE holiday_api_configurations (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    country_code char(2) NOT NULL DEFAULT 'VN',
    api_key_ciphertext bytea NOT NULL,
    api_key_nonce bytea NOT NULL,
    secret_key_version integer NOT NULL,
    tested_at timestamptz,
    tested_by_user_id bigint,
    activated_at timestamptz,
    activated_by_user_id bigint,
    retired_at timestamptz,
    retired_by_user_id bigint,
    created_by_user_id bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_holiday_api_configurations_tested_by
        FOREIGN KEY (tested_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_holiday_api_configurations_activated_by
        FOREIGN KEY (activated_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_holiday_api_configurations_retired_by
        FOREIGN KEY (retired_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_holiday_api_configurations_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_holiday_api_configurations_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_holiday_api_configurations_country CHECK (country_code = 'VN'),
    CONSTRAINT ck_holiday_api_configurations_ciphertext CHECK (
        octet_length(api_key_ciphertext) > 16
    ),
    CONSTRAINT ck_holiday_api_configurations_nonce CHECK (octet_length(api_key_nonce) = 12),
    CONSTRAINT ck_holiday_api_configurations_key_version CHECK (secret_key_version > 0),
    CONSTRAINT ck_holiday_api_configurations_test_actor CHECK (
        (tested_at IS NULL AND tested_by_user_id IS NULL)
        OR
        (tested_at IS NOT NULL AND tested_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_holiday_api_configurations_activation CHECK (
        (status = 'DRAFT' AND activated_at IS NULL AND activated_by_user_id IS NULL)
        OR
        (status IN ('ACTIVE', 'RETIRED')
            AND tested_at IS NOT NULL
            AND tested_by_user_id IS NOT NULL
            AND activated_at IS NOT NULL
            AND activated_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_holiday_api_configurations_retirement CHECK (
        (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_by_user_id IS NOT NULL)
        OR
        (status <> 'RETIRED' AND retired_at IS NULL AND retired_by_user_id IS NULL)
    ),
    CONSTRAINT ck_holiday_api_configurations_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_holiday_api_configurations_one_active
    ON holiday_api_configurations ((1)) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_holiday_api_configurations_one_draft
    ON holiday_api_configurations ((1)) WHERE status = 'DRAFT';
CREATE INDEX ix_holiday_api_configurations_created_by
    ON holiday_api_configurations (created_by_user_id);
CREATE INDEX ix_holiday_api_configurations_tested_by
    ON holiday_api_configurations (tested_by_user_id);
CREATE INDEX ix_holiday_api_configurations_activated_by
    ON holiday_api_configurations (activated_by_user_id);
CREATE INDEX ix_holiday_api_configurations_retired_by
    ON holiday_api_configurations (retired_by_user_id);

-- 7. Immutable-on-effective global attendance policy versions.
CREATE TABLE attendance_policy_versions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    effective_from date NOT NULL,
    timezone_name varchar(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    scheduled_start time NOT NULL,
    scheduled_end time NOT NULL,
    check_in_grace_minutes integer NOT NULL,
    checkout_grace_minutes integer NOT NULL,
    monthly_leave_quota integer NOT NULL,
    violation_penalty numeric(5,4) NOT NULL,
    created_by_user_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_attendance_policy_versions_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_policy_versions_effective_from UNIQUE (effective_from),
    CONSTRAINT ck_attendance_policy_versions_timezone CHECK (
        length(btrim(timezone_name)) > 0
    ),
    CONSTRAINT ck_attendance_policy_versions_month_boundary CHECK (
        extract(day FROM effective_from) = 1
    ),
    CONSTRAINT ck_attendance_policy_versions_schedule CHECK (scheduled_end > scheduled_start),
    CONSTRAINT ck_attendance_policy_versions_check_in_grace CHECK (
        check_in_grace_minutes BETWEEN 0 AND 720
    ),
    CONSTRAINT ck_attendance_policy_versions_checkout_grace CHECK (
        checkout_grace_minutes BETWEEN 0 AND 720
    ),
    CONSTRAINT ck_attendance_policy_versions_checkout_cutoff CHECK (
        extract(epoch FROM scheduled_end) + checkout_grace_minutes * 60 < 86400
    ),
    CONSTRAINT ck_attendance_policy_versions_quota CHECK (monthly_leave_quota BETWEEN 0 AND 31),
    CONSTRAINT ck_attendance_policy_versions_penalty CHECK (
        violation_penalty >= 0 AND violation_penalty <= 1
    ),
    CONSTRAINT ck_attendance_policy_versions_version CHECK (version >= 0)
);

CREATE INDEX ix_attendance_policy_versions_lookup
    ON attendance_policy_versions (effective_from DESC);
CREATE INDEX ix_attendance_policy_versions_created_by
    ON attendance_policy_versions (created_by_user_id);

-- 8. ISO-8601 weekdays attached to a policy version.
CREATE TABLE attendance_policy_workdays (
    policy_version_id bigint NOT NULL,
    iso_weekday smallint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (policy_version_id, iso_weekday),
    CONSTRAINT fk_attendance_policy_workdays_policy
        FOREIGN KEY (policy_version_id) REFERENCES attendance_policy_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attendance_policy_workdays_iso_day CHECK (iso_weekday BETWEEN 1 AND 7)
);

-- 9. Locally authoritative global holiday/custom calendar.
CREATE TABLE global_calendar_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    calendar_date date NOT NULL,
    name varchar(200) NOT NULL,
    source varchar(24) NOT NULL,
    source_uuid varchar(100),
    actual_date date,
    observed_date date,
    public_holiday boolean,
    is_day_off boolean NOT NULL,
    imported_at timestamptz,
    created_by_user_id bigint NOT NULL,
    updated_by_user_id bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_global_calendar_events_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_global_calendar_events_updated_by
        FOREIGN KEY (updated_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_global_calendar_events_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_global_calendar_events_source CHECK (source IN ('CUSTOM', 'HOLIDAY_API')),
    CONSTRAINT ck_global_calendar_events_api_provenance CHECK (
        (source = 'CUSTOM'
            AND source_uuid IS NULL
            AND actual_date IS NULL
            AND observed_date IS NULL
            AND public_holiday IS NULL
            AND imported_at IS NULL)
        OR
        (source = 'HOLIDAY_API'
            AND source_uuid IS NOT NULL
            AND length(btrim(source_uuid)) > 0
            AND actual_date IS NOT NULL
            AND observed_date IS NOT NULL
            AND public_holiday IS NOT NULL
            AND imported_at IS NOT NULL)
    ),
    CONSTRAINT ck_global_calendar_events_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_global_calendar_events_source_uuid
    ON global_calendar_events (source, source_uuid)
    WHERE source_uuid IS NOT NULL;
CREATE INDEX ix_global_calendar_events_date
    ON global_calendar_events (calendar_date);
CREATE INDEX ix_global_calendar_events_day_off_date
    ON global_calendar_events (calendar_date)
    WHERE is_day_off = true;
CREATE INDEX ix_global_calendar_events_created_by
    ON global_calendar_events (created_by_user_id);
CREATE INDEX ix_global_calendar_events_updated_by
    ON global_calendar_events (updated_by_user_id);

-- 10. Mentor-owned project aggregate.
CREATE TABLE projects (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mentor_user_id bigint NOT NULL,
    name varchar(160) NOT NULL,
    description text,
    status varchar(16) NOT NULL DEFAULT 'PLANNED',
    start_date date NOT NULL,
    end_date date NOT NULL,
    activated_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_projects_mentor
        FOREIGN KEY (mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_projects_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_projects_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_projects_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_projects_activation CHECK (
        (status = 'PLANNED' AND activated_at IS NULL)
        OR
        (status IN ('ACTIVE', 'COMPLETED') AND activated_at IS NOT NULL)
    ),
    CONSTRAINT ck_projects_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR
        (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_projects_version CHECK (version >= 0)
);

CREATE INDEX ix_projects_mentor_status ON projects (mentor_user_id, status);
CREATE INDEX ix_projects_status_dates ON projects (status, start_date, end_date);

-- 11. Interval-based many-to-many project membership.
-- Initial/direct addition records the owning Mentor in added_by_user_id;
-- invitation acceptance records the accepting Intern.
CREATE TABLE project_memberships (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    intern_user_id bigint NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT current_timestamp,
    left_at timestamptz,
    added_by_user_id bigint NOT NULL,
    removed_by_mentor_user_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_memberships_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_memberships_intern
        FOREIGN KEY (intern_user_id) REFERENCES intern_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_memberships_added_by
        FOREIGN KEY (added_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_memberships_removed_by
        FOREIGN KEY (removed_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_project_memberships_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_project_memberships_interval CHECK (left_at IS NULL OR left_at > joined_at),
    CONSTRAINT ck_project_memberships_removal_actor CHECK (
        (left_at IS NULL AND removed_by_mentor_user_id IS NULL)
        OR
        (left_at IS NOT NULL AND removed_by_mentor_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_project_memberships_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_project_memberships_one_active
    ON project_memberships (project_id, intern_user_id)
    WHERE left_at IS NULL;
CREATE INDEX ix_project_memberships_project_fk
    ON project_memberships (project_id);
CREATE INDEX ix_project_memberships_intern_fk
    ON project_memberships (intern_user_id);
CREATE INDEX ix_project_memberships_project_active
    ON project_memberships (project_id, joined_at)
    WHERE left_at IS NULL;
CREATE INDEX ix_project_memberships_intern_active
    ON project_memberships (intern_user_id, joined_at)
    WHERE left_at IS NULL;
CREATE INDEX ix_project_memberships_added_by
    ON project_memberships (added_by_user_id);
CREATE INDEX ix_project_memberships_removed_by
    ON project_memberships (removed_by_mentor_user_id);

-- 12. Non-overlapping project-scoped leadership terms.
CREATE TABLE project_leadership_terms (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    membership_id bigint NOT NULL,
    started_at timestamptz NOT NULL DEFAULT current_timestamp,
    ended_at timestamptz,
    appointed_by_mentor_user_id bigint NOT NULL,
    ended_by_mentor_user_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_project_leadership_terms_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_leadership_terms_membership_project
        FOREIGN KEY (membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_leadership_terms_appointed_by
        FOREIGN KEY (appointed_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_leadership_terms_ended_by
        FOREIGN KEY (ended_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_project_leadership_terms_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_project_leadership_terms_interval CHECK (ended_at IS NULL OR ended_at > started_at),
    CONSTRAINT ck_project_leadership_terms_end_actor CHECK (
        (ended_at IS NULL AND ended_by_mentor_user_id IS NULL)
        OR
        (ended_at IS NOT NULL AND ended_by_mentor_user_id IS NOT NULL)
    ),
    CONSTRAINT ex_project_leadership_terms_no_overlap
        EXCLUDE USING gist (
            project_id WITH =,
            tstzrange(started_at, ended_at, '[)') WITH &&
        )
);

CREATE UNIQUE INDEX uq_project_leadership_terms_one_current
    ON project_leadership_terms (project_id)
    WHERE ended_at IS NULL;
CREATE INDEX ix_project_leadership_terms_membership
    ON project_leadership_terms (membership_id, project_id, started_at DESC);
CREATE INDEX ix_project_leadership_terms_appointed_by
    ON project_leadership_terms (appointed_by_mentor_user_id);
CREATE INDEX ix_project_leadership_terms_ended_by
    ON project_leadership_terms (ended_by_mentor_user_id);

-- 13. Leader-issued invitation to an eligible Intern. There is no expiry;
-- application transactions recheck Project, leadership, Intern, and membership state.
CREATE TABLE project_invitations (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    invited_intern_user_id bigint NOT NULL,
    issuing_leadership_term_id bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    accepted_membership_id bigint,
    resolved_at timestamptz,
    resolved_by_user_id bigint,
    resolution_code varchar(32),
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_invitations_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_invited_intern
        FOREIGN KEY (invited_intern_user_id) REFERENCES intern_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_issuing_leadership_project
        FOREIGN KEY (issuing_leadership_term_id, project_id)
        REFERENCES project_leadership_terms (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_accepted_membership_project
        FOREIGN KEY (accepted_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_resolved_by
        FOREIGN KEY (resolved_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_invitations_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_project_invitations_resolution_code CHECK (
        resolution_code IS NULL OR resolution_code IN (
            'INVITEE_ACCEPTED', 'INVITEE_DECLINED',
            'INVITER_REVOKED', 'MENTOR_REVOKED', 'LEADER_CHANGED',
            'PROJECT_COMPLETED', 'INVITEE_INELIGIBLE', 'MENTOR_DIRECT_ADD'
        )
    ),
    CONSTRAINT ck_project_invitations_resolution_state CHECK (
        (status = 'PENDING'
            AND accepted_membership_id IS NULL
            AND resolved_at IS NULL
            AND resolved_by_user_id IS NULL
            AND resolution_code IS NULL)
        OR
        (status = 'ACCEPTED'
            AND accepted_membership_id IS NOT NULL
            AND resolved_at IS NOT NULL
            AND resolved_by_user_id IS NOT NULL
            AND resolution_code = 'INVITEE_ACCEPTED')
        OR
        (status = 'DECLINED'
            AND accepted_membership_id IS NULL
            AND resolved_at IS NOT NULL
            AND resolved_by_user_id IS NOT NULL
            AND resolution_code = 'INVITEE_DECLINED')
        OR
        (status = 'REVOKED'
            AND accepted_membership_id IS NULL
            AND resolved_at IS NOT NULL
            AND resolution_code IN (
                'INVITER_REVOKED', 'MENTOR_REVOKED', 'LEADER_CHANGED',
                'PROJECT_COMPLETED', 'INVITEE_INELIGIBLE'
            )
            AND (
                resolved_by_user_id IS NOT NULL
                OR resolution_code IN ('LEADER_CHANGED', 'PROJECT_COMPLETED', 'INVITEE_INELIGIBLE')
            ))
        OR
        (status = 'SUPERSEDED'
            AND accepted_membership_id IS NULL
            AND resolved_at IS NOT NULL
            AND resolved_by_user_id IS NOT NULL
            AND resolution_code = 'MENTOR_DIRECT_ADD')
    ),
    CONSTRAINT ck_project_invitations_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_project_invitations_one_pending
    ON project_invitations (project_id, invited_intern_user_id)
    WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_project_invitations_accepted_membership
    ON project_invitations (accepted_membership_id)
    WHERE accepted_membership_id IS NOT NULL;
CREATE INDEX ix_project_invitations_project_status
    ON project_invitations (project_id, status, created_at, id);
CREATE INDEX ix_project_invitations_invitee_status
    ON project_invitations (invited_intern_user_id, status, created_at, id);
CREATE INDEX ix_project_invitations_issuing_leadership_project
    ON project_invitations (issuing_leadership_term_id, project_id);
CREATE INDEX ix_project_invitations_accepted_membership_project
    ON project_invitations (accepted_membership_id, project_id);
CREATE INDEX ix_project_invitations_resolved_by
    ON project_invitations (resolved_by_user_id);

-- 14. Mentor-decided request to close one active membership. Rights remain
-- unchanged while pending; approval/transfer/closure is one application transaction.
CREATE TABLE project_membership_exit_requests (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    target_membership_id bigint NOT NULL,
    requester_membership_id bigint NOT NULL,
    request_type varchar(24) NOT NULL,
    reason text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    resolution_note text,
    resolved_at timestamptz,
    resolved_by_user_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_membership_exit_requests_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_membership_exit_requests_target_project
        FOREIGN KEY (target_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_membership_exit_requests_requester_project
        FOREIGN KEY (requester_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_membership_exit_requests_resolved_by
        FOREIGN KEY (resolved_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_membership_exit_requests_type CHECK (
        request_type IN ('LEADER_REMOVAL', 'MEMBER_LEAVE')
    ),
    CONSTRAINT ck_project_membership_exit_requests_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_project_membership_exit_requests_resolution_note CHECK (
        resolution_note IS NULL OR length(btrim(resolution_note)) > 0
    ),
    CONSTRAINT ck_project_membership_exit_requests_participants CHECK (
        (request_type = 'LEADER_REMOVAL' AND requester_membership_id <> target_membership_id)
        OR
        (request_type = 'MEMBER_LEAVE' AND requester_membership_id = target_membership_id)
    ),
    CONSTRAINT ck_project_membership_exit_requests_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_project_membership_exit_requests_resolution CHECK (
        (status = 'PENDING'
            AND resolution_note IS NULL
            AND resolved_at IS NULL
            AND resolved_by_user_id IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED', 'CANCELLED')
            AND resolved_at IS NOT NULL
            AND resolved_by_user_id IS NOT NULL)
        OR
        (status = 'SUPERSEDED'
            AND resolved_at IS NOT NULL)
    ),
    CONSTRAINT ck_project_membership_exit_requests_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_project_membership_exit_requests_one_pending_target
    ON project_membership_exit_requests (target_membership_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_project_membership_exit_requests_project_status
    ON project_membership_exit_requests (project_id, status, created_at, id);
CREATE INDEX ix_project_membership_exit_requests_target_project
    ON project_membership_exit_requests (target_membership_id, project_id);
CREATE INDEX ix_project_membership_exit_requests_requester_project
    ON project_membership_exit_requests (requester_membership_id, project_id);
CREATE INDEX ix_project_membership_exit_requests_requester_status
    ON project_membership_exit_requests (requester_membership_id, status, created_at, id);
CREATE INDEX ix_project_membership_exit_requests_resolved_by
    ON project_membership_exit_requests (resolved_by_user_id);

-- 15. Single-assignee task. Assignee and actor memberships are constrained to one project.
CREATE TABLE tasks (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    assignee_membership_id bigint NOT NULL,
    title varchar(200) NOT NULL,
    description text,
    status varchar(24) NOT NULL DEFAULT 'TODO',
    due_date date,
    assigned_at timestamptz NOT NULL DEFAULT current_timestamp,
    created_by_membership_id bigint NOT NULL,
    assigned_by_membership_id bigint NOT NULL,
    deleted_at timestamptz,
    deleted_by_membership_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_assignee_project
        FOREIGN KEY (assignee_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_creator_project
        FOREIGN KEY (created_by_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_assigner_project
        FOREIGN KEY (assigned_by_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_deleter_project
        FOREIGN KEY (deleted_by_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT uq_tasks_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_tasks_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'BLOCKED')),
    CONSTRAINT ck_tasks_soft_delete_actor CHECK (
        (deleted_at IS NULL AND deleted_by_membership_id IS NULL)
        OR
        (deleted_at IS NOT NULL AND deleted_by_membership_id IS NOT NULL)
    ),
    CONSTRAINT ck_tasks_version CHECK (version >= 0)
);

CREATE INDEX ix_tasks_project_status_active
    ON tasks (project_id, status, id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_tasks_assignee_status_active
    ON tasks (assignee_membership_id, status, id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_tasks_project_fk ON tasks (project_id);
CREATE INDEX ix_tasks_assignee_project_fk
    ON tasks (assignee_membership_id, project_id);
CREATE INDEX ix_tasks_due_date_active
    ON tasks (due_date, project_id)
    WHERE deleted_at IS NULL AND due_date IS NOT NULL;
CREATE INDEX ix_tasks_creator ON tasks (created_by_membership_id, project_id);
CREATE INDEX ix_tasks_assigner ON tasks (assigned_by_membership_id, project_id);
CREATE INDEX ix_tasks_deleter ON tasks (deleted_by_membership_id, project_id);

-- 16. Append-only task discussion.
CREATE TABLE task_comments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL,
    author_user_id bigint NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_task_comments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_comments_author
        FOREIGN KEY (author_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_comments_body CHECK (length(btrim(body)) > 0)
);

CREATE INDEX ix_task_comments_task_created
    ON task_comments (task_id, created_at, id);
CREATE INDEX ix_task_comments_author
    ON task_comments (author_user_id, created_at DESC);

-- 17. Dated task effort; independent from attendance punches.
CREATE TABLE task_work_logs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    task_id bigint NOT NULL,
    membership_id bigint NOT NULL,
    work_date date NOT NULL,
    minutes integer NOT NULL,
    note text,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_work_logs_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_work_logs_task_project
        FOREIGN KEY (task_id, project_id) REFERENCES tasks (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_work_logs_membership_project
        FOREIGN KEY (membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_work_logs_minutes CHECK (minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_task_work_logs_note CHECK (note IS NULL OR length(btrim(note)) > 0),
    CONSTRAINT ck_task_work_logs_version CHECK (version >= 0)
);

CREATE INDEX ix_task_work_logs_task_date
    ON task_work_logs (task_id, project_id, work_date, id);
CREATE INDEX ix_task_work_logs_membership_date
    ON task_work_logs (membership_id, project_id, work_date, id);
CREATE INDEX ix_task_work_logs_project_date
    ON task_work_logs (project_id, work_date, id);

-- 18. Raw server-authoritative attendance punch data.
CREATE TABLE attendance_records (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    intern_user_id bigint NOT NULL,
    work_date date NOT NULL,
    policy_version_id bigint NOT NULL,
    check_in_at timestamptz NOT NULL,
    check_out_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_attendance_records_intern
        FOREIGN KEY (intern_user_id) REFERENCES intern_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_records_policy
        FOREIGN KEY (policy_version_id) REFERENCES attendance_policy_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_records_intern_date UNIQUE (intern_user_id, work_date),
    CONSTRAINT ck_attendance_records_checkout CHECK (
        check_out_at IS NULL OR check_out_at > check_in_at
    ),
    CONSTRAINT ck_attendance_records_version CHECK (version >= 0)
);

CREATE INDEX ix_attendance_records_date_intern
    ON attendance_records (work_date, intern_user_id);
CREATE INDEX ix_attendance_records_policy ON attendance_records (policy_version_id);
CREATE INDEX ix_attendance_records_missing_checkout
    ON attendance_records (work_date, intern_user_id)
    WHERE check_out_at IS NULL;

-- 19. One missed-checkout correction request per attendance record.
CREATE TABLE attendance_corrections (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attendance_record_id bigint NOT NULL,
    requested_checkout_at timestamptz NOT NULL,
    reason text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    submitted_at timestamptz NOT NULL DEFAULT current_timestamp,
    submission_deadline timestamptz NOT NULL,
    decision_deadline timestamptz NOT NULL,
    decided_by_mentor_user_id bigint,
    decided_at timestamptz,
    decision_note text,
    locked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_attendance_corrections_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_corrections_decided_by
        FOREIGN KEY (decided_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_corrections_record UNIQUE (attendance_record_id),
    CONSTRAINT ck_attendance_corrections_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_attendance_corrections_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_attendance_corrections_submission_window CHECK (
        submission_deadline >= submitted_at
    ),
    CONSTRAINT ck_attendance_corrections_decision_window CHECK (
        decision_deadline > submitted_at
    ),
    CONSTRAINT ck_attendance_corrections_pending_decision CHECK (
        status <> 'PENDING' OR (decided_by_mentor_user_id IS NULL AND decided_at IS NULL)
    ),
    CONSTRAINT ck_attendance_corrections_decided_at CHECK (
        status = 'PENDING' OR decided_at IS NOT NULL
    ),
    CONSTRAINT ck_attendance_corrections_approval_actor CHECK (
        status <> 'APPROVED' OR decided_by_mentor_user_id IS NOT NULL
    ),
    CONSTRAINT ck_attendance_corrections_locked_state CHECK (
        locked_at IS NULL OR status IN ('APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_attendance_corrections_version CHECK (version >= 0)
);

CREATE INDEX ix_attendance_corrections_decided_by
    ON attendance_corrections (decided_by_mentor_user_id);
CREATE INDEX ix_attendance_corrections_pending_deadline
    ON attendance_corrections (decision_deadline, id)
    WHERE status = 'PENDING' AND locked_at IS NULL;

-- 20. Append-only correction decision history.
CREATE TABLE attendance_correction_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correction_id bigint NOT NULL,
    event_type varchar(24) NOT NULL,
    from_status varchar(16),
    to_status varchar(16) NOT NULL,
    actor_user_id bigint,
    note text,
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_attendance_correction_events_correction
        FOREIGN KEY (correction_id) REFERENCES attendance_corrections (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_correction_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attendance_correction_events_type CHECK (
        event_type IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'REOPENED', 'AUTO_REJECTED', 'LOCKED')
    ),
    CONSTRAINT ck_attendance_correction_events_from_status CHECK (
        from_status IS NULL OR from_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_attendance_correction_events_to_status CHECK (
        to_status IN ('PENDING', 'APPROVED', 'REJECTED')
    )
);

CREATE INDEX ix_attendance_correction_events_correction
    ON attendance_correction_events (correction_id, occurred_at, id);
CREATE INDEX ix_attendance_correction_events_actor
    ON attendance_correction_events (actor_user_id, occurred_at DESC);

-- 21. Full-day leave request and current decision state.
CREATE TABLE leave_requests (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    intern_user_id bigint NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    submitted_at timestamptz NOT NULL DEFAULT current_timestamp,
    first_counted_start_at timestamptz NOT NULL,
    decided_by_mentor_user_id bigint,
    decided_at timestamptz,
    decision_note text,
    cancelled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_leave_requests_intern
        FOREIGN KEY (intern_user_id) REFERENCES intern_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_requests_decided_by
        FOREIGN KEY (decided_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_leave_requests_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_requests_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_leave_requests_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_leave_requests_first_start CHECK (first_counted_start_at > submitted_at),
    CONSTRAINT ck_leave_requests_decision CHECK (
        status IN ('PENDING', 'CANCELLED') OR decided_at IS NOT NULL
    ),
    CONSTRAINT ck_leave_requests_approval_actor CHECK (
        status <> 'APPROVED' OR decided_by_mentor_user_id IS NOT NULL
    ),
    CONSTRAINT ck_leave_requests_cancellation CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR
        (status <> 'CANCELLED' AND cancelled_at IS NULL)
    ),
    CONSTRAINT ck_leave_requests_version CHECK (version >= 0),
    CONSTRAINT ex_leave_requests_no_overlap
        EXCLUDE USING gist (
            intern_user_id WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        )
        WHERE (status IN ('PENDING', 'APPROVED'))
);

CREATE INDEX ix_leave_requests_intern_status_dates
    ON leave_requests (intern_user_id, status, start_date, end_date);
CREATE INDEX ix_leave_requests_decided_by
    ON leave_requests (decided_by_mentor_user_id);
CREATE INDEX ix_leave_requests_pending_cutoff
    ON leave_requests (first_counted_start_at, id)
    WHERE status = 'PENDING';

-- 22. Frozen workdays that consume leave quota.
CREATE TABLE leave_request_days (
    leave_request_id bigint NOT NULL,
    leave_date date NOT NULL,
    quota_month date NOT NULL,
    policy_version_id bigint NOT NULL,
    monthly_quota_snapshot integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (leave_request_id, leave_date),
    CONSTRAINT fk_leave_request_days_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_request_days_policy
        FOREIGN KEY (policy_version_id) REFERENCES attendance_policy_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_leave_request_days_quota_month CHECK (
        extract(day FROM quota_month) = 1
        AND quota_month = date_trunc('month', leave_date)::date
    ),
    CONSTRAINT ck_leave_request_days_quota CHECK (monthly_quota_snapshot BETWEEN 0 AND 31)
);

CREATE INDEX ix_leave_request_days_month
    ON leave_request_days (quota_month, leave_date, leave_request_id);
CREATE INDEX ix_leave_request_days_policy
    ON leave_request_days (policy_version_id);

-- 23. In-app notification plus optional non-secret email outbox state.
CREATE TABLE notifications (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_user_id bigint NOT NULL,
    notification_type varchar(32) NOT NULL,
    title varchar(200) NOT NULL,
    body text NOT NULL,
    action_url varchar(500),
    read_at timestamptz,
    email_status varchar(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    email_to varchar(320),
    email_subject varchar(255),
    email_body text,
    email_attempts integer NOT NULL DEFAULT 0,
    email_next_attempt_at timestamptz,
    email_sent_at timestamptz,
    email_last_error varchar(1000),
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_type CHECK (
        notification_type IN (
            'LEAVE_SUBMITTED', 'LEAVE_DECIDED',
            'CORRECTION_SUBMITTED', 'CORRECTION_DECIDED',
            'MEMBERSHIP_CHANGED', 'LEADERSHIP_CHANGED',
            'PROJECT_INVITATION_CREATED', 'PROJECT_INVITATION_RESOLVED',
            'MEMBERSHIP_EXIT_REQUESTED', 'MEMBERSHIP_EXIT_RESOLVED',
            'TASK_ASSIGNED', 'TASK_REASSIGNED',
            'TASK_STATUS_CHANGED', 'TASK_COMMENTED', 'SYSTEM'
        )
    ),
    CONSTRAINT ck_notifications_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_notifications_body CHECK (length(btrim(body)) > 0),
    CONSTRAINT ck_notifications_email_status CHECK (
        email_status IN ('NOT_REQUIRED', 'PENDING', 'SENT', 'FAILED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_notifications_email_attempts CHECK (email_attempts BETWEEN 0 AND 6),
    CONSTRAINT ck_notifications_email_payload CHECK (
        email_status IN ('NOT_REQUIRED', 'UNAVAILABLE')
        OR (email_to IS NOT NULL AND email_subject IS NOT NULL AND email_body IS NOT NULL)
    ),
    CONSTRAINT ck_notifications_email_sent CHECK (
        email_status <> 'SENT' OR email_sent_at IS NOT NULL
    ),
    CONSTRAINT ck_notifications_email_retry CHECK (
        email_status <> 'PENDING' OR email_next_attempt_at IS NOT NULL
    ),
    CONSTRAINT ck_notifications_version CHECK (version >= 0)
);

CREATE INDEX ix_notifications_recipient_created
    ON notifications (recipient_user_id, created_at DESC, id DESC);
CREATE INDEX ix_notifications_unread
    ON notifications (recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;
CREATE INDEX ix_notifications_pending_email
    ON notifications (email_next_attempt_at, id)
    WHERE email_status = 'PENDING';

-- Default global policy. Monday=1 through Friday=5 under ISO-8601.
INSERT INTO attendance_policy_versions (
    effective_from,
    timezone_name,
    scheduled_start,
    scheduled_end,
    check_in_grace_minutes,
    checkout_grace_minutes,
    monthly_leave_quota,
    violation_penalty,
    created_by_user_id
) VALUES (
    DATE '1970-01-01',
    'Asia/Ho_Chi_Minh',
    TIME '08:30:00',
    TIME '15:30:00',
    30,
    30,
    3,
    0.2500,
    NULL
);

INSERT INTO attendance_policy_workdays (policy_version_id, iso_weekday)
SELECT id, weekday
FROM attendance_policy_versions
CROSS JOIN generate_series(1, 5) AS weekday
WHERE effective_from = DATE '1970-01-01';

COMMIT;
