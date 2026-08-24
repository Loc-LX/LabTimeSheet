-- Delete ALL
-- Lab Timesheet tech-demo seed data.
--
-- WARNING: This script removes every row from the 23 application tables below.
-- It deliberately leaves flyway_schema_history untouched. Apply the Flyway
-- schema first, then run this file against a disposable/demo database only.
--
-- Demo password for every credential-bearing account:
--     DemoPassword123!
-- The stored value is a BCrypt hash; no cleartext password is persisted.
--
-- External integration note:
-- The SMTP row is an unauthenticated Mailpit draft. The HolidayAPI row contains
-- schema-valid placeholder ciphertext in an untested DRAFT. Replace it through
-- the Admin console before testing or activating it under the current
-- LAB_SECURITY_MASTER_KEY; the seed must not embed a runtime encryption key.

BEGIN;

SET TIME ZONE 'Asia/Ho_Chi_Minh';

TRUNCATE TABLE
    notifications,
    leave_request_days,
    leave_requests,
    attendance_correction_events,
    attendance_corrections,
    attendance_records,
    task_work_logs,
    task_comments,
    tasks,
    project_membership_exit_requests,
    project_invitations,
    project_leadership_terms,
    project_memberships,
    projects,
    global_calendar_events,
    attendance_policy_workdays,
    attendance_policy_versions,
    holiday_api_configurations,
    smtp_configurations,
    intern_profiles,
    user_action_tokens,
    system_state,
    app_users
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- Accounts and internship profiles
-- ---------------------------------------------------------------------------

-- The first Admin is the durable bootstrap owner. All later accounts are
-- attributed to this Admin through created_by_user_id.
INSERT INTO app_users (
    email, display_name, password_hash, global_role, account_status,
    activated_at, locked_at, deactivated_at, last_login_at,
    created_by_user_id, created_at, updated_at, version
) VALUES (
    'admin1@example.com',
    'Nguyen Minh Admin',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'ADMIN', 'ACTIVE',
    TIMESTAMPTZ '2026-08-01 08:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-24 08:45:00+07',
    NULL, TIMESTAMPTZ '2026-08-01 08:00:00+07',
    TIMESTAMPTZ '2026-08-24 08:45:00+07', 0
);

INSERT INTO app_users (
    email, display_name, password_hash, global_role, account_status,
    activated_at, locked_at, deactivated_at, last_login_at,
    created_by_user_id, created_at, updated_at, version
) VALUES
(
    'admin2@example.com', 'Tran Operations Admin',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'ADMIN', 'ACTIVE', TIMESTAMPTZ '2026-08-02 09:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-23 16:30:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-02 09:00:00+07', TIMESTAMPTZ '2026-08-23 16:30:00+07', 0
),
(
    'admin3@example.com', 'Le Audit Admin',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'ADMIN', 'LOCKED', TIMESTAMPTZ '2026-08-03 09:00:00+07',
    TIMESTAMPTZ '2026-08-22 14:20:00+07', NULL,
    TIMESTAMPTZ '2026-08-22 14:15:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-03 09:00:00+07', TIMESTAMPTZ '2026-08-22 14:20:00+07', 0
),
(
    'mentor1@example.com', 'Minh Nguyen',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'MENTOR', 'ACTIVE', TIMESTAMPTZ '2026-08-01 09:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-24 08:35:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-01 09:00:00+07', TIMESTAMPTZ '2026-08-24 08:35:00+07', 0
),
(
    'mentor2@example.com', 'Hoa Tran',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'MENTOR', 'ACTIVE', TIMESTAMPTZ '2026-08-02 09:30:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-23 10:00:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-02 09:30:00+07', TIMESTAMPTZ '2026-08-23 10:00:00+07', 0
),
(
    'mentor3@example.com', 'Quang Le',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'MENTOR', 'ACTIVE', TIMESTAMPTZ '2026-08-04 10:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-20 09:10:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-04 10:00:00+07', TIMESTAMPTZ '2026-08-20 09:10:00+07', 0
),
(
    'mentor4@example.com', 'Pham Former Mentor',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'MENTOR', 'DEACTIVATED', TIMESTAMPTZ '2026-06-01 09:00:00+07', NULL,
    TIMESTAMPTZ '2026-08-10 17:00:00+07', TIMESTAMPTZ '2026-08-10 16:55:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-06-01 09:00:00+07', TIMESTAMPTZ '2026-08-10 17:00:00+07', 0
),
(
    'intern1@example.com', 'Mai Linh',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'ACTIVE', TIMESTAMPTZ '2026-08-01 09:15:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-24 08:20:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-01 09:15:00+07', TIMESTAMPTZ '2026-08-24 08:20:00+07', 0
),
(
    'intern2@example.com', 'Bao Nguyen',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'ACTIVE', TIMESTAMPTZ '2026-07-15 09:15:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-24 08:25:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-07-15 09:15:00+07', TIMESTAMPTZ '2026-08-24 08:25:00+07', 0
),
(
    'intern3@example.com', 'Chi Pham',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'ACTIVE', TIMESTAMPTZ '2026-08-05 10:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-23 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-05 10:00:00+07', TIMESTAMPTZ '2026-08-23 09:00:00+07', 0
),
(
    'intern4@example.com', 'Duy Hoang',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'ACTIVE', TIMESTAMPTZ '2026-06-01 09:30:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-20 08:15:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-06-01 09:30:00+07', TIMESTAMPTZ '2026-08-20 08:15:00+07', 0
),
(
    'intern5@example.com', 'Thao Withdrawn',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'DEACTIVATED', TIMESTAMPTZ '2026-05-01 09:00:00+07', NULL,
    TIMESTAMPTZ '2026-08-16 17:00:00+07', TIMESTAMPTZ '2026-08-16 16:30:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-05-01 09:00:00+07', TIMESTAMPTZ '2026-08-16 17:00:00+07', 0
),
(
    'intern6@example.com', 'Khanh Vo',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'ACTIVE', TIMESTAMPTZ '2026-07-10 09:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-22 11:20:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-07-10 09:00:00+07', TIMESTAMPTZ '2026-08-22 11:20:00+07', 0
),
(
    'intern7@example.com', 'Long Pending Activation',
    NULL,
    'INTERN', 'PENDING_ACTIVATION', NULL, NULL, NULL, NULL,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-24 08:00:00+07', TIMESTAMPTZ '2026-08-24 08:00:00+07', 0
),
(
    'intern8@example.com', 'Yen Bui',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'INTERN', 'ACTIVE', TIMESTAMPTZ '2026-08-08 09:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-21 09:30:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-08 09:00:00+07', TIMESTAMPTZ '2026-08-21 09:30:00+07', 0
);

