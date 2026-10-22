"""Verifica el caso mensual diurno sintético, sin conectarse a PostgreSQL.

Los límites proceden del JSON y solo validan el ejercicio. Este verificador
no implementa la planificación real, cuotas institucionales ni turnos nocturnos.
Solo admite America/Lima y comprueba los metadatos declarados; no demuestra
pertenencia institucional de recursos ni autorización de usuarios.
"""

import argparse
import calendar
import json
import re
import sys
from collections import defaultdict
from datetime import date as Fecha, datetime as FechaHora, timedelta as Diferencia
from pathlib import Path as Ruta


def exigir(condicion, mensaje):
    if not condicion:
        raise ValueError(mensaje)


def lista(datos, campo):
    elementos = datos.get(campo)
    exigir(isinstance(elementos, list), f"{campo}: se requiere una lista")
    return elementos


def objeto(valor, contexto):
    exigir(isinstance(valor, dict), f"{contexto}: se requiere un objeto")
    return valor


def entero(valor, contexto, minimo=1):
    exigir(type(valor) is int and valor >= minimo, f"{contexto}: entero >= {minimo}")
    return valor


def texto_no_vacio(valor, contexto):
    exigir(isinstance(valor, str) and valor.strip(), f"{contexto}: texto no vacío requerido")
    return valor


