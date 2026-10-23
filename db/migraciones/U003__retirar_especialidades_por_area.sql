-- Retirada conservadora: primero resolver explícitamente los datos de la ampliación.
DO $retirada$
BEGIN
    IF EXISTS (SELECT 1 FROM specialty WHERE id_institution IS NOT NULL)
       OR EXISTS (SELECT 1 FROM planning_unit WHERE id_specialty IS NOT NULL) THEN
        RAISE EXCEPTION 'Hay especialidades propias o áreas configuradas. No se retira V003 con datos en uso.';
    END IF;
END $retirada$;

DROP INDEX planning_unit_specialty_index;
ALTER TABLE planning_unit DROP COLUMN id_specialty;
DROP INDEX specialty_institution_index;
ALTER TABLE specialty DROP COLUMN id_institution;
