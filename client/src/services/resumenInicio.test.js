import afirmar from 'node:assert/strict';
import prueba from 'node:test';
import { obtenerResumenInicio } from './resumenInicio.js';

const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });
const semanaPrueba = () => ({
  fechaInicio: '2026-10-12', fechaFin: '2026-10-18', zonaHoraria: 'Pacific/Kiritimati',
  idHorario: null, version: 0, estado: 'Sin horario', revision: 'a'.repeat(64),
  versionAprobada: null, turnos: [],
});
const rutasPanel = ['/api/profesionales', '/api/consultorios', '/api/horarios'];
const responderPanel = (ruta) => respuesta(200, ruta === '/api/horarios' ? semanaPrueba() : []);

prueba('carga el panel en tres consultas y usa la semana institucional calculada por el servidor', async () => {
  const solicitudes = [];
  const resultado = await obtenerResumenInicio({ ejecutarSolicitud: async (ruta, opciones) => {
    solicitudes.push(ruta);
    afirmar.equal(opciones.method, 'GET');
    afirmar.equal(opciones.credentials, 'same-origin');
    afirmar.equal(opciones.cache, 'no-store');
    afirmar.ok(opciones.signal instanceof AbortSignal);
    return responderPanel(ruta);
  } });

  afirmar.deepEqual(solicitudes, rutasPanel);
  afirmar.deepEqual(resultado, { profesionales: [], consultorios: [], semana: semanaPrueba() });
});

prueba('propaga los errores de cada consulta sin devolver un panel parcialmente exitoso', async () => {
  for (const rutaFallida of rutasPanel) {
    for (const estado of [401, 503]) {
      await afirmar.rejects(obtenerResumenInicio({ ejecutarSolicitud: async (ruta) => {
        return ruta === rutaFallida ? respuesta(estado, null) : responderPanel(ruta);
      } }), (error) => error.estado === estado);
    }
  }
});

prueba('una cancelación previa evita solicitudes al backend', async () => {
  const controlador = new AbortController();
  controlador.abort();
  let solicitudes = 0;
  await afirmar.rejects(obtenerResumenInicio({ senal: controlador.signal, ejecutarSolicitud: async () => {
    solicitudes += 1;
  } }), (error) => error.tipo === 'cancelado');
  afirmar.equal(solicitudes, 0);
});

prueba('cancelar la carga aborta las tres consultas aunque el transporte ignore la señal', async () => {
  const controlador = new AbortController();
  const senales = [];
  const consulta = obtenerResumenInicio({ senal: controlador.signal, ejecutarSolicitud: (ruta, opciones) => {
    senales.push(opciones.signal);
    return new Promise(() => {});
  } });
  afirmar.equal(senales.length, 3);
  controlador.abort();
  await afirmar.rejects(consulta, (error) => error.tipo === 'cancelado');
  afirmar.equal(senales.every((senal) => senal.aborted), true);
});
