"""Carga la demostración clínica en el PostgreSQL local configurado en server/.env."""

import argparse
import hashlib
import json
import os
import secrets
import sys
from contextlib import contextmanager

from administrar_migracion_mensual import AdministradorMigracion, RAIZ, exigir, literal


NOMBRE_ACCESOS = "accesos-demostracion.local"
SQL_DEMOSTRACION = RAIZ / "db/datos_demostracion_clinica.sql"
PAISES = ("pe", "co")
ROLES = ("admin", "doctor")

COMPROBAR_DEMOSTRACION_SUPABASE = """
DO $accesos$
DECLARE
    claves jsonb := current_setting('medishift.accesos_demostracion')::jsonb;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM schema_migration WHERE version = '009') THEN
        RAISE EXCEPTION 'Instala y verifica el esquema de MediShift antes de cargar la demostración.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM jsonb_each(claves) AS sede(pais, cuentas)
        CROSS JOIN LATERAL jsonb_each_text(sede.cuentas) AS cuenta(tipo, huella)
        JOIN institution i ON i.code_institution = 'DEMO-MEDISHIFT-' || upper(sede.pais)
        LEFT JOIN user_account u ON u.id_institution = i.id_institution
            AND u.email_user_account = cuenta.tipo || '.' || sede.pais || '@medishift.example.test'
        WHERE u.password_hash_user_account IS DISTINCT FROM cuenta.huella
    ) THEN
        RAISE EXCEPTION 'La demostración ya tiene otros accesos o falta alguna cuenta. Conserva los accesos vigentes.';
    END IF;
END;
$accesos$;
"""


class CargadorDemostracion(AdministradorMigracion):
    def ejecutar_demostracion(self, claves):
        entrada = generar_sql_demostracion(claves)
        salida = self.ejecutar("psql", ["--dbname", self.base, "-X", "-qAt",
                                        "-v", "ON_ERROR_STOP=1", "--file", "-"], entrada)
        return json.loads(salida)

    def contar_instituciones_demo(self):
        return int(self.consultar(self.base, """
            SELECT count(*) FROM public.institution
            WHERE id_institution IN (
                md5('medishift:demo-clinica:v1:pe:institucion:')::uuid,
                md5('medishift:demo-clinica:v1:co:institucion:')::uuid)
        """))


def proteger_clave(clave):
    sal = secrets.token_bytes(16)
    hash_clave = hashlib.pbkdf2_hmac("sha256", clave.encode("utf-8"), sal, 600_000)
    return "{pbkdf2@medishift-v1}" + (sal + hash_clave).hex()


def generar_sql_demostracion(claves, para_supabase=False):
    huellas = {pais: {rol: proteger_clave(claves[pais][rol]) for rol in ROLES} for pais in PAISES}
    configuracion = "SET LOCAL medishift.accesos_demostracion = " + literal(json.dumps(huellas)) + ";"
    ruta_publica = "SET LOCAL search_path = public, pg_catalog;"
    sql = SQL_DEMOSTRACION.read_text(encoding="utf-8")
    exigir(sql.count(ruta_publica) == 1, "Cambió la entrada de la carga de demostración.")
    inicio = [ruta_publica, configuracion]
    if para_supabase:
        inicio = ["SET LOCAL ROLE medishift_propietario;", "SET LOCAL search_path TO medishift, pg_temp;",
                  configuracion, COMPROBAR_DEMOSTRACION_SUPABASE]
    return sql.replace(ruta_publica, "\n".join(inicio), 1)


def archivo_accesos(cargador):
    destino = f"{cargador.servidor}:{cargador.puerto}/{cargador.base}"
    nombre = NOMBRE_ACCESOS
    if (cargador.servidor, cargador.puerto, cargador.base) != ("localhost", 5432, "MediShift"):
        sufijo = hashlib.sha256(destino.encode("utf-8")).hexdigest()[:10]
        nombre = f"accesos-demostracion-{sufijo}.local"
    return RAIZ / "server" / nombre, destino


def leer_accesos(ruta, destino):
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    exigir(isinstance(datos, dict) and datos.get("destino") == destino,
           "El archivo de accesos corresponde a otra base.")
    cuentas = datos.get("cuentas")
    exigir(isinstance(cuentas, dict), "El archivo de accesos clínicos está incompleto.")
    claves = {}
    for pais in PAISES:
        sede = cuentas.get(pais)
        exigir(isinstance(sede, dict), "Falta una sede en el archivo de accesos.")
        claves[pais] = {}
        for rol in ROLES:
            clave = sede.get(rol, {}).get("contrasena") if isinstance(sede.get(rol), dict) else None
            exigir(isinstance(clave, str) and 12 <= len(clave) <= 128,
                   "Falta una contraseña de demostración válida en el archivo local.")
            claves[pais][rol] = clave
    return claves


def crear_accesos(ruta, destino):
    cuentas = {pais: {rol: {"institucion": f"DEMO-MEDISHIFT-{pais.upper()}",
                            "correo": f"{rol}.{pais}@medishift.example.test",
                            "contrasena": secrets.token_urlsafe(24)}
                      for rol in ROLES} for pais in PAISES}
    descriptor = os.open(ruta, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as archivo:
            json.dump({"destino": destino, "cuentas": cuentas}, archivo, ensure_ascii=False, indent=2)
            archivo.write("\n")
    except OSError:
        ruta.unlink(missing_ok=True)
        raise


@contextmanager
def bloqueo_local(ruta):
    ruta_bloqueo = ruta.with_suffix(".bloqueo.local")
    try:
        descriptor = os.open(ruta_bloqueo, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as error:
        raise ValueError("Hay otra carga en curso. Si fue interrumpida, comprueba que terminó antes de retirar "
                         f"el archivo {ruta_bloqueo.name}.") from error
    try:
        os.close(descriptor)
        yield
    finally:
        ruta_bloqueo.unlink()


def cargar(cargador):
    ruta, destino = archivo_accesos(cargador)
    with bloqueo_local(ruta):
        if not ruta.exists():
            exigir(cargador.contar_instituciones_demo() == 0,
                   "Ya existen las cuentas de la demostración clínica. Recupera el archivo de accesos "
                   "original. La carga conserva las contraseñas vigentes.")
            crear_accesos(ruta, destino)
        resumen = cargador.ejecutar_demostracion(leer_accesos(ruta, destino))
    return ruta, resumen


def principal():
    analizador = argparse.ArgumentParser(description=__doc__)
    analizador.parse_args()
    try:
        ruta, resumen = cargar(CargadorDemostracion())
    except (OSError, ValueError) as error:
        mensaje = str(error) if type(error) is ValueError else "No se pudo leer el resultado o el archivo de accesos."
        print(mensaje, file=sys.stderr)
        return 1
    print(json.dumps({"accesos": str(ruta), "instituciones": resumen}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(principal())
