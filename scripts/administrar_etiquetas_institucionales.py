"""Respalda, ensaya y aplica V009 en la instalación local de MediShift."""

import argparse
import json
import sys

from administrar_etiquetas import AdministradorEtiquetas
from administrar_migracion_mensual import RAIZ, exigir, identificador, literal


class AdministradorEtiquetasInstitucionales(AdministradorEtiquetas):
    archivo_migracion = RAIZ / "db/migraciones/V009__etiquetas_institucionales.sql"
    version_migracion = "009"

    def estado(self, base):
        anterior = AdministradorEtiquetas().estado(base)
        exigir(anterior["estado"] == "aplicada", "V009 requiere V008 aplicada y verificada.")
        registro, propietario_obligatorio, control_cobertura = json.loads(self.consultar(base, """
            SELECT json_build_array(
                (SELECT row_to_json(m) FROM public.schema_migration m WHERE version='009'),
                (SELECT attnotnull FROM pg_attribute WHERE attrelid='public.specialty'::regclass
                    AND attname='id_institution' AND NOT attisdropped),
                EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.coverage_requirement'::regclass
                    AND tgname='cobertura_etiqueta_institucional' AND tgenabled='O')
            )
            """))
        exigir(propietario_obligatorio == control_cobertura == bool(registro),
               "Los objetos y el registro de V009 no coinciden.")
        if registro:
            exigir(registro["checksum_sha256"] == self.checksum and registro["name"] == self.archivo_migracion.name,
                   "V009 figura aplicada con otro archivo o checksum.")
        return {**anterior, "version": self.version_migracion, "archivo": self.archivo_migracion.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente",
                "tablas": [tabla for tabla in anterior["tablas"] if tabla != "specialty"]}

    def tablas(self, base):
        # La migración transforma las definiciones globales en copias propias.
        return [tabla for tabla in super().tablas(base) if tabla != "specialty"]

    @staticmethod
    def expresion_filas(tabla):
        campos = {"professional_specialty": "id_specialty", "room_specialty": "id_specialty",
                  "area_etiqueta": "id_etiqueta", "planning_unit": "id_specialty",
                  "coverage_requirement": "id_specialty", "shift": "id_specialty"}
        expresion = "to_jsonb(fila)"
        if tabla in campos:
            expresion += " - " + literal(campos[tabla])
        elif tabla == "historial_plan_mensual":
            # Una versión antigua puede recibir el nombre que le faltaba a un turno.
            expresion += """ || jsonb_build_object('contenido', jsonb_set(fila.contenido, '{turnos}', (
                SELECT coalesce(jsonb_agg(turno - 'nombre_etiqueta' ORDER BY orden), '[]'::jsonb)
                FROM jsonb_array_elements(fila.contenido->'turnos') WITH ORDINALITY t(turno,orden)
            ), false))"""
        return f"SELECT ({expresion})::text AS dato FROM public.{identificador(tabla)} fila"

    def ensayar(self, **opciones):
        resultado = super().ensayar(**opciones)
        if resultado.get("resultado") == "sin cambios; V008 ya aplicada":
            resultado["resultado"] = "sin cambios; V009 ya aplicada"
        return resultado


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorEtiquetasInstitucionales()
        if opciones.comando == "estado":
            resultado = administrador.estado(administrador.base)
        else:
            resultado = administrador.ensayar(aplicar=opciones.comando == "aplicar")
        print(json.dumps({clave: valor for clave, valor in resultado.items() if clave != "huellas_originales"},
                         ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError) as error:
        print(str(error) if type(error) is ValueError else "No se pudo verificar la migración local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
