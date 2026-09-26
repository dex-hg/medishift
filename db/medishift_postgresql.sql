-- Medishift: esquema físico PostgreSQL
-- La aplicación suministra los UUID y valida las reglas de aprobación entre tablas.

BEGIN;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE institution (
    id_institution uuid PRIMARY KEY,
    code_institution varchar(32) NOT NULL UNIQUE,
    name_institution varchar(140) NOT NULL,
    time_zone_institution varchar(64) NOT NULL,
    status_institution varchar(16) NOT NULL,
    CONSTRAINT institution_code_not_blank CHECK (btrim(code_institution) <> ''),
    CONSTRAINT institution_name_not_blank CHECK (btrim(name_institution) <> ''),
    CONSTRAINT institution_time_zone_not_blank CHECK (btrim(time_zone_institution) <> ''),
    CONSTRAINT institution_status_valid CHECK (status_institution IN ('active', 'inactive'))
);

CREATE TABLE role (
    id_role smallint PRIMARY KEY,
    code_role varchar(32) NOT NULL UNIQUE,
    description_role varchar(120) NOT NULL,
    CONSTRAINT role_id_positive CHECK (id_role > 0),
    CONSTRAINT role_code_not_blank CHECK (btrim(code_role) <> ''),
    CONSTRAINT role_description_not_blank CHECK (btrim(description_role) <> '')
);

CREATE TABLE specialty (
    id_specialty uuid PRIMARY KEY,
    code_specialty varchar(32) NOT NULL UNIQUE,
    name_specialty varchar(100) NOT NULL,
    CONSTRAINT specialty_code_not_blank CHECK (btrim(code_specialty) <> ''),
    CONSTRAINT specialty_name_not_blank CHECK (btrim(name_specialty) <> '')
);

CREATE TABLE user_account (
    id_user_account uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    email_user_account varchar(254) NOT NULL,
    password_hash_user_account text NOT NULL,
    status_user_account varchar(16) NOT NULL,
    created_at_user_account timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_account_tenant_id_unique UNIQUE (id_institution, id_user_account),
    CONSTRAINT user_account_email_valid CHECK (
        email_user_account = btrim(email_user_account)
        AND position('@' IN email_user_account) > 1
    ),
    CONSTRAINT user_account_password_not_blank CHECK (btrim(password_hash_user_account) <> ''),
    CONSTRAINT user_account_status_valid CHECK (
        status_user_account IN ('active', 'inactive', 'locked')
    )
);

CREATE UNIQUE INDEX user_account_email_tenant_unique
    ON user_account (id_institution, lower(email_user_account));

CREATE TABLE user_role (
    id_user_account uuid NOT NULL REFERENCES user_account (id_user_account),
    id_role smallint NOT NULL REFERENCES role (id_role),
    PRIMARY KEY (id_user_account, id_role)
);

CREATE TABLE professional (
    id_professional uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    id_user_account uuid UNIQUE,
    first_name_professional varchar(80) NOT NULL,
    last_name_professional varchar(100) NOT NULL,
    license_number_professional varchar(32) NOT NULL,
    category_professional varchar(40) NOT NULL,
    email_professional varchar(254) NOT NULL,
    phone_professional varchar(20),
    status_professional varchar(16) NOT NULL,
    CONSTRAINT professional_tenant_id_unique UNIQUE (id_institution, id_professional),
    CONSTRAINT professional_license_tenant_unique UNIQUE (
        id_institution, license_number_professional
    ),
    CONSTRAINT professional_user_same_tenant FOREIGN KEY (id_institution, id_user_account)
        REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT professional_first_name_not_blank CHECK (btrim(first_name_professional) <> ''),
    CONSTRAINT professional_last_name_not_blank CHECK (btrim(last_name_professional) <> ''),
    CONSTRAINT professional_license_not_blank CHECK (btrim(license_number_professional) <> ''),
    CONSTRAINT professional_category_not_blank CHECK (btrim(category_professional) <> ''),
    CONSTRAINT professional_email_valid CHECK (
        email_professional = btrim(email_professional)
        AND position('@' IN email_professional) > 1
    ),
    CONSTRAINT professional_status_valid CHECK (status_professional IN ('active', 'inactive'))
);

