"""Pruebas sin PostgreSQL para límites y saneamiento de la herramienta local."""

import os
from pathlib import Path as Ruta
import subprocess
import unittest
from unittest.mock import patch

from administrar_migracion_mensual import AdministradorMigracion, leer_propiedades


class SeguridadMigracionTests(unittest.TestCase):
    def crear_administrador(self, direccion="jdbc:postgresql://localhost:5432/MediShift"):
        entorno = {"MEDISHIFT_BD_URL": direccion, "MEDISHIFT_BD_USUARIO": "postgres",
                   "MEDISHIFT_BD_CLAVE": "clave-privada"}
        with patch.dict(os.environ, entorno, clear=True), \
                patch("administrar_migracion_mensual.leer_propiedades", return_value={}):
            return AdministradorMigracion()

    def test_rechaza_servidor_remoto_y_credenciales_en_url(self):
        direcciones = ["jdbc:postgresql://remoto:5432/MediShift",
                       "jdbc:postgresql://usuario:clave@localhost/MediShift",
                       "jdbc:postgresql://localhost/MediShift?password=clave",
                       "jdbc:postgresql://localhost/medishift_ensayo_" + "a" * 32]
        for direccion in direcciones:
            with self.subTest(direccion=direccion), self.assertRaises(ValueError):
                self.crear_administrador(direccion)

    def test_no_filtra_secretos_del_proceso_o_timeout(self):
        administrador = self.crear_administrador()
        resultados = [subprocess.CompletedProcess([], 1, "fila-privada", "clave-privada"),
                      subprocess.TimeoutExpired(["clave-privada"], 1, stderr="fila-privada")]
        for resultado in resultados:
            with self.subTest(resultado=type(resultado).__name__), \
                    patch("administrar_migracion_mensual.shutil.which", return_value="psql"), \
                    patch("administrar_migracion_mensual.Ruta.is_file", return_value=False), \
                    patch("administrar_migracion_mensual.subprocess.run",
                          **({"side_effect": resultado} if isinstance(resultado, Exception)
                             else {"return_value": resultado})) as ejecutar:
                with self.assertRaises(ValueError) as capturado:
                    administrador.ejecutar("psql", ["--dbname", "MediShift"])
                self.assertNotIn("clave-privada", str(capturado.exception))
                self.assertNotIn("fila-privada", str(capturado.exception))
                self.assertNotIn("clave-privada", " ".join(ejecutar.call_args.args[0]))

    def test_no_borra_base_original_ni_ensayo_no_creado(self):
        administrador = self.crear_administrador()
        with patch.object(administrador, "ejecutar") as ejecutar:
            administrador.eliminar_temporal()
            ejecutar.assert_not_called()
            for nombre in ["MediShift", "medishift_ensayo_ajeno", "postgres"]:
                administrador.temporal_creada = nombre
                with self.subTest(nombre=nombre), self.assertRaises(ValueError):
                    administrador.eliminar_temporal()
                ejecutar.assert_not_called()

    def test_createdb_fallido_no_autoriza_dropdb(self):
        administrador = self.crear_administrador()
        def ejecutar(programa, argumentos, **opciones):
            if programa == "createdb":
                raise ValueError("Colisión de nombre o permisos insuficientes")
        with patch.object(Ruta, "mkdir"), patch.object(Ruta, "is_file", return_value=True), \
                patch.object(Ruta, "stat") as estado_archivo, \
                patch.object(administrador, "estado", return_value={"estado": "pendiente", "tablas": []}), \
                patch.object(administrador, "inventario", return_value={}), \
                patch.object(administrador, "ejecutar", side_effect=ejecutar) as proceso:
            estado_archivo.return_value.st_size = 1
            with self.assertRaises(ValueError):
                administrador.ensayar()
            self.assertNotIn("dropdb", [llamada.args[0] for llamada in proceso.call_args_list])

    def test_propiedades_con_barra_final_literal(self):
        with patch.object(Ruta, "exists", return_value=True), \
                patch.object(Ruta, "read_text", return_value="MEDISHIFT_BD_CLAVE=abc\\\\\n"):
            self.assertEqual("abc\\", leer_propiedades(Ruta(".env"))["MEDISHIFT_BD_CLAVE"])


if __name__ == "__main__":
    unittest.main()