INSERT INTO system_state (
    singleton_id, initialized, initialized_at, bootstrap_admin_id,
    created_at, updated_at, version
) VALUES (
    1, true, TIMESTAMPTZ '2026-08-01 08:00:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-01 08:00:00+07', TIMESTAMPTZ '2026-08-01 08:00:00+07', 1
);

INSERT INTO intern_profiles (
    user_id, student_code, department, phone,
    internship_start_date, internship_end_date, internship_status,
    activated_at, completed_at, withdrawn_at, created_at, updated_at, version
) VALUES
(
    (SELECT id FROM app_users WHERE email = 'intern1@example.com'), 'STU-1001',
    'Software Engineering', '+84 901 100 001', DATE '2026-08-01', DATE '2026-09-30',
    'ACTIVE', TIMESTAMPTZ '2026-08-01 09:20:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-01 09:20:00+07', TIMESTAMPTZ '2026-08-01 09:20:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern2@example.com'), 'STU-1002',
    'Information Systems', '+84 901 100 002', DATE '2026-07-15', DATE '2026-10-15',
    'ACTIVE', TIMESTAMPTZ '2026-07-15 09:20:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-07-15 09:20:00+07', TIMESTAMPTZ '2026-07-15 09:20:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'), 'STU-1003',
    'Computer Science', '+84 901 100 003', DATE '2026-08-01', DATE '2026-10-31',
    'ACTIVE', TIMESTAMPTZ '2026-08-05 10:05:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-05 10:05:00+07', TIMESTAMPTZ '2026-08-05 10:05:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern4@example.com'), 'STU-1004',
    'Data Engineering', '+84 901 100 004', DATE '2026-06-01', DATE '2026-10-31',
    'ACTIVE', TIMESTAMPTZ '2026-06-01 09:35:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-06-01 09:35:00+07', TIMESTAMPTZ '2026-08-01 17:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern5@example.com'), 'STU-1005',
    'Quality Assurance', '+84 901 100 005', DATE '2026-05-01', DATE '2026-08-15',
    'WITHDRAWN', TIMESTAMPTZ '2026-05-01 09:05:00+07', NULL,
    TIMESTAMPTZ '2026-08-16 17:00:00+07',
    TIMESTAMPTZ '2026-05-01 09:05:00+07', TIMESTAMPTZ '2026-08-16 17:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern6@example.com'), 'STU-1006',
    'Software Engineering', '+84 901 100 006', DATE '2026-07-10', DATE '2026-10-10',
    'ACTIVE', TIMESTAMPTZ '2026-07-10 09:05:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-07-10 09:05:00+07', TIMESTAMPTZ '2026-07-10 09:05:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern7@example.com'), 'STU-1007',
    'Product Engineering', '+84 901 100 007', DATE '2026-09-15', DATE '2026-12-31',
    'NOT_STARTED', NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-24 08:05:00+07', TIMESTAMPTZ '2026-08-24 08:05:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern8@example.com'), 'STU-1008',
    'Web Engineering', '+84 901 100 008', DATE '2026-08-08', DATE '2026-11-08',
    'ACTIVE', TIMESTAMPTZ '2026-08-08 09:05:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-08 09:05:00+07', TIMESTAMPTZ '2026-08-08 09:05:00+07', 0
);