CREATE TABLE professional_specialty (
    id_professional uuid NOT NULL REFERENCES professional (id_professional),
    id_specialty uuid NOT NULL REFERENCES specialty (id_specialty),
    is_primary_professional_specialty boolean NOT NULL,
    PRIMARY KEY (id_professional, id_specialty)
);

CREATE UNIQUE INDEX professional_one_primary_specialty
    ON professional_specialty (id_professional)
    WHERE is_primary_professional_specialty;

CREATE TABLE room (
    id_room uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    code_room varchar(20) NOT NULL,
    name_room varchar(100) NOT NULL,
    location_room varchar(100) NOT NULL,
    is_general_room boolean NOT NULL,
    status_room varchar(16) NOT NULL,
    CONSTRAINT room_tenant_id_unique UNIQUE (id_institution, id_room),
    CONSTRAINT room_code_tenant_unique UNIQUE (id_institution, code_room),
    CONSTRAINT room_code_not_blank CHECK (btrim(code_room) <> ''),
    CONSTRAINT room_name_not_blank CHECK (btrim(name_room) <> ''),
    CONSTRAINT room_location_not_blank CHECK (btrim(location_room) <> ''),
    CONSTRAINT room_status_valid CHECK (status_room IN ('active', 'inactive'))
);

CREATE TABLE room_specialty (
    id_room uuid NOT NULL REFERENCES room (id_room),
    id_specialty uuid NOT NULL REFERENCES specialty (id_specialty),
    PRIMARY KEY (id_room, id_specialty)
);

CREATE TABLE availability (
    id_availability uuid PRIMARY KEY,
    id_professional uuid NOT NULL REFERENCES professional (id_professional),
    weekday_availability smallint NOT NULL,
    start_time_availability time NOT NULL,
    end_time_availability time NOT NULL,
    valid_from_availability date NOT NULL,
    valid_to_availability date NOT NULL,
    status_availability varchar(16) NOT NULL,
    note_availability varchar(240),
    CONSTRAINT availability_weekday_valid CHECK (weekday_availability BETWEEN 1 AND 7),
    CONSTRAINT availability_time_order CHECK (end_time_availability > start_time_availability),
    CONSTRAINT availability_date_order CHECK (
        valid_to_availability >= valid_from_availability
    ),
    CONSTRAINT availability_status_valid CHECK (status_availability IN ('active', 'inactive'))
);

CREATE TABLE professional_leave (
    id_professional_leave uuid PRIMARY KEY,
    id_professional uuid NOT NULL REFERENCES professional (id_professional),
    start_at_professional_leave timestamptz NOT NULL,
    end_at_professional_leave timestamptz NOT NULL,
    reason_professional_leave varchar(80) NOT NULL,
    status_professional_leave varchar(16) NOT NULL,
    CONSTRAINT professional_leave_time_order CHECK (
        end_at_professional_leave > start_at_professional_leave
    ),
    CONSTRAINT professional_leave_reason_not_blank CHECK (
        btrim(reason_professional_leave) <> ''
    ),
    CONSTRAINT professional_leave_status_valid CHECK (
        status_professional_leave IN ('pending', 'approved', 'rejected', 'cancelled')
    ),
    CONSTRAINT professional_leave_no_overlap EXCLUDE USING gist (
        id_professional WITH =,
        tstzrange(start_at_professional_leave, end_at_professional_leave, '[)') WITH &&
    ) WHERE (status_professional_leave = 'approved')
);

