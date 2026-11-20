import hashlib
import json
from pathlib import Path
import re
from tempfile import TemporaryDirectory
import unittest

from preparar_sql_editor_supabase import ARCHIVOS_PRIVADOS, preparar_archivos


class PrepararSqlEditorSupabaseTests(unittest.TestCase):
    def test_genera_sql_para_el_editor_y_preserva_los_accesos_en_cada_ejecucion(self):
        with TemporaryDirectory() as carpeta:
            salida = Path(carpeta)
            preparar_archivos(salida)
            for nombre in ("instalar-esquema.sql", "verificar-esquema.sql"):
                sql = (salida / nombre).read_text(encoding="utf-8")
                self.assertIn("\nBEGIN;", sql)
                self.assertIn("\nCOMMIT;", sql)
                self.assertNotRegex(sql, r"(?m)^\s*\\(?:set|if|endif|gset)\b")
                self.assertNotIn("LOGIN PASSWORD", sql)

            privado = salida / "privado.local"

            def huellas():
                return {nombre: hashlib.sha256((privado / nombre).read_bytes()).hexdigest()
                        for nombre in ARCHIVOS_PRIVADOS}

            primera_generacion = huellas()
            preparar_archivos(salida)
            self.assertEqual(primera_generacion, huellas())

    def test_guarda_solo_verificadores_en_los_sql_privados(self):
        with TemporaryDirectory() as carpeta:
            salida = Path(carpeta)
            preparar_archivos(salida)
            privado = salida / "privado.local"
            clave_backend = (privado / "clave-backend.local").read_text(encoding="utf-8").strip().split("=", 1)[1]
            cuentas = json.loads((privado / "accesos-demostracion.local").read_text(encoding="utf-8"))["cuentas"]
            claves = {valor["contrasena"] for sede in cuentas.values() for valor in sede.values()}
            claves.add(clave_backend)
            self.assertEqual(len(claves), 5)

            activacion = (privado / "activar-backend.sql.local").read_text(encoding="utf-8")
            demostracion = (privado / "cargar-demostracion.sql.local").read_text(encoding="utf-8")
            self.assertTrue("SCRAM-SHA-256$4096:" in activacion)
            self.assertEqual(demostracion.count("{pbkdf2@medishift-v1}"), 4)
            self.assertFalse(any(clave in activacion or clave in demostracion for clave in claves))

    def test_activa_rls_en_las_tablas_temporales_antes_del_commit(self):
        with TemporaryDirectory() as carpeta:
            salida = Path(carpeta)
            preparar_archivos(salida)
            sql = (salida / "privado.local/cargar-demostracion.sql.local").read_text(encoding="utf-8")
            tablas = re.findall(r"(?m)^CREATE TEMP TABLE (\w+) \(", sql)
            self.assertEqual(tablas, ["demo_clinica_areas", "demo_clinica_medicos", "demo_clinica_sedes"])
            confirmacion = sql.index("\nCOMMIT;")
            for tabla in tablas:
                posicion = sql.index(f"ALTER TABLE {tabla} ENABLE ROW LEVEL SECURITY;")
                self.assertLess(posicion, sql.index(f"INSERT INTO {tabla} VALUES"))
                self.assertLess(posicion, confirmacion)


if __name__ == "__main__":
    unittest.main()
