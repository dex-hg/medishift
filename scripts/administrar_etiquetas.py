"""Ensaya y aplica V008 en PostgreSQL local, conservando los registros anteriores."""

import argparse
import json
import sys

from administrar_migracion_mensual import RAIZ, exigir, identificador, literal
from administrar_pacientes_citas import AdministradorPacientesCitas
from administrar_portal_pacientes import AdministradorPortalPacientes


COLUMNAS_NUEVAS = {
    "specialty": ("activa", "nombre_normalizado"),
    "professional_specialty": ("activo",),
    "planning_unit": ("id_area_padre",),
    "shift": ("nombre_etiqueta",),
}


class AdministradorEtiquetas(AdministradorPacientesCitas):
    archivo_migracion = RAIZ / "db/migraciones/V008__etiquetas_y_subareas.sql"
    version_migracion = "008"

    def estado(self, base):
        anterior = AdministradorPortalPacientes().estado(base)
        exigir(anterior["estado"] == "aplicada", "V008 requiere V007 aplicada y verificada.")
        registro, tabla, vista, indice, controles = json.loads(self.consultar(base, """
            SELECT json_build_array(
                (SELECT row_to_json(m) FROM public.schema_migration m WHERE version='008'),
                to_regclass('public.area_etiqueta') IS NOT NULL,
                to_regclass('public.etiqueta_consultorio_programable') IS NOT NULL,
                to_regclass('public.specialty_nombre_institucional') IS NOT NULL,
                (SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='O'
                 AND tgname IN ('planning_unit_jerarquia','shift_capturar_etiqueta','area_etiqueta_institucional',
                     'profesional_etiqueta_institucional','consultorio_etiqueta_institucional',
                     'etiqueta_propietario_inmutable','area_etiqueta_legada')
                 AND tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace))
            )
            """))
        exigir((tabla, vista, indice, controles) == ((True, True, True, 7) if registro else (False, False, False, 0)),
               "Los objetos y el registro de V008 no coinciden.")
        if registro:
            exigir(registro["checksum_sha256"] == self.checksum and registro["name"] == self.archivo_migracion.name,
                   "V008 figura aplicada con otro archivo o checksum.")
        else:
            self.verificar_catalogo(base)
        return {**anterior, "version": self.version_migracion, "archivo": self.archivo_migracion.name,
                "checksum_sha256": self.checksum, "estado": "aplicada" if registro else "pendiente"}

    def verificar_catalogo(self, base):
        duplicados = self.consultar(base, """
            SELECT count(*) FROM (
                SELECT id_institution,lower(btrim(regexp_replace(normalize(name_specialty,NFC),'[[:space:]]+',' ','g')))
                FROM public.specialty GROUP BY 1,2 HAVING count(*)>1
            ) grupos
            """)
        exigir(duplicados == "0", f"Hay {duplicados} grupos de etiquetas con nombre duplicado. Revisa el catálogo antes de migrar.")

    @staticmethod
    def expresion_filas(tabla):
        expresion = "to_jsonb(fila)"
        if tabla in COLUMNAS_NUEVAS:
            expresion += " - ARRAY[" + ",".join(map(literal, COLUMNAS_NUEVAS[tabla])) + "]::text[]"
        return f"SELECT ({expresion})::text AS dato FROM public.{identificador(tabla)} fila"

    def ensayar(self, **opciones):
        resultado = super().ensayar(**opciones)
        if resultado.get("resultado") == "sin cambios; V001 ya aplicada":
            resultado["resultado"] = "sin cambios; V008 ya aplicada"
        return resultado


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("comando", choices=("estado", "ensayar", "aplicar"))
    opciones = argumentos.parse_args()
    try:
        administrador = AdministradorEtiquetas()
        if opciones.comando == "estado":
            resultado = administrador.estado(administrador.base)
        else:
            resultado = administrador.ensayar(aplicar=opciones.comando == "aplicar")
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError) as error:
        print(str(error) if type(error) is ValueError else "No se pudo verificar la migración local.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