-- Activation/recovery token history uses deterministic 32-byte hashes only.
INSERT INTO user_action_tokens (
    user_id, purpose, token_hash, expires_at, used_at, invalidated_at,
    issued_by_user_id, created_at
) VALUES
(
    (SELECT id FROM app_users WHERE email = 'intern7@example.com'), 'ACTIVATION',
    decode(repeat('11', 32), 'hex'), TIMESTAMPTZ '2026-09-01 08:00:00+07',
    NULL, NULL, (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-24 08:00:00+07'
),
(
    (SELECT id FROM app_users WHERE email = 'intern1@example.com'), 'ACTIVATION',
    decode(repeat('22', 32), 'hex'), TIMESTAMPTZ '2026-08-30 08:00:00+07',
    TIMESTAMPTZ '2026-08-01 09:20:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-07-31 09:20:00+07'
),
(
    (SELECT id FROM app_users WHERE email = 'intern2@example.com'), 'ACTIVATION',
    decode(repeat('33', 32), 'hex'), TIMESTAMPTZ '2026-08-15 09:20:00+07',
    NULL, TIMESTAMPTZ '2026-07-15 09:25:00+07',
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-07-15 09:20:00+07'
),
(
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'), 'PASSWORD_RESET',
    decode(repeat('44', 32), 'hex'), TIMESTAMPTZ '2026-08-25 09:00:00+07',
    NULL, NULL, NULL, TIMESTAMPTZ '2026-08-24 08:00:00+07'
),
(
    (SELECT id FROM app_users WHERE email = 'intern4@example.com'), 'PASSWORD_RESET',
    decode(repeat('55', 32), 'hex'), TIMESTAMPTZ '2026-08-10 09:00:00+07',
    TIMESTAMPTZ '2026-08-02 10:00:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-02 09:00:00+07'
);

-- ---------------------------------------------------------------------------
-- Integration metadata, attendance policies, and calendar
-- ---------------------------------------------------------------------------

INSERT INTO smtp_configurations (
    status, host, port, security_mode, username, password_ciphertext,
    password_nonce, secret_key_version, from_address, from_name,
    tested_at, tested_by_user_id, activated_at, activated_by_user_id,
    retired_at, retired_by_user_id, created_by_user_id, created_at, updated_at, version
) VALUES (
    'DRAFT', 'mailpit', 1025, 'NONE', NULL, NULL, NULL, NULL,
    'noreply@example.com', 'Lab Timesheet Demo',
    NULL, NULL, NULL, NULL, NULL, NULL,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-24 08:10:00+07', TIMESTAMPTZ '2026-08-24 08:10:00+07', 0
);

INSERT INTO holiday_api_configurations (
    status, country_code, api_key_ciphertext, api_key_nonce, secret_key_version,
    tested_at, tested_by_user_id, activated_at, activated_by_user_id,
    retired_at, retired_by_user_id, created_by_user_id, created_at, updated_at, version
) VALUES (
    'DRAFT', 'VN', decode(repeat('ab', 24), 'hex'), decode(repeat('cd', 12), 'hex'), 1,
    NULL, NULL, NULL, NULL, NULL, NULL,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-24 08:15:00+07', TIMESTAMPTZ '2026-08-24 08:15:00+07', 0
);

INSERT INTO attendance_policy_versions (
    effective_from, timezone_name, scheduled_start, scheduled_end,
    check_in_grace_minutes, checkout_grace_minutes, monthly_leave_quota,
    violation_penalty, created_by_user_id, created_at, updated_at, version
) VALUES
(
    DATE '1970-01-01', 'Asia/Ho_Chi_Minh', TIME '08:30:00', TIME '15:30:00',
    30, 30, 3, 0.2500, NULL,
    TIMESTAMPTZ '2026-08-01 08:00:00+07', TIMESTAMPTZ '2026-08-01 08:00:00+07', 0
),
(
    DATE '2026-08-01', 'Asia/Ho_Chi_Minh', TIME '08:30:00', TIME '15:30:00',
    15, 45, 3, 0.5000,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-01 08:05:00+07', TIMESTAMPTZ '2026-08-01 08:05:00+07', 0
);

INSERT INTO attendance_policy_workdays (policy_version_id, iso_weekday)
SELECT id, weekday
FROM attendance_policy_versions
CROSS JOIN generate_series(1, 5) AS weekday;

INSERT INTO global_calendar_events (
    calendar_date, name, source, source_uuid, actual_date, observed_date,
    public_holiday, is_day_off, imported_at, created_by_user_id,
    updated_by_user_id, created_at, updated_at, version
) VALUES
(
    DATE '2026-08-22', 'Demo company retreat', 'CUSTOM', NULL, NULL, NULL,
    NULL, true, NULL,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-01 09:00:00+07', TIMESTAMPTZ '2026-08-01 09:00:00+07', 0
),
(
    DATE '2026-09-02', 'Vietnam National Day observed', 'CUSTOM', NULL, NULL, NULL,
    NULL, true, NULL,
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    TIMESTAMPTZ '2026-08-02 09:00:00+07', TIMESTAMPTZ '2026-08-02 09:00:00+07', 0
),
(
    DATE '2026-08-20', 'Quarterly demo day', 'CUSTOM', NULL, NULL, NULL,
    NULL, false, NULL,
    (SELECT id FROM app_users WHERE email = 'admin2@example.com'),
    (SELECT id FROM app_users WHERE email = 'admin2@example.com'),
    TIMESTAMPTZ '2026-08-10 09:00:00+07', TIMESTAMPTZ '2026-08-10 09:00:00+07', 0
);

-- ---------------------------------------------------------------------------
-- Projects, memberships, leadership, invitations, and exit workflows
-- ---------------------------------------------------------------------------

INSERT INTO projects (
    mentor_user_id, name, description, status, start_date, end_date,
    activated_at, completed_at, created_at, updated_at, version
) VALUES
(
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    'Lab Timesheet Platform',
    'Core attendance, project, task, and reporting experience for the lab.',
    'ACTIVE', DATE '2026-08-01', DATE '2026-10-31',
    TIMESTAMPTZ '2026-08-01 09:00:00+07', NULL,
    TIMESTAMPTZ '2026-08-01 09:00:00+07', TIMESTAMPTZ '2026-08-01 09:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'),
    'Attendance Analytics',
    'Attendance summaries, correction flows, and policy-aware reporting.',
    'ACTIVE', DATE '2026-07-01', DATE '2026-09-30',
    TIMESTAMPTZ '2026-07-01 09:00:00+07', NULL,
    TIMESTAMPTZ '2026-07-01 09:00:00+07', TIMESTAMPTZ '2026-08-20 10:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    'Legacy Import Cleanup',
    'Completed migration and validation of historical timesheet records.',
    'COMPLETED', DATE '2026-05-01', DATE '2026-07-31',
    TIMESTAMPTZ '2026-05-01 09:00:00+07', TIMESTAMPTZ '2026-08-01 17:00:00+07',
    TIMESTAMPTZ '2026-05-01 09:00:00+07', TIMESTAMPTZ '2026-08-01 17:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    'Demo Onboarding Portal',
    'Planned onboarding improvements for the next internship cohort.',
    'PLANNED', DATE '2026-09-01', DATE '2026-12-31',
    NULL, NULL,
    TIMESTAMPTZ '2026-08-20 09:00:00+07', TIMESTAMPTZ '2026-08-20 09:00:00+07', 0
);

