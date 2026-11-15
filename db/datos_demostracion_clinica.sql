-- Ejecutar mediante scripts/cargar_demostracion_clinica.py con V001–V007 instaladas.
BEGIN;
SET LOCAL search_path = public, pg_catalog;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '90s';

CREATE TEMP TABLE demo_clinica_areas (
    numero integer PRIMARY KEY, codigo text NOT NULL, especialidad text NOT NULL,
    nombre text NOT NULL, salas integer NOT NULL
) ON COMMIT DROP;
INSERT INTO demo_clinica_areas VALUES
    (0,'DER','Dermatología','Dermatología',2),
    (1,'CIR','Cirugía plástica','Cirugía plástica ambulatoria',2),
    (2,'MED','Medicina general','Medicina general',2),
    (3,'PED','Pediatría','Pediatría',2),
    (4,'GIN','Ginecología','Salud de la mujer',2),
    (5,'CAR','Cardiología','Cardiología',2),
    (6,'TRA','Traumatología','Traumatología y ortopedia',1),
    (7,'OFT','Oftalmología','Oftalmología',1);

CREATE TEMP TABLE demo_clinica_medicos (
    numero integer PRIMARY KEY, nombres text NOT NULL, apellidos_pe text NOT NULL, apellidos_co text NOT NULL
) ON COMMIT DROP;
INSERT INTO demo_clinica_medicos VALUES
    (1,'Lucía','Vega Salazar','Mejía Ocampo'), (2,'Alonso','Paredes Li','Restrepo Gil'),
    (3,'Rocío','Núñez Arias','Valencia Ríos'), (4,'Iván','Soto Ponce','Quintero Ruiz'),
    (5,'Mateo','Ibarra Santos','Cabrera Vallejo'), (6,'Irene','Salas Flores','Pardo Suárez'),
    (7,'Sara','Benavides Roque','Ospina Montoya'), (8,'Gabriel','Fuentes Peña','Arango Franco'),
    (9,'Mariana','Rojas Vergara','Cuéllar Duarte'), (10,'Andrés','Quiroz Alva','Rincón Lara'),
    (11,'Silvia','Mendoza León','Arias Vélez'), (12,'Renato','Torres Pino','Mora Patiño'),
    (13,'Elena','Valdez Ramos','Pérez Roldán'), (14,'Martín','Ríos Díaz','Vargas Naranjo'),
    (15,'Patricia','Rivas Lozano','Sánchez Solano'), (16,'Javier','Gálvez Soria','Díaz Páez'),
    (17,'Jimena','Montes Guerra','Clavijo Serrano'), (18,'Diana','Herrera Luis','Gómez Rueda'),
    (19,'Camila','Palacios Vera','Salcedo Luna'), (20,'Valeria','Zegarra Núñez','Beltrán Cárdenas'),
    (21,'Nicolás','Ugarte Chávez','Londoño Bernal'), (22,'Isabel','García Montalvo','Castillo Melo'),
    (23,'José','Barrera Meza','Gaitán Cortés'), (24,'Adriana','Medina Huamán','Forero Parra'),
    (25,'Lorena','Campos Mena','Correa Nieto'), (26,'Pablo','Durand Vidal','Medina Hoyos'),
    (27,'Claudia','Peralta Pacheco','Salazar Trujillo'), (28,'Diego','Aguirre Zapata','Mendoza Rivas'),
    (29,'Inés','Silva Castillo','Cardona Miranda'), (30,'Bruno','Aguilar Carrasco','Núñez Acosta'),
    (31,'Ana Paula','Oviedo Quispe','Delgado Lozano'), (32,'Daniel','Mori Navarro','Molina Escobar');

CREATE TEMP TABLE demo_clinica_sedes (
    pais text PRIMARY KEY, codigo text NOT NULL, nombre text NOT NULL, zona text NOT NULL, base_dni integer NOT NULL
) ON COMMIT DROP;
INSERT INTO demo_clinica_sedes VALUES
    ('pe','DEMO-MEDISHIFT-PE','Clínica Los Sauces · Demo Perú','America/Lima',90100),
    ('co','DEMO-MEDISHIFT-CO','Centro Médico La Alameda · Demo Colombia','America/Bogota',91100);

CREATE FUNCTION pg_temp.id_demo_clinica(sede text, entidad text, clave text DEFAULT '')
RETURNS uuid LANGUAGE sql IMMUTABLE STRICT
RETURN md5('medishift:demo-clinica:v1:' || sede || ':' || entidad || ':' || clave)::uuid;

