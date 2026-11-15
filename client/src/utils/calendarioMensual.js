import { periodoValido } from '../services/planesMensuales.js';
import { fechaValida, UUID } from './validacionesProgramacion.js';
import { consultorioAdmiteEtiqueta, etiquetasDelRecurso } from './seleccionRecursosTurno.js';

export const TIPO_ARRASTRE = 'application/x-medishift';

export function diasDelMes(periodo) {
  if (!periodoValido(periodo)) return [];
  const [anio, mes] = periodo.split('-').map(Number);
  const bisiesto = anio % 4 === 0 && (anio % 100 !== 0 || anio % 400 === 0);
  const cantidad = mes === 2 ? (bisiesto ? 29 : 28) : [4, 6, 9, 11].includes(mes) ? 30 : 31;
  return Array.from({ length: cantidad }, (_, indice) => `${periodo}-${String(indice + 1).padStart(2, '0')}`);
}

export function celdasDelMes(periodo) {
  const dias = diasDelMes(periodo);
  if (!dias.length) return [];
  const inicio = (new Date(`${dias[0]}T12:00:00Z`).getUTCDay() + 6) % 7;
  const celdas = [...Array(inicio).fill(null), ...dias];
  while (celdas.length % 7) celdas.push(null);
  return celdas;
}

export function desplazarMes(periodo, cantidad) {
  if (!periodoValido(periodo) || !Number.isInteger(cantidad)) return '';
  const fecha = new Date(`${periodo}-01T12:00:00Z`);
  fecha.setUTCMonth(fecha.getUTCMonth() + cantidad);
  return fecha.getUTCFullYear() >= 1 && fecha.getUTCFullYear() <= 9999 ? fecha.toISOString().slice(0, 7) : periodo;
}

export function nombreMes(periodo) {
  return periodoValido(periodo) ? new Intl.DateTimeFormat('es-PE', {
    month: 'long', year: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${periodo}-01T12:00:00Z`)) : '';
}

export function leerArrastre(contenido) {
  if (typeof contenido !== 'string' || contenido.length > 150) return null;
  try {
    const datos = JSON.parse(contenido);
    return ['profesional', 'turno'].includes(datos?.tipo) && typeof datos.id === 'string' && UUID.test(datos.id)
      ? { tipo: datos.tipo, id: datos.id } : null;
  } catch { return null; }
}

export function especialidadesCompatibles(profesional, consultorio) {
  return etiquetasDelRecurso(profesional).filter((etiqueta) => consultorioAdmiteEtiqueta(consultorio, etiqueta.id));
}

export function etiquetasDelArea(area) {
  return area?.etiquetas ?? (area?.idEspecialidad ? [{ id: area.idEspecialidad, nombre: area.especialidad }] : []);
}

export function nombreUnidad(area, areas) {
  const padre = area.idAreaPadre && areas.find((registro) => registro.id === area.idAreaPadre);
  return padre ? `${padre.nombre} › ${area.nombre}` : area.nombre;
}

export function profesionalAdmitido(profesional, area) {
  const etiquetas = etiquetasDelArea(area);
  return profesional?.estado === 'Activo' && etiquetasDelRecurso(profesional).some((etiqueta) =>
    etiquetas.some((admitida) => admitida.id === etiqueta.id));
}

export function etiquetasDisponibles(profesional, area, consultorio) {
  if (consultorio) return especialidadesCompatibles(profesional, consultorio);
  const admitidas = etiquetasDelArea(area);
  return etiquetasDelRecurso(profesional).filter((etiqueta) => admitidas.some((opcion) => opcion.id === etiqueta.id));
}

export function elegirEtiqueta(actual, opciones) {
  if (opciones.some((etiqueta) => etiqueta.id === actual)) return actual;
  return opciones.length === 1 ? opciones[0].id : '';
}

export function turnoInicial({ profesional, consultorio, area, fecha, turno }, preferencias) {
  const compatibles = etiquetasDisponibles(profesional, area, consultorio);
  return { idProfesional: profesional?.id || '', idConsultorio: consultorio?.id || '',
    idArea: consultorio?.idArea || area?.id || '', fecha: fechaValida(fecha) ? fecha : '',
    horaInicio: turno?.horaInicio || preferencias?.horaInicio || '08:00', horaFin: turno?.horaFin || preferencias?.horaFin || '12:00',
    idEspecialidad: turno && turno.idConsultorio === consultorio?.id
      ? turno.idEspecialidad : elegirEtiqueta(turno?.idEspecialidad, compatibles), observacion: turno?.observacion || '' };
}