CREATE TABLE room_block (
    id_room_block uuid PRIMARY KEY,
    id_room uuid NOT NULL REFERENCES room (id_room),
    start_at_room_block timestamptz NOT NULL,
    end_at_room_block timestamptz NOT NULL,
    reason_room_block varchar(100) NOT NULL,
    status_room_block varchar(16) NOT NULL,
    CONSTRAINT room_block_time_order CHECK (end_at_room_block > start_at_room_block),
    CONSTRAINT room_block_reason_not_blank CHECK (btrim(reason_room_block) <> ''),
    CONSTRAINT room_block_status_valid CHECK (status_room_block IN ('active', 'cancelled')),
    CONSTRAINT room_block_no_overlap EXCLUDE USING gist (
        id_room WITH =,
        tstzrange(start_at_room_block, end_at_room_block, '[)') WITH &&
    ) WHERE (status_room_block = 'active')
);

CREATE TABLE schedule (
    id_schedule uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    period_start_schedule date NOT NULL,
    period_end_schedule date NOT NULL,
    version_schedule integer NOT NULL,
    status_schedule varchar(16) NOT NULL,
    id_creator_user_account uuid NOT NULL,
    id_approver_user_account uuid,
    created_at_schedule timestamptz NOT NULL DEFAULT now(),
    approved_at_schedule timestamptz,
    CONSTRAINT schedule_tenant_id_unique UNIQUE (id_institution, id_schedule),
    CONSTRAINT schedule_version_unique UNIQUE (
        id_institution, period_start_schedule, period_end_schedule, version_schedule
    ),
    CONSTRAINT schedule_creator_same_tenant FOREIGN KEY (
        id_institution, id_creator_user_account
    ) REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT schedule_approver_same_tenant FOREIGN KEY (
        id_institution, id_approver_user_account
    ) REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT schedule_period_order CHECK (period_end_schedule >= period_start_schedule),
    CONSTRAINT schedule_version_positive CHECK (version_schedule > 0),
    CONSTRAINT schedule_status_valid CHECK (
        status_schedule IN ('draft', 'pending', 'approved', 'superseded', 'cancelled')
    ),
    CONSTRAINT schedule_approval_complete CHECK (
        status_schedule NOT IN ('approved', 'superseded')
        OR (id_approver_user_account IS NOT NULL AND approved_at_schedule IS NOT NULL)
    ),
    CONSTRAINT schedule_approval_time_order CHECK (
        approved_at_schedule IS NULL OR approved_at_schedule >= created_at_schedule
    )
);

CREATE UNIQUE INDEX schedule_one_approved_period
    ON schedule (id_institution, period_start_schedule, period_end_schedule)
    WHERE status_schedule = 'approved';

CREATE TABLE generation_run (
    id_generation_run uuid PRIMARY KEY,
    id_schedule uuid NOT NULL REFERENCES schedule (id_schedule),
    method_generation_run varchar(16) NOT NULL,
    algorithm_version_generation_run varchar(40) NOT NULL,
    rules_snapshot_generation_run jsonb NOT NULL,
    metrics_generation_run jsonb,
    status_generation_run varchar(16) NOT NULL,
    started_at_generation_run timestamptz NOT NULL DEFAULT now(),
    finished_at_generation_run timestamptz,
    CONSTRAINT generation_run_schedule_id_unique UNIQUE (id_schedule, id_generation_run),
    CONSTRAINT generation_run_method_valid CHECK (
        method_generation_run IN ('manual', 'rules', 'hybrid')
    ),
    CONSTRAINT generation_run_algorithm_not_blank CHECK (
        btrim(algorithm_version_generation_run) <> ''
    ),
    CONSTRAINT generation_run_rules_object CHECK (
        jsonb_typeof(rules_snapshot_generation_run) = 'object'
    ),
    CONSTRAINT generation_run_metrics_object CHECK (
        metrics_generation_run IS NULL OR jsonb_typeof(metrics_generation_run) = 'object'
    ),
    CONSTRAINT generation_run_status_valid CHECK (
        status_generation_run IN ('pending', 'running', 'completed', 'failed')
    ),
    CONSTRAINT generation_run_finish_consistent CHECK (
        (finished_at_generation_run IS NULL) =
        (status_generation_run IN ('pending', 'running'))
    ),
    CONSTRAINT generation_run_time_order CHECK (
        finished_at_generation_run IS NULL
        OR finished_at_generation_run >= started_at_generation_run
    )
);