DO $demostracion$
DECLARE
    sede record;
    entidad uuid;
    administrador uuid;
    doctor uuid;
    primer_mes date;
    ultimo_mes date;
    hoy date;
    claves jsonb := nullif(current_setting('medishift.accesos_demostracion', true), '')::jsonb;
    usa_etiquetas boolean := to_regclass(current_schema() || '.area_etiqueta') IS NOT NULL;
    nombres text[] := ARRAY['Valeria','Sebastián','Rosa','Óscar','Luz Marina','David','Micaela','Ricardo',
        'Carmen','Luis','Noelia','Santiago','Ruth','Carlos','Elisa','Fernando','Natalia','Joel','Emilia','Rafael'];
    apellidos text[] := ARRAY['Paredes','Mendoza','Ruiz','Salazar','Duarte','Arias','Molina','López',
        'Ortega','Rivas','Vargas','Rojas','Mora','Peña','Navarro','Flores','Suárez'];
BEGIN
    IF to_regclass(current_schema() || '.cita') IS NULL
       OR to_regclass(current_schema() || '.paciente_dni_activo_portal') IS NULL THEN
        RAISE EXCEPTION 'Aplica V001–V007 antes de cargar la demostración clínica.';
    END IF;
    IF claves IS NULL THEN
        RAISE EXCEPTION 'Ejecuta scripts/cargar_demostracion_clinica.py para preparar los accesos.';
    END IF;

    -- Primero los bloqueos de institución que también usa el servidor.
    PERFORM pg_advisory_xact_lock(hashtextextended('catalogos:' ||
        pg_temp.id_demo_clinica(d.pais,'institucion')::text, 0))
    FROM demo_clinica_sedes d ORDER BY d.pais;
    IF (SELECT count(*) FROM role WHERE code_role IN ('administrador','doctor')) < 2 THEN
        LOCK TABLE role IN SHARE ROW EXCLUSIVE MODE;
        INSERT INTO role(id_role,code_role,description_role)
        SELECT (SELECT coalesce(max(id_role),0) FROM role) + row_number() OVER (ORDER BY r.codigo),
               r.codigo, r.descripcion
        FROM (VALUES ('administrador','Administración institucional'),('doctor','Profesional médico')) r(codigo,descripcion)
        WHERE NOT EXISTS (SELECT 1 FROM role WHERE code_role=r.codigo);
    END IF;

    FOR sede IN SELECT * FROM demo_clinica_sedes ORDER BY pais LOOP
        entidad := pg_temp.id_demo_clinica(sede.pais,'institucion');
        administrador := pg_temp.id_demo_clinica(sede.pais,'cuenta','admin');
        doctor := pg_temp.id_demo_clinica(sede.pais,'cuenta','doctor');
        hoy := (now() AT TIME ZONE sede.zona)::date;
        primer_mes := date_trunc('month', hoy::timestamp)::date;
        ultimo_mes := (primer_mes + interval '3 months')::date;

        IF EXISTS (SELECT 1 FROM institution i
                   WHERE (i.code_institution=sede.codigo OR i.id_institution=entidad)
                     AND (i.id_institution<>entidad OR i.code_institution<>sede.codigo
                          OR i.time_zone_institution<>sede.zona OR i.status_institution<>'active')) THEN
            RAISE EXCEPTION 'El código de % ya está en uso o su configuración no coincide.', sede.codigo;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM institution WHERE id_institution=entidad) THEN
            IF EXISTS (SELECT 1 FROM paciente WHERE dni BETWEEN lpad((sede.base_dni+1)::text,8,'0')
                                               AND lpad((sede.base_dni+240)::text,8,'0')) THEN
                RAISE EXCEPTION 'El rango de DNI ficticios de % coincide con fichas existentes.', sede.codigo;
            END IF;
            INSERT INTO institution(id_institution,code_institution,name_institution,time_zone_institution,status_institution)
            VALUES (entidad,sede.codigo,sede.nombre,sede.zona,'active');
            INSERT INTO institution_settings(id_institution,medical_self_scheduling) VALUES (entidad,true);
            INSERT INTO user_account(id_user_account,id_institution,email_user_account,password_hash_user_account,status_user_account)
            SELECT cuenta.id,entidad,cuenta.tipocuenta || '.' || sede.pais || '@medishift.example.test',
                   claves -> sede.pais ->> cuenta.tipocuenta,'active'
            FROM (VALUES (administrador,'admin'),(doctor,'doctor')) cuenta(id,tipocuenta)
            WHERE (claves -> sede.pais ->> cuenta.tipocuenta) ~ '^[{]pbkdf2@medishift-v1[}][0-9a-f]{96}$';
            IF NOT FOUND OR (SELECT count(*) FROM user_account WHERE id_institution=entidad) <> 2 THEN
                RAISE EXCEPTION 'Faltan los accesos válidos para %.', sede.codigo;
            END IF;
            INSERT INTO user_role(id_user_account,id_role)
            SELECT CASE WHEN code_role='administrador' THEN administrador ELSE doctor END,id_role
            FROM role WHERE code_role IN ('administrador','doctor');

            INSERT INTO specialty(id_specialty,code_specialty,name_specialty,id_institution)
            SELECT pg_temp.id_demo_clinica(sede.pais,'especialidad',a.codigo),
                   'CLINICA-DEMO-' || upper(sede.pais) || '-' || a.codigo,a.especialidad,entidad
            FROM demo_clinica_areas a;
            INSERT INTO planning_unit(id_planning_unit,id_institution,code_planning_unit,name_planning_unit,
                                      type_planning_unit,status_planning_unit,id_specialty)
            SELECT pg_temp.id_demo_clinica(sede.pais,'area',a.codigo),entidad,a.codigo,a.nombre,'service','active',
                   pg_temp.id_demo_clinica(sede.pais,'especialidad',a.codigo)
            FROM demo_clinica_areas a;
            INSERT INTO room(id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
            SELECT pg_temp.id_demo_clinica(sede.pais,'consultorio',a.codigo || ':' || n.numero),
                   entidad,a.codigo || '-0' || n.numero,
                   CASE WHEN a.codigo='CIR' AND n.numero=2 THEN 'Sala de procedimientos' ELSE 'Consultorio ' || n.numero END,
                   'Piso ' || (1 + a.numero / 2) || ', ala ' || CASE WHEN a.numero%2=0 THEN 'A' ELSE 'B' END,
                   false,'active',pg_temp.id_demo_clinica(sede.pais,'area',a.codigo)
            FROM demo_clinica_areas a CROSS JOIN LATERAL generate_series(1,a.salas) n(numero);
            INSERT INTO room_specialty(id_room,id_specialty)
            SELECT r.id_room,a.id_specialty FROM room r JOIN planning_unit a USING (id_institution,id_planning_unit)
            WHERE r.id_institution=entidad;

            INSERT INTO professional(id_professional,id_institution,id_user_account,first_name_professional,last_name_professional,
                                     license_number_professional,category_professional,email_professional,status_professional)
            SELECT pg_temp.id_demo_clinica(sede.pais,'profesional',m.numero::text),entidad,CASE WHEN m.numero=1 THEN doctor END,
                   m.nombres,CASE WHEN sede.pais='pe' THEN m.apellidos_pe ELSE m.apellidos_co END,
                   'DEMO-' || upper(sede.pais) || '-' || lpad(m.numero::text,4,'0'),'Médico',
                   CASE WHEN m.numero=1 THEN 'doctor' ELSE 'medico' || m.numero END || '.' || sede.pais || '@medishift.example.test',
                   'active'
            FROM demo_clinica_medicos m;
            INSERT INTO professional_specialty(id_professional,id_specialty,is_primary_professional_specialty)
            SELECT pg_temp.id_demo_clinica(sede.pais,'profesional',m.numero::text),
                   pg_temp.id_demo_clinica(sede.pais,'especialidad',a.codigo),(m.numero-1)/4=a.numero
            FROM demo_clinica_medicos m JOIN demo_clinica_areas a
              ON (m.numero-1)/4=a.numero OR (m.numero=1 AND a.numero=1) OR (m.numero=5 AND a.numero=0);

            INSERT INTO paciente(id,id_institucion,codigo,nombres,apellidos,dni,fecha_nacimiento,correo,activo)
            SELECT pg_temp.id_demo_clinica(sede.pais,'paciente',n.numero::text),entidad,'DEMO-P' || lpad(n.numero::text,4,'0'),
                   nombres[1+(n.numero-1)%20],apellidos[1+((n.numero-1)/20+(n.numero-1)%17)%17]
                   || ' ' || apellidos[1+((n.numero-1)/20+(n.numero-1)%17+1+(n.numero-1)/17)%17],
                   lpad((sede.base_dni+n.numero)::text,8,'0'),
                   CASE WHEN n.numero=1 THEN date '1990-05-14'
                        WHEN (n.numero-1)/30=3 THEN date '2013-06-01'+((n.numero-1)*149)%3650
                        ELSE date '1951-01-01'+((n.numero-1)*317)%20300 END,
                   CASE WHEN n.numero%3=0 THEN '' ELSE 'paciente' || n.numero || '.' || sede.pais || '@medishift.example.test' END,
                   n.numero%30<>0
            FROM generate_series(1,240) n(numero);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM user_account u JOIN user_role ur USING (id_user_account)
                       JOIN role r USING (id_role)
                       WHERE u.id_user_account=administrador AND u.id_institution=entidad
                         AND u.status_user_account='active' AND r.code_role='administrador') THEN
            RAISE EXCEPTION 'La cuenta administrativa de % no está activa. Revisa sus permisos.', sede.codigo;
        END IF;

        CREATE TEMP TABLE demo_clinica_planes ON COMMIT DROP AS
        SELECT a.*,fecha.mes::date AS mes,pg_temp.id_demo_clinica(sede.pais,'area',a.codigo) AS id_area,
               pg_temp.id_demo_clinica(sede.pais,'plan',a.codigo || ':' || to_char(fecha.mes,'YYYY-MM')) AS id,
               CASE WHEN fecha.mes::date=ultimo_mes AND a.numero<2 THEN 'draft' ELSE 'approved' END AS estado
        FROM demo_clinica_areas a
        CROSS JOIN generate_series(primer_mes::timestamp,ultimo_mes::timestamp,interval '1 month') fecha(mes)
        WHERE EXISTS (
            SELECT 1 FROM generate_series(greatest((hoy+1)::timestamp,fecha.mes),
                        fecha.mes+interval '1 month - 1 day',interval '1 day') d(dia)
            WHERE extract(isodow FROM d.dia)<=5 AND to_char(d.dia,'MM-DD') NOT IN ('01-01','12-25'))
          AND NOT EXISTS (
            SELECT 1 FROM schedule h
            WHERE h.id_institution=entidad AND h.id_planning_unit=pg_temp.id_demo_clinica(sede.pais,'area',a.codigo)
              AND h.period_type_schedule='monthly' AND h.period_start_schedule=fecha.mes::date)
          AND NOT EXISTS (
            SELECT 1 FROM audit_event e WHERE e.id_institution=entidad AND e.entity_name_audit_event='schedule'
              AND e.entity_id_audit_event=pg_temp.id_demo_clinica(sede.pais,'plan',a.codigo || ':' || to_char(fecha.mes,'YYYY-MM'))
              AND e.action_audit_event='seed_demo');

        CREATE TEMP TABLE demo_clinica_turnos ON COMMIT DROP AS
        WITH franjas AS (
            SELECT p.id AS plan,p.numero AS area,p.codigo,p.id_area,p.mes,p.estado,d.dia::date AS dia,s.sala,f.franja,
                   (extract(isodow FROM d.dia)::integer-1 + (s.sala-1)*2 + f.franja + extract(week FROM d.dia)::integer)%4
                       AS numero_medico
            FROM demo_clinica_planes p
            CROSS JOIN LATERAL generate_series(greatest(p.mes,hoy+1)::timestamp,
                       p.mes+interval '1 month - 1 day',interval '1 day') d(dia)
            CROSS JOIN LATERAL generate_series(1,p.salas) s(sala)
            CROSS JOIN generate_series(0,1) f(franja)
            WHERE to_char(d.dia,'MM-DD') NOT IN ('01-01','12-25')
              AND ((extract(isodow FROM d.dia)<=5 AND (s.sala=1 OR (extract(isodow FROM d.dia)::integer+f.franja)%2=1))
                OR (extract(isodow FROM d.dia)=6 AND p.numero IN (2,3) AND s.sala=1 AND f.franja=0))
        )
        SELECT f.*,pg_temp.id_demo_clinica(sede.pais,'turno',f.codigo || ':' || to_char(f.dia,'YYYYMMDD') || ':' || f.sala || ':' || f.franja) AS id,
               pg_temp.id_demo_clinica(sede.pais,'especialidad',f.codigo) AS especialidad,
               pg_temp.id_demo_clinica(sede.pais,'consultorio',f.codigo || ':' || f.sala) AS consultorio,
               pg_temp.id_demo_clinica(sede.pais,'profesional',
                   (1+f.numero_medico+4*CASE WHEN f.area<2 AND f.numero_medico=0 AND extract(isodow FROM f.dia) IN (2,4)
                                            THEN 1-f.area ELSE f.area END)::text) AS profesional,
               (f.dia+CASE WHEN f.franja=0 THEN time '08:00' ELSE time '14:00' END) AT TIME ZONE sede.zona AS inicio,
               (f.dia+CASE WHEN f.franja=0 THEN time '12:00' ELSE time '18:00' END) AT TIME ZONE sede.zona AS fin,
               f.area IN (2,3) AND f.sala=1 AND f.franja=1 AND f.dia-f.mes<7
                   AND extract(isodow FROM f.dia)=4 AND f.estado='approved' AS falta
        FROM franjas f;

        CREATE TEMP TABLE demo_clinica_etiquetas(id_area uuid, id_etiqueta uuid) ON COMMIT DROP;
        IF usa_etiquetas THEN
            INSERT INTO demo_clinica_etiquetas
            SELECT a.id_area,a.id_etiqueta FROM area_etiqueta a JOIN specialty e ON e.id_specialty=a.id_etiqueta
            WHERE a.id_institucion=entidad AND e.activa;
        ELSE
            INSERT INTO demo_clinica_etiquetas
            SELECT id_planning_unit,id_specialty FROM planning_unit
            WHERE id_institution=entidad AND id_specialty IS NOT NULL;
        END IF;

        -- Validar los recursos y los cruces también para los borradores.
        IF EXISTS (
            SELECT 1 FROM demo_clinica_turnos t
            JOIN demo_clinica_planes h ON h.id=t.plan
            LEFT JOIN planning_unit a ON a.id_planning_unit=h.id_area AND a.id_institution=entidad
                AND a.status_planning_unit='active'
            LEFT JOIN professional p ON p.id_professional=t.profesional AND p.id_institution=entidad AND p.status_professional='active'
            LEFT JOIN room r ON r.id_room=t.consultorio AND r.id_institution=entidad AND r.status_room='active'
                AND r.id_planning_unit=h.id_area
            WHERE a.id_planning_unit IS NULL OR p.id_professional IS NULL OR r.id_room IS NULL
               OR NOT EXISTS (SELECT 1 FROM demo_clinica_etiquetas e WHERE (e.id_area,e.id_etiqueta)=(h.id_area,t.especialidad))
               OR NOT EXISTS (SELECT 1 FROM professional_specialty v WHERE id_professional=t.profesional AND id_specialty=t.especialidad
                    AND coalesce((to_jsonb(v)->>'activo')::boolean,true))
               OR (NOT r.is_general_room AND NOT EXISTS (
                    SELECT 1 FROM room_specialty WHERE id_room=t.consultorio AND id_specialty=t.especialidad))
               OR EXISTS (SELECT 1 FROM professional_leave l WHERE l.id_professional=t.profesional
                    AND l.status_professional_leave='approved' AND l.start_at_professional_leave<t.fin AND l.end_at_professional_leave>t.inicio)
               OR EXISTS (SELECT 1 FROM room_block b WHERE b.id_room=t.consultorio
                    AND b.status_room_block='active' AND b.start_at_room_block<t.fin AND b.end_at_room_block>t.inicio)
               OR EXISTS (SELECT 1 FROM shift s JOIN schedule existente USING (id_institution,id_schedule)
                    WHERE s.id_institution=entidad AND (s.id_professional=t.profesional OR s.id_room=t.consultorio)
                      AND s.status_shift IN ('draft','pending','approved') AND existente.status_schedule IN ('draft','pending','approved')
                      AND s.start_at_shift<t.fin AND s.end_at_shift>t.inicio)
               OR EXISTS (SELECT 1 FROM demo_clinica_turnos otro
                          WHERE otro.id<>t.id AND (otro.profesional=t.profesional OR otro.consultorio=t.consultorio)
                            AND otro.inicio<t.fin AND otro.fin>t.inicio)
        ) THEN
            RAISE EXCEPTION 'Hay recursos incompatibles o cruces con la programación de %.', sede.codigo;
        END IF;

        IF EXISTS (
            WITH turnos AS (
                SELECT profesional,inicio,fin FROM demo_clinica_turnos
                UNION ALL
                SELECT s.id_professional,s.start_at_shift,s.end_at_shift
                FROM shift s JOIN schedule h USING (id_institution,id_schedule)
                WHERE s.id_institution=entidad AND s.status_shift IN ('draft','pending','approved')
                  AND h.status_schedule IN ('draft','pending','approved')
                  AND s.end_at_shift > (date_trunc('week',hoy::timestamp) AT TIME ZONE sede.zona)
                  AND s.start_at_shift < (date_trunc('week',ultimo_mes::timestamp+interval '1 month - 1 day')
                                           +interval '1 week') AT TIME ZONE sede.zona
            ), minutos AS (
                SELECT profesional,d.dia,
                       sum(extract(epoch FROM least(fin,(d.dia+interval '1 day') AT TIME ZONE sede.zona)
                                          -greatest(inicio,d.dia AT TIME ZONE sede.zona))/60) AS cantidad
                FROM turnos CROSS JOIN LATERAL generate_series(date_trunc('day',inicio AT TIME ZONE sede.zona),
                                    date_trunc('day',fin AT TIME ZONE sede.zona),interval '1 day') d(dia)
                GROUP BY profesional,d.dia
            )
            SELECT 1 FROM minutos m LEFT JOIN institution_settings i ON i.id_institution=entidad
            WHERE EXISTS (SELECT 1 FROM demo_clinica_turnos t
                          WHERE t.profesional=m.profesional AND date_trunc('week',t.dia::timestamp)=date_trunc('week',m.dia))
            GROUP BY m.profesional,date_trunc('week',m.dia),i.daily_minutes,i.weekly_minutes
            HAVING max(m.cantidad)>coalesce(i.daily_minutes,360) OR sum(m.cantidad)>coalesce(i.weekly_minutes,2160)
        ) THEN
            RAISE EXCEPTION 'Los turnos excederían los límites horarios vigentes de %.', sede.codigo;
        END IF;

        INSERT INTO schedule(id_schedule,id_institution,period_start_schedule,period_end_schedule,version_schedule,
                             status_schedule,id_creator_user_account,id_approver_user_account,approved_at_schedule,
                             period_type_schedule,id_planning_unit)
        SELECT id,entidad,mes,(mes+interval '1 month - 1 day')::date,1,estado,administrador,
               CASE WHEN estado='approved' THEN administrador END,CASE WHEN estado='approved' THEN now() END,'monthly',id_area
        FROM demo_clinica_planes;
        INSERT INTO shift(id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
                          start_at_shift,end_at_shift,status_shift,note_shift)
        SELECT id,entidad,plan,profesional,consultorio,especialidad,inicio,fin,estado,
               CASE WHEN area=1 AND sala=2 THEN 'Procedimientos ambulatorios'
                    WHEN franja=1 THEN 'Consulta ambulatoria · tarde' ELSE 'Consulta ambulatoria · mañana' END
        FROM demo_clinica_turnos;
        INSERT INTO attendance(id_attendance,id_institution,id_shift,id_recorder_user_account,status_attendance)
        SELECT pg_temp.id_demo_clinica(sede.pais,'falta',id::text),entidad,id,administrador,'absent'
        FROM demo_clinica_turnos WHERE falta;

        WITH disponibles AS (
            SELECT t.*,row_number() OVER (PARTITION BY t.plan ORDER BY t.profesional<>pg_temp.id_demo_clinica(sede.pais,'profesional','1'),t.inicio,t.sala) AS orden
            FROM demo_clinica_turnos t WHERE t.estado='approved' AND NOT t.falta
        ), reservas AS (
            SELECT t.*,n.puesto,
                CASE WHEN t.area=0 AND t.orden=1 AND n.puesto=0 THEN 1
                     -- Una cohorte por área, con posiciones distintas para las franjas de cada día.
                     ELSE 2+((t.dia-date '2000-01-01')*11+(t.sala-1)*4+t.franja*2+n.puesto)%28+t.area*30 END AS paciente
            FROM disponibles t CROSS JOIN generate_series(0,1) n(puesto)
            WHERE (t.area=0 AND t.orden=1 AND n.puesto=0)
               OR (extract(doy FROM t.dia)::integer+t.area+t.sala+t.franja)%3=0
        )
        INSERT INTO cita(id,id_institucion,id_paciente,id_turno,id_profesional,id_consultorio,inicio,fin,id_creador)
        SELECT pg_temp.id_demo_clinica(sede.pais,'cita',r.id || ':' || r.puesto),entidad,p.id,r.id,r.profesional,r.consultorio,
               r.inicio+interval '35 minutes'+r.puesto*interval '80 minutes',
               r.inicio+interval '60 minutes'+r.puesto*interval '85 minutes',administrador
        FROM reservas r JOIN paciente p ON p.id_institucion=entidad AND p.activo
            AND p.id=pg_temp.id_demo_clinica(sede.pais,'paciente',r.paciente::text);

        INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
                                entity_id_audit_event,action_audit_event,after_audit_event)
        SELECT pg_temp.id_demo_clinica(sede.pais,'auditoria',h.id::text),entidad,administrador,'schedule',h.id,'seed_demo',
               jsonb_build_object('periodo',h.mes,'estado',h.estado,'origen','db/datos_demostracion_clinica.sql')
        FROM demo_clinica_planes h;
        DROP TABLE pg_temp.demo_clinica_turnos,pg_temp.demo_clinica_planes,pg_temp.demo_clinica_etiquetas;
    END LOOP;
