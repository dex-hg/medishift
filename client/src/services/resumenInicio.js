import { obtenerConsultorios, obtenerProfesionales } from './catalogos.js';
import { listarSemanaActual } from './programacion.js';

export async function obtenerResumenInicio(opciones = {}) {
  const [profesionales, consultorios, semana] = await Promise.all([
    obtenerProfesionales(opciones),
    obtenerConsultorios(opciones),
    listarSemanaActual(opciones),
  ]);
  return { profesionales, consultorios, semana };
}
