-- Ejecutar después de V008 dentro de una sola transacción.
LOCK TABLE institution, specialty, professional, professional_specialty, room, room_specialty,
    planning_unit, area_etiqueta, schedule, coverage_requirement, shift IN SHARE ROW EXCLUSIVE MODE;

-- Las versiones anteriores a V008 pueden tener un ID sin el nombre archivado.
DO $historial$
BEGIN
    IF to_regclass(format('%I.historial_plan_mensual',current_schema())) IS NOT NULL THEN
        WITH versiones AS (
            SELECT h.id, jsonb_agg(CASE WHEN e.id_specialty IS NOT NULL AND turno->>'nombre_etiqueta' IS NULL
                THEN turno || jsonb_build_object('nombre_etiqueta', e.name_specialty)
                ELSE turno END ORDER BY orden) AS turnos
            FROM historial_plan_mensual h
            CROSS JOIN LATERAL jsonb_array_elements(h.contenido->'turnos') WITH ORDINALITY AS t(turno, orden)
            LEFT JOIN specialty e ON e.id_specialty=(turno->>'id_specialty')::uuid AND e.id_institution IS NULL
            GROUP BY h.id
            HAVING bool_or(e.id_specialty IS NOT NULL AND turno->>'nombre_etiqueta' IS NULL)
        )
        UPDATE historial_plan_mensual h SET contenido=jsonb_set(h.contenido, '{turnos}', v.turnos)
        FROM versiones v WHERE h.id=v.id;
    END IF;
END;
$historial$;

CREATE TEMP TABLE etiquetas_por_clinica (
    origen uuid NOT NULL,
    institucion uuid NOT NULL,
    destino uuid NOT NULL,
    PRIMARY KEY (origen,institucion)
) ON COMMIT DROP;

WITH usos(id_etiqueta,id_institucion) AS (
    SELECT v.id_specialty,p.id_institution FROM professional_specialty v JOIN professional p USING (id_professional)
    UNION
    SELECT v.id_specialty,r.id_institution FROM room_specialty v JOIN room r USING (id_room)
    UNION
    SELECT id_etiqueta,id_institucion FROM area_etiqueta
    UNION
    SELECT id_specialty,id_institution FROM planning_unit WHERE id_specialty IS NOT NULL
    UNION
    SELECT id_specialty,id_institution FROM shift
    UNION
    SELECT v.id_specialty,h.id_institution FROM coverage_requirement v JOIN schedule h USING (id_schedule)
)
INSERT INTO pg_temp.etiquetas_por_clinica (origen,institucion,destino)
SELECT u.id_etiqueta,u.id_institucion,u.id_etiqueta FROM usos u
JOIN specialty e ON e.id_specialty=u.id_etiqueta AND e.id_institution IS NULL;

-- Reutiliza el ID histórico en una clínica que ya lo usaba, si el nombre está disponible.
ALTER TABLE specialty DISABLE TRIGGER etiqueta_propietario_inmutable;
WITH propietarios AS (
    SELECT DISTINCT ON (m.origen) m.origen,m.institucion
    FROM pg_temp.etiquetas_por_clinica m JOIN specialty e ON e.id_specialty=m.origen
    WHERE NOT EXISTS (SELECT 1 FROM specialty actual
        WHERE actual.id_institution=m.institucion AND actual.nombre_normalizado=e.nombre_normalizado)
    ORDER BY m.origen,m.institucion
)
UPDATE specialty e SET id_institution=p.institucion FROM propietarios p WHERE e.id_specialty=p.origen;
ALTER TABLE specialty ENABLE TRIGGER etiqueta_propietario_inmutable;

-- Un nombre coincidente puede tener una etiqueta propia con otras asignaciones.
DO $copias$
DECLARE
    uso record;
    identificador uuid;
    nuevo uuid;
    nombre text;
    sufijo text;
    intento integer;
BEGIN
    FOR uso IN SELECT m.origen,m.institucion,e.name_specialty,e.activa
        FROM pg_temp.etiquetas_por_clinica m JOIN specialty e ON e.id_specialty=m.origen
        WHERE e.id_institution IS DISTINCT FROM m.institucion ORDER BY m.origen,m.institucion
    LOOP
        identificador := gen_random_uuid();
        nombre := uso.name_specialty;
        intento := 1;
        LOOP
            INSERT INTO specialty (id_specialty,code_specialty,name_specialty,id_institution,activa)
            VALUES (identificador,'ET-' || left(replace(identificador::text,'-',''),29),nombre,uso.institucion,uso.activa)
            ON CONFLICT (id_institution,nombre_normalizado) DO NOTHING RETURNING id_specialty INTO nuevo;
            EXIT WHEN nuevo IS NOT NULL;
            intento := intento + 1;
            sufijo := ' (' || intento || ')';
            nombre := rtrim(left(uso.name_specialty,100-length(sufijo))) || sufijo;
        END LOOP;
        UPDATE pg_temp.etiquetas_por_clinica SET destino=nuevo
        WHERE origen=uso.origen AND institucion=uso.institucion;
    END LOOP;
