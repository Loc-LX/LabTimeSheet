-- DB-022: retain exactly the lawful identity row shapes and protect transition timestamps.
ALTER TABLE app_users DROP CONSTRAINT ck_app_users_pending_password;
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_pending_password CHECK (
    (account_status = 'PENDING_ACTIVATION' AND password_hash IS NULL)
    OR (account_status IN ('ACTIVE', 'LOCKED')
        AND password_hash IS NOT NULL AND length(btrim(password_hash)) > 0)
    OR (account_status = 'DEACTIVATED'
        AND ((password_hash IS NULL AND activated_at IS NULL)
             OR (password_hash IS NOT NULL AND length(btrim(password_hash)) > 0
                 AND activated_at IS NOT NULL)))
);

ALTER TABLE app_users DROP CONSTRAINT ck_app_users_activated_state;
-- The hash rules, including a deactivated row's hash and activation pairing, stay in
-- ck_app_users_pending_password; this check constrains the activation timestamp by status only.
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_activated_state CHECK (
    (account_status = 'PENDING_ACTIVATION' AND activated_at IS NULL)
    OR (account_status IN ('ACTIVE', 'LOCKED') AND activated_at IS NOT NULL)
    OR account_status = 'DEACTIVATED'
);

ALTER TABLE app_users DROP CONSTRAINT ck_app_users_lock_timestamp;
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_lock_timestamp CHECK (
    (account_status = 'LOCKED' AND locked_at IS NOT NULL)
    OR (account_status = 'DEACTIVATED' AND (locked_at IS NULL OR activated_at IS NOT NULL))
    OR (account_status NOT IN ('LOCKED', 'DEACTIVATED') AND locked_at IS NULL)
);

CREATE FUNCTION enforce_app_user_identity_timestamps() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.activated_at IS DISTINCT FROM OLD.activated_at
       AND NOT (OLD.account_status = 'PENDING_ACTIVATION'
               AND NEW.account_status = 'ACTIVE'
               AND OLD.activated_at IS NULL
               AND NEW.activated_at IS NOT NULL) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'activated_at may change only when activating a pending account';
    END IF;

    IF NEW.locked_at IS DISTINCT FROM OLD.locked_at
       AND NOT ((OLD.account_status = 'ACTIVE' AND NEW.account_status = 'LOCKED'
                 AND OLD.locked_at IS NULL AND NEW.locked_at IS NOT NULL)
                OR (OLD.account_status = 'LOCKED' AND NEW.account_status = 'ACTIVE'
                    AND OLD.locked_at IS NOT NULL AND NEW.locked_at IS NULL)) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'locked_at may change only when locking or unlocking an account';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_app_users_identity_timestamps
BEFORE UPDATE OF activated_at, locked_at ON app_users
FOR EACH ROW
EXECUTE FUNCTION enforce_app_user_identity_timestamps();
