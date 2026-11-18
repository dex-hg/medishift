-- Entrada del instalador psql. El proceso completo usa una sola transacción.
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

SELECT to_regnamespace('medishift') IS NULL AS instalacion_nueva \gset
\if :instalacion_nueva
    \if :solo_verificar
        DO $$ BEGIN RAISE EXCEPTION 'MediShift todavía no está instalado en este proyecto'; END $$;
    \endif

    CREATE ROLE medishift_propietario NOLOGIN NOINHERIT;
    CREATE ROLE medishift_app NOLOGIN NOINHERIT CONNECTION LIMIT 6;
    GRANT medishift_propietario TO postgres WITH SET TRUE, INHERIT FALSE;
    CREATE SCHEMA medishift AUTHORIZATION medishift_propietario;
    ALTER ROLE medishift_app IN DATABASE postgres SET search_path TO medishift, pg_temp;
    ALTER ROLE medishift_app IN DATABASE postgres SET statement_timeout TO '15s';
    ALTER ROLE medishift_app IN DATABASE postgres SET lock_timeout TO '5s';
    ALTER ROLE medishift_app IN DATABASE postgres SET idle_in_transaction_session_timeout TO '30s';

    CREATE SCHEMA IF NOT EXISTS extensions;
    CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA extensions;
\endif

SELECT n.nspname AS esquema_extension
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'btree_gist' \gset

\if :instalacion_nueva
    SELECT format('GRANT USAGE ON SCHEMA %I TO medishift_propietario', :'esquema_extension')
    WHERE NOT has_schema_privilege('medishift_propietario', :'esquema_extension', 'USAGE') \gexec
\endif

SET LOCAL ROLE medishift_propietario;
SET LOCAL search_path TO medishift, :"esquema_extension", pg_temp;
