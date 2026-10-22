-- Ejecutar después de V001, dentro de una transacción y con respaldo verificado.
-- No asigna áreas a registros existentes ni modifica los turnos históricos.
ALTER TABLE room
    ADD COLUMN id_planning_unit uuid,
    ADD CONSTRAINT room_unit_same_tenant
        FOREIGN KEY (id_institution, id_planning_unit)
        REFERENCES planning_unit (id_institution, id_planning_unit);

CREATE INDEX room_planning_unit_index
    ON room (id_institution, id_planning_unit);
