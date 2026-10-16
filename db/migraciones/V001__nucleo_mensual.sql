-- M02: ejecutar una sola vez dentro de una transacción, después del inventario
-- y del respaldo. No contiene COMMIT ni configura search_path.
-- Conserva los periodos históricos; no los fusiona ni inventa su unidad.

CREATE TABLE planning_unit (
    id_planning_unit uuid PRIMARY KEY,
    id_institution uuid NOT NULL REFERENCES institution (id_institution),
    code_planning_unit varchar(32) NOT NULL,
    name_planning_unit varchar(140) NOT NULL,
    type_planning_unit varchar(16) NOT NULL,
    status_planning_unit varchar(16) NOT NULL,
    CONSTRAINT planning_unit_tenant_id_unique UNIQUE (id_institution, id_planning_unit),
    CONSTRAINT planning_unit_code_tenant_unique UNIQUE (id_institution, code_planning_unit),
    CONSTRAINT planning_unit_code_not_blank CHECK (btrim(code_planning_unit) <> ''),
    CONSTRAINT planning_unit_name_not_blank CHECK (btrim(name_planning_unit) <> ''),
    CONSTRAINT planning_unit_type_valid CHECK (type_planning_unit IN ('department', 'service')),
    CONSTRAINT planning_unit_status_valid CHECK (status_planning_unit IN ('active', 'inactive'))
);

ALTER TABLE schedule
    ADD COLUMN period_type_schedule varchar(16) NOT NULL DEFAULT 'weekly',
    ADD COLUMN id_planning_unit uuid,
    ADD CONSTRAINT schedule_period_type_valid CHECK (period_type_schedule IN ('weekly', 'monthly')),
    ADD CONSTRAINT schedule_unit_same_tenant FOREIGN KEY (id_institution, id_planning_unit)
        REFERENCES planning_unit (id_institution, id_planning_unit),
    ADD CONSTRAINT schedule_period_scope_valid CHECK (
        (period_type_schedule = 'weekly' AND id_planning_unit IS NULL)
        OR (
            period_type_schedule = 'monthly' AND id_planning_unit IS NOT NULL
            AND isfinite(period_start_schedule) AND isfinite(period_end_schedule)
            AND period_start_schedule BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
            AND extract(day FROM period_start_schedule) = 1
            AND period_end_schedule =
                (date_trunc('month', period_start_schedule::timestamp)
                    + INTERVAL '1 month - 1 day')::date
        )
    );

-- Separar claves históricas y mensuales sin permitir referencias nulas
-- que eludan la unicidad de las versiones mensuales.
ALTER TABLE schedule DROP CONSTRAINT schedule_version_unique;
DROP INDEX schedule_one_approved_period;

CREATE UNIQUE INDEX schedule_weekly_version_unique
    ON schedule (id_institution, period_start_schedule, period_end_schedule, version_schedule)
    WHERE period_type_schedule = 'weekly';
CREATE UNIQUE INDEX schedule_weekly_one_approved_period
    ON schedule (id_institution, period_start_schedule, period_end_schedule)
    WHERE period_type_schedule = 'weekly' AND status_schedule = 'approved';
CREATE UNIQUE INDEX schedule_monthly_version_unique
    ON schedule (id_institution, id_planning_unit, period_start_schedule, version_schedule)
    WHERE period_type_schedule = 'monthly';
CREATE UNIQUE INDEX schedule_monthly_one_approved_period
    ON schedule (id_institution, id_planning_unit, period_start_schedule)
    WHERE period_type_schedule = 'monthly' AND status_schedule = 'approved';

-- shift conserva su identidad, FK y exclusiones globales. La aprobación y
-- las asignaciones mensuales requieren todavía los módulos M03 a M16.
