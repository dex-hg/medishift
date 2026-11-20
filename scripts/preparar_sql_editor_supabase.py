"""Prepara SQL para el panel de Supabase y accesos locales exclusivos del despliegue."""

import argparse
import base64
import hashlib
import hmac
import os
from pathlib import Path
import secrets
from tempfile import TemporaryDirectory

from administrar_migracion_mensual import RAIZ, exigir, literal
from administrar_supabase import generar_sql
from cargar_demostracion_clinica import crear_accesos, generar_sql_demostracion, leer_accesos


SALIDA = RAIZ / "docs/supabase"
DESTINO_DEMOSTRACION = "MediShift en Supabase"
ARCHIVOS_PRIVADOS = (
    "activar-backend.sql.local",
    "cargar-demostracion.sql.local",
    "clave-backend.local",
    "accesos-demostracion.local",
)


def verificador_scram(clave):
    # Las claves generadas son ASCII y PostgreSQL acepta el verificador SCRAM ya calculado.
    iteraciones = 4096
    sal = secrets.token_bytes(16)
    derivada = hashlib.pbkdf2_hmac("sha256", clave.encode("ascii"), sal, iteraciones)
    clave_cliente = hmac.digest(derivada, b"Client Key", "sha256")
    clave_almacenada = hashlib.sha256(clave_cliente).digest()
    clave_servidor = hmac.digest(derivada, b"Server Key", "sha256")
    sal_texto, almacenada, servidor = (
        base64.b64encode(valor).decode("ascii") for valor in (sal, clave_almacenada, clave_servidor)
    )
    return f"SCRAM-SHA-256${iteraciones}:{sal_texto}${almacenada}:{servidor}"


def generar_activacion(clave):
    return f"""-- Ejecutar en SQL Editor después de instalar el esquema.
BEGIN;
SET LOCAL search_path TO pg_catalog, pg_temp;
SET LOCAL lock_timeout TO '10s';
SELECT pg_advisory_xact_lock(723941650102);

DO $activacion$
BEGIN
    IF current_user <> 'postgres' OR current_database() <> 'postgres'
        OR NOT EXISTS (
            SELECT 1 FROM pg_class
            WHERE relnamespace = to_regnamespace('medishift') AND relname = 'schema_migration' AND relkind = 'r'
        )
        OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medishift_app') THEN
        RAISE EXCEPTION 'Instala MediShift primero y ejecuta la activación con el rol postgres.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medishift_app' AND rolcanlogin) THEN
        RAISE EXCEPTION 'medishift_app ya está habilitado. Conserva su contraseña vigente.';
    END IF;
END;
$activacion$;

ALTER ROLE medishift_app LOGIN PASSWORD {literal(verificador_scram(clave))};
COMMIT;
SELECT rolname AS rol, rolcanlogin AS acceso_habilitado FROM pg_catalog.pg_roles WHERE rolname = 'medishift_app';
"""


def escribir_privado(ruta, contenido):
    descriptor = os.open(ruta, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as archivo:
        archivo.write(contenido)


def preparar_archivos(salida):
    salida.mkdir(parents=True, exist_ok=True)
    privado = salida / "privado.local"

    with TemporaryDirectory(dir=salida) as ruta_temporal:
        temporal = Path(ruta_temporal)
        for nombre, solo_verificar in (("instalar-esquema.sql", False), ("verificar-esquema.sql", True)):
            sql = generar_sql(solo_verificar=solo_verificar, para_editor=True)
            archivo = temporal / nombre
            archivo.write_text(sql, encoding="utf-8", newline="\n")
            archivo.replace(salida / nombre)

        if privado.exists():
            exigir(all((privado / nombre).is_file() for nombre in ARCHIVOS_PRIVADOS),
                   "La carpeta privada está incompleta. Recupera sus archivos antes de generar otros accesos.")
            return

        paquete = temporal / "privado.local"
        paquete.mkdir(mode=0o700)
        clave = secrets.token_urlsafe(32)
        escribir_privado(paquete / "clave-backend.local", f"MEDISHIFT_BD_CLAVE={clave}\n")
        escribir_privado(paquete / "activar-backend.sql.local", generar_activacion(clave))

        accesos = paquete / "accesos-demostracion.local"
        crear_accesos(accesos, DESTINO_DEMOSTRACION)
        claves = leer_accesos(accesos, DESTINO_DEMOSTRACION)
        escribir_privado(paquete / "cargar-demostracion.sql.local", generar_sql_demostracion(claves, para_supabase=True))
        paquete.rename(privado)


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.parse_args()
    try:
        preparar_archivos(SALIDA)
    except (OSError, ValueError) as error:
        argumentos.exit(1, f"{error}\n")
    print(f"SQL para el editor y accesos locales preparados en {SALIDA}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(principal())
