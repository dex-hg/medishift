-- Valores vigentes por institución y preferencias privadas por cuenta.
-- No modifica turnos, asistencias, cuentas ni políticas existentes.
CREATE TABLE IF NOT EXISTS institution_settings (
    id_institution uuid PRIMARY KEY REFERENCES institution (id_institution),
    daily_minutes integer NOT NULL DEFAULT 360 CHECK (daily_minutes BETWEEN 1 AND 1440),
    weekly_minutes integer NOT NULL DEFAULT 2160,
    holiday_credit_minutes integer NOT NULL DEFAULT 360 CHECK (holiday_credit_minutes BETWEEN 0 AND 1440),
    revision_settings bigint NOT NULL DEFAULT 1 CHECK (revision_settings > 0),
    CONSTRAINT institution_settings_week_valid CHECK (
        weekly_minutes >= daily_minutes AND weekly_minutes <= daily_minutes * 7
    )
);

CREATE TABLE IF NOT EXISTS account_settings (
    id_institution uuid NOT NULL,
    id_user_account uuid PRIMARY KEY,
    display_name varchar(120) NOT NULL DEFAULT '',
    sidebar_compact boolean NOT NULL DEFAULT false,
    default_start_time time NOT NULL DEFAULT '08:00',
    default_end_time time NOT NULL DEFAULT '12:00',
    revision_settings bigint NOT NULL DEFAULT 1 CHECK (revision_settings > 0),
    CONSTRAINT account_settings_same_tenant FOREIGN KEY (id_institution, id_user_account)
        REFERENCES user_account (id_institution, id_user_account),
    CONSTRAINT account_settings_time_valid CHECK (
        default_start_time < default_end_time AND default_end_time < time '24:00'
        AND extract(second FROM default_start_time) = 0 AND extract(second FROM default_end_time) = 0
    )
);
