import test from 'node:test';
import assert from 'node:assert/strict';
import {
  actualizarTurno, aprobarSemana, cancelarSemana, crearTurno, eliminarTurno, ErrorProgramacion,
  listarRecursosHorario, listarSemana, listarSemanaActual, obtenerTurno, reabrirSemana,
} from './programacion.js';

const ID = '10000000-0000-0000-0000-000000000001';
const REVISION = 'a'.repeat(64);
const turno = () => ({ id: ID, idProfesional: ID, profesional: 'Profesional QA', idConsultorio: ID,
  consultorio: 'Consultorio QA', idEspecialidad: ID, especialidad: 'Especialidad QA', fecha: '2026-10-05',
  horaInicio: '08:00', horaFin: '14:00', estado: 'Borrador', observacion: '', revision: REVISION, zonaHoraria: 'America/Lima' });
const semana = () => ({ fechaInicio: '2026-10-05', fechaFin: '2026-10-11', zonaHoraria: 'America/Lima',
  idHorario: ID, version: 1, estado: 'Borrador', revision: REVISION, versionAprobada: null, turnos: [turno()] });
const responder = (status, datos) => ({ status, json: async () => datos });

test('la semana actual usa cookie, no-store y campos de respuesta permitidos', async () => {
  let opciones;
  const registros = await listarSemanaActual({ ejecutarSolicitud: async (ruta, configuracion) => {
    assert.equal(ruta, '/api/horarios');
    opciones = configuracion;
    return responder(200, { ...semana(), secreto: 'omitido' });
  } });
  assert.equal(opciones.credentials, 'same-origin');
  assert.equal(opciones.cache, 'no-store');
  assert.ok(opciones.signal instanceof AbortSignal);
  assert.deepEqual(registros, semana());
});

test('POST turno envía solo campos del formulario sin exigir disponibilidad previa', async () => {
  await crearTurno({ ...turno(), idInstitucion: 'ajeno' }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(opciones.method, 'POST');
      const datos = JSON.parse(opciones.body);
      assert.equal(ruta, '/api/horarios');
      assert.equal(datos.idProfesional, ID);
      for (const campo of ['id', 'idInstitucion', 'profesional', 'revision']) assert.equal(Object.hasOwn(datos, campo), false);
      return responder(201, turno());
    },
  });
});

test('PUT y DELETE usan If-Match con comillas y rechazan revisión ausente antes del fetch', async () => {
  const ejecutarSolicitud = async (ruta, opciones) => {
    assert.equal(opciones.headers['If-Match'], `"${REVISION}"`);
    assert.equal(ruta, `/api/horarios/${ID}`);
    return responder(opciones.method === 'DELETE' ? 204 : 200, turno());
  };
  await actualizarTurno(ID, turno(), { revision: REVISION, ejecutarSolicitud });
  await eliminarTurno(ID, { revision: REVISION, ejecutarSolicitud });
  await assert.rejects(actualizarTurno(ID, turno(), { ejecutarSolicitud }), (error) => error.estado === 409);
  assert.throws(() => eliminarTurno(ID, { ejecutarSolicitud }), (error) => error.estado === 409);
});

test('semana recibe un lunes ISO y conserva la aprobación anterior del borrador', async () => {
  const datos = { ...semana(), version: 2, versionAprobada: 1 };
  const actual = await listarSemana('2026-10-05', { ejecutarSolicitud: async (ruta) => {
    assert.equal(ruta, '/api/horarios?fechaInicio=2026-10-05');
    return responder(200, datos);
  } });
  assert.equal(actual.versionAprobada, 1);
  assert.throws(() => listarSemana('2026-10-06'), (error) => error.estado === 400);
});

test('aprobar reabrir cancelar envían semana UUID y revisión sin atributos de respuesta', async () => {
  for (const [operacion, accion] of [[aprobarSemana, 'aprobar'], [reabrirSemana, 'reabrir'], [cancelarSemana, 'cancelar']]) {
    await operacion(semana(), { ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(ruta, `/api/horarios/${accion}`);
      assert.equal(opciones.headers['If-Match'], undefined);
      assert.deepEqual(JSON.parse(opciones.body), { fechaInicio: '2026-10-05', idHorario: ID, revision: REVISION });
      return responder(200, semana());
    } });
  }
});

test('recursos admiten varias competencias y consultorios de uso general', async () => {
  const datos = { zonaHoraria: 'America/Lima', profesionales: [{ id: ID, nombre: 'Profesional QA', estado: 'Activo', especialidades: [{ id: ID, nombre: 'QA' }] }],
    consultorios: [{ id: ID, codigo: 'C-1', nombre: 'Consultorio QA', estado: 'Activo', usoGeneral: true, especialidades: [] }] };
  assert.deepEqual(await listarRecursosHorario({ ejecutarSolicitud: async () => responder(200, datos) }), datos);
});

test('errores409 y401 conservan estado y campos permitidos sin declarar éxito', async () => {
  await assert.rejects(crearTurno(turno(), { ejecutarSolicitud: async () => responder(409, {
    mensaje: 'El profesional ya tiene un turno.', errores: { idProfesional: 'Turno superpuesto', interno: 'no exponer' },
  }) }), (error) => error instanceof ErrorProgramacion && error.estado === 409
    && error.errores.idProfesional === 'Turno superpuesto' && !Object.hasOwn(error.errores, 'interno'));
  await assert.rejects(listarSemanaActual({ ejecutarSolicitud: async () => responder(401, { mensaje: 'Mensaje interno' }) }),
    (error) => error.estado === 401 && error.message.includes('sesión'));
});

test('respuesta malformada o éxito inesperado se rechaza', async () => {
  for (const datos of [{ ...semana(), version: '1' }, { ...semana(), turnos: [turno(), turno()] }, null]) {
    await assert.rejects(listarSemanaActual({ ejecutarSolicitud: async () => responder(200, datos) }), (error) => error.tipo === 'respuesta');
  }
  await assert.rejects(obtenerTurno(ID, { ejecutarSolicitud: async () => responder(200, { ...turno(), estado: 'Inventado' }) }), (error) => error.tipo === 'respuesta');
  await assert.rejects(crearTurno(turno(), { ejecutarSolicitud: async () => responder(200, turno()) }), (error) => error.estado === 200);
  await assert.rejects(listarSemana('2026-10-05', { ejecutarSolicitud: async () => responder(200, { ...semana(), zonaHoraria: 'Zona/Inventada' }) }), (error) => error.tipo === 'respuesta');
});

test('timeout cubre fetch y lectura JSON e invalida señal', async () => {
  let senal;
  await assert.rejects(listarSemanaActual({ tiempoEspera: 10, ejecutarSolicitud: async (ruta, opciones) => {
    senal = opciones.signal;
    return { status: 200, json: () => new Promise(() => {}) };
  } }), (error) => error.tipo === 'tiempo');
  assert.equal(senal.aborted, true);
});

test('cancelación previa evita llamar al backend y cancelación en curso aborta la petición', async () => {
  const cancelado = new AbortController();
  cancelado.abort();
  let llamadas = 0;
  await assert.rejects(listarSemanaActual({ senal: cancelado.signal, ejecutarSolicitud: async () => { llamadas++; } }), (error) => error.tipo === 'cancelado');
  assert.equal(llamadas, 0);
  const controlador = new AbortController();
  const solicitud = listarSemanaActual({ senal: controlador.signal, ejecutarSolicitud: () => new Promise(() => {}) });
  controlador.abort();
  await assert.rejects(solicitud, (error) => error.tipo === 'cancelado');
});
