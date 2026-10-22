-- Recuperación limitada: ejecutar dentro de una transacción.
-- No elimina asociaciones para forzar la recuperación.
LOCK TABLE room IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM room WHERE id_planning_unit IS NOT NULL) THEN
        RAISE EXCEPTION 'Existen consultorios organizados por área; conserve sus asociaciones.'
            USING ERRCODE = '55000';
    END IF;
END;
$$;
DROP INDEX room_planning_unit_index;
ALTER TABLE room
    DROP CONSTRAINT room_unit_same_tenant,
    DROP COLUMN id_planning_unit;
