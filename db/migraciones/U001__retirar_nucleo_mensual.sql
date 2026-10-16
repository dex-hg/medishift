-- Recuperación limitada: ejecutar dentro de una transacción.
-- Si existen datos nuevos, conservarlos y recuperar mediante respaldo o una
-- migración correctiva; nunca eliminar planes o unidades para forzar U001.
LOCK TABLE schedule, planning_unit IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM schedule WHERE period_type_schedule <> 'weekly')
       OR EXISTS (SELECT 1 FROM planning_unit) THEN
        RAISE EXCEPTION 'La recuperación requiere conservar los datos mensuales y las unidades.'
            USING ERRCODE = '55000';
    END IF;
END;
$$;

DROP INDEX schedule_monthly_one_approved_period;
DROP INDEX schedule_monthly_version_unique;
DROP INDEX schedule_weekly_one_approved_period;
DROP INDEX schedule_weekly_version_unique;
ALTER TABLE schedule
    DROP CONSTRAINT schedule_period_scope_valid,
    DROP CONSTRAINT schedule_unit_same_tenant,
    DROP CONSTRAINT schedule_period_type_valid,
    DROP COLUMN id_planning_unit,
    DROP COLUMN period_type_schedule,
    ADD CONSTRAINT schedule_version_unique UNIQUE (
        id_institution, period_start_schedule, period_end_schedule, version_schedule
    );
CREATE UNIQUE INDEX schedule_one_approved_period
    ON schedule (id_institution, period_start_schedule, period_end_schedule)
    WHERE status_schedule = 'approved';
DROP TABLE planning_unit;
