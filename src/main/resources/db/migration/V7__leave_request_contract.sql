-- Reclassify legacy Intern withdrawals before tightening the status-dependent predicates.
UPDATE leave_requests
SET status = 'WITHDRAWN',
    withdrawn_at = cancelled_at,
    cancelled_at = NULL
WHERE status = 'CANCELLED'
  AND decided_at IS NULL;

ALTER TABLE leave_requests DROP CONSTRAINT ck_leave_requests_decision;
ALTER TABLE leave_requests ADD CONSTRAINT ck_leave_requests_decision CHECK (
    (status IN ('PENDING', 'OVERDUE', 'WITHDRAWN')
        AND decided_by_mentor_user_id IS NULL AND decided_at IS NULL)
    OR (status IN ('APPROVED', 'REJECTED', 'CANCELLED') AND decided_at IS NOT NULL)
);

ALTER TABLE leave_requests DROP CONSTRAINT ck_leave_requests_approval_actor;
ALTER TABLE leave_requests ADD CONSTRAINT ck_leave_requests_approval_actor CHECK (
    status NOT IN ('APPROVED', 'CANCELLED') OR decided_by_mentor_user_id IS NOT NULL
);

ALTER TABLE leave_requests ADD CONSTRAINT ck_leave_requests_withdrawal CHECK (
    (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL AND cancelled_at IS NULL)
    OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
);
