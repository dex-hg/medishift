"""Crea datos ficticios por las API locales, sin modificar otras instituciones.

Uso: python scripts/crear_cuenta_demostracion.py --url http://127.0.0.1:8080
Cuenta pública de demostración; úsela únicamente en desarrollo local.
Puede repetirse: conserva registros encontrados y no restablece contraseñas.
"""

import argparse
from datetime import date as Fecha, timedelta as Intervalo
from http.cookiejar import CookieJar as AlmacenCookies
import json
import sys
from urllib.error import HTTPError as ErrorHttp, URLError as ErrorConexion
from urllib.parse import urlsplit
from urllib.request import build_opener as crear_cliente, HTTPCookieProcessor as CookiesHttp
from urllib.request import Request as Solicitud


CODIGO = "DEMO-HORARIOS"
CORREO = "admin.horarios@example.test"
CLAVE = "HorariosDemo2026!"
NOMBRE = "Clínica de prueba MediShift"
PROFESIONALES = [
    ("Ana", "Torres", "Dermatología"), ("Luis", "Ramos", "Dermatología"),
    ("Lucía", "Vega", "Dermatología"), ("Carlos", "Díaz", "Odontología"),
    ("María", "Soto", "Odontología"), ("Pedro", "Rojas", "Odontología"),
    ("Elena", "Castro", "Medicina general"), ("Jorge", "Paz", "Medicina general"),
]


class ApiDemostracion:
    def __init__(self, direccion, origen):
        for valor in (direccion, origen):
            partes = urlsplit(valor)
            if partes.scheme != "http" or partes.hostname not in ("127.0.0.1", "localhost") \
                    or partes.path not in ("", "/") or partes.query or partes.fragment \
                    or partes.username or partes.password:
                raise ValueError("Se admite solo un origen HTTP local, sin rutas ni credenciales.")
        self.direccion = direccion.rstrip("/")
        self.origen = origen.rstrip("/")
        self.cliente = crear_cliente(CookiesHttp(AlmacenCookies()))

    def solicitar(self, ruta, datos=None, permitir_duplicado=False):
        cabeceras = {"Accept": "application/json", "Origin": self.origen}
        contenido = None
        if datos is not None:
            contenido = json.dumps(datos, ensure_ascii=False).encode("utf-8")
            cabeceras["Content-Type"] = "application/json"
        solicitud = Solicitud(self.direccion + ruta, data=contenido, headers=cabeceras)
        try:
            with self.cliente.open(solicitud, timeout=20) as respuesta:
                return json.load(respuesta)
        except ErrorHttp as error:
            if error.code == 409 and permitir_duplicado:
                return None
            raise ValueError(f"La API {ruta.split('?')[0]} devolvió HTTP {error.code}; "
                             "no se alteran ni borran datos para forzar la demostración.") from error
        except (ErrorConexion, TimeoutError, json.JSONDecodeError) as error:
            raise ValueError("No se pudo contactar o interpretar la API local.") from error


def fecha_demostracion():
    hoy = Fecha.today()
    lunes = hoy + Intervalo(days=7 - hoy.weekday())
    if (lunes + Intervalo(days=2)).month != lunes.month:
        siguiente = (lunes.replace(day=28) + Intervalo(days=4)).replace(day=1)
        lunes = siguiente + Intervalo(days=(-siguiente.weekday()) % 7)
    return lunes