INSERT INTO project_memberships (
    project_id, intern_user_id, joined_at, left_at,
    added_by_user_id, removed_by_mentor_user_id, created_at, updated_at, version
) VALUES
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1001'),
    TIMESTAMPTZ '2026-08-01 09:30:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'), NULL,
    TIMESTAMPTZ '2026-08-01 09:30:00+07', TIMESTAMPTZ '2026-08-01 09:30:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1002'),
    TIMESTAMPTZ '2026-08-03 10:00:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'intern2@example.com'), NULL,
    TIMESTAMPTZ '2026-08-03 10:00:00+07', TIMESTAMPTZ '2026-08-03 10:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1003'),
    TIMESTAMPTZ '2026-08-16 10:00:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'), NULL,
    TIMESTAMPTZ '2026-08-16 10:00:00+07', TIMESTAMPTZ '2026-08-16 10:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1002'),
    TIMESTAMPTZ '2026-07-01 09:30:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'), NULL,
    TIMESTAMPTZ '2026-07-01 09:30:00+07', TIMESTAMPTZ '2026-07-01 09:30:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1004'),
    TIMESTAMPTZ '2026-07-01 09:35:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'intern4@example.com'), NULL,
    TIMESTAMPTZ '2026-07-01 09:35:00+07', TIMESTAMPTZ '2026-07-01 09:35:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1006'),
    TIMESTAMPTZ '2026-07-10 10:00:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'), NULL,
    TIMESTAMPTZ '2026-07-10 10:00:00+07', TIMESTAMPTZ '2026-07-10 10:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1004'),
    TIMESTAMPTZ '2026-05-01 09:30:00+07', TIMESTAMPTZ '2026-07-31 17:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    TIMESTAMPTZ '2026-05-01 09:30:00+07', TIMESTAMPTZ '2026-07-31 17:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1005'),
    TIMESTAMPTZ '2026-05-01 09:35:00+07', TIMESTAMPTZ '2026-08-16 17:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    TIMESTAMPTZ '2026-05-01 09:35:00+07', TIMESTAMPTZ '2026-08-16 17:00:00+07', 0
);

INSERT INTO project_leadership_terms (
    project_id, membership_id, started_at, ended_at,
    appointed_by_mentor_user_id, ended_by_mentor_user_id, created_at
) VALUES
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1002'
    ),
    TIMESTAMPTZ '2026-08-01 09:30:00+07', TIMESTAMPTZ '2026-08-15 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    TIMESTAMPTZ '2026-08-01 09:30:00+07'
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1001'
    ),
    TIMESTAMPTZ '2026-08-15 09:00:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'), NULL,
    TIMESTAMPTZ '2026-08-15 09:00:00+07'
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1002'
    ),
    TIMESTAMPTZ '2026-07-01 09:30:00+07', NULL,
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'), NULL,
    TIMESTAMPTZ '2026-07-01 09:30:00+07'
),
(
    (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Legacy Import Cleanup' AND ip.student_code = 'STU-1004'
    ),
    TIMESTAMPTZ '2026-05-01 09:30:00+07', TIMESTAMPTZ '2026-07-31 17:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    TIMESTAMPTZ '2026-05-01 09:30:00+07'
);

INSERT INTO project_invitations (
    project_id, invited_intern_user_id, issuing_leadership_term_id, status,
    accepted_membership_id, resolved_at, resolved_by_user_id, resolution_code,
    created_at, updated_at, version
) VALUES
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1002'),
    (
        SELECT plt.id FROM project_leadership_terms plt
        JOIN projects p ON p.id = plt.project_id
        JOIN project_memberships pm ON pm.id = plt.membership_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1002'
    ),
    'ACCEPTED',
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1002'
    ),
    TIMESTAMPTZ '2026-08-03 10:00:00+07',
    (SELECT id FROM app_users WHERE email = 'intern2@example.com'), 'INVITEE_ACCEPTED',
    TIMESTAMPTZ '2026-08-02 09:00:00+07', TIMESTAMPTZ '2026-08-03 10:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1003'),
    (
        SELECT plt.id FROM project_leadership_terms plt
        JOIN projects p ON p.id = plt.project_id
        JOIN project_memberships pm ON pm.id = plt.membership_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1001'
    ),
    'ACCEPTED',
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1003'
    ),
    TIMESTAMPTZ '2026-08-16 10:00:00+07',
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'), 'INVITEE_ACCEPTED',
    TIMESTAMPTZ '2026-08-15 10:00:00+07', TIMESTAMPTZ '2026-08-16 10:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1008'),
    (
        SELECT plt.id FROM project_leadership_terms plt
        JOIN projects p ON p.id = plt.project_id
        WHERE p.name = 'Attendance Analytics' AND plt.ended_at IS NULL
    ),
    'PENDING', NULL, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-23 09:00:00+07', TIMESTAMPTZ '2026-08-23 09:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1004'),
    (
        SELECT plt.id FROM project_leadership_terms plt
        JOIN projects p ON p.id = plt.project_id
        WHERE p.name = 'Attendance Analytics' AND plt.ended_at IS NULL
    ),
    'ACCEPTED',
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1004'
    ),
    TIMESTAMPTZ '2026-07-03 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'intern4@example.com'), 'INVITEE_ACCEPTED',
    TIMESTAMPTZ '2026-07-02 09:00:00+07', TIMESTAMPTZ '2026-07-03 09:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1006'),
    (
        SELECT plt.id FROM project_leadership_terms plt
        JOIN projects p ON p.id = plt.project_id
        WHERE p.name = 'Legacy Import Cleanup'
    ),
    'REVOKED', NULL, TIMESTAMPTZ '2026-07-15 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'), 'INVITER_REVOKED',
    TIMESTAMPTZ '2026-07-10 09:00:00+07', TIMESTAMPTZ '2026-07-15 09:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1006'),
    (
        SELECT plt.id FROM project_leadership_terms plt
        JOIN projects p ON p.id = plt.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND plt.ended_at IS NULL
    ),
    'DECLINED', NULL, TIMESTAMPTZ '2026-08-18 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'intern6@example.com'), 'INVITEE_DECLINED',
    TIMESTAMPTZ '2026-08-17 09:00:00+07', TIMESTAMPTZ '2026-08-18 09:00:00+07', 0
);

