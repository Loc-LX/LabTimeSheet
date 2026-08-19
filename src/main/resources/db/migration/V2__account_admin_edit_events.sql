-- 1. Append-only Admin account edit trail (I2-PLAT-07).
-- GOV-009 requires a specific, narrow audit record for each changed account field instead of generic domain events.
CREATE TABLE account_admin_edit_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_user_id bigint NOT NULL,
    actor_user_id bigint NOT NULL,
    field_name varchar(32) NOT NULL,
    old_value varchar(320),
    new_value varchar(320),
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_account_admin_edit_events_target
        FOREIGN KEY (target_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_admin_edit_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_account_admin_edit_events_field CHECK (
        field_name IN ('EMAIL', 'DISPLAY_NAME', 'STUDENT_CODE', 'INTERNSHIP_START', 'INTERNSHIP_END')
    )
);

CREATE INDEX ix_account_admin_edit_events_target
    ON account_admin_edit_events (target_user_id, occurred_at, id);
CREATE INDEX ix_account_admin_edit_events_actor
    ON account_admin_edit_events (actor_user_id, occurred_at DESC);