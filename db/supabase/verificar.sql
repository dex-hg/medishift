SET LOCAL search_path TO pg_catalog, pg_temp;

DO $verificacion$
DECLARE
    esquema oid := 'medishift'::regnamespace;
    cuenta oid := 'medishift_app'::regrole;
    propietario oid := 'medishift_propietario'::regrole;
    rol_api oid;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE oid IN (cuenta, propietario)
        AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls OR rolinherit))
        OR EXISTS (SELECT 1 FROM pg_roles WHERE oid = propietario AND rolcanlogin)
        OR EXISTS (SELECT 1 FROM pg_auth_members WHERE member IN (propietario, cuenta)
            OR (roleid IN (propietario, cuenta) AND member <> 'postgres'::regrole)) THEN
        RAISE EXCEPTION 'Los roles de MediShift tienen atributos o membresías inesperadas';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE oid = esquema AND nspowner = propietario)
        OR EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = esquema AND relowner <> propietario)
        OR has_database_privilege(cuenta, current_database(), 'CREATE')
        OR has_schema_privilege(cuenta, 'public', 'CREATE')
        OR NOT has_schema_privilege(cuenta, esquema, 'USAGE')
        OR has_schema_privilege(cuenta, esquema, 'CREATE') THEN
        RAISE EXCEPTION 'El backend requiere acceso al esquema privado sin derechos de creación ni propiedad';
    END IF;

    FOR rol_api IN SELECT oid FROM pg_roles WHERE rolname IN ('anon', 'authenticated', 'service_role', 'authenticator')
    LOOP
        IF has_schema_privilege(rol_api, esquema, 'USAGE, CREATE') OR EXISTS (
            SELECT 1 FROM pg_class WHERE relnamespace = esquema AND relkind IN ('r', 'p', 'v', 'm', 'f')
                AND (has_table_privilege(rol_api, oid, 'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
                    OR has_any_column_privilege(rol_api, oid, 'SELECT, INSERT, UPDATE, REFERENCES'))
        ) OR EXISTS (
            SELECT 1 FROM pg_proc WHERE pronamespace = esquema AND has_function_privilege(rol_api, oid, 'EXECUTE')
        ) THEN
            RAISE EXCEPTION 'Un rol de la API de Supabase tiene acceso al esquema privado';
        END IF;
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = esquema AND relkind IN ('r', 'p')
        AND NOT relrowsecurity) OR EXISTS (
        SELECT 1 FROM pg_policy p JOIN pg_class c ON c.oid = p.polrelid
        WHERE c.relnamespace = esquema AND p.polroles <> ARRAY[cuenta]
    ) OR EXISTS (SELECT 1 FROM pg_publication_tables WHERE schemaname = 'medishift' AND pubname = 'supabase_realtime') THEN
        RAISE EXCEPTION 'Verifica RLS y retira de Realtime las tablas de MediShift';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = esquema AND relkind IN ('r', 'p', 'v', 'm', 'f')
        AND has_table_privilege(cuenta, oid, 'TRUNCATE, REFERENCES, TRIGGER')) OR EXISTS (
        SELECT 1 FROM pg_proc WHERE pronamespace = esquema AND (prosecdef OR proowner <> propietario
            OR NOT coalesce(proconfig @> ARRAY['search_path=medishift, pg_temp'], false)
            OR has_function_privilege(cuenta, oid, 'EXECUTE'))
    ) THEN
        RAISE EXCEPTION 'El backend tiene permisos de mantenimiento o alguna rutina ignora el esquema seguro';
    END IF;

    IF NOT has_table_privilege(cuenta, 'medishift.audit_event', 'INSERT')
        OR has_table_privilege(cuenta, 'medishift.audit_event', 'SELECT, UPDATE, DELETE')
        OR has_table_privilege(cuenta, 'medishift.historial_plan_mensual', 'UPDATE, DELETE')
        OR has_table_privilege(cuenta, 'medishift.schema_migration', 'SELECT, INSERT, UPDATE, DELETE')
        OR has_table_privilege(cuenta, 'medishift.cuenta_portal_paciente', 'SELECT, INSERT, UPDATE, DELETE')
        OR has_table_privilege(cuenta, 'medishift.codigo_activacion_paciente', 'SELECT, INSERT, UPDATE, DELETE')
        OR NOT EXISTS (SELECT 1 FROM pg_class WHERE oid = 'medishift.etiqueta_consultorio_programable'::regclass
            AND reloptions @> ARRAY['security_invoker=true']) THEN
        RAISE EXCEPTION 'Revisa los permisos de la auditoría, el registro de migraciones, el portal y la vista';
    END IF;
END;
$verificacion$;
