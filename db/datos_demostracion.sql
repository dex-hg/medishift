-- MediShift: datos y credenciales PUBLICOS, exclusivamente demostrativos.
-- Requiere el esquema base y las migraciones V001, V002 y V003 instalados.
-- Ejecutar conectado a la base de MediShift, desde pgAdmin o psql.
-- No instala ni reemplaza el esquema. Cada institución se carga una sola vez.
--
-- Cuentas de administrador:
-- DEMO-FERIADOS-PE | admin.feriados.pe@example.test | FeriadosDemo2026!
-- DEMO-FERIADOS-CO | admin.feriados.co@example.test | FeriadosDemo2026!
--
-- Mes: diciembre del año en que se ejecuta; si ya es diciembre, enero siguiente.
-- El resultado de la ejecución informa el mes que se debe consultar en Horarios.
-- Por institución: 9 especialistas, 3 áreas, 6 consultorios, 3 planes mensuales,
-- 12 turnos en borrador y 2 faltas. Un especialista queda sin turnos.
-- Los feriados se obtienen al abrir la aplicación, mediante su API y copia local.

BEGIN;
SET LOCAL search_path = public, pg_catalog;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '30s';

DO $demostracion$
DECLARE
    hoy date := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date;
    periodo date;
    fin_periodo date;
    dia_feriado integer;
    identificador_rol integer;
    identificador_institucion uuid;
    identificador_cuenta uuid;
    identificador_especialidad uuid;
    identificador_area uuid;
    identificador_profesional uuid;
    identificador_consultorio uuid;
    identificador_plan uuid;
    identificador_turno uuid;
    fecha_turno date;
    numero_consultorio integer;
    datos_institucion record;
    datos_area record;
    datos_profesional record;
    datos_turno record;
    hash_clave constant text := '{pbkdf2@medishift-v1}00112233445566778899aabbccddeeffaf2911df0a2adefaf6368dab78b9c37278bfb55ba4618dfb0e483bb5f6f386de';
