-- Seed thủ công cho môi trường local/test; chạy sau khi migration V1 đã hoàn tất.
-- Tài khoản mẫu dùng chung mật khẩu: SeedPass123!

BEGIN;

DO $seed$
DECLARE
    seed_now timestamptz := clock_timestamp();
    seed_password text := '{noop}SeedPass123!';
    admin_id bigint;
    mentor_id bigint;
    intern_leader_id bigint;
    intern_member_id bigint;
    intern_active_leader_id bigint;
    intern_alumni_id bigint;
    planned_project_id bigint;
    active_project_id bigint;
    completed_project_id bigint;
    planned_leader_membership_id bigint;
    planned_member_membership_id bigint;
    active_old_leader_membership_id bigint;
    active_current_leader_membership_id bigint;
    completed_old_leader_membership_id bigint;
    completed_current_leader_membership_id bigint;
BEGIN
    -- Tạo Admin/ Mentor/ Intern active để có thể đăng nhập và đi qua Project web flow.
    INSERT INTO app_users (
        email, display_name, password_hash, global_role, account_status, activated_at,
        created_at, updated_at)
    SELECT
        'project.seed.admin@example.test', 'Project Seed Admin', seed_password, 'ADMIN', 'ACTIVE',
        seed_now, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM app_users WHERE lower(btrim(email)) = 'project.seed.admin@example.test');

    SELECT id INTO admin_id
    FROM app_users
    WHERE lower(btrim(email)) = 'project.seed.admin@example.test';

    IF NOT EXISTS (SELECT 1 FROM app_users WHERE id = admin_id AND global_role = 'ADMIN') THEN
        RAISE EXCEPTION 'Seed admin email already belongs to a non-Admin account';
    END IF;

    UPDATE app_users
    SET display_name = 'Project Seed Admin',
        password_hash = seed_password,
        account_status = 'ACTIVE',
        activated_at = COALESCE(activated_at, seed_now),
        locked_at = NULL,
        deactivated_at = NULL,
        updated_at = seed_now
    WHERE id = admin_id;

    INSERT INTO app_users (
        email, display_name, password_hash, global_role, account_status, activated_at,
        created_by_user_id, created_at, updated_at)
    SELECT
        'project.seed.mentor@example.test', 'Project Seed Mentor', seed_password, 'MENTOR', 'ACTIVE',
        seed_now, admin_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM app_users WHERE lower(btrim(email)) = 'project.seed.mentor@example.test');

    SELECT id INTO mentor_id
    FROM app_users
    WHERE lower(btrim(email)) = 'project.seed.mentor@example.test';

    IF NOT EXISTS (SELECT 1 FROM app_users WHERE id = mentor_id AND global_role = 'MENTOR') THEN
        RAISE EXCEPTION 'Seed mentor email already belongs to a non-Mentor account';
    END IF;

    UPDATE app_users
    SET display_name = 'Project Seed Mentor',
        password_hash = seed_password,
        account_status = 'ACTIVE',
        activated_at = COALESCE(activated_at, seed_now),
        locked_at = NULL,
        deactivated_at = NULL,
        updated_at = seed_now
    WHERE id = mentor_id;

    INSERT INTO app_users (
        email, display_name, password_hash, global_role, account_status, activated_at,
        created_by_user_id, created_at, updated_at)
    SELECT
        'project.seed.leader@example.test', 'Project Seed Leader', seed_password, 'INTERN', 'ACTIVE',
        seed_now, admin_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM app_users WHERE lower(btrim(email)) = 'project.seed.leader@example.test');
    SELECT id INTO intern_leader_id
    FROM app_users
    WHERE lower(btrim(email)) = 'project.seed.leader@example.test';

    INSERT INTO app_users (
        email, display_name, password_hash, global_role, account_status, activated_at,
        created_by_user_id, created_at, updated_at)
    SELECT
        'project.seed.member@example.test', 'Project Seed Member', seed_password, 'INTERN', 'ACTIVE',
        seed_now, admin_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM app_users WHERE lower(btrim(email)) = 'project.seed.member@example.test');
    SELECT id INTO intern_member_id
    FROM app_users
    WHERE lower(btrim(email)) = 'project.seed.member@example.test';

    INSERT INTO app_users (
        email, display_name, password_hash, global_role, account_status, activated_at,
        created_by_user_id, created_at, updated_at)
    SELECT
        'project.seed.active-leader@example.test', 'Project Seed Active Leader', seed_password, 'INTERN', 'ACTIVE',
        seed_now, admin_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM app_users WHERE lower(btrim(email)) = 'project.seed.active-leader@example.test');
    SELECT id INTO intern_active_leader_id
    FROM app_users
    WHERE lower(btrim(email)) = 'project.seed.active-leader@example.test';

    INSERT INTO app_users (
        email, display_name, password_hash, global_role, account_status, activated_at,
        created_by_user_id, created_at, updated_at)
    SELECT
        'project.seed.alumni@example.test', 'Project Seed Alumni', seed_password, 'INTERN', 'ACTIVE',
        seed_now, admin_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM app_users WHERE lower(btrim(email)) = 'project.seed.alumni@example.test');
    SELECT id INTO intern_alumni_id
    FROM app_users
    WHERE lower(btrim(email)) = 'project.seed.alumni@example.test';

    IF EXISTS (
        SELECT 1
        FROM app_users
        WHERE id IN (intern_leader_id, intern_member_id, intern_active_leader_id, intern_alumni_id)
          AND global_role <> 'INTERN') THEN
        RAISE EXCEPTION 'One of the Project seed Intern emails belongs to a non-Intern account';
    END IF;

    UPDATE app_users
    SET password_hash = seed_password,
        account_status = 'ACTIVE',
        activated_at = COALESCE(activated_at, seed_now),
        locked_at = NULL,
        deactivated_at = NULL,
        updated_at = seed_now
    WHERE id IN (intern_leader_id, intern_member_id, intern_active_leader_id, intern_alumni_id);

    INSERT INTO intern_profiles (
        user_id, student_code, department, internship_start_date, internship_end_date,
        internship_status, activated_at, created_at, updated_at)
    VALUES
        (intern_leader_id, 'PRJ-SEED-001', 'Software Engineering', DATE '2025-01-01', DATE '2099-12-31',
            'ACTIVE', seed_now, seed_now, seed_now),
        (intern_member_id, 'PRJ-SEED-002', 'Software Engineering', DATE '2025-01-01', DATE '2099-12-31',
            'ACTIVE', seed_now, seed_now, seed_now),
        (intern_active_leader_id, 'PRJ-SEED-003', 'Software Engineering', DATE '2025-01-01', DATE '2099-12-31',
            'ACTIVE', seed_now, seed_now, seed_now),
        (intern_alumni_id, 'PRJ-SEED-004', 'Software Engineering', DATE '2025-01-01', DATE '2099-12-31',
            'ACTIVE', seed_now, seed_now, seed_now)
    ON CONFLICT (user_id) DO UPDATE
    SET student_code = EXCLUDED.student_code,
        department = EXCLUDED.department,
        internship_start_date = EXCLUDED.internship_start_date,
        internship_end_date = EXCLUDED.internship_end_date,
        internship_status = 'ACTIVE',
        activated_at = COALESCE(intern_profiles.activated_at, EXCLUDED.activated_at),
        completed_at = NULL,
        withdrawn_at = NULL,
        updated_at = EXCLUDED.updated_at;

    -- Cho phép truy cập thẳng vào các Project seed mà không phải chạy lại bootstrap thủ công.
    UPDATE system_state
    SET initialized = true,
        initialized_at = COALESCE(initialized_at, seed_now),
        bootstrap_admin_id = COALESCE(bootstrap_admin_id, admin_id),
        updated_at = seed_now
    WHERE singleton_id = 1;

    -- Project Planned: có Leader hiện tại và một thành viên đủ điều kiện để thử đổi Leader.
    INSERT INTO projects (
        mentor_user_id, name, description, status, start_date, end_date,
        created_at, updated_at)
    SELECT
        mentor_id,
        'Seed Project - Planned',
        'Project mẫu ở trạng thái Planned để thử thêm thành viên và đổi Leader.',
        'PLANNED', DATE '2026-08-01', DATE '2026-10-31', seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM projects
        WHERE mentor_user_id = mentor_id AND name = 'Seed Project - Planned');
    SELECT id INTO planned_project_id
    FROM projects
    WHERE mentor_user_id = mentor_id AND name = 'Seed Project - Planned';

    INSERT INTO project_memberships (
        project_id, intern_user_id, joined_at, added_by_user_id, created_at, updated_at)
    SELECT planned_project_id, intern_leader_id, TIMESTAMPTZ '2026-08-01 09:00:00+07', mentor_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_memberships
        WHERE project_id = planned_project_id AND intern_user_id = intern_leader_id AND left_at IS NULL);
    INSERT INTO project_memberships (
        project_id, intern_user_id, joined_at, added_by_user_id, created_at, updated_at)
    SELECT planned_project_id, intern_member_id, TIMESTAMPTZ '2026-08-02 09:00:00+07', mentor_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_memberships
        WHERE project_id = planned_project_id AND intern_user_id = intern_member_id AND left_at IS NULL);

    SELECT id INTO planned_leader_membership_id
    FROM project_memberships
    WHERE project_id = planned_project_id AND intern_user_id = intern_leader_id AND left_at IS NULL;
    SELECT id INTO planned_member_membership_id
    FROM project_memberships
    WHERE project_id = planned_project_id AND intern_user_id = intern_member_id AND left_at IS NULL;

    INSERT INTO project_leadership_terms (
        project_id, membership_id, started_at, appointed_by_mentor_user_id, created_at)
    SELECT planned_project_id, planned_leader_membership_id,
        TIMESTAMPTZ '2026-08-01 09:00:00+07', mentor_id, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_leadership_terms
        WHERE project_id = planned_project_id
          AND membership_id = planned_leader_membership_id
          AND started_at = TIMESTAMPTZ '2026-08-01 09:00:00+07');

    -- Project Active: có một nhiệm kỳ cũ đã đóng và một Leader hiện tại.
    INSERT INTO projects (
        mentor_user_id, name, description, status, start_date, end_date,
        activated_at, created_at, updated_at)
    SELECT
        mentor_id,
        'Seed Project - Active',
        'Project mẫu Active với lịch sử bàn giao Leader.',
        'ACTIVE', DATE '2026-07-01', DATE '2026-11-30',
        TIMESTAMPTZ '2026-07-02 09:00:00+07', seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM projects
        WHERE mentor_user_id = mentor_id AND name = 'Seed Project - Active');
    SELECT id INTO active_project_id
    FROM projects
    WHERE mentor_user_id = mentor_id AND name = 'Seed Project - Active';

    INSERT INTO project_memberships (
        project_id, intern_user_id, joined_at, added_by_user_id, created_at, updated_at)
    SELECT active_project_id, intern_member_id, TIMESTAMPTZ '2026-07-03 09:00:00+07', mentor_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_memberships
        WHERE project_id = active_project_id AND intern_user_id = intern_member_id AND left_at IS NULL);
    INSERT INTO project_memberships (
        project_id, intern_user_id, joined_at, added_by_user_id, created_at, updated_at)
    SELECT active_project_id, intern_active_leader_id, TIMESTAMPTZ '2026-07-04 09:00:00+07', mentor_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_memberships
        WHERE project_id = active_project_id AND intern_user_id = intern_active_leader_id AND left_at IS NULL);

    SELECT id INTO active_old_leader_membership_id
    FROM project_memberships
    WHERE project_id = active_project_id AND intern_user_id = intern_member_id AND left_at IS NULL;
    SELECT id INTO active_current_leader_membership_id
    FROM project_memberships
    WHERE project_id = active_project_id AND intern_user_id = intern_active_leader_id AND left_at IS NULL;

    INSERT INTO project_leadership_terms (
        project_id, membership_id, started_at, ended_at,
        appointed_by_mentor_user_id, ended_by_mentor_user_id, created_at)
    SELECT active_project_id, active_old_leader_membership_id,
        TIMESTAMPTZ '2026-07-03 09:00:00+07', TIMESTAMPTZ '2026-08-12 09:00:00+07',
        mentor_id, mentor_id, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_leadership_terms
        WHERE project_id = active_project_id
          AND membership_id = active_old_leader_membership_id
          AND started_at = TIMESTAMPTZ '2026-07-03 09:00:00+07');
    INSERT INTO project_leadership_terms (
        project_id, membership_id, started_at, appointed_by_mentor_user_id, created_at)
    SELECT active_project_id, active_current_leader_membership_id,
        TIMESTAMPTZ '2026-08-12 09:00:00+07', mentor_id, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_leadership_terms
        WHERE project_id = active_project_id
          AND membership_id = active_current_leader_membership_id
          AND started_at = TIMESTAMPTZ '2026-08-12 09:00:00+07');

    -- Project Completed: giữ lại đầy đủ hai nhiệm kỳ lịch sử và không còn Leader hiện tại.
    INSERT INTO projects (
        mentor_user_id, name, description, status, start_date, end_date,
        activated_at, completed_at, created_at, updated_at)
    SELECT
        mentor_id,
        'Seed Project - Completed',
        'Project mẫu Completed để kiểm tra quyền xem lịch sử.',
        'COMPLETED', DATE '2026-04-01', DATE '2026-06-30',
        TIMESTAMPTZ '2026-04-02 09:00:00+07', TIMESTAMPTZ '2026-07-01 09:00:00+07', seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM projects
        WHERE mentor_user_id = mentor_id AND name = 'Seed Project - Completed');
    SELECT id INTO completed_project_id
    FROM projects
    WHERE mentor_user_id = mentor_id AND name = 'Seed Project - Completed';

    INSERT INTO project_memberships (
        project_id, intern_user_id, joined_at, left_at, added_by_user_id,
        removed_by_mentor_user_id, created_at, updated_at)
    SELECT completed_project_id, intern_leader_id,
        TIMESTAMPTZ '2026-04-02 09:00:00+07', TIMESTAMPTZ '2026-06-15 09:00:00+07',
        mentor_id, mentor_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_memberships
        WHERE project_id = completed_project_id
          AND intern_user_id = intern_leader_id
          AND joined_at = TIMESTAMPTZ '2026-04-02 09:00:00+07');
    INSERT INTO project_memberships (
        project_id, intern_user_id, joined_at, left_at, added_by_user_id,
        removed_by_mentor_user_id, created_at, updated_at)
    SELECT completed_project_id, intern_alumni_id,
        TIMESTAMPTZ '2026-06-15 09:00:00+07', TIMESTAMPTZ '2026-07-01 09:00:00+07',
        mentor_id, mentor_id, seed_now, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_memberships
        WHERE project_id = completed_project_id
          AND intern_user_id = intern_alumni_id
          AND joined_at = TIMESTAMPTZ '2026-06-15 09:00:00+07');

    SELECT id INTO completed_old_leader_membership_id
    FROM project_memberships
    WHERE project_id = completed_project_id
      AND intern_user_id = intern_leader_id
      AND joined_at = TIMESTAMPTZ '2026-04-02 09:00:00+07';
    SELECT id INTO completed_current_leader_membership_id
    FROM project_memberships
    WHERE project_id = completed_project_id
      AND intern_user_id = intern_alumni_id
      AND joined_at = TIMESTAMPTZ '2026-06-15 09:00:00+07';

    INSERT INTO project_leadership_terms (
        project_id, membership_id, started_at, ended_at,
        appointed_by_mentor_user_id, ended_by_mentor_user_id, created_at)
    SELECT completed_project_id, completed_old_leader_membership_id,
        TIMESTAMPTZ '2026-04-02 09:00:00+07', TIMESTAMPTZ '2026-06-15 09:00:00+07',
        mentor_id, mentor_id, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_leadership_terms
        WHERE project_id = completed_project_id
          AND membership_id = completed_old_leader_membership_id
          AND started_at = TIMESTAMPTZ '2026-04-02 09:00:00+07');
    INSERT INTO project_leadership_terms (
        project_id, membership_id, started_at, ended_at,
        appointed_by_mentor_user_id, ended_by_mentor_user_id, created_at)
    SELECT completed_project_id, completed_current_leader_membership_id,
        TIMESTAMPTZ '2026-06-15 09:00:00+07', TIMESTAMPTZ '2026-07-01 09:00:00+07',
        mentor_id, mentor_id, seed_now
    WHERE NOT EXISTS (
        SELECT 1 FROM project_leadership_terms
        WHERE project_id = completed_project_id
          AND membership_id = completed_current_leader_membership_id
          AND started_at = TIMESTAMPTZ '2026-06-15 09:00:00+07');
END
$seed$;

COMMIT;