INSERT INTO project_membership_exit_requests (
    project_id, target_membership_id, requester_membership_id, request_type,
    reason, status, resolution_note, resolved_at, resolved_by_user_id,
    created_at, updated_at, version
) VALUES
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1006'
    ),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1002'
    ),
    'LEADER_REMOVAL', 'Repeatedly missed agreed delivery checkpoints.', 'PENDING',
    NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-23 11:00:00+07', TIMESTAMPTZ '2026-08-23 11:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Legacy Import Cleanup' AND ip.student_code = 'STU-1005'
    ),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Legacy Import Cleanup' AND ip.student_code = 'STU-1005'
    ),
    'MEMBER_LEAVE', 'Internship was withdrawn before project completion.', 'APPROVED',
    'Mentor approved the historical withdrawal.', TIMESTAMPTZ '2026-08-16 17:05:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor3@example.com'),
    TIMESTAMPTZ '2026-08-16 16:00:00+07', TIMESTAMPTZ '2026-08-16 17:05:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1004'
    ),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1004'
    ),
    'MEMBER_LEAVE', 'The member requested to leave after the active milestone.', 'REJECTED',
    'Keep the membership until the current reporting milestone is complete.',
    TIMESTAMPTZ '2026-08-12 15:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'),
    TIMESTAMPTZ '2026-08-11 10:00:00+07', TIMESTAMPTZ '2026-08-12 15:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1003'
    ),
    (
        SELECT pm.id FROM project_memberships pm
        JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1003'
    ),
    'MEMBER_LEAVE', 'Duplicate demo request retained for history.', 'CANCELLED',
    'Cancelled by the member before mentor decision.', TIMESTAMPTZ '2026-08-19 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'),
    TIMESTAMPTZ '2026-08-18 09:00:00+07', TIMESTAMPTZ '2026-08-19 09:00:00+07', 0
);

-- ---------------------------------------------------------------------------
-- Tasks, comments, and work logs
-- ---------------------------------------------------------------------------

WITH refs AS (
    SELECT
        (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform') AS project_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1001') AS leader_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1002') AS intern2_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1003') AS intern3_id
)
INSERT INTO tasks (
    project_id, assignee_membership_id, title, description, status, due_date,
    assigned_at, created_by_membership_id, assigned_by_membership_id,
    deleted_at, deleted_by_membership_id, created_at, updated_at, version
)
SELECT project_id, intern2_id, 'Prepare attendance import mapping',
       'Map legacy CSV columns to the normalized attendance model.', 'TODO', DATE '2026-08-28',
       TIMESTAMPTZ '2026-08-18 09:00:00+07', leader_id, leader_id,
       NULL::timestamptz, NULL::bigint, TIMESTAMPTZ '2026-08-18 09:00:00+07', TIMESTAMPTZ '2026-08-18 09:00:00+07', 0 FROM refs
UNION ALL
SELECT project_id, leader_id, 'Review account lifecycle copy',
       'Review activation, locked-account, and deactivation messages for the demo.', 'IN_PROGRESS', DATE '2026-08-27',
       TIMESTAMPTZ '2026-08-17 10:00:00+07', leader_id, leader_id,
       NULL::timestamptz, NULL::bigint, TIMESTAMPTZ '2026-08-17 10:00:00+07', TIMESTAMPTZ '2026-08-23 14:00:00+07', 0 FROM refs
UNION ALL
SELECT project_id, intern3_id, 'Verify project task filters',
       'Check active, deleted, and status-filtered task views from an Intern account.', 'DONE', DATE '2026-08-20',
       TIMESTAMPTZ '2026-08-16 10:00:00+07', leader_id, leader_id,
       NULL::timestamptz, NULL::bigint, TIMESTAMPTZ '2026-08-16 10:00:00+07', TIMESTAMPTZ '2026-08-20 16:00:00+07', 0 FROM refs
UNION ALL
SELECT project_id, intern2_id, 'Archived demo task',
       'Retained only to demonstrate soft-delete history and old comments.', 'DONE', DATE '2026-08-12',
       TIMESTAMPTZ '2026-08-05 10:00:00+07', leader_id, leader_id,
       TIMESTAMPTZ '2026-08-13 15:00:00+07', leader_id,
       TIMESTAMPTZ '2026-08-05 10:00:00+07', TIMESTAMPTZ '2026-08-13 15:00:00+07', 0 FROM refs;

WITH refs AS (
    SELECT
        (SELECT id FROM projects WHERE name = 'Attendance Analytics') AS project_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1002') AS leader_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1004') AS intern4_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1006') AS intern6_id
)
INSERT INTO tasks (
    project_id, assignee_membership_id, title, description, status, due_date,
    assigned_at, created_by_membership_id, assigned_by_membership_id,
    deleted_at, deleted_by_membership_id, created_at, updated_at, version
)
SELECT project_id, intern4_id, 'Reconcile August attendance totals',
       'Reconcile daily punches against the frozen policy version.', 'DONE', DATE '2026-08-21',
       TIMESTAMPTZ '2026-08-02 09:00:00+07', leader_id, leader_id,
       NULL::timestamptz, NULL::bigint, TIMESTAMPTZ '2026-08-02 09:00:00+07', TIMESTAMPTZ '2026-08-21 16:00:00+07', 0 FROM refs
UNION ALL
SELECT project_id, intern6_id, 'Investigate missing checkout queue',
       'Investigate the rejected correction and document the next action.', 'BLOCKED', DATE '2026-08-26',
       TIMESTAMPTZ '2026-08-19 09:00:00+07', leader_id, leader_id,
       NULL::timestamptz, NULL::bigint, TIMESTAMPTZ '2026-08-19 09:00:00+07', TIMESTAMPTZ '2026-08-22 11:00:00+07', 0 FROM refs;