END;
$demostracion$;

-- Las instalaciones con V008 reciben una subárea programable en cada clínica.
DO $subarea$
DECLARE
    sede record;
    entidad uuid;
    administrador uuid;
    preoperatoria uuid;
    cirugia uuid;
    dermatologia uuid;
    general uuid;
    restringido uuid;
    hoy date;
    ultimo date;
    momento record;
BEGIN
    IF to_regclass(current_schema() || '.area_etiqueta') IS NULL THEN RETURN; END IF;
    FOR sede IN SELECT * FROM demo_clinica_sedes ORDER BY pais LOOP
        entidad := pg_temp.id_demo_clinica(sede.pais,'institucion');
        administrador := pg_temp.id_demo_clinica(sede.pais,'cuenta','admin');
        preoperatoria := pg_temp.id_demo_clinica(sede.pais,'area','PRE');
        cirugia := pg_temp.id_demo_clinica(sede.pais,'especialidad','CIR');
        dermatologia := pg_temp.id_demo_clinica(sede.pais,'especialidad','DER');
        general := pg_temp.id_demo_clinica(sede.pais,'consultorio','PRE:1');
        restringido := pg_temp.id_demo_clinica(sede.pais,'consultorio','PRE:2');
        hoy := (now() AT TIME ZONE sede.zona)::date;
        ultimo := (date_trunc('month',hoy::timestamp)+interval '3 months')::date;

        IF NOT EXISTS (SELECT 1 FROM planning_unit WHERE id_institution=entidad AND id_planning_unit=preoperatoria) THEN
            INSERT INTO planning_unit(id_planning_unit,id_institution,code_planning_unit,name_planning_unit,
                                      type_planning_unit,status_planning_unit,id_area_padre)
            VALUES (preoperatoria,entidad,'PRE','Evaluación preoperatoria','service','active',
                    pg_temp.id_demo_clinica(sede.pais,'area','CIR'));
            INSERT INTO area_etiqueta VALUES (entidad,preoperatoria,cirugia),(entidad,preoperatoria,dermatologia);
            INSERT INTO room(id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
            VALUES (general,entidad,'PRE-01','Evaluación clínica','Piso 1, ala B',true,'active',preoperatoria),
                   (restringido,entidad,'PRE-02','Evaluación quirúrgica','Piso 1, ala B',false,'active',preoperatoria);
            INSERT INTO room_specialty VALUES (restringido,cirugia);
        END IF;

        CREATE TEMP TABLE demo_subarea_turnos ON COMMIT DROP AS
        WITH dias AS (
            SELECT d::date AS dia,date_trunc('month',d)::date AS mes,extract(isodow FROM d)::integer AS semana_dia,
                   extract(week FROM d)::integer AS semana
            FROM generate_series((hoy+1)::timestamp,ultimo+interval '1 month - 1 day',interval '1 day') d
            WHERE extract(isodow FROM d)<=5 AND to_char(d,'MM-DD') NOT IN ('01-01','12-25')
        ), candidatos AS (
            SELECT d.*,pg_temp.id_demo_clinica(sede.pais,'plan','PRE:' || to_char(d.mes,'YYYY-MM')) AS id_plan,
                pg_temp.id_demo_clinica(sede.pais,'turno','PRE:' || to_char(d.dia,'YYYYMMDD')) AS id_turno,
                pg_temp.id_demo_clinica(sede.pais,'profesional',CASE WHEN (semana+semana_dia)%2=0 THEN '1' ELSE '5' END) AS medico,
                CASE WHEN semana_dia%2=0 THEN restringido ELSE general END AS sala,
                CASE WHEN semana_dia%2=0 OR semana_dia=5 THEN cirugia ELSE dermatologia END AS etiqueta,
                (dia+time '12:00') AT TIME ZONE sede.zona AS inicio,(dia+time '14:00') AT TIME ZONE sede.zona AS fin
            FROM dias d
        ), elegibles AS (
            SELECT t.*,permisos.weekly_minutes,semana_ocupada.minutos AS ocupacion,
                row_number() OVER (PARTITION BY t.medico,date_trunc('week',t.dia::timestamp) ORDER BY t.dia) AS orden
            FROM candidatos t
            JOIN room r ON r.id_room=t.sala AND r.id_institution=entidad AND r.id_planning_unit=preoperatoria
            JOIN etiqueta_consultorio_programable ec ON ec.id_room=t.sala AND ec.id_specialty=t.etiqueta
            JOIN professional p ON p.id_professional=t.medico AND p.id_institution=entidad
            JOIN professional_specialty pe ON pe.id_professional=t.medico AND pe.id_specialty=t.etiqueta AND pe.activo
            JOIN institution_settings permisos ON permisos.id_institution=entidad
            CROSS JOIN LATERAL (
                SELECT coalesce(sum(extract(epoch FROM s.end_at_shift-s.start_at_shift)/60),0) AS minutos,
                       coalesce(sum(extract(epoch FROM s.end_at_shift-s.start_at_shift)/60) FILTER (
                           WHERE (s.start_at_shift AT TIME ZONE sede.zona)::date=t.dia),0) AS minutos_dia
                FROM shift s JOIN schedule h USING (id_institution,id_schedule)
                WHERE s.id_institution=entidad AND s.id_professional=t.medico
                  AND s.start_at_shift >= (date_trunc('week',t.dia::timestamp) AT TIME ZONE sede.zona)
                  AND s.start_at_shift < ((date_trunc('week',t.dia::timestamp)+interval '1 week') AT TIME ZONE sede.zona)
                  AND s.status_shift IN ('draft','pending','approved') AND h.status_schedule IN ('draft','pending','approved')
            ) semana_ocupada
            WHERE p.status_professional='active' AND r.status_room='active'
              AND semana_ocupada.minutos_dia+120<=permisos.daily_minutes
              AND NOT EXISTS (SELECT 1 FROM schedule h WHERE h.id_institution=entidad AND h.id_planning_unit=preoperatoria
                              AND h.period_start_schedule=t.mes)
              AND NOT EXISTS (SELECT 1 FROM audit_event a WHERE a.id_institution=entidad
                              AND a.entity_id_audit_event=t.id_plan AND a.action_audit_event='seed_demo')
              AND NOT EXISTS (SELECT 1 FROM shift s JOIN schedule h USING (id_institution,id_schedule)
                              WHERE s.id_institution=entidad AND (s.id_professional=t.medico OR s.id_room=t.sala)
                                AND s.start_at_shift<t.fin AND s.end_at_shift>t.inicio
                                AND s.status_shift IN ('draft','pending','approved') AND h.status_schedule IN ('draft','pending','approved'))
              AND NOT EXISTS (SELECT 1 FROM professional_leave l WHERE l.id_professional=t.medico AND l.status_professional_leave='approved'
                              AND l.start_at_professional_leave<t.fin AND l.end_at_professional_leave>t.inicio)
              AND NOT EXISTS (SELECT 1 FROM room_block b WHERE b.id_room=t.sala AND b.status_room_block='active'
                              AND b.start_at_room_block<t.fin AND b.end_at_room_block>t.inicio)
        )
        SELECT * FROM elegibles WHERE ocupacion+orden*120<=weekly_minutes;

        FOR momento IN SELECT DISTINCT t.id_plan,t.mes FROM demo_subarea_turnos t LOOP
            INSERT INTO schedule(id_schedule,id_institution,period_start_schedule,period_end_schedule,version_schedule,
                                 status_schedule,id_creator_user_account,id_approver_user_account,approved_at_schedule,
                                 period_type_schedule,id_planning_unit)
            VALUES (momento.id_plan,entidad,momento.mes,(momento.mes+interval '1 month - 1 day')::date,1,
                    CASE WHEN momento.mes=ultimo THEN 'draft' ELSE 'approved' END,administrador,
                    CASE WHEN momento.mes<>ultimo THEN administrador END,CASE WHEN momento.mes<>ultimo THEN now() END,'monthly',preoperatoria);
            INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
                                    entity_id_audit_event,action_audit_event,after_audit_event)
            VALUES (pg_temp.id_demo_clinica(sede.pais,'auditoria',momento.id_plan::text),entidad,administrador,'schedule',
                    momento.id_plan,'seed_demo',jsonb_build_object('periodo',momento.mes,'area','PRE'));
        END LOOP;

        INSERT INTO shift(id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
                          start_at_shift,end_at_shift,status_shift,note_shift)
        SELECT t.id_turno,entidad,t.id_plan,t.medico,t.sala,t.etiqueta,t.inicio,t.fin,
               CASE WHEN t.mes=ultimo THEN 'draft' ELSE 'approved' END,'Evaluación preoperatoria'
        FROM demo_subarea_turnos t;
        INSERT INTO cita(id,id_institucion,id_paciente,id_turno,id_profesional,id_consultorio,inicio,fin,id_creador)
        SELECT pg_temp.id_demo_clinica(sede.pais,'cita',t.id_turno::text),entidad,p.id,t.id_turno,t.medico,t.sala,
               t.inicio+interval '20 minutes',t.inicio+interval '50 minutes',administrador
        FROM demo_subarea_turnos t JOIN paciente p ON p.id_institucion=entidad AND p.activo
          AND p.id=pg_temp.id_demo_clinica(sede.pais,'paciente',(121+extract(doy FROM t.dia)::integer%29)::text)
        WHERE t.mes<ultimo AND t.semana_dia IN (1,3,5)
          AND NOT EXISTS (SELECT 1 FROM cita c WHERE c.id_institucion=entidad AND c.id_paciente=p.id AND c.estado<>'cancelada'
                          AND c.inicio<t.inicio+interval '50 minutes' AND c.fin>t.inicio+interval '20 minutes');
        DROP TABLE pg_temp.demo_subarea_turnos;
    END LOOP;
