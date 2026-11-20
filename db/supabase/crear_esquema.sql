DO $esquema_nuevo$
BEGIN
    IF to_regnamespace('medishift') IS NOT NULL THEN
        RAISE EXCEPTION 'MediShift ya está instalado. Usa el SQL de verificación.';
    END IF;
END;
$esquema_nuevo$;

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

DO $extension$
DECLARE
    esquema text := (SELECT extnamespace::regnamespace::text FROM pg_extension WHERE extname = 'btree_gist');
BEGIN
    IF NOT has_schema_privilege('medishift_propietario', esquema, 'USAGE') THEN
        EXECUTE format('GRANT USAGE ON SCHEMA %s TO medishift_propietario', esquema);
    END IF;
    PERFORM set_config('search_path', format('medishift, %s, pg_temp', esquema), true);
END;
$extension$;

SET LOCAL ROLE medishift_propietario;
