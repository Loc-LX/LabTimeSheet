-- Precondition for the correction journey in critical-journeys.spec.mjs.
--
-- Creates one active Mentor, one active Intern, and one attendance row for the given
-- previous workday that has a check-in and no checkout. It seeds the starting state
-- only: no correction, decision, or notification is created, because those are what
-- the journey asserts.
--
-- Run against a disposable end-to-end database only, after Flyway has migrated it and
-- the first administrator has been bootstrapped. The journey passes:
--   stamp        unique suffix for emails and the student code
--   work_date    the previous workday, YYYY-MM-DD
--   check_in_at  check-in instant on that date, e.g. 2026-09-14 08:20:00+07
--
-- Both accounts use the demo password DemoPassword123!, stored as the same BCrypt
-- hash scripts/demo-seed.sql uses.

\set ON_ERROR_STOP on

BEGIN;

INSERT INTO app_users (email, display_name, password_hash, global_role, account_status, activated_at)
VALUES (
    'correction-mentor-' || :'stamp' || '@e2e.test',
    'E2E Correction Mentor ' || :'stamp',
    '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
    'MENTOR', 'ACTIVE', current_timestamp
);

WITH intern AS (
    INSERT INTO app_users (email, display_name, password_hash, global_role, account_status, activated_at)
    VALUES (
        'correction-intern-' || :'stamp' || '@e2e.test',
        'E2E Correction Intern ' || :'stamp',
        '{bcrypt}$2a$10$KIs3YUg8/z10N9SHPk6ZzOnxewibHdzLuO1s5yJoxkMKcED2rSPxC',
        'INTERN', 'ACTIVE', current_timestamp
    )
    RETURNING id
), profile AS (
    INSERT INTO intern_profiles (
        user_id, student_code, internship_start_date, internship_end_date,
        internship_status, activated_at
    )
    SELECT id, 'COR-' || :'stamp',
           DATE :'work_date' - 30, DATE :'work_date' + 60,
           'ACTIVE', current_timestamp
    FROM intern
    RETURNING user_id
)
INSERT INTO attendance_records (intern_user_id, work_date, policy_version_id, check_in_at, check_out_at)
SELECT profile.user_id,
       DATE :'work_date',
       (SELECT id FROM attendance_policy_versions
        WHERE effective_from <= DATE :'work_date'
        ORDER BY effective_from DESC
        LIMIT 1),
       TIMESTAMPTZ :'check_in_at',
       NULL
FROM profile;

COMMIT;