CREATE TABLE coverage_requirement (
    id_coverage_requirement uuid PRIMARY KEY,
    id_schedule uuid NOT NULL REFERENCES schedule (id_schedule),
    id_specialty uuid NOT NULL REFERENCES specialty (id_specialty),
    start_at_coverage_requirement timestamptz NOT NULL,
    end_at_coverage_requirement timestamptz NOT NULL,
    required_count_coverage_requirement smallint NOT NULL,
    note_coverage_requirement varchar(180),
    CONSTRAINT coverage_requirement_time_order CHECK (
        end_at_coverage_requirement > start_at_coverage_requirement
    ),
    CONSTRAINT coverage_requirement_count_positive CHECK (
        required_count_coverage_requirement > 0
    ),
    CONSTRAINT coverage_requirement_no_overlap EXCLUDE USING gist (
        id_schedule WITH =,
        id_specialty WITH =,
        tstzrange(start_at_coverage_requirement, end_at_coverage_requirement, '[)') WITH &&
    )
);

CREATE TABLE shift (
    id_shift uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    id_schedule uuid NOT NULL,
    id_professional uuid NOT NULL,
    id_room uuid NOT NULL,
    id_specialty uuid NOT NULL REFERENCES specialty (id_specialty),
    id_generation_run uuid,
    start_at_shift timestamptz NOT NULL,
    end_at_shift timestamptz NOT NULL,
    status_shift varchar(16) NOT NULL,
    note_shift varchar(180),
    CONSTRAINT shift_tenant_id_unique UNIQUE (id_institution, id_shift),
    CONSTRAINT shift_schedule_same_tenant FOREIGN KEY (id_institution, id_schedule)
        REFERENCES schedule (id_institution, id_schedule),
    CONSTRAINT shift_professional_same_tenant FOREIGN KEY (id_institution, id_professional)
        REFERENCES professional (id_institution, id_professional),
    CONSTRAINT shift_room_same_tenant FOREIGN KEY (id_institution, id_room)
        REFERENCES room (id_institution, id_room),
    -- Conservar la competencia vinculada también para turnos históricos.
    CONSTRAINT shift_professional_has_specialty FOREIGN KEY (id_professional, id_specialty)
        REFERENCES professional_specialty (id_professional, id_specialty),
    CONSTRAINT shift_generation_same_schedule FOREIGN KEY (id_schedule, id_generation_run)
        REFERENCES generation_run (id_schedule, id_generation_run),
    CONSTRAINT shift_time_order CHECK (end_at_shift > start_at_shift),
    CONSTRAINT shift_status_valid CHECK (
        status_shift IN ('draft', 'pending', 'approved', 'superseded', 'cancelled')
    ),
    CONSTRAINT shift_professional_no_overlap EXCLUDE USING gist (
        id_professional WITH =,
        tstzrange(start_at_shift, end_at_shift, '[)') WITH &&
    ) WHERE (status_shift = 'approved'),
    CONSTRAINT shift_room_no_overlap EXCLUDE USING gist (
        id_room WITH =,
        tstzrange(start_at_shift, end_at_shift, '[)') WITH &&
    ) WHERE (status_shift = 'approved')
);

CREATE TABLE attendance (
    id_attendance uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    id_shift uuid NOT NULL UNIQUE,
    id_recorder_user_account uuid NOT NULL,
    check_in_at_attendance timestamptz,
    check_out_at_attendance timestamptz,
    status_attendance varchar(16) NOT NULL,
    CONSTRAINT attendance_shift_same_tenant FOREIGN KEY (id_institution, id_shift)
        REFERENCES shift (id_institution, id_shift),
    CONSTRAINT attendance_recorder_same_tenant FOREIGN KEY (
        id_institution, id_recorder_user_account
    ) REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT attendance_status_valid CHECK (
        status_attendance IN ('pending', 'present', 'absent', 'incomplete')
    ),
    CONSTRAINT attendance_time_order CHECK (
        check_out_at_attendance IS NULL
        OR (check_in_at_attendance IS NOT NULL
            AND check_out_at_attendance > check_in_at_attendance)
    )
);

