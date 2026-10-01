-- DB-017 / DB-018: once a leave day has been marked as withdrawn (approval_withdrawn_at IS NOT NULL),
-- its snapshot columns and the withdrawal timestamp itself become immutable.
-- When setting the mark (OLD null → NEW non-null), snapshot columns must not change in the same statement.

CREATE FUNCTION prevent_leave_day_withdrawn_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.approval_withdrawn_at IS NOT NULL THEN
        -- Already withdrawn: the mark itself must not change
        IF NEW.approval_withdrawn_at IS DISTINCT FROM OLD.approval_withdrawn_at THEN
            RAISE EXCEPTION 'tr_leave_request_days_withdrawn_guard: approval_withdrawn_at is immutable once set on leave_request_days (%,%)',
                OLD.leave_request_id, OLD.leave_date
                USING ERRCODE = '23514', CONSTRAINT = 'tr_leave_request_days_withdrawn_guard';
        END IF;
        -- Already withdrawn: snapshot columns must not change
        IF NEW.leave_request_id IS DISTINCT FROM OLD.leave_request_id
            OR NEW.leave_date IS DISTINCT FROM OLD.leave_date
            OR NEW.quota_month IS DISTINCT FROM OLD.quota_month
            OR NEW.policy_version_id IS DISTINCT FROM OLD.policy_version_id
            OR NEW.monthly_quota_snapshot IS DISTINCT FROM OLD.monthly_quota_snapshot THEN
            RAISE EXCEPTION 'tr_leave_request_days_withdrawn_guard: snapshot columns are immutable once approval is withdrawn on leave_request_days (%,%)',
                OLD.leave_request_id, OLD.leave_date
                USING ERRCODE = '23514', CONSTRAINT = 'tr_leave_request_days_withdrawn_guard';
        END IF;
    END IF;
    -- Setting the mark: snapshot columns must not change in the same statement
    IF OLD.approval_withdrawn_at IS NULL AND NEW.approval_withdrawn_at IS NOT NULL THEN
        IF NEW.leave_request_id IS DISTINCT FROM OLD.leave_request_id
            OR NEW.leave_date IS DISTINCT FROM OLD.leave_date
            OR NEW.quota_month IS DISTINCT FROM OLD.quota_month
            OR NEW.policy_version_id IS DISTINCT FROM OLD.policy_version_id
            OR NEW.monthly_quota_snapshot IS DISTINCT FROM OLD.monthly_quota_snapshot THEN
            RAISE EXCEPTION 'tr_leave_request_days_withdrawn_guard: snapshot columns must not change when setting approval_withdrawn_at on leave_request_days (%,%)',
                OLD.leave_request_id, OLD.leave_date
                USING ERRCODE = '23514', CONSTRAINT = 'tr_leave_request_days_withdrawn_guard';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_leave_request_days_withdrawn_guard
BEFORE UPDATE ON leave_request_days
FOR EACH ROW
EXECUTE FUNCTION prevent_leave_day_withdrawn_mutation();
