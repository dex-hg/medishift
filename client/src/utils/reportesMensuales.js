import { diasDelMes, nombreMes } from './calendarioMensual.js';
import { duracionTurno, fechaValida, horaValida, UUID } from './validacionesProgramacion.js';

const ESTADOS_OPERATIVOS = ['Borrador', 'Pendiente', 'Aprobado'];
const ORDEN_TEXTO = new Intl.Collator('es-PE', { numeric: true });

export function textoReporte(valor) {
  return String(valor ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').trim();
}

export function nombreDia(fecha, corto = false) {
  return new Intl.DateTimeFormat('es-PE', { weekday: corto ? 'short' : 'long', timeZone: 'UTC' })
    .format(new Date(`${fecha}T12:00:00Z`));
}

export function horasReporte(minutos) {
  return (minutos / 60).toLocaleString('es-PE', { maximumFractionDigits: 2 });
}

export function duracionReporte(minutos) {
  return `${Math.floor(minutos / 60)} h${minutos % 60 ? ` ${minutos % 60} min` : ''}`;
}

export function resumenReporte(reporte) {
  return `${reporte.turnos.length} ${reporte.turnos.length === 1 ? 'turno' : 'turnos'}. `
    + `${reporte.diasTrabajados} ${reporte.diasTrabajados === 1 ? 'día' : 'días'} con turnos. ${duracionReporte(reporte.minutos)} en total.`;
}

function exigirLista(registros) {
  if (!Array.isArray(registros) || registros.some((registro) => !registro || typeof registro.id !== 'string' || !UUID.test(registro.id))
    || new Set(registros.map((registro) => registro.id)).size !== registros.length) {
    throw new Error('Los datos del calendario están incompletos. Actualiza el mes antes de exportar.');
  }
}

function validarTurnos(datos, periodo) {
  const profesionales = new Set(datos.profesionales.map((registro) => registro.id));
  const consultorios = new Set(datos.consultorios.map((registro) => registro.id));
  const areas = new Set(datos.areas.map((registro) => registro.id));
  for (const turno of datos.turnos) {
    if (!fechaValida(turno.fecha) || !turno.fecha.startsWith(`${periodo}-`)
      || !horaValida(turno.horaInicio) || !horaValida(turno.horaFin) || duracionTurno(turno) <= 0
      || !profesionales.has(turno.idProfesional) || !consultorios.has(turno.idConsultorio)
      || !areas.has(turno.idArea) || !['Borrador', 'Pendiente', 'Aprobado', 'Cancelado', 'Sustituido'].includes(turno.estado)) {
      throw new Error('Hay turnos inconsistentes. Actualiza el calendario antes de exportar.');
    }
  }
}

function matrizIndividual(dias, turnos) {
  const franjas = [...new Set(turnos.map((turno) => `${turno.horaInicio} - ${turno.horaFin}`))].sort();
  return franjas.map((franja) => {
    const minutos = dias.map((dia) => turnos.filter((turno) => turno.fecha === dia
      && `${turno.horaInicio} - ${turno.horaFin}` === franja).reduce((total, turno) => total + turno.minutos, 0));
    return { franja, minutos, total: minutos.reduce((total, cantidad) => total + cantidad, 0) };
  });
}

/** Una captura de los datos consultados; la ficha individual siempre abarca todos los consultorios. */
export function construirReporteMensual(datos, { periodo, alcance, idConsultorio = '', idProfesional = '',
  institucion, emitido = new Date() } = {}) {
  const dias = diasDelMes(periodo);
  if (!dias.length || !['consultorio', 'mes', 'profesional'].includes(alcance) || !textoReporte(institucion)
    || !(emitido instanceof Date) || !Number.isFinite(emitido.getTime())) {
    throw new Error('Selecciona un mes y un tipo de reporte válidos.');
  }
  for (const campo of ['turnos', 'profesionales', 'consultorios', 'areas']) exigirLista(datos?.[campo]);
  try {
    if (typeof datos.zonaHoraria !== 'string' || !datos.zonaHoraria.trim()) throw new Error();
    new Intl.DateTimeFormat('es-PE', { timeZone: datos.zonaHoraria });
  }
  catch { throw new Error('No se pudo identificar la zona horaria del calendario.'); }
  validarTurnos(datos, periodo);
  const profesional = alcance === 'profesional' ? datos.profesionales.find((registro) => registro.id === idProfesional) : null;
  const consultorio = alcance === 'consultorio' ? datos.consultorios.find((registro) => registro.id === idConsultorio) : null;
  if ((alcance === 'profesional' && !profesional) || (alcance === 'consultorio' && !consultorio)) {
    throw new Error(alcance === 'profesional' ? 'Selecciona el profesional que deseas exportar.' : 'Selecciona un consultorio para exportar.');
  }
  const personas = new Map(datos.profesionales.map((registro) => [registro.id, registro]));
  const areas = new Map(datos.areas.map((registro) => [registro.id, registro.nombre]));
  const salas = new Map(datos.consultorios.map((registro) => [registro.id, registro]));
  const turnos = datos.turnos.filter((turno) => ESTADOS_OPERATIVOS.includes(turno.estado)
    && (!profesional || turno.idProfesional === profesional.id) && (!consultorio || turno.idConsultorio === consultorio.id))
    .map((turno) => {
      const persona = personas.get(turno.idProfesional);
      const sala = salas.get(turno.idConsultorio);
      return { ...turno, profesional: textoReporte(persona.nombre), colegiatura: textoReporte(persona.colegiatura),
        area: textoReporte(areas.get(turno.idArea)), consultorio: textoReporte(`${sala.codigo} · ${sala.nombre}`),
        especialidad: textoReporte(turno.especialidad), observacion: textoReporte(turno.observacion), minutos: duracionTurno(turno) };
    }).sort((primero, segundo) => ORDEN_TEXTO.compare(primero.fecha, segundo.fecha)
      || ORDEN_TEXTO.compare(primero.horaInicio, segundo.horaInicio) || ORDEN_TEXTO.compare(primero.consultorio, segundo.consultorio)
      || ORDEN_TEXTO.compare(primero.profesional, segundo.profesional) || primero.id.localeCompare(segundo.id));
  const minutos = turnos.reduce((total, turno) => total + turno.minutos, 0);
  const titulo = profesional ? 'Horario individual' : consultorio ? 'Horario del consultorio' : 'Horario mensual general';
  const subtitulo = profesional ? textoReporte(profesional.nombre) : consultorio
    ? textoReporte(`${consultorio.codigo} · ${consultorio.nombre}`) : 'Todas las áreas y consultorios';
  return { periodo, alcance, dias, titulo, subtitulo, mes: nombreMes(periodo), institucion: textoReporte(institucion),
    zonaHoraria: datos.zonaHoraria, emitido: new Date(emitido), profesional: profesional
      ? { id: profesional.id, nombre: textoReporte(profesional.nombre), colegiatura: textoReporte(profesional.colegiatura) } : null,
    turnos, matriz: profesional ? matrizIndividual(dias, turnos) : [], minutos,
    diasTrabajados: new Set(turnos.map((turno) => turno.fecha)).size,
    estados: [...new Set(turnos.map((turno) => turno.estado))].sort(),
    totalesDiarios: dias.map((dia) => turnos.filter((turno) => turno.fecha === dia).reduce((total, turno) => total + turno.minutos, 0)) };
}

export function nombreArchivoReporte(reporte, formato) {
  if (!['pdf', 'xlsx'].includes(formato)) throw new Error('Selecciona PDF o Excel.');
  const identificador = reporte.profesional ? `${reporte.profesional.nombre}-${reporte.profesional.colegiatura || 'sin-colegiatura'}`
    : reporte.alcance === 'consultorio' ? reporte.subtitulo : 'general';
  const seguro = identificador.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase().slice(0, 100);
  return `horario-${reporte.periodo}-${seguro || 'mensual'}.${formato}`;
}
