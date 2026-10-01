-- NOT-012: NOT_REQUIRED and UNAVAILABLE carry no email payload; every other state keeps requiring one.
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_email_payload;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_email_payload CHECK (
    (email_status IN ('NOT_REQUIRED', 'UNAVAILABLE')
        AND email_to IS NULL AND email_subject IS NULL AND email_body IS NULL)
    OR (email_status NOT IN ('NOT_REQUIRED', 'UNAVAILABLE')
        AND email_to IS NOT NULL AND email_subject IS NOT NULL AND email_body IS NOT NULL)
);
