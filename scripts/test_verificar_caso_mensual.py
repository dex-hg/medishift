"""Pruebas aisladas del verificador; cada caso negativo parte del JSON original."""

import copy
import io
import json
import subprocess
import sys
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path as Ruta
from unittest.mock import patch

from verificar_caso_mensual import principal, verificar_caso


class PruebasCasoMensual(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ruta = Ruta(__file__).resolve().parents[1] / "db/casos/caso-referencia-mensual.json"
        with cls.ruta.open(encoding="utf-8-sig") as archivo:
            cls.original = json.load(archivo)

    def setUp(self):
        self.caso = copy.deepcopy(self.original)

    def turno(self, codigo):
        return next(turno for turno in self.caso["asignacionesEsperadas"] if turno["codigo"] == codigo)

    def rechazar(self, mensaje):
        with self.assertRaisesRegex(ValueError, mensaje):
            verificar_caso(self.caso)

    def test_referencia_valida_y_totales_exactos(self):
        resultados = verificar_caso(self.caso)
        self.assertEqual(resultados, self.caso["resultadosEsperados"])
        self.assertEqual(resultados["minutosAsignados"], 7920)
        self.assertEqual(resultados["asignaciones"], 44)

    def test_institucion_requerida_y_no_vacia(self):
        for valor in (None, "", "  ", 42):
            with self.subTest(valor=valor):
                self.caso["institucion"] = valor
                self.rechazar("institucion: texto no vacío requerido")

    def test_unidad_requiere_objeto(self):
        for valor in (None, "SERV-PRUEBA", []):
            with self.subTest(valor=valor):
                self.caso["unidadPlanificacion"] = valor
                self.rechazar("unidadPlanificacion: se requiere un objeto")

    def test_codigo_y_nombre_de_unidad_requeridos(self):
        for campo in ("codigo", "nombre"):
            for valor in (None, "", "  ", 42):
                with self.subTest(campo=campo, valor=valor):
                    self.caso = copy.deepcopy(self.original)
                    self.caso["unidadPlanificacion"][campo] = valor
                    self.rechazar(f"unidadPlanificacion.{campo}: texto no vacío requerido")

    def test_tipo_unidad_invalido(self):
        for valor in (None, "institucion", "", []):
            with self.subTest(valor=valor):
                self.caso["unidadPlanificacion"]["tipo"] = valor
                self.rechazar("unidadPlanificacion.tipo: servicio o departamento requerido")

    def test_departamento_es_tipo_valido(self):
        self.caso["unidadPlanificacion"]["tipo"] = "departamento"
        self.assertEqual(verificar_caso(self.caso), self.caso["resultadosEsperados"])

    def test_zona_solo_lima_para_referencia_diurna(self):
        for valor in (None, "", "Zona/Inexistente", "Europe/Madrid"):
            with self.subTest(valor=valor):
                self.caso["zonaHoraria"] = valor
                self.rechazar("zonaHoraria: el caso diurno solo admite America/Lima")

    def test_hueco_un_minuto_en_disponibilidad(self):
        disponibilidad = self.caso["disponibilidades"][0]
        disponibilidad["horaFin"] = "10:00"
        segunda = dict(disponibilidad, horaInicio="10:01", horaFin="14:00")
        self.caso["disponibilidades"].append(segunda)
        self.rechazar("disponibilidad no continua")

    def test_disponibilidad_contigua_cubre_franja(self):
        disponibilidad = self.caso["disponibilidades"][0]
        disponibilidad["horaFin"] = "10:00"
        self.caso["disponibilidades"].append(dict(disponibilidad, horaInicio="10:00", horaFin="14:00"))
        self.assertEqual(verificar_caso(self.caso), self.caso["resultadosEsperados"])

    def test_ausencia_profesional_nueve_noviembre(self):
        self.turno("T-2026-11-09-A01-1")["profesional"] = "P01"
        self.rechazar("conflicto con ausencias")

    def test_bloqueo_consultorio_doce_noviembre(self):
        self.turno("T-2026-11-12-A02-1")["consultorio"] = "C02"
        self.rechazar("conflicto con bloqueos")

    def test_bloqueo_contiguo_no_solapa(self):
        self.caso["bloqueos"].append({"consultorio": "C01", "fecha": "2026-11-02",
                                      "horaInicio": "11:00", "horaFin": "14:00", "estado": "Activo"})
        self.assertEqual(verificar_caso(self.caso), self.caso["resultadosEsperados"])

    def test_solapamiento_consultorio(self):
        self.turno("T-2026-11-06-A01-2")["consultorio"] = "C01"
        self.rechazar("solapamiento de consultorio")

    def test_solapamiento_profesional(self):
        self.turno("T-2026-11-06-A01-2")["profesional"] = "P01"
        self.rechazar("solapamiento de profesional")

    def test_elegibilidad_por_actividad_aunque_especialidad_igual(self):
        self.assertEqual(self.caso["actividades"][0]["especialidad"],
                         self.caso["actividades"][1]["especialidad"])
        self.turno("T-2026-11-02-A02-1")["profesional"] = "P01"
        self.rechazar("profesional no elegible para actividad A02")

    def test_elegibilidad_del_consultorio_por_actividad(self):
        self.caso["consultorios"][1]["actividades"] = ["A01"]
        self.rechazar("consultorio no elegible para actividad A02")

    def test_cobertura_segunda_posicion_faltante(self):
        self.caso["asignacionesEsperadas"].remove(self.turno("T-2026-11-06-A01-2"))
        self.rechazar("cobertura A01 2026-11-06.*1 posiciones asignadas; 2 requeridas")

    def test_cobertura_de_toda_la_franja(self):
        self.turno("T-2026-11-02-A01-1")["horaFin"] = "10:59"
        self.rechazar("cobertura A01 2026-11-02 10:59-11:00")

    def test_asignacion_fijada_removida(self):
        self.caso["asignacionesEsperadas"].remove(self.turno("T-2026-11-30-A01-1"))
        self.rechazar("asignación fijada.*removida o modificada")

    def test_asignacion_fijada_modificada(self):
        self.turno("T-2026-11-30-A01-1")["profesional"] = "P01"
        self.rechazar("asignación fijada.*removida o modificada")

    def test_semana_completa_incluye_seis_diciembre(self):
        vecina = dict(self.caso["asignacionesVecinas"][-1], codigo="V-2026-12-06-P02", fecha="2026-12-06")
        self.caso["asignacionesVecinas"].append(vecina)
        self.rechazar("P02 semana 2026-11-30: límite semanal excedido \\(2340 min\\)")

    def test_limite_diario_usa_parametro_del_caso(self):
        self.caso["parametrosPrueba"]["limiteDiarioMinutos"] = 300
        self.rechazar("límite diario excedido")

    def test_calendario_incompleto(self):
        self.caso["diasSinDemanda"].remove("2026-11-01")
        self.rechazar("mes completo")

    def test_calendario_demanda_y_sin_demanda_superpuestos(self):
        self.caso["diasSinDemanda"].append("2026-11-02")
        self.rechazar("se superponen")

    def test_fecha_fuera_del_periodo(self):
        self.turno("T-2026-11-02-A01-1")["fecha"] = "2026-12-01"
        self.rechazar("fecha fuera del mes")

    def test_referencia_desconocida(self):
        self.turno("T-2026-11-02-A01-1")["profesional"] = "P99"
        self.rechazar("referencia desconocida P99")

    def test_codigo_duplicado(self):
        self.caso["asignacionesEsperadas"].append(dict(self.caso["asignacionesEsperadas"][0]))
        self.rechazar("código duplicado")

    def test_profesional_inactivo(self):
        self.caso["profesionales"][0]["estado"] = "Inactivo"
        self.rechazar("profesional inactivo")

    def test_consultorio_inactivo(self):
        self.caso["consultorios"][0]["estado"] = "Inactivo"
        self.rechazar("consultorio inactivo")

    def test_resultados_de_referencia_no_coinciden(self):
        self.caso["resultadosEsperados"]["minutosAsignados"] = 7921
        self.rechazar("totales calculados difieren")

    def test_estructura_lista_invalida(self):
        self.caso["necesidades"] = None
        self.rechazar("necesidades: se requiere una lista")

    def test_estructura_hora_invalida(self):
        self.turno("T-2026-11-02-A01-1")["horaInicio"] = "25:00"
        self.rechazar("hora inválida")

    def test_caso_diurno_no_simula_nocturnos(self):
        self.turno("T-2026-11-02-A01-1")["horaInicio"] = "22:00"
        self.rechazar("alcance diurno")

    def test_cli_valido_desde_otro_directorio(self):
        resultado = subprocess.run([sys.executable, str(Ruta(__file__).with_name("verificar_caso_mensual.py"))],
                                   cwd=Ruta(__file__).parent, capture_output=True, text=True, check=False)
        self.assertEqual(resultado.returncode, 0, resultado.stderr)
        self.assertEqual(json.loads(resultado.stdout), self.caso["resultadosEsperados"])

    def test_cli_json_invalido_stderr_y_codigo_uno(self):
        salida, errores = io.StringIO(), io.StringIO()
        with patch("verificar_caso_mensual.Ruta.open", return_value=io.StringIO("{")):
            with redirect_stdout(salida), redirect_stderr(errores):
                codigo = principal(["invalido.json"])
        self.assertEqual(codigo, 1)
        self.assertIn("Caso rechazado:", errores.getvalue())
        self.assertEqual(salida.getvalue(), "")


if __name__ == "__main__":
    unittest.main()
