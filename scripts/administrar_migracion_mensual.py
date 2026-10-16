"""Respalda, ensaya y aplica V001 explícitamente; nunca se ejecuta desde Spring.

Uso: python scripts/administrar_migracion_mensual.py estado|ensayar|aplicar
El ensayo admite --conservar. Los respaldos contienen datos privados y quedan
en server/target/respaldos, ya excluido de Git. Requiere PostgreSQL 18 local.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path as Ruta
import re
import shutil
import subprocess
import sys
from urllib.parse import unquote, urlsplit
import uuid


RAIZ = Ruta(__file__).resolve().parents[1]
MIGRACION = RAIZ / "db/migraciones/V001__nucleo_mensual.sql"
VERSION = "001"
PREFIJO_ENSAYO = "medishift_ensayo_"
PATRON_ENSAYO = re.compile(r"medishift_ensayo_[0-9a-f]{32}\Z")
COLUMNAS_NUEVAS = ["period_type_schedule", "id_planning_unit"]
INDICES_NUEVOS = ["schedule_weekly_version_unique", "schedule_weekly_one_approved_period",
                  "schedule_monthly_version_unique", "schedule_monthly_one_approved_period"]


def exigir(condicion, mensaje):
    if not condicion:
        raise ValueError(mensaje)


def literal(valor):
    return "'" + str(valor).replace("'", "''") + "'"


def identificador(valor):
    return '"' + valor.replace('"', '""') + '"'


def leer_propiedades(ruta):
    propiedades = {}
    if ruta.exists():
        for linea in ruta.read_text(encoding="utf-8-sig").splitlines():
            if not linea.strip() or linea.lstrip().startswith(("#", "!")):
                continue
            barras_finales = len(linea) - len(linea.rstrip("\\"))
            exigir("=" in linea and barras_finales % 2 == 0,
                   "El .env debe usar propiedades clave=valor, sin continuaciones.")
            clave, valor = linea.split("=", 1)
            valor = re.sub(r"\\(u[0-9a-fA-F]{4}|.)", lambda coincidencia:
                           chr(int(coincidencia[1][1:], 16)) if coincidencia[1].startswith("u")
                           else {"n": "\n", "r": "\r", "t": "\t", "f": "\f"}
                           .get(coincidencia[1], coincidencia[1]), valor.lstrip())
            propiedades[clave.strip()] = valor
    return propiedades


class AdministradorMigracion:
    def __init__(self):
        propiedades = leer_propiedades(RAIZ / "server/.env")
        def obtener(clave, defecto=""):
            return os.environ.get(clave, propiedades.get(clave, defecto))
        direccion = obtener("MEDISHIFT_BD_URL", "jdbc:postgresql://localhost:5432/MediShift")
        exigir(direccion.startswith("jdbc:postgresql://"), "Se requiere una URL JDBC PostgreSQL.")
        try:
            partes = urlsplit(direccion[5:])
            self.puerto = partes.port or 5432
        except ValueError as error:
            raise ValueError("La URL de PostgreSQL tiene un puerto inválido.") from error
        exigir(partes.hostname in ("localhost", "127.0.0.1", "::1"),
               "Esta herramienta solo admite PostgreSQL local.")
        exigir(not (partes.username or partes.password or partes.query or partes.fragment),
               "La URL debe contener solo servidor, puerto y base; use las variables de credenciales.")
        self.servidor = partes.hostname
        self.base = unquote(partes.path.lstrip("/"))
        exigir(bool(re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}", self.base))
               and not self.base.startswith(PREFIJO_ENSAYO), "Nombre de base original inválido.")
        self.usuario = obtener("MEDISHIFT_BD_USUARIO", "postgres")
        exigir(bool(self.usuario) and "\x00" not in self.usuario, "Usuario PostgreSQL requerido.")
        self.entorno = os.environ.copy()
        self.entorno.update(PGPASSWORD=obtener("MEDISHIFT_BD_CLAVE"), PGCONNECT_TIMEOUT="10",
                            PGCLIENTENCODING="UTF8", PGAPPNAME="medishift_migracion_controlada")
        self.entorno.pop("PGOPTIONS", None)
        self.entorno.pop("PGSERVICE", None)
        self.entorno.pop("PGSERVICEFILE", None)
        contenido = MIGRACION.read_bytes()
        self.archivo_sql = contenido.decode("utf-8-sig")
        self.checksum = hashlib.sha256(contenido).hexdigest()
        self.temporal_creada = None

    def ejecutar(self, programa, argumentos, entrada=None, tiempo=300):
        instalado = Ruta(os.environ.get("ProgramFiles", "C:/Program Files")) / "PostgreSQL/18/bin"
        ejecutable = instalado / (programa + ".exe")
        ruta = str(ejecutable) if ejecutable.is_file() else shutil.which(programa)
        exigir(ruta is not None, f"No se encuentra {programa}; instale PostgreSQL 18 o ajuste PATH.")
        comando = [ruta, "--host", self.servidor, "--port", str(self.puerto),
                   "--username", self.usuario, "--no-password", *argumentos]
        try:
            resultado = subprocess.run(comando, input=entrada, capture_output=True,
                                       text=True, encoding="utf-8", env=self.entorno,
                                       timeout=tiempo, check=False)
        except (OSError, subprocess.TimeoutExpired, UnicodeError) as error:
            # El stderr, comando y excepciones originales pueden contener credenciales o filas.
            raise ValueError(f"No se pudo completar {programa}; revise conexión y permisos locales.") from error
        exigir(resultado.returncode == 0,
               f"{programa} falló (código {resultado.returncode}); no se muestran datos privados.")
        return resultado.stdout.strip()

    def consultar(self, base, sql):
        return self.ejecutar("psql", ["--dbname", base, "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                                       "--command", sql])

    def tablas(self, base):
        respuesta = self.consultar(base, """
            SELECT COALESCE(json_agg(c.relname ORDER BY c.relname), '[]'::json)
            FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind IN ('r','p') AND c.relname<>'schema_migration'
        """)
        return json.loads(respuesta)

    def estado(self, base):
        tablas = self.tablas(base)
        exigir({"institution", "schedule", "shift"}.issubset(tablas),
               "Faltan tablas de MediShift en public; no se usa search_path como alternativa.")
        existe = self.consultar(base, "SELECT to_regclass('public.schema_migration') IS NOT NULL") == "t"
        registro = None
        if existe:
            registro = json.loads(self.consultar(base, "SELECT coalesce((SELECT row_to_json(m) FROM "
                                                 "public.schema_migration m WHERE version='001'), 'null')"))
        columnas = json.loads(self.consultar(base, "SELECT coalesce(json_agg(attname), '[]') FROM "
                              "pg_catalog.pg_attribute WHERE attrelid='public.schedule'::regclass "
                              "AND attnum>0 AND NOT attisdropped"))
        indices = json.loads(self.consultar(base, "SELECT json_build_array(" + ",".join(
            "to_regclass(" + literal("public." + nombre) + ") IS NOT NULL"
            for nombre in INDICES_NUEVOS) + ")"))
        objetos = ["planning_unit" in tablas, *(nombre in columnas for nombre in COLUMNAS_NUEVAS), *indices]
        if registro:
            exigir(registro.get("checksum_sha256") == self.checksum
                   and registro.get("name") == MIGRACION.name,
                   "V001 figura aplicada con otro archivo o checksum; se rechaza continuar.")
            exigir(all(objetos), "V001 tiene registro pero su estructura mensual está incompleta.")
        else:
            exigir(not any(objetos), "Hay objetos mensuales sin registro de V001; regularice el estado primero.")
        return {"base": base, "version": VERSION, "archivo": MIGRACION.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": tablas}

    @staticmethod
    def expresion_filas(tabla):
        expresion = "to_jsonb(fila)"
        if tabla == "schedule":
            expresion += " - ARRAY[" + ",".join(map(literal, COLUMNAS_NUEVAS)) + "]::text[]"
        return f"SELECT ({expresion})::text AS dato FROM public.{identificador(tabla)} fila"

    @classmethod
    def expresion_huella(cls, tabla):
        return "SELECT json_build_object('filas', count(*), 'sha256', encode(sha256(convert_to(" \
               "coalesce(string_agg(dato, E'\\n' ORDER BY dato COLLATE \"C\"), '') || " \
               "CASE WHEN count(*)>0 THEN E'\\n' ELSE '' END, 'UTF8')), 'hex')) FROM (" \
               + cls.expresion_filas(tabla) + ") datos"

    def inventario(self, base, tablas):
        return {tabla: json.loads(self.consultar(base, self.expresion_huella(tabla))) for tabla in tablas}

    def guardas_datos(self, inventario):
        guardas = []
        for tabla, huella in inventario.items():
            esperada = json.dumps(huella, sort_keys=True)
            guardas.append("IF (" + self.expresion_huella(tabla) + ")::jsonb <> "
                           + literal(esperada) + "::jsonb THEN RAISE EXCEPTION "
                           "'Los datos originales cambiaron; se cancela V001'; END IF;")
        return "DO $guardas$ BEGIN\n" + "\n".join(guardas) + "\nEND $guardas$;\n"

    def aplicar_sql(self, base, inventario):
        tablas = ",".join("public." + identificador(tabla) for tabla in inventario)
        objetos = ["to_regclass('public.planning_unit') IS NOT NULL"]
        objetos += ["to_regclass(" + literal("public." + nombre) + ") IS NOT NULL"
                    for nombre in INDICES_NUEVOS]
        objetos.append("EXISTS (SELECT 1 FROM pg_catalog.pg_attribute WHERE "
                       "attrelid='public.schedule'::regclass AND NOT attisdropped AND attname IN ("
                       + ",".join(map(literal, COLUMNAS_NUEVAS)) + "))")
        sql = """
