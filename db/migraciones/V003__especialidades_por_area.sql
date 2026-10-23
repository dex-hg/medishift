-- Ejecutar después de V002, dentro de una transacción y con respaldo verificado.
-- Las especialidades históricas siguen compartidas. Las nuevas pueden tener dueño.
ALTER TABLE specialty
    ADD COLUMN id_institution uuid REFERENCES institution (id_institution) ON DELETE CASCADE;

CREATE INDEX specialty_institution_index ON specialty (id_institution);

ALTER TABLE planning_unit
    ADD COLUMN id_specialty uuid REFERENCES specialty (id_specialty);

-- No deduce especialidades del nombre del área ni cambia turnos existentes.
CREATE INDEX planning_unit_specialty_index ON planning_unit (id_institution, id_specialty);
