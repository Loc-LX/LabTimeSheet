ALTER TABLE projects
    ADD CONSTRAINT ck_projects_cancellation CHECK (
        status <> 'CANCELLED'
        OR (
            cancelled_by_mentor_user_id IS NOT NULL
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND btrim(cancellation_reason) <> ''
        )
    );
