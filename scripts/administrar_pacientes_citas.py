"""Respalda, restaura un ensayo y aplica V006 sin cambiar las filas existentes.

Uso: python -B scripts/administrar_pacientes_citas.py estado|ensayar|aplicar
"""
import argparse
import hashlib
import json
import sys

from administrar_autoprogramacion import AdministradorAutoprogramacion
from administrar_migracion_mensual import AdministradorMigracion, RAIZ, exigir, identificador, literal

MIGRACION = RAIZ / "db/migraciones/V006__pacientes_citas.sql"
TABLAS = {"paciente", "paciente_profesional", "cita", "historial_plan_mensual"}


class AdministradorPacientesCitas(AdministradorMigracion):
    archivo_migracion = MIGRACION
    version_migracion = "006"

    def __init__(self):
        super().__init__()
        contenido = self.archivo_migracion.read_bytes()
        self.archivo_sql = contenido.decode("utf-8-sig")
        self.checksum = hashlib.sha256(contenido).hexdigest()

    def estado(self, base):
        anterior = AdministradorAutoprogramacion().estado(base)
        exigir(anterior["estado"] == "aplicada", "V006 requiere V005 aplicada y verificada.")
        registro = json.loads(self.consultar(base, "SELECT coalesce((SELECT row_to_json(m) "
                              "FROM public.schema_migration m WHERE version='006'), 'null')"))
        existentes = TABLAS.intersection(anterior["tablas"])
        exigir(not existentes or existentes == TABLAS, "La estructura de pacientes y citas está incompleta.")
        exigir(bool(registro) == bool(existentes), "Las tablas y el registro de V006 no coinciden; revisa la instalación.")
        if registro:
            exigir(registro.get("checksum_sha256") == self.checksum and registro.get("name") == MIGRACION.name,
                   "V006 figura aplicada con otro archivo o checksum.")
            self.validar_estructura(base)
        return {"base": base, "version": "006", "archivo": MIGRACION.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": anterior["tablas"]}

    def validar_estructura(self, base):
        controles = json.loads(self.consultar(base, """
            SELECT json_build_array(
                (SELECT count(*)=2 FROM pg_constraint WHERE conrelid='public.cita'::regclass AND contype='x' AND convalidated),
                (SELECT count(*)=3 FROM pg_trigger WHERE NOT tgisinternal AND tgname IN
                    ('validar_cita','proteger_citas_turno','proteger_citas_plan') AND tgenabled='O'
                    AND tgrelid IN ('public.cita'::regclass,'public.shift'::regclass,'public.schedule'::regclass)),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.paciente'::regclass AND contype='u'
                    AND pg_get_constraintdef(oid)='UNIQUE (id_institucion, dni)')
            )
            """))
        exigir(all(controles), "Faltan controles de duplicados, cruces o protección de citas en V006.")

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
""" + f"LOCK TABLE {tablas},public.schema_migration IN ACCESS EXCLUSIVE MODE;\n"
        version = literal(self.version_migracion)
        nombre = literal(self.archivo_migracion.name)
        checksum = literal(self.checksum)
        sql += f"""DO $registro$ BEGIN
IF EXISTS (SELECT 1 FROM public.schema_migration WHERE version={version}
    AND (checksum_sha256<>{checksum} OR name<>{nombre})) THEN
    RAISE EXCEPTION 'Archivo de migración diferente';
END IF;
END $registro$;
SELECT NOT EXISTS(SELECT 1 FROM public.schema_migration WHERE version={version}) AS pendiente \\gset
\\if :pendiente
"""
        sql += self.guardas_datos(inventario) + self.archivo_sql + "\n"
        sql += f"INSERT INTO public.schema_migration(version,name,checksum_sha256) VALUES ({version},{nombre},{checksum});\n"
        sql += self.guardas_datos(inventario) + "\\endif\n"
        self.ejecutar("psql", ["--dbname", base, "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                               "--single-transaction", "--file=-"], sql)


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorPacientesCitas()
        resultado = administrador.estado(administrador.base) if opciones.comando == "estado" else \
            administrador.ensayar(aplicar=opciones.comando == "aplicar")
        if resultado.get("resultado") == "sin cambios; V001 ya aplicada":
            resultado["resultado"] = "sin cambios; V006 ya aplicada"
        if opciones.comando != "estado":
            destino = RAIZ / "server/target/migracion-pacientes-citas.json"
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