BEGIN
    IF to_regclass('institution') IS NULL OR to_regclass('planning_unit') IS NULL
       OR to_regclass('attendance') IS NULL
       OR (SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()
           AND ((table_name='specialty' AND column_name='id_institution')
             OR (table_name='planning_unit' AND column_name='id_specialty')
             OR (table_name='room' AND column_name='id_planning_unit')
             OR (table_name='schedule' AND column_name='period_type_schedule'))) <> 4 THEN
        RAISE EXCEPTION 'Falta el esquema de MediShift o sus migraciones V001-V003. Instálelos antes de cargar datos.';
    END IF;

    periodo := make_date(extract(year FROM hoy)::integer, 12, 1);
    IF extract(month FROM hoy)=12 THEN
        periodo := make_date(extract(year FROM hoy)::integer+1, 1, 1);
    END IF;
    fin_periodo := (periodo + interval '1 month - 1 day')::date;
    dia_feriado := CASE WHEN extract(month FROM periodo)=12 THEN 8 ELSE 1 END;

    LOCK TABLE role IN SHARE ROW EXCLUSIVE MODE;
    SELECT id_role INTO identificador_rol FROM role WHERE code_role='administrador';
    IF identificador_rol IS NULL THEN
        SELECT coalesce(max(id_role),0)+1 INTO identificador_rol FROM role;
        IF identificador_rol > 32767 THEN RAISE EXCEPTION 'No queda un identificador de rol disponible.'; END IF;
        INSERT INTO role(id_role,code_role,description_role)
        VALUES(identificador_rol,'administrador','Administración institucional');
    END IF;

    FOR datos_institucion IN
        SELECT * FROM (VALUES
            ('PE','DEMO-FERIADOS-PE','America/Lima','Clínica ficticia de demostración Perú','admin.feriados.pe@example.test'),
            ('CO','DEMO-FERIADOS-CO','America/Bogota','Clínica ficticia de demostración Colombia','admin.feriados.co@example.test')
        ) AS instituciones(pais,codigo,zona,nombre,correo)
    LOOP
        identificador_institucion := md5('medishift-demo-feriados:institucion:'||datos_institucion.codigo)::uuid;
        identificador_cuenta := md5('medishift-demo-feriados:cuenta:'||datos_institucion.codigo)::uuid;
        IF EXISTS (SELECT 1 FROM institution WHERE code_institution=datos_institucion.codigo
                   AND id_institution<>identificador_institucion)
           OR EXISTS (SELECT 1 FROM institution WHERE id_institution=identificador_institucion
                      AND code_institution<>datos_institucion.codigo) THEN
            RAISE EXCEPTION 'El código o identificador reservado para % está ocupado. Se revierte esta carga.', datos_institucion.codigo;
        END IF;
        IF EXISTS (SELECT 1 FROM institution WHERE id_institution=identificador_institucion) THEN
            RAISE NOTICE '% ya existe: se conserva su contenido y su contraseña, sin duplicar la carga.', datos_institucion.codigo;
            CONTINUE;
        END IF;

        INSERT INTO institution(id_institution,code_institution,name_institution,time_zone_institution,status_institution)
        VALUES(identificador_institucion,datos_institucion.codigo,datos_institucion.nombre,datos_institucion.zona,'active');
        INSERT INTO user_account(id_user_account,id_institution,email_user_account,password_hash_user_account,status_user_account)
        VALUES(identificador_cuenta,identificador_institucion,datos_institucion.correo,hash_clave,'active');
        INSERT INTO user_role(id_user_account,id_role) VALUES(identificador_cuenta,identificador_rol);

        FOR datos_area IN
            SELECT * FROM (VALUES ('DER','Dermatología'),('ODO','Odontología'),('MG','Medicina general')) AS areas(codigo,nombre)
        LOOP
            identificador_especialidad := md5('medishift-demo-feriados:especialidad:'||datos_institucion.codigo||':'||datos_area.codigo)::uuid;
            identificador_area := md5('medishift-demo-feriados:area:'||datos_institucion.codigo||':'||datos_area.codigo)::uuid;
            identificador_plan := md5('medishift-demo-feriados:plan:'||datos_institucion.codigo||':'||datos_area.codigo||':'||periodo)::uuid;
            INSERT INTO specialty(id_specialty,code_specialty,name_specialty,id_institution)
            VALUES(identificador_especialidad,'DEMO-'||datos_institucion.pais||'-'||datos_area.codigo,datos_area.nombre,identificador_institucion);
            INSERT INTO planning_unit(id_planning_unit,id_institution,code_planning_unit,name_planning_unit,
                                      type_planning_unit,status_planning_unit,id_specialty)
            VALUES(identificador_area,identificador_institucion,datos_area.codigo,datos_area.nombre,'service','active',identificador_especialidad);
            INSERT INTO schedule(id_schedule,id_institution,period_start_schedule,period_end_schedule,version_schedule,
                                 status_schedule,id_creator_user_account,period_type_schedule,id_planning_unit)
            VALUES(identificador_plan,identificador_institucion,periodo,fin_periodo,1,'draft',identificador_cuenta,'monthly',identificador_area);
            FOR numero_consultorio IN 1..2 LOOP
                identificador_consultorio := md5('medishift-demo-feriados:consultorio:'||datos_institucion.codigo||':'||datos_area.codigo||':'||numero_consultorio)::uuid;
                INSERT INTO room(id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
                VALUES(identificador_consultorio,identificador_institucion,datos_area.codigo||'-0'||numero_consultorio,
                       'Consultorio '||numero_consultorio,'Piso de demostración',false,'active',identificador_area);
                INSERT INTO room_specialty(id_room,id_specialty) VALUES(identificador_consultorio,identificador_especialidad);
            END LOOP;
        END LOOP;

        FOR datos_profesional IN
            SELECT * FROM (VALUES
                (1,'Ana','Torres','DER'),(2,'Luis','Ramos','DER'),(3,'Lucía','Vega','DER'),
                (4,'Carlos','Díaz','ODO'),(5,'María','Soto','ODO'),(6,'Pedro','Rojas','ODO'),
                (7,'Elena','Castro','MG'),(8,'Jorge','Paz','MG'),(9,'Persona','Sin turnos','DER')
            ) AS profesionales(numero,nombres,apellidos,area)
        LOOP
            identificador_profesional := md5('medishift-demo-feriados:profesional:'||datos_institucion.codigo||':'||datos_profesional.numero)::uuid;
            identificador_especialidad := md5('medishift-demo-feriados:especialidad:'||datos_institucion.codigo||':'||datos_profesional.area)::uuid;
            INSERT INTO professional(id_professional,id_institution,first_name_professional,last_name_professional,
                                     license_number_professional,category_professional,email_professional,status_professional)
            VALUES(identificador_profesional,identificador_institucion,datos_profesional.nombres,datos_profesional.apellidos,
                   'DEMO-'||datos_institucion.pais||'-'||lpad(datos_profesional.numero::text,4,'0'),'Médico',
                   'medico'||datos_profesional.numero||'.'||lower(datos_institucion.pais)||'@example.test','active');
            INSERT INTO professional_specialty(id_professional,id_specialty,is_primary_professional_specialty)
            VALUES(identificador_profesional,identificador_especialidad,true);
        END LOOP;

        FOR datos_turno IN
            SELECT * FROM (VALUES
                (1,1,'DER',1,2,'08:00','12:00',false),(2,2,'DER',2,2,'08:00','12:00',true),
                (3,3,'DER',1,3,'08:00','12:00',false),(4,4,'ODO',1,2,'08:00','12:00',false),
                (5,5,'ODO',2,2,'08:00','12:00',false),(6,6,'ODO',1,3,'08:00','12:00',false),
                (7,7,'MG',1,2,'08:00','12:00',false),(8,8,'MG',2,2,'08:00','12:00',false),
                (9,1,'DER',1,0,'08:00','10:00',true),(10,1,'DER',2,0,'10:00','12:00',false),
                (11,4,'ODO',1,5,'12:00','16:00',false),(12,7,'MG',1,6,'08:00','12:00',false)
            ) AS turnos(numero,profesional,area,consultorio,dia,inicio,fin,falta)
        LOOP
            fecha_turno := periodo + (CASE WHEN datos_turno.dia=0 THEN dia_feriado ELSE datos_turno.dia END)-1;
            identificador_turno := md5('medishift-demo-feriados:turno:'||datos_institucion.codigo||':'||periodo||':'||datos_turno.numero)::uuid;
            identificador_profesional := md5('medishift-demo-feriados:profesional:'||datos_institucion.codigo||':'||datos_turno.profesional)::uuid;
            identificador_especialidad := md5('medishift-demo-feriados:especialidad:'||datos_institucion.codigo||':'||datos_turno.area)::uuid;
            identificador_consultorio := md5('medishift-demo-feriados:consultorio:'||datos_institucion.codigo||':'||datos_turno.area||':'||datos_turno.consultorio)::uuid;
            identificador_plan := md5('medishift-demo-feriados:plan:'||datos_institucion.codigo||':'||datos_turno.area||':'||periodo)::uuid;
            INSERT INTO shift(id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
                              start_at_shift,end_at_shift,status_shift,note_shift)
            VALUES(identificador_turno,identificador_institucion,identificador_plan,identificador_profesional,
                   identificador_consultorio,identificador_especialidad,
                   (fecha_turno+datos_turno.inicio::time) AT TIME ZONE datos_institucion.zona,
                   (fecha_turno+datos_turno.fin::time) AT TIME ZONE datos_institucion.zona,
                   'draft','Registro ficticio cargado por db/datos_demostracion.sql');
            IF datos_turno.falta THEN
                INSERT INTO attendance(id_attendance,id_institution,id_shift,id_recorder_user_account,status_attendance)
                VALUES(md5('medishift-demo-feriados:asistencia:'||identificador_turno)::uuid,
                       identificador_institucion,identificador_turno,identificador_cuenta,'absent');
            END IF;
        END LOOP;

        INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
                                entity_id_audit_event,action_audit_event,after_audit_event)
        SELECT md5('medishift-demo-feriados:auditoria:'||entidad||':'||identificador)::uuid,
               identificador_institucion,identificador_cuenta,entidad,identificador,'create',
               captura||jsonb_build_object('demostrativo',true,'origen','db/datos_demostracion.sql')
        FROM (
            SELECT 'institution' AS entidad,id_institution AS identificador,to_jsonb(fila) AS captura FROM institution fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'user_account',id_user_account,to_jsonb(fila)-'password_hash_user_account' FROM user_account fila WHERE id_user_account=identificador_cuenta
            UNION ALL SELECT 'specialty',id_specialty,to_jsonb(fila) FROM specialty fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'planning_unit',id_planning_unit,to_jsonb(fila) FROM planning_unit fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'room',id_room,to_jsonb(fila) FROM room fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'professional',id_professional,to_jsonb(fila) FROM professional fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'schedule',id_schedule,to_jsonb(fila) FROM schedule fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'shift',id_shift,to_jsonb(fila) FROM shift fila WHERE id_institution=identificador_institucion
            UNION ALL SELECT 'attendance',id_attendance,to_jsonb(fila) FROM attendance fila WHERE id_institution=identificador_institucion
        ) AS registros;
        RAISE NOTICE 'Creada % | correo % | mes % | 9 especialistas, 6 consultorios, 12 turnos y 2 faltas.',
                     datos_institucion.codigo,datos_institucion.correo,to_char(periodo,'YYYY-MM');
    END LOOP;
END;
$demostracion$;

COMMIT;