END;
$copias$;

-- Mueve la competencia y sus turnos juntos. La foto del nombre conserva su valor anterior.
ALTER TABLE shift ALTER CONSTRAINT shift_professional_has_specialty DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE shift DISABLE TRIGGER shift_capturar_etiqueta;

UPDATE professional_specialty v SET id_specialty=m.destino
FROM professional p JOIN pg_temp.etiquetas_por_clinica m ON m.institucion=p.id_institution
WHERE v.id_professional=p.id_professional AND v.id_specialty=m.origen AND m.destino<>m.origen;

UPDATE room_specialty v SET id_specialty=m.destino
FROM room r JOIN pg_temp.etiquetas_por_clinica m ON m.institucion=r.id_institution
WHERE v.id_room=r.id_room AND v.id_specialty=m.origen AND m.destino<>m.origen;

UPDATE area_etiqueta v SET id_etiqueta=m.destino FROM pg_temp.etiquetas_por_clinica m
WHERE v.id_institucion=m.institucion AND v.id_etiqueta=m.origen AND m.destino<>m.origen;

UPDATE planning_unit u SET id_specialty=m.destino FROM pg_temp.etiquetas_por_clinica m
WHERE u.id_institution=m.institucion AND u.id_specialty=m.origen AND m.destino<>m.origen;

UPDATE coverage_requirement v SET id_specialty=m.destino
FROM schedule h JOIN pg_temp.etiquetas_por_clinica m ON m.institucion=h.id_institution
WHERE v.id_schedule=h.id_schedule AND v.id_specialty=m.origen AND m.destino<>m.origen;

UPDATE shift t SET id_specialty=m.destino FROM pg_temp.etiquetas_por_clinica m
WHERE t.id_institution=m.institucion AND t.id_specialty=m.origen AND m.destino<>m.origen;

SET CONSTRAINTS shift_professional_has_specialty IMMEDIATE;
ALTER TABLE shift ENABLE TRIGGER shift_capturar_etiqueta;
ALTER TABLE shift ALTER CONSTRAINT shift_professional_has_specialty NOT DEFERRABLE INITIALLY IMMEDIATE;

DELETE FROM specialty WHERE id_institution IS NULL;
ALTER TABLE specialty ALTER COLUMN id_institution SET NOT NULL;

DO $validacion$
BEGIN
    IF EXISTS (SELECT 1 FROM coverage_requirement v JOIN schedule h USING (id_schedule)
        JOIN specialty e USING (id_specialty) WHERE e.id_institution<>h.id_institution) THEN
        RAISE EXCEPTION 'Hay cobertura con etiquetas de otra institución. Revisa los vínculos antes de aplicar V009.';
    END IF;
END;
$validacion$;

CREATE OR REPLACE FUNCTION verificar_propietario_etiqueta() RETURNS trigger LANGUAGE plpgsql AS $$
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
        WHEN 'coverage_requirement' THEN
            SELECT id_institution INTO institucion FROM schedule WHERE id_schedule=NEW.id_schedule;
            etiqueta := NEW.id_specialty;
    END CASE;
    IF NOT EXISTS (SELECT 1 FROM specialty WHERE id_specialty=etiqueta AND id_institution=institucion) THEN
        RAISE EXCEPTION 'La etiqueta no pertenece al catálogo de la institución' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

DO $ruta$
BEGIN
    EXECUTE format('ALTER FUNCTION verificar_propietario_etiqueta() SET search_path TO %I, pg_temp',current_schema());
END;
$ruta$;

CREATE TRIGGER cobertura_etiqueta_institucional
    BEFORE INSERT OR UPDATE OF id_schedule,id_specialty ON coverage_requirement
    FOR EACH ROW EXECUTE FUNCTION verificar_propietario_etiqueta();

CREATE OR REPLACE VIEW etiqueta_consultorio_programable WITH (security_invoker=true) AS
SELECT r.id_institution,r.id_room,e.id_specialty,e.name_specialty
FROM room r
JOIN planning_unit u ON (u.id_institution,u.id_planning_unit)=(r.id_institution,r.id_planning_unit)
JOIN area_etiqueta a ON (a.id_institucion,a.id_area)=(u.id_institution,u.id_planning_unit)
JOIN specialty e ON e.id_specialty=a.id_etiqueta AND e.activa AND e.id_institution=r.id_institution
WHERE u.status_planning_unit='active' AND (r.is_general_room OR EXISTS (
    SELECT 1 FROM room_specialty v WHERE (v.id_room,v.id_specialty)=(r.id_room,e.id_specialty)))
UNION ALL
SELECT r.id_institution,r.id_room,e.id_specialty,e.name_specialty
FROM room r JOIN room_specialty v USING (id_room) JOIN specialty e USING (id_specialty)
WHERE r.id_planning_unit IS NULL AND NOT r.is_general_room AND e.activa AND e.id_institution=r.id_institution;
