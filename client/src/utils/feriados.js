import { fechaValida } from './validacionesProgramacion.js';
import { minutosComputables } from './asistencia.js';

const ESTADOS_VIGENTES = ['Borrador', 'Pendiente', 'Aprobado'];

export function validarFeriados(datos, periodo) {
  return datos && ['Disponible', 'Pendiente'].includes(datos.estado)
    && (datos.codigoPais === null || /^(PE|CO|CL|BO|MX|AR)$/.test(datos.codigoPais))
    && typeof datos.pais === 'string' && typeof datos.fuente === 'string' && Boolean(datos.fuente.trim())
    && typeof datos.mensaje === 'string' && typeof datos.requiereActualizacion === 'boolean'
    && Number.isInteger(datos.minutosPorFeriado) && datos.minutosPorFeriado > 0 && datos.minutosPorFeriado <= 1440
    && Array.isArray(datos.dias) && new Set(datos.dias.map((dia) => dia?.fecha)).size === datos.dias.length
    && datos.dias.every((dia) => fechaValida(dia?.fecha) && dia.fecha.startsWith(`${periodo}-`)
      && typeof dia.nombre === 'string' && Boolean(dia.nombre.trim()) && dia.nombre.length <= 1000)
    && (datos.estado === 'Disponible' ? Boolean(datos.codigoPais) && Boolean(datos.pais.trim())
      && typeof datos.actualizado === 'string' && Number.isFinite(Date.parse(datos.actualizado))
      : datos.actualizado === null && datos.dias.length === 0);
}

export function buscarFeriado(feriados, fecha) {
  return feriados?.estado === 'Disponible' ? feriados.dias.find((dia) => dia.fecha === fecha) : undefined;
}

/** El abono pertenece al profesional y a la fecha; nunca a un consultorio. */
export function crearAbonosFeriados(turnos, feriados) {
  if (feriados?.estado !== 'Disponible') return [];
  const profesionales = new Map(turnos.filter((turno) => ESTADOS_VIGENTES.includes(turno.estado))
    .map((turno) => [turno.idProfesional, turno.profesional]));
  return feriados.dias.flatMap((feriado) => [...profesionales].map(([idProfesional, profesional]) => ({
    id: `${idProfesional}-${feriado.fecha}`, idProfesional, profesional, fecha: feriado.fecha,
    nombre: feriado.nombre, minutos: feriados.minutosPorFeriado,
  })));
}

export function horasProfesional(idProfesional, turnos, feriados) {
  const propios = turnos.filter((turno) => turno.idProfesional === idProfesional && ESTADOS_VIGENTES.includes(turno.estado));
  const minutosTurnos = propios.reduce((total, turno) => total + minutosComputables(turno, feriados), 0);
  const minutosFeriados = crearAbonosFeriados(propios, feriados).reduce((total, abono) => total + abono.minutos, 0);
  return { minutos: minutosTurnos + minutosFeriados, minutosTurnos, minutosFeriados, completo: feriados?.estado === 'Disponible' };
}
