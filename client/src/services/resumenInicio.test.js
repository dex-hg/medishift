import afirmar from 'node:assert/strict';
import prueba from 'node:test';
import { obtenerResumenInicio } from './resumenInicio.js';

const REVISION = 'a'.repeat(64);
const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });
const recursosPrueba = (zonaHoraria = 'America/Lima') => ({ zonaHoraria, profesionales: [], consultorios: [] });
const semanaPrueba = (zonaHoraria = 'America/Lima', fechaInicio = '2026-10-05', fechaFin = '2026-10-11') => ({
  fechaInicio, fechaFin, zonaHoraria, idHorario: null, version: 0, estado: 'Sin horario',
  revision: REVISION, versionAprobada: null, turnos: [],
});

function responderPanel(ruta) {
  if (ruta === '/api/horarios/recursos') return respuesta(200, recursosPrueba());
  if (ruta.startsWith('/api/horarios?')) return respuesta(200, semanaPrueba());
  return respuesta(200, []);
}

prueba('integra los servicios existentes y consulta la semana tras cargar los cuatro recursos', async () => {
  const solicitudes = [];
  const resultado = await obtenerResumenInicio({ ejecutarSolicitud: async (ruta, opciones) => {
    solicitudes.push(ruta);
    afirmar.equal(opciones.method, 'GET');
    afirmar.equal(opciones.credentials, 'same-origin');
    afirmar.equal(opciones.cache, 'no-store');
    afirmar.ok(opciones.signal instanceof AbortSignal);
    return responderPanel(ruta);
  } }, { obtenerFecha: () => '2026-10-10' });

  afirmar.deepEqual(solicitudes, ['/api/profesionales', '/api/consultorios', '/api/disponibilidades',
    '/api/horarios/recursos', '/api/horarios?fechaInicio=2026-10-05']);
  afirmar.deepEqual(resultado, { profesionales: [], consultorios: [], disponibilidades: [], semana: semanaPrueba() });
});

prueba('calcula el lunes con la fecha de la zona institucional al cruzar el cambio de semana', async () => {
  const zonaHoraria = 'Pacific/Kiritimati';
  const solicitudes = [];
  const resultado = await obtenerResumenInicio({ ejecutarSolicitud: async (ruta) => {
    solicitudes.push(ruta);
    if (ruta === '/api/horarios/recursos') return respuesta(200, recursosPrueba(zonaHoraria));
    if (ruta.startsWith('/api/horarios?')) return respuesta(200, semanaPrueba(zonaHoraria, '2026-10-12', '2026-10-18'));
    return respuesta(200, []);
  } }, { obtenerFecha: (zona) => {
    afirmar.equal(zona, zonaHoraria);
    return zona === 'Pacific/Kiritimati' ? '2026-10-12' : '2026-10-11';
  } });

  afirmar.equal(solicitudes.at(-1), '/api/horarios?fechaInicio=2026-10-12');
  afirmar.equal(resultado.semana.zonaHoraria, zonaHoraria);
});

prueba('propaga HTTP 401 de cada carga inicial y evita consultar una semana con datos incompletos', async () => {
  for (const rutaFallida of ['/api/profesionales', '/api/consultorios', '/api/disponibilidades', '/api/horarios/recursos']) {
    const solicitudes = [];
    await afirmar.rejects(obtenerResumenInicio({ ejecutarSolicitud: async (ruta) => {
      solicitudes.push(ruta);
      return ruta === rutaFallida ? respuesta(401, null) : responderPanel(ruta);
    } }, { obtenerFecha: () => '2026-10-10' }), (error) => error.estado === 401);
    afirmar.equal(solicitudes.some((ruta) => ruta.startsWith('/api/horarios?')), false);
  }
});

prueba('propaga el fallo de la semana sin devolver un resumen parcialmente exitoso', async () => {
  await afirmar.rejects(obtenerResumenInicio({ ejecutarSolicitud: async (ruta) => {
    return ruta.startsWith('/api/horarios?') ? respuesta(503, { mensaje: 'Servicio no disponible.' }) : responderPanel(ruta);
  } }, { obtenerFecha: () => '2026-10-10' }), (error) => error.estado === 503 && error.message === 'Servicio no disponible.');
});

prueba('una cancelación previa no envía solicitudes al backend', async () => {
  const controlador = new AbortController();
  controlador.abort();
  let solicitudes = 0;
  await afirmar.rejects(obtenerResumenInicio({ senal: controlador.signal, ejecutarSolicitud: async () => {
    solicitudes += 1;
  } }), (error) => error.tipo === 'cancelado');
  afirmar.equal(solicitudes, 0);
});

prueba('cancelar la carga inicial aborta sus cuatro solicitudes aunque el transporte ignore la señal', async () => {
  const controlador = new AbortController();
  const senales = [];
  const consulta = obtenerResumenInicio({ senal: controlador.signal, ejecutarSolicitud: (ruta, opciones) => {
    senales.push(opciones.signal);
    return new Promise(() => {});
  } });
  afirmar.equal(senales.length, 4);
  controlador.abort();
  await afirmar.rejects(consulta, (error) => error.tipo === 'cancelado');
  afirmar.equal(senales.every((senal) => senal.aborted), true);
});

prueba('cancelar mientras se consulta la semana impide confirmar el resumen', async () => {
  const controlador = new AbortController();
  let senalSemana;
  let avisarSemana;
  const semanaIniciada = new Promise((resolver) => { avisarSemana = resolver; });
  const consulta = obtenerResumenInicio({ senal: controlador.signal, ejecutarSolicitud: async (ruta, opciones) => {
    if (!ruta.startsWith('/api/horarios?')) return responderPanel(ruta);
    senalSemana = opciones.signal;
    avisarSemana();
    return new Promise(() => {});
  } }, { obtenerFecha: () => '2026-10-10' });

  await semanaIniciada;
  controlador.abort();
  await afirmar.rejects(consulta, (error) => error.tipo === 'cancelado');
  afirmar.equal(senalSemana.aborted, true);
});

prueba('rechaza un proveedor de fecha inválido antes de solicitar datos', async () => {
  let solicitudes = 0;
  await afirmar.rejects(obtenerResumenInicio({ ejecutarSolicitud: async () => { solicitudes += 1; } },
    { obtenerFecha: null }), (error) => error.tipo === 'configuracion');
  afirmar.equal(solicitudes, 0);
});
