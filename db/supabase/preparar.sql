-- Comprobaciones comunes al instalador y al verificador.
SET LOCAL search_path TO pg_catalog, pg_temp;
SET LOCAL client_min_messages TO warning;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '120s';
SELECT pg_advisory_xact_lock(723941650102);

DO $preparacion$
BEGIN
    IF current_user <> 'postgres' OR current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Conecta con el rol de mantenimiento postgres a la base postgres del proyecto';
    END IF;
    IF current_setting('server_version_num')::integer < 160000
        OR current_setting('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'La instalación requiere PostgreSQL 16 o posterior y UTF-8';
    END IF;
    IF to_regclass('public.institution') IS NOT NULL OR to_regclass('public.paciente') IS NOT NULL THEN
        RAISE EXCEPTION 'Ya hay tablas de MediShift en public. Prepara una migración de datos aparte';
    END IF;
    IF to_regnamespace('medishift') IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relnamespace = to_regnamespace('medishift')
            AND relname = 'schema_migration' AND relkind = 'r'
    ) THEN
        RAISE EXCEPTION 'El esquema medishift existe sin registro de migraciones';
    END IF;
END;
$preparacion$;
