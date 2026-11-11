"""Ensaya y aplica V007 conservando las filas de las fichas y citas existentes."""

import argparse
import json
import sys

from administrar_migracion_mensual import RAIZ, exigir
from administrar_pacientes_citas import AdministradorPacientesCitas


class AdministradorPortalPacientes(AdministradorPacientesCitas):
    archivo_migracion = RAIZ / "db/migraciones/V007__portal_pacientes.sql"
    version_migracion = "007"

    def estado(self, base):
        anterior = AdministradorPacientesCitas().estado(base)
        exigir(anterior["estado"] == "aplicada", "V007 requiere V006 aplicada y verificada.")
        registro = json.loads(self.consultar(base, """
            SELECT coalesce((SELECT row_to_json(m) FROM public.schema_migration m WHERE version='007'), 'null')
            """))
        tablas = {"cuenta_portal_paciente", "codigo_activacion_paciente"}
        existentes = tablas.intersection(anterior["tablas"])
        exigir(existentes == (tablas if registro else set()), "Las tablas y el registro de V007 no coinciden.")
        if registro:
            exigir(registro["checksum_sha256"] == self.checksum and registro["name"] == self.archivo_migracion.name,
                   "V007 figura aplicada con otro archivo o checksum.")
            self.validar_estructura(base)
        return {"base": base, "version": self.version_migracion, "archivo": self.archivo_migracion.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": anterior["tablas"]}

    def validar_estructura(self, base):
        controles = json.loads(self.consultar(base, """
            SELECT json_build_array(
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.cuenta_portal_paciente'::regclass
                    AND contype='u' AND convalidated AND pg_get_constraintdef(oid)='UNIQUE (dni)'),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.codigo_activacion_paciente'::regclass
                    AND contype='p' AND convalidated AND pg_get_constraintdef(oid)='PRIMARY KEY (dni)'),
                EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.codigo_activacion_paciente'::regclass
                    AND conname='codigo_activacion_ficha' AND contype='f' AND convalidated),
                EXISTS (SELECT 1 FROM pg_index WHERE indexrelid=to_regclass('public.paciente_dni_activo_portal')
                    AND indisvalid AND indpred IS NOT NULL)
            )
            """))
        exigir(all(controles), "Faltan controles de identidad o el índice de fichas activas de V007.")


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorPortalPacientes()
        if opciones.comando == "estado":
            resultado = administrador.estado(administrador.base)
        else:
            resultado = administrador.ensayar(aplicar=opciones.comando == "aplicar")
            if resultado.get("resultado") == "sin cambios; V001 ya aplicada":
                resultado["resultado"] = "sin cambios; V007 ya aplicada"
            destino = RAIZ / "server/target/migracion-portal-pacientes.json"
            destino.parent.mkdir(parents=True, exist_ok=True)
            destino.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError) as error:
        print(str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError)
              else "No se pudo verificar el archivo o la respuesta local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
