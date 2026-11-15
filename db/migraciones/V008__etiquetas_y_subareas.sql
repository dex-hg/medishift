-- Conserva los identificadores y las referencias históricas del catálogo.
ALTER TABLE specialty
    ADD COLUMN activa boolean NOT NULL DEFAULT true,
    ADD COLUMN nombre_normalizado text GENERATED ALWAYS AS (
        lower(btrim(regexp_replace(normalize(name_specialty, NFC), '[[:space:]]+', ' ', 'g')))
    ) STORED;
CREATE UNIQUE INDEX specialty_nombre_institucional
    ON specialty (id_institution, nombre_normalizado) NULLS NOT DISTINCT;

ALTER TABLE professional_specialty ADD COLUMN activo boolean NOT NULL DEFAULT true;

ALTER TABLE planning_unit
    ADD COLUMN id_area_padre uuid,
    ADD CONSTRAINT planning_unit_padre_institucional FOREIGN KEY (id_institution, id_area_padre)
        REFERENCES planning_unit (id_institution, id_planning_unit),
    ADD CONSTRAINT planning_unit_padre_distinto CHECK (id_area_padre <> id_planning_unit);
CREATE INDEX planning_unit_por_padre ON planning_unit (id_institution, id_area_padre)
    WHERE id_area_padre IS NOT NULL;

CREATE TABLE area_etiqueta (
    id_institucion uuid NOT NULL,
    id_area uuid NOT NULL,
    id_etiqueta uuid NOT NULL REFERENCES specialty(id_specialty),
    PRIMARY KEY (id_institucion, id_area, id_etiqueta),
    FOREIGN KEY (id_institucion, id_area) REFERENCES planning_unit (id_institution, id_planning_unit)
);
CREATE INDEX area_etiqueta_por_etiqueta ON area_etiqueta (id_etiqueta, id_institucion, id_area);

-- Las relaciones antiguas pueden usar etiquetas compartidas o propias.
CREATE FUNCTION verificar_propietario_etiqueta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    institucion uuid;
    etiqueta uuid;
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'area_etiqueta' THEN
            institucion := NEW.id_institucion;
            etiqueta := NEW.id_etiqueta;
        WHEN 'professional_specialty' THEN
            SELECT id_institution INTO institucion FROM professional WHERE id_professional=NEW.id_professional;
            etiqueta := NEW.id_specialty;
        WHEN 'room_specialty' THEN
            SELECT id_institution INTO institucion FROM room WHERE id_room=NEW.id_room;
            etiqueta := NEW.id_specialty;
    END CASE;
    IF institucion IS NULL OR NOT EXISTS (
        SELECT 1 FROM specialty WHERE id_specialty=etiqueta
        AND (id_institution IS NULL OR id_institution=institucion)
    ) THEN
        RAISE EXCEPTION 'La etiqueta no pertenece al catálogo de la institución' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER area_etiqueta_institucional
    BEFORE INSERT OR UPDATE ON area_etiqueta FOR EACH ROW EXECUTE FUNCTION verificar_propietario_etiqueta();
CREATE TRIGGER profesional_etiqueta_institucional
    BEFORE INSERT OR UPDATE OF id_professional,id_specialty ON professional_specialty
    FOR EACH ROW EXECUTE FUNCTION verificar_propietario_etiqueta();
CREATE TRIGGER consultorio_etiqueta_institucional
    BEFORE INSERT OR UPDATE OF id_room,id_specialty ON room_specialty
    FOR EACH ROW EXECUTE FUNCTION verificar_propietario_etiqueta();

DO $validacion$
BEGIN
    IF EXISTS (
        SELECT 1 FROM professional_specialty v JOIN professional p USING (id_professional)
        JOIN specialty e USING (id_specialty) WHERE e.id_institution<>p.id_institution
    ) OR EXISTS (
        SELECT 1 FROM room_specialty v JOIN room r USING (id_room)
        JOIN specialty e USING (id_specialty) WHERE e.id_institution<>r.id_institution
    ) THEN
        RAISE EXCEPTION 'Hay vínculos de etiquetas entre instituciones. Revísalos antes de aplicar V008.';
    END IF;
END;
$validacion$;

CREATE FUNCTION conservar_propietario_etiqueta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.id_institution IS DISTINCT FROM NEW.id_institution THEN
        RAISE EXCEPTION 'La institución de una etiqueta es inmutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER etiqueta_propietario_inmutable BEFORE UPDATE OF id_institution ON specialty
    FOR EACH ROW EXECUTE FUNCTION conservar_propietario_etiqueta();

INSERT INTO area_etiqueta (id_institucion,id_area,id_etiqueta)
SELECT id_institution,id_planning_unit,id_specialty FROM planning_unit WHERE id_specialty IS NOT NULL;