CREATE TABLE notification (
    id_notification uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    id_user_account uuid NOT NULL,
    id_shift uuid,
    type_notification varchar(32) NOT NULL,
    message_notification text NOT NULL,
    created_at_notification timestamptz NOT NULL DEFAULT now(),
    read_at_notification timestamptz,
    CONSTRAINT notification_user_same_tenant FOREIGN KEY (id_institution, id_user_account)
        REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT notification_shift_same_tenant FOREIGN KEY (id_institution, id_shift)
        REFERENCES shift (id_institution, id_shift),
    CONSTRAINT notification_type_not_blank CHECK (btrim(type_notification) <> ''),
    CONSTRAINT notification_message_not_blank CHECK (btrim(message_notification) <> ''),
    CONSTRAINT notification_read_time_order CHECK (
        read_at_notification IS NULL OR read_at_notification >= created_at_notification
    )
);

CREATE TABLE audit_event (
    id_audit_event uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    id_actor_user_account uuid,
    entity_name_audit_event varchar(50) NOT NULL,
    entity_id_audit_event uuid NOT NULL,
    action_audit_event varchar(24) NOT NULL,
    before_audit_event jsonb,
    after_audit_event jsonb,
    occurred_at_audit_event timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT audit_event_actor_same_tenant FOREIGN KEY (
        id_institution, id_actor_user_account
    ) REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT audit_event_entity_not_blank CHECK (btrim(entity_name_audit_event) <> ''),
    CONSTRAINT audit_event_action_not_blank CHECK (btrim(action_audit_event) <> ''),
    CONSTRAINT audit_event_has_snapshot CHECK (
        before_audit_event IS NOT NULL OR after_audit_event IS NOT NULL
    )
);

-- El historial de auditoría solo admite nuevos eventos.
CREATE FUNCTION prevent_audit_event_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_event_insert_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();

-- Índices para relaciones y consultas habituales. Las PK, UNIQUE y EXCLUDE
-- ya crean sus propios índices; no se duplican aquí.
CREATE INDEX user_role_role_idx ON user_role (id_role);
CREATE INDEX professional_specialty_specialty_idx ON professional_specialty (id_specialty);
CREATE INDEX room_specialty_specialty_idx ON room_specialty (id_specialty);
CREATE INDEX availability_professional_weekday_idx
    ON availability (id_professional, weekday_availability, valid_from_availability);
CREATE INDEX schedule_creator_idx ON schedule (id_creator_user_account);
CREATE INDEX schedule_approver_idx ON schedule (id_approver_user_account)
    WHERE id_approver_user_account IS NOT NULL;
CREATE INDEX coverage_requirement_specialty_idx ON coverage_requirement (id_specialty);
CREATE INDEX shift_schedule_start_idx ON shift (id_schedule, start_at_shift);
CREATE INDEX shift_professional_start_idx ON shift (id_professional, start_at_shift);
CREATE INDEX shift_room_start_idx ON shift (id_room, start_at_shift);
CREATE INDEX shift_generation_run_idx ON shift (id_generation_run)
    WHERE id_generation_run IS NOT NULL;
CREATE INDEX attendance_recorder_idx ON attendance (id_recorder_user_account);
CREATE INDEX notification_user_created_idx
    ON notification (id_user_account, created_at_notification DESC);
CREATE INDEX notification_shift_idx ON notification (id_shift)
    WHERE id_shift IS NOT NULL;
CREATE INDEX audit_event_entity_idx ON audit_event (
    id_institution, entity_name_audit_event, entity_id_audit_event,
    occurred_at_audit_event DESC
);
CREATE INDEX audit_event_occurred_idx
    ON audit_event (id_institution, occurred_at_audit_event DESC);

COMMIT;