SET LOCAL search_path = public, pg_catalog;
SET LOCAL standard_conforming_strings = on;
SET LOCAL client_min_messages = warning;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(723941650102);
""" + f"LOCK TABLE {tablas} IN ACCESS EXCLUSIVE MODE;\n" + """
CREATE TABLE IF NOT EXISTS public.schema_migration (
    version varchar(32) PRIMARY KEY,
    name varchar(255) NOT NULL,
    checksum_sha256 varchar(64) NOT NULL CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    applied_at timestamptz NOT NULL DEFAULT now()
);
DO $registro$ BEGIN
    IF EXISTS (SELECT 1 FROM public.schema_migration WHERE version='001' AND (
""" + "checksum_sha256 <> " + literal(self.checksum) + " OR name <> " + literal(MIGRACION.name) + """
    )) THEN RAISE EXCEPTION 'Checksum o archivo de V001 diferente'; END IF;
END $registro$;
SELECT NOT EXISTS (SELECT 1 FROM public.schema_migration WHERE version='001') AS pendiente \\gset
\\if :pendiente
""" + "DO $objetos$ BEGIN IF " + " OR ".join(objetos) + " THEN RAISE EXCEPTION " \
            "'Objetos mensuales sin registro'; END IF; END $objetos$;\n"
        sql += self.guardas_datos(inventario) + self.archivo_sql + "\n"
        sql += "INSERT INTO public.schema_migration(version,name,checksum_sha256) VALUES ('001'," \
               + literal(MIGRACION.name) + "," + literal(self.checksum) + ");\n"
        sql += self.guardas_datos(inventario) + "\\endif\n"
        self.ejecutar("psql", ["--dbname", base, "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                               "--single-transaction", "--file=-"], sql)

    def eliminar_temporal(self):
        if self.temporal_creada is not None:
            exigir(bool(PATRON_ENSAYO.fullmatch(self.temporal_creada))
                   and self.temporal_creada != self.base,
                   "Se rechazó eliminar una base ajena al ensayo actual.")
            self.ejecutar("dropdb", [self.temporal_creada])
            self.temporal_creada = None

    def ensayar(self, conservar=False, aplicar=False):
        estado = self.estado(self.base)
        if aplicar and estado["estado"] == "aplicada":
            return {**estado, "resultado": "sin cambios; V001 ya aplicada"}
        inventario = self.inventario(self.base, estado["tablas"])
        carpeta = RAIZ / "server/target/respaldos"
        carpeta.mkdir(parents=True, exist_ok=True)
        respaldo = carpeta / ("medishift_" + uuid.uuid4().hex + ".dump")
        self.ejecutar("pg_dump", ["--dbname", self.base, "--format=custom", "--file", str(respaldo)])
        exigir(respaldo.is_file() and respaldo.stat().st_size > 0, "El respaldo quedó vacío.")
        nombre = PREFIJO_ENSAYO + uuid.uuid4().hex
        try:
            self.ejecutar("createdb", ["--template=template0", "--encoding=UTF8", nombre])
            self.temporal_creada = nombre  # Se autoriza borrar solo tras createdb correcto en este proceso.
            self.ejecutar("pg_restore", ["--dbname", nombre, "--no-owner", "--no-privileges",
                                         "--exit-on-error", "--single-transaction", str(respaldo)])
            exigir(self.tablas(nombre) == estado["tablas"], "El respaldo restaurado cambió el inventario.")
            exigir(self.inventario(nombre, estado["tablas"]) == inventario,
                   "El respaldo restaurado no conserva las filas originales.")
            if estado["estado"] == "pendiente":
                self.aplicar_sql(nombre, inventario)
            self.estado(nombre)
            exigir(self.inventario(nombre, estado["tablas"]) == inventario,
                   "El ensayo cambió filas originales; se cancela la aplicación.")
            if aplicar:
                exigir(self.tablas(self.base) == estado["tablas"], "Cambió el inventario original durante el ensayo.")
                self.aplicar_sql(self.base, inventario)
                self.estado(self.base)
            resultado = {"resultado": "aplicada" if aplicar else "ensayo correcto", "base": self.base,
                         "checksum_sha256": self.checksum, "respaldo": str(respaldo),
                         "huellas_originales": inventario}
            if conservar:
                resultado["base_ensayo"] = nombre
                self.temporal_creada = None
            return resultado
        finally:
            self.eliminar_temporal()


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    argumentos.add_argument("--conservar", action="store_true", help="Conservar solo la base del ensayo correcto.")
    opciones = argumentos.parse_args()
    try:
        exigir(not opciones.conservar or opciones.comando == "ensayar", "--conservar requiere ensayar.")
        administrador = AdministradorMigracion()
        resultado = administrador.estado(administrador.base) if opciones.comando == "estado" else \
            administrador.ensayar(opciones.conservar, opciones.comando == "aplicar")
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as error:
        # ValueError controlados no incluyen SQL, respuestas del servidor ni credenciales.
        print(str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError)
              else "No se pudo leer o verificar un archivo/respuesta local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
