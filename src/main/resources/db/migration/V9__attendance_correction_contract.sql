-- DB-018: an OVERDUE correction is undecided.
ALTER TABLE attendance_corrections DROP CONSTRAINT ck_attendance_corrections_pending_decision;
ALTER TABLE attendance_corrections ADD CONSTRAINT ck_attendance_corrections_pending_decision CHECK (
    status NOT IN ('PENDING', 'OVERDUE') OR (decided_by_mentor_user_id IS NULL AND decided_at IS NULL)
);

-- DB-017: a correction amendment or reversal carries a nonblank reason.
ALTER TABLE attendance_correction_events ADD CONSTRAINT ck_attendance_correction_events_reason CHECK (
    event_type NOT IN ('AMENDED', 'REVERSED') OR (note IS NOT NULL AND length(btrim(note)) > 0)
);

-- DB-017: no new AUTO_REJECTED or LOCKED event; NOT VALID keeps the stored history (D39).
ALTER TABLE attendance_correction_events ADD CONSTRAINT ck_attendance_correction_events_no_expiry_kinds
    CHECK (event_type NOT IN ('AUTO_REJECTED', 'LOCKED')) NOT VALID;

-- DB-017: correction history is append-only.
CREATE TRIGGER tr_attendance_correction_events_append_only
    BEFORE UPDATE OR DELETE ON attendance_correction_events
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_mutation('attendance correction events are append-only');
