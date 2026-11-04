"""Respalda, ensaya y aplica V005; conserva los datos previos y deja la opción desactivada.

Uso: python scripts/administrar_autoprogramacion.py estado|ensayar|aplicar
"""
import argparse
import hashlib
import json
import sys

from administrar_configuracion import AdministradorConfiguracion
from administrar_migracion_mensual import AdministradorMigracion, RAIZ, exigir, identificador, literal

MIGRACION = RAIZ / "db/migraciones/V005__autoprogramacion_medica.sql"


class AdministradorAutoprogramacion(AdministradorMigracion):
    def __init__(self):
        super().__init__()
        contenido = MIGRACION.read_bytes()
        self.archivo_sql = contenido.decode("utf-8-sig")
        self.checksum = hashlib.sha256(contenido).hexdigest()

    def estado(self, base):
        anterior = AdministradorConfiguracion().estado(base)
        exigir(anterior["estado"] == "aplicada", "V005 requiere V004 aplicada y verificada.")
        registro = json.loads(self.consultar(base, "SELECT coalesce((SELECT row_to_json(m) "
                            "FROM public.schema_migration m WHERE version='005'), 'null')"))
        columna = json.loads(self.consultar(base, """
            SELECT coalesce((SELECT json_build_object('tipo',format_type(a.atttypid,a.atttypmod),
                'requerida',a.attnotnull,'defecto',pg_get_expr(d.adbin,d.adrelid))
                FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                WHERE a.attrelid='public.institution_settings'::regclass
                AND a.attname='medical_self_scheduling' AND NOT a.attisdropped),'null')
            """))
        if columna:
            exigir(columna == {"tipo": "boolean", "requerida": True, "defecto": "false"},
                   "La opción de autoprogramación tiene una estructura inesperada.")
        if registro:
            exigir(registro.get("checksum_sha256") == self.checksum and registro.get("name") == MIGRACION.name,
                   "V005 figura aplicada con otro archivo o checksum.")
            exigir(columna is not None, "V005 figura aplicada pero falta su columna.")
        return {"base": base, "version": "005", "archivo": MIGRACION.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": anterior["tablas"]}

    @staticmethod
    def expresion_filas(tabla):
        expresion = "to_jsonb(fila)"
        if tabla == "institution_settings":
            expresion += " - 'medical_self_scheduling'"
        return f"SELECT ({expresion})::text AS dato FROM public.{identificador(tabla)} fila"

    def aplicar_sql(self, base, inventario):
        tablas = ",".join("public." + identificador(tabla) for tabla in inventario)
        sql = """
SET LOCAL search_path = public, pg_catalog;
SET LOCAL standard_conforming_strings = on;
SET LOCAL client_min_messages = warning;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(723941650102);
""" + f"LOCK TABLE {tablas},public.schema_migration IN ACCESS EXCLUSIVE MODE;\n"
        sql += "DO $registro$ BEGIN IF EXISTS(SELECT 1 FROM public.schema_migration WHERE version='005' " \
               "AND (checksum_sha256<>" + literal(self.checksum) + " OR name<>" + literal(MIGRACION.name) \
               + ")) THEN RAISE EXCEPTION 'Archivo de V005 diferente'; END IF; END $registro$;\n"
        sql += "SELECT NOT EXISTS(SELECT 1 FROM public.schema_migration WHERE version='005') AS pendiente \\gset\n\\if :pendiente\n"
        sql += self.guardas_datos(inventario) + self.archivo_sql + "\n"
        sql += "INSERT INTO public.schema_migration(version,name,checksum_sha256) VALUES ('005'," \
               + literal(MIGRACION.name) + "," + literal(self.checksum) + ");\n"
        sql += self.guardas_datos(inventario) + "\\endif\n"
        self.ejecutar("psql", ["--dbname", base, "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                               "--single-transaction", "--file=-"], sql)


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorAutoprogramacion()
        resultado = administrador.estado(administrador.base) if opciones.comando == "estado" else \
            administrador.ensayar(aplicar=opciones.comando == "aplicar")
        if resultado.get("resultado") == "sin cambios; V001 ya aplicada":
            resultado["resultado"] = "sin cambios; V005 ya aplicada"
        if opciones.comando != "estado":
            destino = RAIZ / "server/target/migracion-autoprogramacion.json"
            destino.parent.mkdir(parents=True, exist_ok=True)
            destino.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print(str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError)
              else "No se pudo verificar el archivo o la respuesta local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
