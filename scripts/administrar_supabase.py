"""Instala o verifica el esquema privado de MediShift con psql y TLS verificado."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess

from administrar_migracion_mensual import RAIZ, exigir, literal


BASE = RAIZ / "db/medishift_postgresql.sql"
MIGRACIONES = RAIZ / "db/migraciones"
SUPABASE = RAIZ / "db/supabase"
PERMISOS = SUPABASE / "privilegios.sql"
PATRON_MIGRACION = re.compile(r"V([0-9]{3})__[a-z0-9_]+\.sql")
PATRON_PROYECTO = re.compile(r"[a-z0-9]{20}")
PATRON_POOLER = re.compile(r"[a-z0-9-]+\.pooler\.supabase\.com")

REGISTRO = """
CREATE TABLE medishift.schema_migration (
    version varchar(32) PRIMARY KEY,
    name varchar(255) NOT NULL,
    checksum_sha256 varchar(64) NOT NULL CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    applied_at timestamptz NOT NULL DEFAULT now()
);
"""


def leer_sql(ruta):
    # read_text conserva el mismo SQL y checksum con finales de línea LF o CRLF.
    return ruta.read_text(encoding="utf-8")


def leer_migraciones():
    cambios = []
    for ruta in sorted(MIGRACIONES.glob("V*.sql")):
        nombre = PATRON_MIGRACION.fullmatch(ruta.name)
        exigir(nombre is not None, f"Nombre de migración inválido: {ruta.name}.")
        version = nombre.group(1)
        exigir(int(version) == len(cambios) + 1, f"Secuencia de migraciones inválida en {ruta.name}.")
        cambios.append((version, ruta.name, leer_sql(ruta)))
    exigir(len(cambios) >= 9, "La instalación requiere las migraciones desde V001 hasta V009.")
    return cambios


def generar_sql(solo_verificar=False):
    base = leer_sql(BASE)
    # psql administra la transacción y ubica btree_gist en el esquema de extensiones.
    cuerpo_base, retiradas = re.subn(r"(?m)^(BEGIN;|COMMIT;|CREATE EXTENSION IF NOT EXISTS btree_gist;)$", "", base)
    exigir(retiradas == 3, "Cambió la estructura de la línea base. Revisa el instalador antes de continuar.")
    cambios = leer_migraciones()
    permisos = leer_sql(PERMISOS)
    archivos = [("000", BASE.name, base), *cambios, ("supabase-001", PERMISOS.name, permisos)]
    firmas = []
    for version, nombre, sql in archivos:
        huella = hashlib.sha256(sql.encode("utf-8")).hexdigest()
        firmas.append(f"({literal(version)}, {literal(nombre)}, {literal(huella)})")
    filas = ",\n".join(firmas)
    es_verificacion = "on" if solo_verificar else "off"
    sentencia_verificacion = f"""
DO $registro$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES {filas}) AS esperado(version, nombre, huella)
        FULL JOIN medishift.schema_migration aplicado USING (version)
        WHERE aplicado.name IS DISTINCT FROM esperado.nombre
            OR aplicado.checksum_sha256 IS DISTINCT FROM esperado.huella
    ) THEN
        RAISE EXCEPTION 'El registro de migraciones no coincide con esta versión de MediShift';
    END IF;
END;
$registro$;
"""
    return "\n".join((
        f"\\set solo_verificar {es_verificacion}",
        "SET TRANSACTION READ ONLY;" if solo_verificar else "",
        leer_sql(SUPABASE / "preparar.sql"),
        r"\if :instalacion_nueva",
        cuerpo_base,
        *(sql for _, _, sql in cambios),
        REGISTRO,
        f"INSERT INTO medishift.schema_migration (version, name, checksum_sha256) VALUES\n{filas};",
        permisos,
        r"\endif",
        sentencia_verificacion,
        leer_sql(SUPABASE / "verificar.sql"),
    ))


def localizar_psql():
    encontrado = shutil.which("psql")
    if encontrado is not None:
        return encontrado
    instalado = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "PostgreSQL/18/bin/psql.exe"
    exigir(instalado.is_file(), "Instala psql y añádelo a PATH.")
    return str(instalado)


def ejecutar(opciones):
    servidor = opciones.servidor.lower()
    exigir(PATRON_POOLER.fullmatch(servidor), "Copia solo el host del Session pooler de Supabase.")
    exigir(PATRON_PROYECTO.fullmatch(opciones.proyecto), "La referencia de proyecto de Supabase debe tener 20 caracteres.")
    certificado = opciones.ca.resolve(strict=True)
    exigir(certificado.is_file(), "Indica el archivo de certificado CA que descargaste de Supabase.")
    sql = generar_sql(solo_verificar=opciones.accion == "verificar")

    # Evita que libpq herede credenciales u opciones de otra base.
    entorno = {clave: valor for clave, valor in os.environ.items() if not clave.upper().startswith("PG")}
    entorno.update(PGSSLMODE="verify-full", PGSSLROOTCERT=str(certificado), PGGSSENCMODE="disable",
                   PGCONNECT_TIMEOUT="10", PGCLIENTENCODING="UTF8", PGAPPNAME="medishift_migraciones")
    comando = [localizar_psql(), "-X", "-W", "--quiet", "--host", servidor, "--port", "5432",
               "--username", f"postgres.{opciones.proyecto}", "--dbname", "postgres", "--set", "ON_ERROR_STOP=1",
               "--single-transaction", "--file", "-"]
    resultado = subprocess.run(comando, input=sql.encode("utf-8"), env=entorno, check=False)
    exigir(resultado.returncode == 0, "psql no pudo completar la operación. Revisa el mensaje anterior.")


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("accion", choices=("instalar", "verificar"))
    argumentos.add_argument("--servidor", required=True, help="Host del Session pooler, sin puerto ni URL")
    argumentos.add_argument("--proyecto", required=True, help="Referencia del proyecto de MediShift")
    argumentos.add_argument("--ca", type=Path, required=True, help="Certificado CA del proyecto")
    try:
        ejecutar(argumentos.parse_args())
    except (OSError, ValueError) as error:
        argumentos.exit(1, f"{error}\n")
    print("Esquema y permisos de MediShift verificados.")
    return 0


if __name__ == "__main__":
    raise SystemExit(principal())
