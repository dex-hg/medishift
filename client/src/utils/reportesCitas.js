import { nombreMes } from './calendarioMensual.js';
import { etiquetasEstadoCita, partesLocales } from './validacionesAtencion.js';

const SIN_AREA = 'sin-area';

export const filtrosReporteCitas = [
  { clave: 'area', etiqueta: 'Área', general: 'Todas las áreas', id: (cita) => cita.idArea || SIN_AREA },
  { clave: 'consultorio', etiqueta: 'Consultorio', general: 'Todos los consultorios', id: (cita) => cita.idConsultorio },
  { clave: 'profesional', etiqueta: 'Médico', general: 'Todos los médicos', id: (cita) => cita.idProfesional },
];

export const columnasReporteCitas = [
  { etiqueta: 'Fecha', ancho: 15 }, { etiqueta: 'Inicio', ancho: 10 }, { etiqueta: 'Fin', ancho: 22 },
  { etiqueta: 'Paciente', ancho: 30 }, { etiqueta: 'Área', ancho: 25 }, { etiqueta: 'Consultorio', ancho: 29 },
  { etiqueta: 'Médico', ancho: 30 }, { etiqueta: 'Estado', ancho: 15 },
];

export function prepararCitasDelMes({ citas, periodo, zonaHoraria }) {
  return citas.map((cita) => {
    const inicio = partesLocales(cita.inicio, zonaHoraria);
    const fin = partesLocales(cita.fin, zonaHoraria);
    return { ...cita, area: cita.area || 'Sin área', fecha: inicio.fecha, horaInicio: inicio.hora,
      fechaFin: fin.fecha, horaFin: fin.hora };
  }).filter((cita) => cita.fecha.startsWith(periodo + '-'))
    .sort((primera, segunda) => Date.parse(primera.inicio) - Date.parse(segunda.inicio));
}

export function opcionesReporteCitas(citas) {
  return Object.fromEntries(filtrosReporteCitas.map(({ clave, id }) => [clave,
    [...new Map(citas.map((cita) => [id(cita), cita[clave]]))]
      .sort((primera, segunda) => primera[1].localeCompare(segunda[1], 'es-PE')),
  ]));
}

export function construirReporteCitas(datos, citas, opciones, filtros, institucion) {
  const seleccion = filtrosReporteCitas.flatMap((campo) => {
    const opcion = opciones[campo.clave].find(([id]) => id === filtros[campo.clave]);
    return opcion ? [{ ...campo, nombre: opcion[1] }] : [];
  });
  const subtitulo = seleccion.map(({ etiqueta, nombre }) => etiqueta + ' · ' + nombre).join('  /  ');
  return {
    institucion,
    periodo: datos.periodo,
    mes: nombreMes(datos.periodo),
    zonaHoraria: datos.zonaHoraria,
    subtitulo: subtitulo || 'Todas las citas del mes',
    seleccion: seleccion.map(({ clave, nombre }) => ({ clave, nombre })),
    citas: citas.filter((cita) => seleccion.every(({ clave, id }) => id(cita) === filtros[clave])),
  };
}

export function nombreArchivoCitas(reporte, formato) {
  if (!['pdf', 'xlsx'].includes(formato)) throw new Error('Selecciona PDF o Excel.');
  const sufijos = reporte.seleccion.map(({ clave, nombre }) => {
    const seguro = nombre.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
      .replace(/[^a-z0-9]+/g, '-').slice(0, 35).replace(/^-|-$/g, '');
    return seguro ? clave + '-' + seguro : clave;
  });
  return ['citas', reporte.periodo, ...sufijos].join('-') + '.' + formato;
}

export function datosReporteCita(cita) {
  return [cita.paciente, cita.area, cita.consultorio, cita.profesional, etiquetasEstadoCita[cita.estado]];
}