END;
$subarea$;

SELECT json_agg(json_build_object(
    'institucion',i.code_institution,
    'areas',(SELECT count(*) FROM planning_unit WHERE id_institution=i.id_institution),
    'consultorios',(SELECT count(*) FROM room WHERE id_institution=i.id_institution),
    'medicos',(SELECT count(*) FROM professional WHERE id_institution=i.id_institution),
    'pacientes',(SELECT count(*) FROM paciente WHERE id_institucion=i.id_institution),
    'periodos',(SELECT json_agg(json_build_object('mes',mes,'planes',planes,'aprobados',aprobados,
        'turnos',turnos,'citas',citas) ORDER BY mes) FROM (
        SELECT to_char(h.period_start_schedule,'YYYY-MM') AS mes,count(DISTINCT h.id_schedule) AS planes,
               count(DISTINCT h.id_schedule) FILTER (WHERE h.status_schedule='approved') AS aprobados,
               count(DISTINCT t.id_shift) AS turnos,count(c.id) AS citas
        FROM schedule h LEFT JOIN shift t USING (id_institution,id_schedule)
        LEFT JOIN cita c ON c.id_institucion=t.id_institution AND c.id_turno=t.id_shift
        WHERE h.id_institution=i.id_institution AND h.period_type_schedule='monthly'
          AND h.status_schedule IN ('draft','pending','approved')
          AND h.period_end_schedule >= (now() AT TIME ZONE i.time_zone_institution)::date
        GROUP BY h.period_start_schedule
    ) meses)) ORDER BY i.code_institution)
FROM demo_clinica_sedes d JOIN institution i ON i.id_institution=pg_temp.id_demo_clinica(d.pais,'institucion');

DROP FUNCTION pg_temp.id_demo_clinica(text,text,text);
COMMIT;
