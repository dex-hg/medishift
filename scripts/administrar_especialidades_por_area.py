"""Respalda, restaura y ensaya V003 antes de aplicar las especialidades por área.

Uso: python scripts/administrar_especialidades_por_area.py estado|ensayar|aplicar
Conserva los datos existentes y registra el checksum del archivo aplicado.
"""

import argparse
import hashlib
import json
import sys

from administrar_consultorios_por_area import AdministradorConsultorios
from administrar_migracion_mensual import AdministradorMigracion, RAIZ, exigir, identificador, literal


MIGRACION = RAIZ / "db/migraciones/V003__especialidades_por_area.sql"


class AdministradorEspecialidades(AdministradorMigracion):
    def __init__(self):
        super().__init__()
        contenido = MIGRACION.read_bytes()
        self.archivo_sql = contenido.decode("utf-8-sig")
        self.checksum = hashlib.sha256(contenido).hexdigest()

    def estado(self, base):
        anterior = AdministradorConsultorios().estado(base)
        exigir(anterior["estado"] == "aplicada", "V003 requiere V002 aplicada y verificada.")
        registro = json.loads(self.consultar(base, "SELECT coalesce((SELECT row_to_json(m) "
                              "FROM public.schema_migration m WHERE version='003'), 'null')"))
        objetos = json.loads(self.consultar(base, """
            SELECT json_build_array(
                EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid='public.specialty'::regclass
                    AND attname='id_institution' AND NOT attisdropped AND NOT attnotnull
                    AND atttypid='uuid'::regtype),
                EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid='public.planning_unit'::regclass
                    AND attname='id_specialty' AND NOT attisdropped AND NOT attnotnull
                    AND atttypid='uuid'::regtype),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.specialty'::regclass
                    AND conname='specialty_id_institution_fkey' AND contype='f' AND convalidated
                    AND confrelid='public.institution'::regclass AND confdeltype='c'),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.planning_unit'::regclass
                    AND conname='planning_unit_id_specialty_fkey' AND contype='f' AND convalidated
                    AND confrelid='public.specialty'::regclass),
                EXISTS (SELECT 1 FROM pg_index WHERE indexrelid=to_regclass('public.specialty_institution_index')
                    AND indisvalid AND indrelid='public.specialty'::regclass AND indnatts=1
                    AND pg_get_indexdef(indexrelid,1,true)='id_institution'),
                EXISTS (SELECT 1 FROM pg_index WHERE indexrelid=to_regclass('public.planning_unit_specialty_index')
                    AND indisvalid AND indrelid='public.planning_unit'::regclass AND indnatts=2
                    AND pg_get_indexdef(indexrelid,1,true)='id_institution'
                    AND pg_get_indexdef(indexrelid,2,true)='id_specialty')
            )
        """))
        if registro:
            exigir(registro.get("checksum_sha256") == self.checksum
                   and registro.get("name") == MIGRACION.name,
                   "V003 figura aplicada con otro archivo o checksum; se rechaza continuar.")
            exigir(all(objetos), "V003 tiene registro pero su estructura está incompleta.")
        else:
            exigir(not any(objetos), "Hay objetos V003 sin registro; regularice el estado primero.")
        return {"base": base, "version": "003", "archivo": MIGRACION.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": anterior["tablas"]}

    @staticmethod
    def expresion_filas(tabla):
        expresion = "to_jsonb(fila)"
        if tabla == "specialty":
            expresion += " - 'id_institution'"
        if tabla == "planning_unit":
            expresion += " - 'id_specialty'"
        return f"SELECT ({expresion})::text AS dato FROM public.{identificador(tabla)} fila"

    def guardas_datos(self, inventario):
        return super().guardas_datos(inventario).replace("se cancela V001", "se cancela V003")

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
               "WHERE version='003' AND (checksum_sha256 <> " + literal(self.checksum) \
               + " OR name <> " + literal(MIGRACION.name) + ")) THEN RAISE EXCEPTION " \
               "'Checksum o archivo de V003 diferente'; END IF; END $registro$;\n"
        sql += "SELECT NOT EXISTS (SELECT 1 FROM public.schema_migration WHERE version='003') " \
               "AS pendiente \\gset\n\\if :pendiente\n"
        sql += self.guardas_datos(inventario) + self.archivo_sql + "\n"
        sql += "INSERT INTO public.schema_migration(version,name,checksum_sha256) VALUES ('003'," \
               + literal(MIGRACION.name) + "," + literal(self.checksum) + ");\n"
        sql += self.guardas_datos(inventario) + "\\endif\n"
        self.ejecutar("psql", ["--dbname", base, "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                               "--single-transaction", "--file=-"], sql)

    def ensayar(self, conservar=False, aplicar=False):
        resultado = super().ensayar(conservar, aplicar)
        if resultado.get("resultado") == "sin cambios; V001 ya aplicada":
            resultado["resultado"] = "sin cambios; V003 ya aplicada"
        return resultado


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorEspecialidades()
        resultado = administrador.estado(administrador.base) if opciones.comando == "estado" else \
            administrador.ensayar(aplicar=opciones.comando == "aplicar")
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print(str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError)
              else "No se pudo leer o verificar un archivo/respuesta local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