WITH refs AS (
    SELECT
        (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup') AS project_id,
        (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
            JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
            WHERE p.name = 'Legacy Import Cleanup' AND ip.student_code = 'STU-1004') AS intern4_id
)
INSERT INTO tasks (
    project_id, assignee_membership_id, title, description, status, due_date,
    assigned_at, created_by_membership_id, assigned_by_membership_id,
    deleted_at, deleted_by_membership_id, created_at, updated_at, version
)
SELECT project_id, intern4_id, 'Close legacy import reconciliation',
       'Historical task retained for the completed project timeline.', 'DONE', DATE '2026-07-25',
       TIMESTAMPTZ '2026-07-10 09:00:00+07', intern4_id, intern4_id,
       NULL, NULL, TIMESTAMPTZ '2026-07-10 09:00:00+07', TIMESTAMPTZ '2026-07-25 16:00:00+07', 0 FROM refs;

INSERT INTO task_comments (task_id, author_user_id, body, created_at) VALUES
(
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Review account lifecycle copy'),
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    'Please keep the pending, locked, and deactivated distinctions visible in the demo.',
    TIMESTAMPTZ '2026-08-17 11:00:00+07'
),
(
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Review account lifecycle copy'),
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    'The Admin account detail screen now exposes those states separately.',
    TIMESTAMPTZ '2026-08-18 09:30:00+07'
),
(
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Verify project task filters'),
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'),
    'Verified that deleted tasks remain available only through history views.',
    TIMESTAMPTZ '2026-08-20 16:10:00+07'
),
(
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Attendance Analytics' AND t.title = 'Investigate missing checkout queue'),
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'),
    'Waiting for the correction decision history before closing this blocker.',
    TIMESTAMPTZ '2026-08-22 11:15:00+07'
),
(
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Archived demo task'),
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    'This note is retained after soft deletion for the historical report.',
    TIMESTAMPTZ '2026-08-13 15:05:00+07'
);

INSERT INTO task_work_logs (
    project_id, task_id, membership_id, work_date, minutes, note,
    created_at, updated_at, version
) VALUES
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Review account lifecycle copy'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1001'),
    DATE '2026-08-18', 120, 'Reviewed the account status wording.',
    TIMESTAMPTZ '2026-08-18 17:00:00+07', TIMESTAMPTZ '2026-08-18 17:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Review account lifecycle copy'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1001'),
    DATE '2026-08-19', 90, 'Applied the final wording review.',
    TIMESTAMPTZ '2026-08-19 17:00:00+07', TIMESTAMPTZ '2026-08-19 17:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Prepare attendance import mapping'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1002'),
    DATE '2026-08-19', 60, 'Mapped legacy check-in and check-out columns.',
    TIMESTAMPTZ '2026-08-19 17:10:00+07', TIMESTAMPTZ '2026-08-19 17:10:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Lab Timesheet Platform'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Lab Timesheet Platform' AND t.title = 'Verify project task filters'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Lab Timesheet Platform' AND ip.student_code = 'STU-1003'),
    DATE '2026-08-18', 180, 'Ran the Intern task-list filter scenarios.',
    TIMESTAMPTZ '2026-08-18 17:15:00+07', TIMESTAMPTZ '2026-08-18 17:15:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Attendance Analytics' AND t.title = 'Reconcile August attendance totals'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1004'),
    DATE '2026-07-15', 240, 'Closed out the historical reconciliation work.',
    TIMESTAMPTZ '2026-07-15 17:00:00+07', TIMESTAMPTZ '2026-07-15 17:00:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Attendance Analytics'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Attendance Analytics' AND t.title = 'Investigate missing checkout queue'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Attendance Analytics' AND ip.student_code = 'STU-1006'),
    DATE '2026-08-19', 45, 'Collected evidence for the rejected correction.',
    TIMESTAMPTZ '2026-08-19 17:20:00+07', TIMESTAMPTZ '2026-08-19 17:20:00+07', 0
),
(
    (SELECT id FROM projects WHERE name = 'Legacy Import Cleanup'),
    (SELECT t.id FROM tasks t JOIN projects p ON p.id = t.project_id
        WHERE p.name = 'Legacy Import Cleanup' AND t.title = 'Close legacy import reconciliation'),
    (SELECT pm.id FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
        JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
        WHERE p.name = 'Legacy Import Cleanup' AND ip.student_code = 'STU-1004'),
    DATE '2026-07-15', 300, 'Historical effort retained for reporting.',
    TIMESTAMPTZ '2026-07-15 17:30:00+07', TIMESTAMPTZ '2026-07-15 17:30:00+07', 0
);

-- ---------------------------------------------------------------------------
-- Attendance, corrections, leave, quota snapshots, and notifications
-- ---------------------------------------------------------------------------

INSERT INTO attendance_records (
    intern_user_id, work_date, policy_version_id, check_in_at, check_out_at,
    created_at, updated_at, version
) VALUES
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1001'), DATE '2026-08-18',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-18 08:25:00+07', TIMESTAMPTZ '2026-08-18 15:35:00+07',
    TIMESTAMPTZ '2026-08-18 15:35:00+07', TIMESTAMPTZ '2026-08-18 15:35:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1001'), DATE '2026-08-19',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-19 08:40:00+07', TIMESTAMPTZ '2026-08-19 15:20:00+07',
    TIMESTAMPTZ '2026-08-19 15:20:00+07', TIMESTAMPTZ '2026-08-19 15:20:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1002'), DATE '2026-08-18',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-18 08:30:00+07', TIMESTAMPTZ '2026-08-18 15:30:00+07',
    TIMESTAMPTZ '2026-08-18 15:30:00+07', TIMESTAMPTZ '2026-08-18 15:30:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1002'), DATE '2026-08-19',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-19 08:31:00+07', NULL,
    TIMESTAMPTZ '2026-08-19 08:31:00+07', TIMESTAMPTZ '2026-08-19 08:31:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1003'), DATE '2026-08-18',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-18 08:20:00+07', TIMESTAMPTZ '2026-08-18 15:30:00+07',
    TIMESTAMPTZ '2026-08-18 15:30:00+07', TIMESTAMPTZ '2026-08-18 15:30:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1003'), DATE '2026-08-20',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-20 08:45:00+07', NULL,
    TIMESTAMPTZ '2026-08-20 08:45:00+07', TIMESTAMPTZ '2026-08-20 08:45:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1006'), DATE '2026-08-19',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-19 08:55:00+07', NULL,
    TIMESTAMPTZ '2026-08-19 08:55:00+07', TIMESTAMPTZ '2026-08-19 08:55:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1008'), DATE '2026-08-19',
    (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
    TIMESTAMPTZ '2026-08-19 08:28:00+07', TIMESTAMPTZ '2026-08-19 15:25:00+07',
    TIMESTAMPTZ '2026-08-19 15:25:00+07', TIMESTAMPTZ '2026-08-19 15:25:00+07', 0
);

