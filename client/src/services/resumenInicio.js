import { obtenerConsultorios, obtenerProfesionales } from './catalogos.js';
import { ErrorProgramacion, listarDisponibilidades, listarRecursosHorario, listarSemana } from './programacion.js';
import { fechaActual, lunesDeSemana } from '../utils/validacionesProgramacion.js';

export async function obtenerResumenInicio(opciones = {}, { obtenerFecha = fechaActual } = {}) {
  if (typeof obtenerFecha !== 'function') {
    throw new ErrorProgramacion('No se pudo iniciar la consulta del panel.', { tipo: 'configuracion' });
  }

  const [profesionales, consultorios, disponibilidades, recursos] = await Promise.all([
    obtenerProfesionales(opciones),
    obtenerConsultorios(opciones),
    listarDisponibilidades(opciones),
    listarRecursosHorario(opciones),
  ]);
  const fechaInicio = lunesDeSemana(obtenerFecha(recursos.zonaHoraria));
  const semana = await listarSemana(fechaInicio, opciones);

  return { profesionales, consultorios, disponibilidades, semana };
}
