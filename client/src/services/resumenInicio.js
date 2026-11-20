import { obtenerConsultorios, obtenerProfesionales } from './catalogos.js';

export async function obtenerResumenInicio(opciones = {}) {
  const [profesionales, consultorios] = await Promise.all([
    obtenerProfesionales(opciones),
    obtenerConsultorios(opciones),
  ]);
  return { profesionales, consultorios };
}