INSERT INTO attendance_corrections (
    attendance_record_id, requested_checkout_at, reason, status, submitted_at,
    submission_deadline, decision_deadline, decided_by_mentor_user_id,
    decided_at, decision_note, locked_at, created_at, updated_at, version
) VALUES
(
    (
        SELECT ar.id FROM attendance_records ar
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1002' AND ar.work_date = DATE '2026-08-19'
    ),
    TIMESTAMPTZ '2026-08-19 15:45:00+07',
    'Forgot to press checkout after the import-mapping session.', 'PENDING',
    TIMESTAMPTZ '2026-08-19 16:00:00+07', TIMESTAMPTZ '2026-08-20 09:00:00+07',
    TIMESTAMPTZ '2026-08-22 09:00:00+07', NULL, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-19 16:00:00+07', TIMESTAMPTZ '2026-08-19 16:00:00+07', 0
),
(
    (
        SELECT ar.id FROM attendance_records ar
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1003' AND ar.work_date = DATE '2026-08-20'
    ),
    TIMESTAMPTZ '2026-08-20 15:40:00+07',
    'The browser closed after the final task update.', 'APPROVED',
    TIMESTAMPTZ '2026-08-20 16:00:00+07', TIMESTAMPTZ '2026-08-21 09:00:00+07',
    TIMESTAMPTZ '2026-08-22 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    TIMESTAMPTZ '2026-08-21 10:00:00+07', 'Evidence matched the task activity timeline.',
    TIMESTAMPTZ '2026-08-21 10:00:00+07',
    TIMESTAMPTZ '2026-08-20 16:00:00+07', TIMESTAMPTZ '2026-08-21 10:00:00+07', 0
),
(
    (
        SELECT ar.id FROM attendance_records ar
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1006' AND ar.work_date = DATE '2026-08-19'
    ),
    TIMESTAMPTZ '2026-08-19 15:50:00+07',
    'Requested checkout is outside the available evidence window.', 'REJECTED',
    TIMESTAMPTZ '2026-08-19 16:10:00+07', TIMESTAMPTZ '2026-08-20 09:00:00+07',
    TIMESTAMPTZ '2026-08-21 09:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'),
    TIMESTAMPTZ '2026-08-20 10:00:00+07', 'Please use a correction note tied to a task or message timestamp.',
    TIMESTAMPTZ '2026-08-20 10:00:00+07',
    TIMESTAMPTZ '2026-08-19 16:10:00+07', TIMESTAMPTZ '2026-08-20 10:00:00+07', 0
);

INSERT INTO attendance_correction_events (
    correction_id, event_type, from_status, to_status, actor_user_id, note, occurred_at
) VALUES
(
    (
        SELECT ac.id FROM attendance_corrections ac
        JOIN attendance_records ar ON ar.id = ac.attendance_record_id
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1002'
    ),
    'SUBMITTED', NULL, 'PENDING',
    (SELECT id FROM app_users WHERE email = 'intern2@example.com'),
    'Correction request submitted.', TIMESTAMPTZ '2026-08-19 16:00:00+07'
),
(
    (
        SELECT ac.id FROM attendance_corrections ac
        JOIN attendance_records ar ON ar.id = ac.attendance_record_id
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1003'
    ),
    'SUBMITTED', NULL, 'PENDING',
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'),
    'Correction request submitted.', TIMESTAMPTZ '2026-08-20 16:00:00+07'
),
(
    (
        SELECT ac.id FROM attendance_corrections ac
        JOIN attendance_records ar ON ar.id = ac.attendance_record_id
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1003'
    ),
    'APPROVED', 'PENDING', 'APPROVED',
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    'Correction approved.', TIMESTAMPTZ '2026-08-21 10:00:00+07'
),
(
    (
        SELECT ac.id FROM attendance_corrections ac
        JOIN attendance_records ar ON ar.id = ac.attendance_record_id
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1006'
    ),
    'SUBMITTED', NULL, 'PENDING',
    (SELECT id FROM app_users WHERE email = 'intern6@example.com'),
    'Correction request submitted.', TIMESTAMPTZ '2026-08-19 16:10:00+07'
),
(
    (
        SELECT ac.id FROM attendance_corrections ac
        JOIN attendance_records ar ON ar.id = ac.attendance_record_id
        JOIN intern_profiles ip ON ip.user_id = ar.intern_user_id
        WHERE ip.student_code = 'STU-1006'
    ),
    'REJECTED', 'PENDING', 'REJECTED',
    (SELECT id FROM app_users WHERE email = 'mentor2@example.com'),
    'Correction rejected.', TIMESTAMPTZ '2026-08-20 10:00:00+07'
);