-- Los cargadores antiguos pueden seguir declarando una etiqueta al insertar el área.
-- La API actual guarda primero el conjunto y después sincroniza su proyección singular.
CREATE FUNCTION sincronizar_etiqueta_legada_area() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE etiquetas uuid[];
BEGIN
    SELECT coalesce(array_agg(id_etiqueta), ARRAY[]::uuid[]) INTO etiquetas
    FROM area_etiqueta WHERE id_institucion=NEW.id_institution AND id_area=NEW.id_planning_unit;
    IF cardinality(etiquetas)>1 THEN
        IF NEW.id_specialty IS NOT NULL THEN
            RAISE EXCEPTION 'Edita las etiquetas del área como conjunto' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.id_specialty IS DISTINCT FROM etiquetas[1] THEN
        DELETE FROM area_etiqueta WHERE id_institucion=NEW.id_institution AND id_area=NEW.id_planning_unit;
        IF NEW.id_specialty IS NOT NULL THEN
            INSERT INTO area_etiqueta VALUES (NEW.id_institution,NEW.id_planning_unit,NEW.id_specialty);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER area_etiqueta_legada AFTER INSERT OR UPDATE OF id_specialty ON planning_unit
    FOR EACH ROW EXECUTE FUNCTION sincronizar_etiqueta_legada_area();

CREATE FUNCTION verificar_jerarquia_areas() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('catalogos:' || NEW.id_institution::text, 0));
    IF NEW.id_area_padre IS NOT NULL AND (
        NOT EXISTS (SELECT 1 FROM planning_unit p WHERE p.id_institution=NEW.id_institution
            AND p.id_planning_unit=NEW.id_area_padre AND p.id_area_padre IS NULL
            AND (NEW.status_planning_unit='inactive' OR p.status_planning_unit='active') FOR SHARE)
        OR EXISTS (SELECT 1 FROM planning_unit h WHERE h.id_institution=NEW.id_institution
            AND h.id_area_padre=NEW.id_planning_unit)
    ) THEN
        RAISE EXCEPTION 'La subárea requiere un área raíz válida de la misma institución' USING ERRCODE='23514';
    END IF;
    IF NEW.status_planning_unit='inactive' AND EXISTS (
        SELECT 1 FROM planning_unit h WHERE h.id_institution=NEW.id_institution
        AND h.id_area_padre=NEW.id_planning_unit AND h.status_planning_unit='active'
    ) THEN
        RAISE EXCEPTION 'El área todavía tiene subáreas activas' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER planning_unit_jerarquia BEFORE INSERT OR UPDATE OF id_area_padre,status_planning_unit ON planning_unit
    FOR EACH ROW EXECUTE FUNCTION verificar_jerarquia_areas();

ALTER TABLE shift ADD COLUMN nombre_etiqueta varchar(100);
UPDATE shift t SET nombre_etiqueta=e.name_specialty FROM specialty e WHERE t.id_specialty=e.id_specialty;
ALTER TABLE shift ALTER COLUMN nombre_etiqueta SET NOT NULL;
CREATE FUNCTION capturar_etiqueta_turno() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' OR NEW.id_specialty IS DISTINCT FROM OLD.id_specialty THEN
        SELECT name_specialty INTO NEW.nombre_etiqueta FROM specialty WHERE id_specialty=NEW.id_specialty;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER shift_capturar_etiqueta BEFORE INSERT OR UPDATE OF id_specialty ON shift
    FOR EACH ROW EXECUTE FUNCTION capturar_etiqueta_turno();

-- Opciones activas de los consultorios restringidos o adscritos a un área.
CREATE VIEW etiqueta_consultorio_programable AS
SELECT r.id_institution,r.id_room,e.id_specialty,e.name_specialty
FROM room r
JOIN planning_unit u ON (u.id_institution,u.id_planning_unit)=(r.id_institution,r.id_planning_unit)
JOIN area_etiqueta a ON (a.id_institucion,a.id_area)=(u.id_institution,u.id_planning_unit)
JOIN specialty e ON e.id_specialty=a.id_etiqueta AND e.activa
    AND (e.id_institution IS NULL OR e.id_institution=r.id_institution)
WHERE u.status_planning_unit='active' AND (r.is_general_room OR EXISTS (
    SELECT 1 FROM room_specialty v WHERE (v.id_room,v.id_specialty)=(r.id_room,e.id_specialty)))
UNION ALL
SELECT r.id_institution,r.id_room,e.id_specialty,e.name_specialty
FROM room r JOIN room_specialty v USING (id_room) JOIN specialty e USING (id_specialty)
WHERE r.id_planning_unit IS NULL AND NOT r.is_general_room AND e.activa
    AND (e.id_institution IS NULL OR e.id_institution=r.id_institution);