def crear_demostracion(api):
    api.solicitar("/api/registro", {
        "codigoInstitucion": CODIGO, "nombreInstitucion": NOMBRE,
        "zonaHoraria": "America/Lima", "correo": CORREO, "contrasena": CLAVE,
    }, permitir_duplicado=True)
    cuenta = api.solicitar("/api/sesion", {
        "codigoInstitucion": CODIGO, "correo": CORREO, "contrasena": CLAVE,
    })
    if cuenta.get("nombreInstitucion") != NOMBRE or cuenta.get("esAdministrador") is not True:
        raise ValueError("La cuenta encontrada no coincide con la demostración administrativa.")
    inicio = fecha_demostracion()
    periodo = inicio.strftime("%Y-%m")
    calendario = api.solicitar("/api/planificador?periodo=" + periodo)
    areas = {area["codigo"]: area for area in calendario["areas"]}
    consultorios = {sala["codigo"]: sala for sala in calendario["consultorios"]}
    especialidades = {registro["nombre"]: registro for registro in api.solicitar("/api/especialidades")}
    for nombre in ("Dermatología", "Odontología", "Medicina general"):
        if nombre not in especialidades:
            especialidades[nombre] = api.solicitar("/api/especialidades", {"nombre": nombre})
    salas_creadas = []
    for codigo, nombre, cantidad in (("DER", "Dermatología", 2), ("ODO", "Odontología", 3), ("MG", "Medicina general", 1)):
        if codigo not in areas:
            areas[codigo] = api.solicitar("/api/planificador/areas", {
                "codigo": codigo, "nombre": nombre, "idEspecialidad": especialidades[nombre]["id"],
            })
        if areas[codigo].get("idEspecialidad") != especialidades[nombre]["id"]:
            raise ValueError("El área de demostración requiere configurar su especialidad desde Horarios. "
                             "Se conservan sus turnos; corrige primero cualquier asignación incompatible.")
        for numero in range(1, cantidad + 1):
            clave = f"{codigo}-{numero:02}"
            if clave not in consultorios:
                consultorios[clave] = api.solicitar("/api/planificador/consultorios", {
                    "idArea": areas[codigo]["id"], "codigo": clave,
                    "nombre": f"Consultorio {numero}", "ubicacion": f"Primer piso, {nombre}",
                })
            if consultorios[clave].get("idArea") != areas[codigo]["id"]:
                raise ValueError("Un consultorio demo fue reorganizado; se conserva su ubicación actual.")
            salas_creadas.append(consultorios[clave])
    registrados = {persona["colegiatura"]: persona for persona in api.solicitar("/api/profesionales")}
    for numero, (nombres, apellidos, especialidad) in enumerate(PROFESIONALES, 1):
        licencia = f"DEMO-{numero:04}"
        if licencia not in registrados:
            registrados[licencia] = api.solicitar("/api/profesionales", {
                "nombres": nombres, "apellidos": apellidos, "colegiatura": licencia,
                "correo": f"profesional{numero}@example.test", "telefono": "",
                "categoria": "Médico" if especialidad != "Odontología" else "Odontólogo",
                "estado": "Activo", "especialidad": especialidad,
            })
    calendario = api.solicitar("/api/planificador?periodo=" + periodo)
    personas = {persona["id"]: persona for persona in calendario["profesionales"]}
    observaciones = {turno["observacion"] for turno in calendario["turnos"]}
    for numero in range(8):
        observacion = f"Demostración inicial {numero + 1:02}: datos ficticios"
        if observacion in observaciones:
            continue
        profesional = personas[registrados[f"DEMO-{numero + 1:04}"]["id"]]
        sala = salas_creadas[(0, 1, 0, 2, 3, 4, 5, 5)[numero]]
        # Un turno por profesional; los consultorios repetidos usan días diferentes.
        fecha = inicio + Intervalo(days=(0, 0, 1, 0, 0, 0, 2, 1)[numero])
        api.solicitar("/api/planificador/turnos", {
            "idArea": sala["idArea"], "idProfesional": profesional["id"],
            "idConsultorio": sala["id"], "idEspecialidad": profesional["especialidades"][0]["id"],
            "fecha": fecha.isoformat(), "horaInicio": "08:00", "horaFin": "12:00",
            "observacion": observacion,
        })
    resultado = api.solicitar("/api/planificador?periodo=" + periodo)
    return {"codigoInstitucion": CODIGO, "correo": CORREO, "contrasena": CLAVE,
            "periodo": periodo, "areas": len(resultado["areas"]),
            "consultorios": len(resultado["consultorios"]),
            "profesionales": len(resultado["profesionales"]), "turnos": len(resultado["turnos"])}


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("--url", default="http://127.0.0.1:8080")
    argumentos.add_argument("--origen", default="http://127.0.0.1:5173")
    opciones = argumentos.parse_args()
    try:
        resultado = crear_demostracion(ApiDemostracion(opciones.url, opciones.origen))
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, KeyError, OSError) as error:
        print(str(error) if isinstance(error, ValueError) else "La respuesta de la API está incompleta.",
              file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(principal())