INSERT INTO leave_requests (
    intern_user_id, start_date, end_date, reason, status, submitted_at,
    first_counted_start_at, decided_by_mentor_user_id, decided_at,
    decision_note, cancelled_at, created_at, updated_at, version
) VALUES
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1001'),
    DATE '2026-08-26', DATE '2026-08-27', 'University examination preparation.', 'PENDING',
    TIMESTAMPTZ '2026-08-20 10:00:00+07', TIMESTAMPTZ '2026-08-25 17:00:00+07',
    NULL, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-20 10:00:00+07', TIMESTAMPTZ '2026-08-20 10:00:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1002'),
    DATE '2026-08-12', DATE '2026-08-13', 'University workshop attendance.', 'APPROVED',
    TIMESTAMPTZ '2026-08-01 10:00:00+07', TIMESTAMPTZ '2026-08-11 17:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    TIMESTAMPTZ '2026-08-02 10:00:00+07', 'Approved for the two scheduled workdays.', NULL,
    TIMESTAMPTZ '2026-08-01 10:00:00+07', TIMESTAMPTZ '2026-08-02 10:00:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1003'),
    DATE '2026-08-25', DATE '2026-08-26', 'Family appointment.', 'REJECTED',
    TIMESTAMPTZ '2026-08-05 10:00:00+07', TIMESTAMPTZ '2026-08-24 17:00:00+07',
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    TIMESTAMPTZ '2026-08-06 10:00:00+07', 'The request did not match the project delivery window.', NULL,
    TIMESTAMPTZ '2026-08-05 10:00:00+07', TIMESTAMPTZ '2026-08-06 10:00:00+07', 0
),
(
    (SELECT user_id FROM intern_profiles WHERE student_code = 'STU-1006'),
    DATE '2026-08-22', DATE '2026-08-22', 'Personal appointment.', 'CANCELLED',
    TIMESTAMPTZ '2026-08-01 11:00:00+07', TIMESTAMPTZ '2026-08-21 17:00:00+07',
    NULL, NULL, NULL, TIMESTAMPTZ '2026-08-10 10:00:00+07',
    TIMESTAMPTZ '2026-08-01 11:00:00+07', TIMESTAMPTZ '2026-08-10 10:00:00+07', 0
);

INSERT INTO leave_request_days (
    leave_request_id, leave_date, quota_month, policy_version_id,
    monthly_quota_snapshot, created_at
) SELECT lr.id, day.leave_date, DATE '2026-08-01',
         (SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '2026-08-01'),
         3, TIMESTAMPTZ '2026-08-02 10:00:00+07'
FROM leave_requests lr
JOIN intern_profiles ip ON ip.user_id = lr.intern_user_id
CROSS JOIN (VALUES (DATE '2026-08-12'), (DATE '2026-08-13')) AS day(leave_date)
WHERE ip.student_code = 'STU-1002' AND lr.status = 'APPROVED';

INSERT INTO notifications (
    recipient_user_id, notification_type, title, body, action_url, read_at,
    email_status, email_to, email_subject, email_body, email_attempts,
    email_next_attempt_at, email_sent_at, email_last_error, created_at, updated_at, version
) VALUES
(
    (SELECT id FROM app_users WHERE email = 'intern2@example.com'),
    'CORRECTION_DECIDED', 'Attendance correction approved',
    'Your missed-checkout correction was approved by the Mentor.', '/attendance/corrections', NULL,
    'NOT_REQUIRED', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-21 10:01:00+07', TIMESTAMPTZ '2026-08-21 10:01:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'mentor1@example.com'),
    'LEAVE_SUBMITTED', 'Leave request needs a decision',
    'An Intern submitted a leave request for your project review.', '/attendance/leave', NULL,
    'PENDING', 'mentor1@example.com', 'Leave request needs a decision',
    'An Intern submitted a leave request for your project review.', 1,
    TIMESTAMPTZ '2026-08-24 09:30:00+07', NULL, NULL,
    TIMESTAMPTZ '2026-08-24 09:00:00+07', TIMESTAMPTZ '2026-08-24 09:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern6@example.com'),
    'CORRECTION_DECIDED', 'Attendance correction rejected',
    'Your missed-checkout correction needs stronger supporting evidence.', '/attendance/corrections', NULL,
    'SENT', 'intern6@example.com', 'Attendance correction rejected',
    'Your missed-checkout correction needs stronger supporting evidence.', 1,
    NULL, TIMESTAMPTZ '2026-08-20 10:05:00+07', NULL,
    TIMESTAMPTZ '2026-08-20 10:05:00+07', TIMESTAMPTZ '2026-08-20 10:05:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern3@example.com'),
    'PROJECT_INVITATION_CREATED', 'You joined Lab Timesheet Platform',
    'Your project invitation was accepted and your membership is active.', '/projects',
    TIMESTAMPTZ '2026-08-16 12:00:00+07',
    'NOT_REQUIRED', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-16 10:01:00+07', TIMESTAMPTZ '2026-08-16 12:00:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern8@example.com'),
    'PROJECT_INVITATION_CREATED', 'New project invitation',
    'You have a pending invitation to Attendance Analytics.', '/projects', NULL,
    'UNAVAILABLE', 'intern8@example.com', 'New project invitation',
    'You have a pending invitation to Attendance Analytics.', 0, NULL, NULL,
    'SMTP is not active in the demo seed.',
    TIMESTAMPTZ '2026-08-23 09:01:00+07', TIMESTAMPTZ '2026-08-23 09:01:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'intern1@example.com'),
    'TASK_ASSIGNED', 'Task assigned to you',
    'Review account lifecycle copy is now assigned to you.', '/tasks', NULL,
    'NOT_REQUIRED', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-17 10:01:00+07', TIMESTAMPTZ '2026-08-17 10:01:00+07', 0
),
(
    (SELECT id FROM app_users WHERE email = 'admin1@example.com'),
    'SYSTEM', 'Demo seed completed',
    'The Lab Timesheet tech-demo dataset was loaded successfully.', '/admin',
    TIMESTAMPTZ '2026-08-24 08:30:00+07',
    'NOT_REQUIRED', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    TIMESTAMPTZ '2026-08-24 08:30:00+07', TIMESTAMPTZ '2026-08-24 08:30:00+07', 0
);

-- Keep the script all-or-nothing: a failed foreign-key, check, or exclusion
-- constraint rolls back the reset and every demo row above.
COMMIT;