def fecha(valor, contexto):
    exigir(isinstance(valor, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", valor),
           f"{contexto}: fecha ISO AAAA-MM-DD requerida")
    try:
        return Fecha.fromisoformat(valor)
    except ValueError as error:
        raise ValueError(f"{contexto}: fecha inválida {valor}") from error


def intervalo(elemento, contexto):
    dia = fecha(elemento.get("fecha"), contexto)
    horas = []
    for campo in ("horaInicio", "horaFin"):
        valor = elemento.get(campo)
        exigir(isinstance(valor, str) and re.fullmatch(r"\d{2}:\d{2}", valor),
               f"{contexto}.{campo}: hora HH:MM requerida")
        try:
            horas.append(FechaHora.combine(dia, FechaHora.strptime(valor, "%H:%M").time()))
        except ValueError as error:
            raise ValueError(f"{contexto}.{campo}: hora inválida") from error
    exigir(horas[0] < horas[1], f"{contexto}: intervalo inválido; alcance diurno [inicio, fin)")
    return tuple(horas)


def catalogo(datos, campo):
    indice = {}
    for posicion, elemento in enumerate(lista(datos, campo)):
        objeto(elemento, f"{campo}[{posicion}]")
        codigo = elemento.get("codigo")
        exigir(isinstance(codigo, str) and codigo.strip(), f"{campo}: código requerido")
        exigir(codigo not in indice, f"{campo}: código duplicado {codigo}")
        indice[codigo] = elemento
    return indice


def referencia(elemento, campo, indice, contexto):
    codigo = elemento.get(campo)
    exigir(isinstance(codigo, str) and codigo in indice,
           f"{contexto}.{campo}: referencia desconocida {codigo}")
    return codigo


def minutos(inicio, fin):
    return int((fin - inicio).total_seconds() // 60)


def lunes(dia):
    return dia - Diferencia(days=dia.weekday())


def verificar_caso(datos):
    """Devuelve los resultados calculados o rechaza el JSON con ValueError."""
    objeto(datos, "caso")
    texto_no_vacio(datos.get("institucion"), "institucion")
    unidad = objeto(datos.get("unidadPlanificacion"), "unidadPlanificacion")
    for campo in ("codigo", "nombre"):
        texto_no_vacio(unidad.get(campo), f"unidadPlanificacion.{campo}")
    exigir(unidad.get("tipo") in ("servicio", "departamento"),
           "unidadPlanificacion.tipo: servicio o departamento requerido")
    exigir(datos.get("zonaHoraria") == "America/Lima",
           "zonaHoraria: el caso diurno solo admite America/Lima")
    periodo = datos.get("periodo")
    exigir(isinstance(periodo, str) and re.fullmatch(r"\d{4}-\d{2}", periodo),
           "periodo: formato AAAA-MM requerido")
    inicio_mes = fecha(periodo + "-01", "periodo")
    fin_mes = inicio_mes.replace(day=calendar.monthrange(inicio_mes.year, inicio_mes.month)[1])
    inicio_semanas, fin_semanas = lunes(inicio_mes), lunes(fin_mes) + Diferencia(days=6)
    calendario = {inicio_mes + Diferencia(days=numero) for numero in range(fin_mes.day)}
    parametros = objeto(datos.get("parametrosPrueba"), "parametrosPrueba")
    limite_diario = entero(parametros.get("limiteDiarioMinutos"), "limiteDiarioMinutos")
    limite_semanal = entero(parametros.get("limiteSemanalMinutos"), "limiteSemanalMinutos")
    actividades = catalogo(datos, "actividades")
    profesionales = catalogo(datos, "profesionales")
    consultorios = catalogo(datos, "consultorios")
    for codigo, actividad in actividades.items():
        exigir(type(actividad.get("requiereConsultorio")) is bool,
               f"{codigo}: requiereConsultorio debe ser booleano")
    for indice in (profesionales, consultorios):
        for codigo, elemento in indice.items():
            habilitadas = lista(elemento, "actividades")
            exigir(all(isinstance(valor, str) and valor in actividades for valor in habilitadas),
                   f"{codigo}: actividad desconocida")
            exigir(len(habilitadas) == len(set(habilitadas)), f"{codigo}: actividad duplicada")
    necesidades = catalogo(datos, "necesidades")
    intervalos_necesidades = {}
    for codigo, necesidad in necesidades.items():
        referencia(necesidad, "actividad", actividades, codigo)
        entero(necesidad.get("cantidad"), f"{codigo}.cantidad")
        intervalos_necesidades[codigo] = intervalo(necesidad, codigo)
    dias_demanda = {inicio.date() for inicio, _ in intervalos_necesidades.values()}
    sin_demanda = [fecha(valor, "diasSinDemanda") for valor in lista(datos, "diasSinDemanda")]
    exigir(len(sin_demanda) == len(set(sin_demanda)), "diasSinDemanda: fecha duplicada")
    exigir(not dias_demanda.intersection(sin_demanda), "calendario: demanda y sin demanda se superponen")
    exigir(dias_demanda.union(sin_demanda) == calendario,
           "calendario: demandas y días sin demanda deben cubrir exactamente el mes completo")
    restricciones = []
    for campo, recurso, indice, estado in (("ausencias", "profesional", profesionales, "Aprobado"),
                                           ("bloqueos", "consultorio", consultorios, "Activo")):
        for elemento in lista(datos, campo):
            objeto(elemento, campo)
            referencia(elemento, recurso, indice, campo)
            restricciones.append((campo, recurso, elemento, intervalo(elemento, campo), estado))
    mensuales = catalogo(datos, "asignacionesEsperadas")
    vecinas = catalogo(datos, "asignacionesVecinas")
    exigir(not mensuales.keys() & vecinas.keys(), "asignaciones: código duplicado entre mes y vecinos")
    fijadas = catalogo(datos, "asignacionesFijadas")
    for codigo, fijada in fijadas.items():
        exigir(fijada.get("fijada") is True and mensuales.get(codigo) == fijada,
               f"asignación fijada {codigo}: removida o modificada")
    todos = dict(mensuales, **vecinas)
    intervalos_turnos = {}
    diarios, semanales = defaultdict(int), defaultdict(int)
    por_profesional = {codigo: {"asignaciones": 0, "minutos": 0} for codigo in profesionales}
    for codigo, turno in todos.items():
        actividad = referencia(turno, "actividad", actividades, codigo)
        profesional = referencia(turno, "profesional", profesionales, codigo)
        sala = turno.get("consultorio")
        exigir(type(turno.get("fijada")) is bool, f"{codigo}: fijada debe ser booleano")
        exigir(not turno["fijada"] or codigo in fijadas or codigo in vecinas,
               f"{codigo}: asignación fijada no declarada")
        exigir(profesionales[profesional].get("estado") == "Activo", f"{codigo}: profesional inactivo")
        exigir(actividad in profesionales[profesional]["actividades"],
               f"{codigo}: profesional no elegible para actividad {actividad}")
        if actividades[actividad]["requiereConsultorio"] or sala is not None:
            referencia(turno, "consultorio", consultorios, codigo)
            exigir(consultorios[sala].get("estado") == "Activo", f"{codigo}: consultorio inactivo")
            exigir(actividad in consultorios[sala]["actividades"],
                   f"{codigo}: consultorio no elegible para actividad {actividad}")
        inicio, fin = intervalo(turno, codigo)
        dia = inicio.date()
        exigir(dia in calendario if codigo in mensuales else
               inicio_semanas <= dia <= fin_semanas and dia not in calendario,
               f"{codigo}: fecha fuera del mes o de sus semanas completas vecinas")
        for campo, recurso, restriccion, (desde, hasta), estado in restricciones:
            exigir(not (restriccion.get("estado") == estado and turno.get(recurso) == restriccion[recurso]
                        and inicio < hasta and desde < fin), f"{codigo}: conflicto con {campo}")
        for anterior, (desde, hasta) in intervalos_turnos.items():
            if inicio < hasta and desde < fin:
                exigir(profesional != todos[anterior]["profesional"],
                       f"{codigo}: solapamiento de profesional con {anterior}")
                exigir(sala is None or sala != todos[anterior].get("consultorio"),
                       f"{codigo}: solapamiento de consultorio con {anterior}")
        intervalos_turnos[codigo] = (inicio, fin)
        duracion = minutos(inicio, fin)
        diarios[profesional, dia] += duracion
        semanales[profesional, lunes(dia)] += duracion
        if codigo in mensuales:
            por_profesional[profesional]["asignaciones"] += 1
            por_profesional[profesional]["minutos"] += duracion
    for (profesional, dia), cantidad in diarios.items():
        exigir(cantidad <= limite_diario, f"{profesional} {dia}: límite diario excedido ({cantidad} min)")
    for (profesional, semana), cantidad in semanales.items():
        exigir(cantidad <= limite_semanal,
               f"{profesional} semana {semana}: límite semanal excedido ({cantidad} min)")
    grupos = defaultdict(lambda: {"necesidades": [], "turnos": []})
    for codigo, necesidad in necesidades.items():
        inicio, fin = intervalos_necesidades[codigo]
        grupos[necesidad["actividad"], inicio.date()]["necesidades"].append((inicio, fin, necesidad["cantidad"]))
    for codigo, turno in mensuales.items():
        inicio, fin = intervalos_turnos[codigo]
        grupos[turno["actividad"], inicio.date()]["turnos"].append((inicio, fin, 1))
    for (actividad, dia), grupo in grupos.items():
        extremos = sorted({momento for tramos in grupo.values() for inicio, fin, _ in tramos
                           for momento in (inicio, fin)})
        for inicio, fin in zip(extremos, extremos[1:]):
            requeridos = sum(cantidad for desde, hasta, cantidad in grupo["necesidades"]
                             if desde <= inicio and fin <= hasta)
            asignados = sum(cantidad for desde, hasta, cantidad in grupo["turnos"]
                            if desde <= inicio and fin <= hasta)
            exigir(asignados == requeridos,
                   f"cobertura {actividad} {dia} {inicio:%H:%M}-{fin:%H:%M}: "
                   f"{asignados} posiciones asignadas; {requeridos} requeridas")
    resultados = {
        "diasMes": len(calendario), "diasConDemanda": len(dias_demanda), "diasSinDemanda": len(sin_demanda),
        "necesidades": len(necesidades), "posicionesRequeridas": sum(n["cantidad"] for n in necesidades.values()),
        "asignaciones": len(mensuales),
        "minutosRequeridos": sum(minutos(*intervalos_necesidades[c]) * n["cantidad"] for c, n in necesidades.items()),
        "minutosAsignados": sum(valor["minutos"] for valor in por_profesional.values()),
        "minutosFaltantes": 0, "porProfesional": por_profesional,
        "semanasIntersectadas": [ (inicio_semanas + Diferencia(days=7 * numero)).isoformat()
                                  for numero in range((lunes(fin_mes) - inicio_semanas).days // 7 + 1)],
    }
    esperados = objeto(datos.get("resultadosEsperados"), "resultadosEsperados")
    exigir(resultados == esperados, "resultadosEsperados: los totales calculados difieren del resultado de referencia")
    return resultados


def principal(argumentos=None):
    analizador = argparse.ArgumentParser(description=__doc__)
    analizador.add_argument("ruta", nargs="?", type=Ruta,
                            default=Ruta(__file__).resolve().parents[1] / "db/casos/caso-referencia-mensual.json")
    opciones = analizador.parse_args(argumentos)
    try:
        with opciones.ruta.open(encoding="utf-8-sig") as archivo:
            resultados = verificar_caso(json.load(archivo))
    except (OSError, UnicodeError, ValueError) as error:
        print(f"Caso rechazado: {error}", file=sys.stderr)
        return 1
    print(json.dumps(resultados, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(principal())
