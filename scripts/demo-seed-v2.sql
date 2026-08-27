-- Lab Timesheet V2 add-on demo data.
--
-- Run order:
--   1. Apply Flyway V1 and V2 (the application does this automatically).
--   2. Run scripts/demo-seed.sql to load the complete V1 demo fixture.
--   3. Run this file to add the V2 task-effort fixture.
--
-- This file deliberately does not recreate accounts or V1 rows. The account
-- credentials are defined by demo-seed.sql: credential-bearing accounts use
-- adminN@example.com, mentorN@example.com, or internN@example.com and the
-- demo password DemoPassword123!. The pending-activation account has no
-- password by design, matching the account lifecycle contract.
--
-- Re-running this file replaces only V2 forecast rows and V2 task estimates.
-- It is intended for the disposable/demo database, not production data.

BEGIN;

SET TIME ZONE 'Asia/Ho_Chi_Minh';

DO $$
DECLARE
    matched_tasks integer;
BEGIN
    IF to_regclass('public.task_remaining_effort_forecasts') IS NULL
            OR NOT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'tasks'
                  AND column_name = 'estimated_minutes'
            ) THEN
        RAISE EXCEPTION
            'Flyway V2 is not applied; run the application or apply V1 and V2 before this add-on';
    END IF;

    SELECT count(*)
    INTO matched_tasks
    FROM (
        VALUES
            ('Lab Timesheet Platform', 'Prepare attendance import mapping'),
            ('Lab Timesheet Platform', 'Review account lifecycle copy'),
            ('Lab Timesheet Platform', 'Verify project task filters'),
            ('Lab Timesheet Platform', 'Archived demo task'),
            ('Attendance Analytics', 'Reconcile August attendance totals'),
            ('Attendance Analytics', 'Investigate missing checkout queue'),
            ('Legacy Import Cleanup', 'Close legacy import reconciliation')
    ) AS expected(project_name, title)
    JOIN projects p ON p.name = expected.project_name
    JOIN tasks t ON t.project_id = p.id AND t.title = expected.title;

    IF matched_tasks <> 7 THEN
        RAISE EXCEPTION
            'Run scripts/demo-seed.sql first; expected 7 V1 demo tasks but found %', matched_tasks;
    END IF;
END;
$$;

-- A TRUNCATE is intentional here: it resets only the V2 append-only table for
-- a repeatable disposable fixture without issuing row-level DELETE mutations.
TRUNCATE TABLE task_remaining_effort_forecasts RESTART IDENTITY;

UPDATE tasks AS t
SET estimated_minutes = estimates.estimated_minutes
FROM (
    VALUES
        ('Lab Timesheet Platform', 'Prepare attendance import mapping', 240),
        ('Lab Timesheet Platform', 'Review account lifecycle copy', 360),
        ('Lab Timesheet Platform', 'Verify project task filters', 180),
        ('Lab Timesheet Platform', 'Archived demo task', 120),
        ('Attendance Analytics', 'Reconcile August attendance totals', 300),
        ('Attendance Analytics', 'Investigate missing checkout queue', 120),
        ('Legacy Import Cleanup', 'Close legacy import reconciliation', 360)
) AS estimates(project_name, title, estimated_minutes)
JOIN projects AS p ON p.name = estimates.project_name
WHERE t.project_id = p.id
  AND t.title = estimates.title;

-- Worked Tasks show a forecast whose snapshot is the lifetime effort before
-- the incoming assignment. Later incoming work closes the correction window
-- for the two unfinished examples; the completed example remains historical.
INSERT INTO task_remaining_effort_forecasts (
    project_id,
    task_id,
    incoming_membership_id,
    forecasting_leader_membership_id,
    assignment_started_at,
    remaining_minutes,
    actual_minutes_snapshot,
    initial_note,
    correction_reason,
    supersedes_forecast_id,
    created_at
)
SELECT
    p.id,
    t.id,
    incoming.id,
    leader.id,
    t.assigned_at,
    forecast.remaining_minutes,
    COALESCE((
        SELECT sum(w.minutes)
        FROM task_work_logs AS w
        WHERE w.task_id = t.id
          AND w.project_id = p.id
          AND w.created_at < t.assigned_at
    ), 0),
    forecast.initial_note,
    NULL,
    NULL,
    t.assigned_at
FROM (
    VALUES
        ('Lab Timesheet Platform', 'Prepare attendance import mapping', 'STU-1002', 'STU-1001', 240,
            'Baseline forecast after the legacy-column review.'),
        ('Attendance Analytics', 'Investigate missing checkout queue', 'STU-1006', 'STU-1002', 180,
            'Blocked pending the correction decision.'),
        ('Attendance Analytics', 'Reconcile August attendance totals', 'STU-1004', 'STU-1002', 120,
            'Historical forecast retained for the completed task.')
) AS forecast(project_name, title, incoming_code, leader_code, remaining_minutes, initial_note)
JOIN projects AS p ON p.name = forecast.project_name
JOIN tasks AS t ON t.project_id = p.id AND t.title = forecast.title
JOIN project_memberships AS incoming
    ON incoming.project_id = p.id
JOIN intern_profiles AS incoming_profile
    ON incoming_profile.user_id = incoming.intern_user_id
   AND incoming_profile.student_code = forecast.incoming_code
JOIN project_memberships AS leader
    ON leader.project_id = p.id
JOIN intern_profiles AS leader_profile
    ON leader_profile.user_id = leader.intern_user_id
   AND leader_profile.student_code = forecast.leader_code;

-- Append one valid correction successor for the completed historical task.
-- Its predecessor remains immutable and its actual snapshot includes the
-- pre-assignment work log, matching the V2 append-only correction shape.
INSERT INTO task_remaining_effort_forecasts (
    project_id,
    task_id,
    incoming_membership_id,
    forecasting_leader_membership_id,
    assignment_started_at,
    remaining_minutes,
    actual_minutes_snapshot,
    initial_note,
    correction_reason,
    supersedes_forecast_id,
    created_at
)
SELECT
    predecessor.project_id,
    predecessor.task_id,
    predecessor.incoming_membership_id,
    predecessor.forecasting_leader_membership_id,
    predecessor.assignment_started_at,
    150,
    predecessor.actual_minutes_snapshot,
    NULL,
    'Updated after reviewing the historical attendance variance.',
    predecessor.id,
    TIMESTAMPTZ '2026-08-03 10:00:00+07'
FROM task_remaining_effort_forecasts AS predecessor
JOIN tasks AS t ON t.id = predecessor.task_id
JOIN projects AS p ON p.id = predecessor.project_id
WHERE p.name = 'Attendance Analytics'
  AND t.title = 'Reconcile August attendance totals'
  AND predecessor.supersedes_forecast_id IS NULL;

COMMIT;
