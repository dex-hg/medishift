import { duracionTurno } from './validacionesProgramacion.js';

/** Las faltas conservan su turno, pero no aportan horas a los totales. */
export function minutosComputables(turno, feriados) {
  if (!turno || turno.asistencia === 'Falta' || ['Cancelado', 'Sustituido'].includes(turno.estado)
    || (feriados?.estado === 'Disponible' && feriados.dias.some((dia) => dia.fecha === turno.fecha))) return 0;
  const minutos = duracionTurno(turno);
  return Number.isFinite(minutos) && minutos > 0 ? minutos : 0;
}

function normalizarBusqueda(texto) {
  return String(texto ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLocaleLowerCase('es');
}

export function filtrarTurnosAsistencia(turnos, { busqueda = '', fecha = '', idArea = '', idConsultorio = '', estado = '' } = {}) {
  const palabras = normalizarBusqueda(busqueda).split(/\s+/).filter(Boolean);
  return turnos.filter((turno) => (!fecha || turno.fecha === fecha) && (!idArea || turno.idArea === idArea)
    && (!idConsultorio || turno.idConsultorio === idConsultorio) && (!estado || turno.asistencia === estado)
    && palabras.every((palabra) => normalizarBusqueda(turno.profesional).includes(palabra)));
}

export function resumirAsistencia(turnos, feriados) {
  return { cantidad: turnos.length, faltas: turnos.filter((turno) => turno.asistencia === 'Falta').length,
    minutos: turnos.reduce((total, turno) => total + minutosComputables(turno, feriados), 0) };
}
