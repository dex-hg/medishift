-- Ejecutar como medishift_propietario después de instalar el esquema de la aplicación.
SET LOCAL search_path TO medishift, pg_temp;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

REVOKE ALL ON SCHEMA medishift FROM PUBLIC, anon, authenticated, service_role, authenticator, medishift_app;
REVOKE ALL ON ALL TABLES IN SCHEMA medishift FROM PUBLIC, anon, authenticated, service_role, authenticator, medishift_app;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA medishift FROM PUBLIC, anon, authenticated, service_role, authenticator, medishift_app;
REVOKE ALL ON ALL ROUTINES IN SCHEMA medishift FROM PUBLIC, anon, authenticated, service_role, authenticator, medishift_app;
GRANT USAGE ON SCHEMA medishift TO medishift_app;

-- El acceso corresponde a las operaciones de los DAO JDBC y sus triggers.
GRANT SELECT ON account_settings, area_etiqueta, attendance, availability, cita, coverage_requirement,
    etiqueta_consultorio_programable, historial_plan_mensual, institution, institution_settings, paciente,
    paciente_profesional, planning_unit, professional, professional_leave, professional_specialty, role,
    room, room_block, room_specialty, schedule, shift, specialty, user_account, user_role TO medishift_app;

GRANT INSERT ON account_settings, area_etiqueta, attendance, audit_event, cita, coverage_requirement,
    historial_plan_mensual, institution, institution_settings, paciente, paciente_profesional, planning_unit,
    professional, professional_specialty, role, room, room_specialty, schedule, shift, specialty,
    user_account, user_role TO medishift_app;

GRANT UPDATE ON account_settings, attendance, cita, institution, institution_settings, paciente,
    planning_unit, professional, professional_specialty, room, schedule, shift, specialty, user_account TO medishift_app;

GRANT DELETE ON area_etiqueta, attendance, professional, professional_specialty, room,
    room_specialty, shift, user_role TO medishift_app;

-- La política solo admite al backend; los permisos anteriores delimitan cada operación.
DO $seguridad$
DECLARE
    tabla record;
    funcion regprocedure;
BEGIN
    FOR tabla IN
        SELECT c.oid::regclass AS nombre, has_table_privilege('medishift_app', c.oid,
            'SELECT, INSERT, UPDATE, DELETE') AS usada
        FROM pg_class c WHERE c.relnamespace = 'medishift'::regnamespace AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tabla.nombre);
        IF tabla.usada THEN
            EXECUTE format('CREATE POLICY acceso_backend ON %s TO medishift_app USING (true) WITH CHECK (true)',
                tabla.nombre);
        END IF;
    END LOOP;

    FOR funcion IN
        SELECT p.oid::regprocedure FROM pg_proc p WHERE p.pronamespace = 'medishift'::regnamespace
    LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path TO medishift, pg_temp', funcion);
    END LOOP;
END;
$seguridad$;

ALTER VIEW etiqueta_consultorio_programable SET (security_invoker = true);
