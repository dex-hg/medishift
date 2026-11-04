"""Respalda, ensaya y aplica V004 sin alterar los datos originales.

Uso: python scripts/administrar_configuracion.py estado|ensayar|aplicar
Los respaldos privados se guardan en server/target/respaldos.
"""

import argparse
import hashlib
import json
import sys

from administrar_especialidades_por_area import AdministradorEspecialidades
from administrar_migracion_mensual import AdministradorMigracion, RAIZ, exigir, identificador, literal


MIGRACION = RAIZ / "db/migraciones/V004__configuracion_cuenta_institucion.sql"
TABLAS = {"institution_settings", "account_settings"}


class AdministradorConfiguracion(AdministradorMigracion):
    def __init__(self):
        super().__init__()
        contenido = MIGRACION.read_bytes()
        self.archivo_sql = contenido.decode("utf-8-sig")
        self.checksum = hashlib.sha256(contenido).hexdigest()

    def estado(self, base):
        anterior = AdministradorEspecialidades().estado(base)
        exigir(anterior["estado"] == "aplicada", "V004 requiere V003 aplicada y verificada.")
        registro = json.loads(self.consultar(base, "SELECT coalesce((SELECT row_to_json(m) "
                              "FROM public.schema_migration m WHERE version='004'), 'null')"))
        existentes = TABLAS.intersection(anterior["tablas"])
        exigir(not existentes or existentes == TABLAS, "La estructura de configuración está incompleta.")
        if existentes:
            self.validar_estructura(base)
        if registro:
            exigir(registro.get("checksum_sha256") == self.checksum and registro.get("name") == MIGRACION.name,
                   "V004 figura aplicada con otro archivo o checksum.")
            exigir(existentes == TABLAS, "V004 figura aplicada pero faltan sus tablas.")
        return {"base": base, "version": "004", "archivo": MIGRACION.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": anterior["tablas"]}

    def validar_estructura(self, base):
        columnas = {
            "institution_settings": ["id_institution", "daily_minutes", "weekly_minutes", "holiday_credit_minutes", "revision_settings"],
            "account_settings": ["id_institution", "id_user_account", "display_name", "sidebar_compact",
                                 "default_start_time", "default_end_time", "revision_settings"],
        }
        for tabla, esperadas in columnas.items():
            actuales = json.loads(self.consultar(base, "SELECT json_agg(attname ORDER BY attnum) FROM pg_attribute "
                                                "WHERE attrelid=" + literal("public." + tabla)
                                                + "::regclass AND attnum>0 AND NOT attisdropped"))
            # V005 añade una opción sin cambiar las columnas ni los valores de V004.
            actuales = [columna for columna in actuales if columna != "medical_self_scheduling"]
            exigir(actuales == esperadas, "Las columnas de configuración no coinciden con V004.")
        restricciones = json.loads(self.consultar(base, """
            SELECT json_build_array(
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.institution_settings'::regclass
                    AND conname='institution_settings_week_valid' AND contype='c' AND convalidated),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.account_settings'::regclass
                    AND conname='account_settings_same_tenant' AND contype='f' AND convalidated),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.account_settings'::regclass
                    AND conname='account_settings_time_valid' AND contype='c' AND convalidated)
            )
        """))
        exigir(all(restricciones), "Faltan restricciones de configuración o aislamiento institucional.")

    @staticmethod
    def expresion_filas(tabla):
        return f"SELECT to_jsonb(fila)::text AS dato FROM public.{identificador(tabla)} fila"

    def aplicar_sql(self, base, inventario):
        tablas = ",".join("public." + identificador(tabla) for tabla in inventario)
        sql = """
SET LOCAL search_path = public, pg_catalog;
SET LOCAL standard_conforming_strings = on;
SET LOCAL client_min_messages = warning;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(723941650102);
""" + f"LOCK TABLE {tablas}, public.schema_migration IN ACCESS EXCLUSIVE MODE;\n"
        sql += "DO $registro$ BEGIN IF EXISTS (SELECT 1 FROM public.schema_migration " \
               "WHERE version='004' AND (checksum_sha256 <> " + literal(self.checksum) \
               + " OR name <> " + literal(MIGRACION.name) + ")) THEN RAISE EXCEPTION " \
               "'Checksum o archivo de V004 diferente'; END IF; END $registro$;\n"
        sql += "SELECT NOT EXISTS (SELECT 1 FROM public.schema_migration WHERE version='004') " \
               "AS pendiente \\gset\n\\if :pendiente\n"
        sql += self.guardas_datos(inventario) + self.archivo_sql + "\n"
        sql += "INSERT INTO public.schema_migration(version,name,checksum_sha256) VALUES ('004'," \
               + literal(MIGRACION.name) + "," + literal(self.checksum) + ");\n"
        sql += self.guardas_datos(inventario) + "\\endif\n"
        self.ejecutar("psql", ["--dbname", base, "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                               "--single-transaction", "--file=-"], sql)


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorConfiguracion()
        resultado = administrador.estado(administrador.base) if opciones.comando == "estado" else \
            administrador.ensayar(aplicar=opciones.comando == "aplicar")
        if resultado.get("resultado") == "sin cambios; V001 ya aplicada":
            resultado["resultado"] = "sin cambios; V004 ya aplicada"
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print(str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError)
              else "No se pudo leer o verificar un archivo/respuesta local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
