-- Platform schema expansion for the attendance, project, identity, and notification contracts.

ALTER TABLE intern_profiles
    ADD COLUMN responsible_mentor_user_id bigint,
    ADD CONSTRAINT fk_intern_profiles_responsible_mentor
        FOREIGN KEY (responsible_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT;

CREATE FUNCTION enforce_responsible_mentor_role() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.responsible_mentor_user_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM app_users
           WHERE id = NEW.responsible_mentor_user_id AND global_role = 'MENTOR'
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'responsible mentor must be a MENTOR account';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_intern_profiles_responsible_mentor_role
    BEFORE INSERT OR UPDATE OF responsible_mentor_user_id ON intern_profiles
    FOR EACH ROW EXECUTE FUNCTION enforce_responsible_mentor_role();

ALTER TABLE projects
    ADD COLUMN cancelled_by_mentor_user_id bigint,
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancellation_reason text,
    ADD CONSTRAINT fk_projects_cancelled_by_mentor
        FOREIGN KEY (cancelled_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT;

ALTER TABLE projects DROP CONSTRAINT ck_projects_status;
ALTER TABLE projects ADD CONSTRAINT ck_projects_status
    CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'));
ALTER TABLE projects DROP CONSTRAINT ck_projects_activation;
ALTER TABLE projects ADD CONSTRAINT ck_projects_activation CHECK (
    (status = 'PLANNED' AND activated_at IS NULL)
    OR (status IN ('ACTIVE', 'COMPLETED') AND activated_at IS NOT NULL)
    OR status = 'CANCELLED'
);
ALTER TABLE project_invitations DROP CONSTRAINT ck_project_invitations_resolution_code;
ALTER TABLE project_invitations ADD CONSTRAINT ck_project_invitations_resolution_code CHECK (
    resolution_code IS NULL OR resolution_code IN (
        'INVITEE_ACCEPTED', 'INVITEE_DECLINED',
        'INVITER_REVOKED', 'MENTOR_REVOKED', 'LEADER_CHANGED',
        'PROJECT_COMPLETED', 'PROJECT_CANCELLED', 'INVITEE_INELIGIBLE', 'MENTOR_DIRECT_ADD'
    )
);
ALTER TABLE project_invitations DROP CONSTRAINT ck_project_invitations_resolution_state;
ALTER TABLE project_invitations ADD CONSTRAINT ck_project_invitations_resolution_state CHECK (
    (status = 'PENDING'
        AND accepted_membership_id IS NULL AND resolved_at IS NULL
        AND resolved_by_user_id IS NULL AND resolution_code IS NULL)
    OR (status = 'ACCEPTED'
        AND accepted_membership_id IS NOT NULL AND resolved_at IS NOT NULL
        AND resolved_by_user_id IS NOT NULL AND resolution_code = 'INVITEE_ACCEPTED')
    OR (status = 'DECLINED'
        AND accepted_membership_id IS NULL AND resolved_at IS NOT NULL
        AND resolved_by_user_id IS NOT NULL AND resolution_code = 'INVITEE_DECLINED')
    OR (status = 'REVOKED'
        AND accepted_membership_id IS NULL AND resolved_at IS NOT NULL
        AND resolution_code IN (
            'INVITER_REVOKED', 'MENTOR_REVOKED', 'LEADER_CHANGED',
            'PROJECT_COMPLETED', 'PROJECT_CANCELLED', 'INVITEE_INELIGIBLE'
        )
        AND (resolved_by_user_id IS NOT NULL
             OR resolution_code IN ('LEADER_CHANGED', 'PROJECT_COMPLETED', 'PROJECT_CANCELLED', 'INVITEE_INELIGIBLE')))
    OR (status = 'SUPERSEDED'
        AND accepted_membership_id IS NULL AND resolved_at IS NOT NULL
        AND resolved_by_user_id IS NOT NULL AND resolution_code = 'MENTOR_DIRECT_ADD')
);

ALTER TABLE leave_requests ADD COLUMN withdrawn_at timestamptz;
ALTER TABLE leave_requests DROP CONSTRAINT ck_leave_requests_status;
ALTER TABLE leave_requests ADD CONSTRAINT ck_leave_requests_status CHECK (
    status IN ('PENDING', 'OVERDUE', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')
);
ALTER TABLE leave_requests DROP CONSTRAINT ck_leave_requests_decision;
ALTER TABLE leave_requests ADD CONSTRAINT ck_leave_requests_decision CHECK (
    status IN ('PENDING', 'OVERDUE', 'WITHDRAWN', 'CANCELLED') OR decided_at IS NOT NULL
);
ALTER TABLE leave_requests DROP CONSTRAINT ex_leave_requests_no_overlap;
ALTER TABLE leave_requests ADD CONSTRAINT ex_leave_requests_no_overlap
    EXCLUDE USING gist (
        intern_user_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status IN ('PENDING', 'OVERDUE', 'APPROVED'));

ALTER TABLE leave_request_days ADD COLUMN approval_withdrawn_at timestamptz;

ALTER TABLE attendance_corrections DROP CONSTRAINT ck_attendance_corrections_status;
ALTER TABLE attendance_corrections ADD CONSTRAINT ck_attendance_corrections_status
    CHECK (status IN ('PENDING', 'OVERDUE', 'APPROVED', 'REJECTED'));
ALTER TABLE attendance_corrections DROP CONSTRAINT ck_attendance_corrections_decided_at;
ALTER TABLE attendance_corrections ADD CONSTRAINT ck_attendance_corrections_decided_at CHECK (
    status IN ('PENDING', 'OVERDUE') OR decided_at IS NOT NULL
);

ALTER TABLE attendance_correction_events DROP CONSTRAINT ck_attendance_correction_events_type;
ALTER TABLE attendance_correction_events ADD CONSTRAINT ck_attendance_correction_events_type CHECK (
    event_type IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'REOPENED', 'AUTO_REJECTED', 'LOCKED',
                   'OVERDUE', 'AMENDED', 'REVERSED')
);
ALTER TABLE attendance_correction_events DROP CONSTRAINT ck_attendance_correction_events_from_status;
ALTER TABLE attendance_correction_events ADD CONSTRAINT ck_attendance_correction_events_from_status CHECK (
    from_status IS NULL OR from_status IN ('PENDING', 'OVERDUE', 'APPROVED', 'REJECTED')
);
ALTER TABLE attendance_correction_events DROP CONSTRAINT ck_attendance_correction_events_to_status;
ALTER TABLE attendance_correction_events ADD CONSTRAINT ck_attendance_correction_events_to_status CHECK (
    to_status IN ('PENDING', 'OVERDUE', 'APPROVED', 'REJECTED')
);

ALTER TABLE app_users DROP CONSTRAINT ck_app_users_pending_password;
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_pending_password CHECK (
    (account_status = 'PENDING_ACTIVATION' AND password_hash IS NULL)
    OR (account_status = 'DEACTIVATED'
        AND (password_hash IS NULL OR length(btrim(password_hash)) > 0))
    OR (account_status NOT IN ('PENDING_ACTIVATION', 'DEACTIVATED') AND length(btrim(password_hash)) > 0)
);
ALTER TABLE app_users DROP CONSTRAINT ck_app_users_activated_state;
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_activated_state CHECK (
    (account_status = 'PENDING_ACTIVATION' AND activated_at IS NULL)
    OR account_status = 'DEACTIVATED'
    OR (account_status <> 'PENDING_ACTIVATION' AND activated_at IS NOT NULL)
);
ALTER TABLE app_users DROP CONSTRAINT ck_app_users_lock_timestamp;
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_lock_timestamp CHECK (
    (account_status = 'LOCKED' AND locked_at IS NOT NULL)
    OR (account_status = 'DEACTIVATED')
    OR (account_status <> 'LOCKED' AND locked_at IS NULL)
);

ALTER TABLE notifications ADD COLUMN project_id bigint;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL;
UPDATE notifications AS n
SET project_id = p.id
FROM projects AS p
WHERE substring(n.action_url FROM '^/projects/([0-9]+)(/|\?|$)') = p.id::text;
CREATE INDEX ix_notifications_project ON notifications (project_id);

CREATE INDEX ix_intern_profiles_responsible_mentor_user_id
    ON intern_profiles (responsible_mentor_user_id);
CREATE INDEX ix_projects_cancelled_by_mentor_user_id
    ON projects (cancelled_by_mentor_user_id);

CREATE TABLE attendance_periods (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    intern_user_id bigint NOT NULL,
    period_month date NOT NULL,
    status varchar(16) NOT NULL,
    finalized_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_attendance_periods_intern
        FOREIGN KEY (intern_user_id) REFERENCES intern_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_periods_intern_month UNIQUE (intern_user_id, period_month),
    CONSTRAINT ck_attendance_periods_status CHECK (status IN ('OPEN', 'FINALIZED')),
    CONSTRAINT ck_attendance_periods_finalized_at CHECK (
        status NOT IN ('OPEN', 'FINALIZED')
        OR (status = 'OPEN' AND finalized_at IS NULL)
        OR (status = 'FINALIZED' AND finalized_at IS NOT NULL)
    ),
    CONSTRAINT ck_attendance_periods_month_start CHECK (extract(day FROM period_month) = 1),
    CONSTRAINT ck_attendance_periods_version CHECK (version >= 0)
);

CREATE TABLE attendance_period_reopens (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attendance_period_id bigint NOT NULL,
    requester_user_id bigint NOT NULL,
    attendance_record_id bigint,
    start_date date,
    end_date date,
    reason text NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT current_timestamp,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    decided_by_admin_user_id bigint,
    decided_at timestamptz,
    rejection_reason text,
    refinalized_by_mentor_user_id bigint,
    refinalized_at timestamptz,
    CONSTRAINT fk_attendance_period_reopens_period
        FOREIGN KEY (attendance_period_id) REFERENCES attendance_periods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_period_reopens_requester
        FOREIGN KEY (requester_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_period_reopens_attendance_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_period_reopens_decided_by_admin
        FOREIGN KEY (decided_by_admin_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_period_reopens_refinalized_by_mentor
        FOREIGN KEY (refinalized_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attendance_period_reopens_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_attendance_period_reopens_scope CHECK (
        (attendance_record_id IS NOT NULL AND start_date IS NULL AND end_date IS NULL)
        OR (attendance_record_id IS NULL AND start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date)
    ),
    CONSTRAINT ck_attendance_period_reopens_decision CHECK (
        (status = 'PENDING' AND decided_by_admin_user_id IS NULL AND decided_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND decided_by_admin_user_id IS NOT NULL AND decided_at IS NOT NULL)
    ),
    CONSTRAINT ck_attendance_period_reopens_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_attendance_period_reopens_rejection_reason CHECK (
        (status = 'REJECTED' AND length(btrim(rejection_reason)) > 0)
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    ),
    CONSTRAINT ck_attendance_period_reopens_refinalized CHECK (
        (refinalized_by_mentor_user_id IS NULL AND refinalized_at IS NULL)
        OR (refinalized_by_mentor_user_id IS NOT NULL AND refinalized_at IS NOT NULL)
    )
);

CREATE TABLE attendance_exceptions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attendance_record_id bigint NOT NULL,
    violation_kind varchar(24) NOT NULL,
    source varchar(16) NOT NULL,
    reason text NOT NULL,
    submitted_at timestamptz NOT NULL DEFAULT current_timestamp,
    submission_deadline timestamptz NOT NULL,
    decision_deadline timestamptz NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    decided_by_mentor_user_id bigint,
    decided_at timestamptz,
    decision_note text,
    CONSTRAINT fk_attendance_exceptions_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_exceptions_decided_by
        FOREIGN KEY (decided_by_mentor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attendance_exceptions_record_kind UNIQUE (attendance_record_id, violation_kind),
    CONSTRAINT ck_attendance_exceptions_kind CHECK (violation_kind IN ('LATE_ARRIVAL', 'EARLY_DEPARTURE')),
    CONSTRAINT ck_attendance_exceptions_source CHECK (source IN ('REQUEST', 'MENTOR_MARK')),
    CONSTRAINT ck_attendance_exceptions_status CHECK (status IN ('PENDING', 'OVERDUE', 'EXCUSED', 'UNEXCUSED')),
    CONSTRAINT ck_attendance_exceptions_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_attendance_exceptions_submission_deadline CHECK (submission_deadline >= submitted_at),
    CONSTRAINT ck_attendance_exceptions_decision_deadline CHECK (decision_deadline > submitted_at)
);

CREATE TABLE attendance_exception_decisions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attendance_exception_id bigint NOT NULL,
    decision_kind varchar(16) NOT NULL,
    outcome varchar(16) NOT NULL,
    decision_note text,
    actor_user_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp,
    reason text,
    CONSTRAINT fk_attendance_exception_decisions_exception
        FOREIGN KEY (attendance_exception_id) REFERENCES attendance_exceptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_exception_decisions_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attendance_exception_decisions_kind CHECK (decision_kind IN ('DECISION', 'AMENDMENT', 'REVERSAL')),
    CONSTRAINT ck_attendance_exception_decisions_outcome CHECK (outcome IN ('EXCUSED', 'UNEXCUSED')),
    CONSTRAINT ck_attendance_exception_decisions_reason CHECK (
        decision_kind = 'DECISION' OR length(btrim(reason)) > 0
    )
);

CREATE TABLE leave_request_decisions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leave_request_id bigint NOT NULL,
    decision_kind varchar(16) NOT NULL,
    outcome varchar(16) NOT NULL,
    decision_note text,
    actor_user_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp,
    reason text,
    CONSTRAINT fk_leave_request_decisions_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_request_decisions_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_leave_request_decisions_kind CHECK (decision_kind IN ('DECISION', 'AMENDMENT', 'REVERSAL')),
    CONSTRAINT ck_leave_request_decisions_outcome CHECK (outcome IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_leave_request_decisions_reason CHECK (
        decision_kind = 'DECISION' OR length(btrim(reason)) > 0
    )
);

CREATE TABLE task_status_transitions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL,
    from_status varchar(24) NOT NULL,
    to_status varchar(24) NOT NULL,
    actor_user_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp,
    reason text,
    CONSTRAINT fk_task_status_transitions_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_status_transitions_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_status_transitions_reopen_reason CHECK (
        from_status <> 'DONE' OR to_status <> 'IN_PROGRESS' OR length(btrim(reason)) > 0
    )
);

CREATE INDEX ix_attendance_period_reopens_attendance_period_id
    ON attendance_period_reopens (attendance_period_id);
CREATE INDEX ix_attendance_period_reopens_requester_user_id
    ON attendance_period_reopens (requester_user_id);
CREATE INDEX ix_attendance_period_reopens_attendance_record_id
    ON attendance_period_reopens (attendance_record_id);
CREATE INDEX ix_attendance_period_reopens_decided_by_admin_user_id
    ON attendance_period_reopens (decided_by_admin_user_id);
CREATE INDEX ix_attendance_period_reopens_refinalized_by_mentor_user_id
    ON attendance_period_reopens (refinalized_by_mentor_user_id);
CREATE INDEX ix_attendance_exceptions_decided_by_mentor_user_id
    ON attendance_exceptions (decided_by_mentor_user_id);
CREATE INDEX ix_attendance_exception_decisions_attendance_exception_id
    ON attendance_exception_decisions (attendance_exception_id);
CREATE INDEX ix_attendance_exception_decisions_actor_user_id
    ON attendance_exception_decisions (actor_user_id);
CREATE INDEX ix_leave_request_decisions_leave_request_id
    ON leave_request_decisions (leave_request_id);
CREATE INDEX ix_leave_request_decisions_actor_user_id
    ON leave_request_decisions (actor_user_id);
CREATE INDEX ix_task_status_transitions_task_id
    ON task_status_transitions (task_id);
CREATE INDEX ix_task_status_transitions_actor_user_id
    ON task_status_transitions (actor_user_id);

CREATE FUNCTION reject_append_only_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = TG_ARGV[0];
END;
$$;

CREATE TRIGGER tr_attendance_exception_decisions_append_only
    BEFORE UPDATE OR DELETE ON attendance_exception_decisions
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_mutation('attendance exception decisions are append-only');
CREATE TRIGGER tr_leave_request_decisions_append_only
    BEFORE UPDATE OR DELETE ON leave_request_decisions
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_mutation('leave request decisions are append-only');
CREATE TRIGGER tr_task_status_transitions_append_only
    BEFORE UPDATE OR DELETE ON task_status_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_mutation('task status transitions are append-only');

-- Seed one period for each Intern and attendance month, then apply ATT-020 at this transaction's
-- server time using the policy timezone in force on the fifth day of the following month.
INSERT INTO attendance_periods (intern_user_id, period_month, status)
SELECT DISTINCT intern_user_id, date_trunc('month', work_date)::date, 'OPEN'
FROM attendance_records;

WITH deadlines AS (
    SELECT p.id,
           ((p.period_month + INTERVAL '1 month' + INTERVAL '4 days')::date + TIME '23:59')
               AT TIME ZONE policy.timezone_name AS deadline_at
    FROM attendance_periods p
    JOIN LATERAL (
        SELECT timezone_name
        FROM attendance_policy_versions
        WHERE effective_from <= (p.period_month + INTERVAL '1 month' + INTERVAL '4 days')::date
        ORDER BY effective_from DESC
        LIMIT 1
    ) policy ON true
)
UPDATE attendance_periods period
SET status = 'FINALIZED', finalized_at = transaction_timestamp()
FROM deadlines
WHERE period.id = deadlines.id
  AND transaction_timestamp() >= deadlines.deadline_at
  AND NOT EXISTS (
      SELECT 1 FROM leave_requests leave
      WHERE leave.intern_user_id = period.intern_user_id
        AND leave.status IN ('PENDING', 'OVERDUE')
        AND daterange(leave.start_date, leave.end_date, '[]')
            && daterange(period.period_month, (period.period_month + INTERVAL '1 month')::date, '[)')
  )
  AND NOT EXISTS (
      SELECT 1 FROM attendance_corrections correction
      JOIN attendance_records record ON record.id = correction.attendance_record_id
      WHERE record.intern_user_id = period.intern_user_id
        AND date_trunc('month', record.work_date)::date = period.period_month
        AND correction.status IN ('PENDING', 'OVERDUE')
  )
  AND NOT EXISTS (
      SELECT 1 FROM attendance_exceptions exception
      JOIN attendance_records record ON record.id = exception.attendance_record_id
      WHERE record.intern_user_id = period.intern_user_id
        AND date_trunc('month', record.work_date)::date = period.period_month
        AND exception.status IN ('PENDING', 'OVERDUE')
  );
