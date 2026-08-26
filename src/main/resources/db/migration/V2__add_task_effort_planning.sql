-- Partial Jira/Tempo: preserve whole-Task planning and reassignment forecasts.
-- Forecast rows are append-only: corrections are represented by successor rows.

BEGIN;

ALTER TABLE tasks
    ADD COLUMN estimated_minutes integer,
    ADD CONSTRAINT ck_tasks_estimated_minutes
        CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 527040);

CREATE TABLE task_remaining_effort_forecasts (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL,
    task_id bigint NOT NULL,
    incoming_membership_id bigint NOT NULL,
    forecasting_leader_membership_id bigint NOT NULL,
    assignment_started_at timestamptz NOT NULL,
    remaining_minutes integer NOT NULL,
    actual_minutes_snapshot bigint NOT NULL,
    initial_note text,
    correction_reason text,
    supersedes_forecast_id bigint,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_task_forecasts_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_forecasts_task_project
        FOREIGN KEY (task_id, project_id)
        REFERENCES tasks (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_forecasts_incoming_membership_project
        FOREIGN KEY (incoming_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_forecasts_leader_membership_project
        FOREIGN KEY (forecasting_leader_membership_id, project_id)
        REFERENCES project_memberships (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_forecasts_superseded
        FOREIGN KEY (supersedes_forecast_id) REFERENCES task_remaining_effort_forecasts (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_task_forecasts_remaining_minutes
        CHECK (remaining_minutes BETWEEN 1 AND 527040),
    CONSTRAINT ck_task_forecasts_actual_snapshot
        CHECK (actual_minutes_snapshot >= 0),
    CONSTRAINT ck_task_forecasts_initial_or_correction
        CHECK (
            (supersedes_forecast_id IS NULL AND correction_reason IS NULL)
            OR
            (supersedes_forecast_id IS NOT NULL AND length(btrim(correction_reason)) > 0
                AND initial_note IS NULL)
        ),
    CONSTRAINT ck_task_forecasts_initial_note
        CHECK (initial_note IS NULL OR length(btrim(initial_note)) > 0),
    CONSTRAINT uq_task_forecasts_one_successor UNIQUE (supersedes_forecast_id)
);

CREATE INDEX ix_task_forecasts_task_history
    ON task_remaining_effort_forecasts (task_id, project_id, assignment_started_at, id);
CREATE INDEX ix_task_forecasts_task_latest
    ON task_remaining_effort_forecasts (task_id, project_id, created_at DESC, id DESC);
CREATE INDEX ix_task_forecasts_incoming_assignment
    ON task_remaining_effort_forecasts (incoming_membership_id, project_id, assignment_started_at);
CREATE INDEX ix_task_forecasts_project
    ON task_remaining_effort_forecasts (project_id, id);
CREATE INDEX ix_task_forecasts_leader_membership
    ON task_remaining_effort_forecasts (forecasting_leader_membership_id, project_id, id);

CREATE FUNCTION reject_task_forecast_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'task remaining effort forecasts are append-only';
END;
$$;

CREATE TRIGGER tr_task_forecasts_append_only
    BEFORE UPDATE OR DELETE ON task_remaining_effort_forecasts
    FOR EACH ROW EXECUTE FUNCTION reject_task_forecast_mutation();

COMMIT;
