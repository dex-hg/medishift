import test from 'node:test';
import assert from 'node:assert/strict';
import { cargarPlanificador, crearConsultorioEnArea, eliminarTurnoMensual, guardarTurnoMensual, vincularConsultorio } from './planificador.js';

const ID = '10000000-0000-0000-0000-000000000001';
const OTRO = '10000000-0000-0000-0000-000000000002';
const REVISION = 'b'.repeat(64);
const turno = () => ({ id: ID, idPlan: OTRO, idArea: ID, idProfesional: ID, profesional: 'Profesional de prueba',
  idConsultorio: OTRO, consultorio: 'Consultorio 1', idEspecialidad: ID, especialidad: 'Dermatología',
  fecha: '2026-10-26', horaInicio: '08:00', horaFin: '12:00', estado: 'Borrador', observacion: '', revision: REVISION });
const responder = (status, contenido) => ({ status, json: async () => contenido });
const calendario = () => ({ zonaHoraria: 'America/Lima', areas: [{ id: ID, codigo: 'DER', nombre: 'Dermatología', estado: 'Activo' }],
  consultorios: [{ id: OTRO, nombre: 'Consultorio 1', codigo: 'DER-01', idArea: ID, estado: 'Activo', usoGeneral: true, especialidades: [] }],
  profesionales: [{ id: ID, nombre: 'Profesional de prueba', colegiatura: '001234', estado: 'Activo', especialidades: [{ id: ID, nombre: 'Dermatología' }] }],
  planes: [{ id: OTRO, idUnidad: ID, periodo: '2026-10', estado: 'Borrador', version: 1 }], turnos: [turno()] });

test('carga mensual conserva relaciones de carpetas y usa sesión sin caché', async () => {
  const resultado = await cargarPlanificador('2026-10', { ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, '/api/planificador?periodo=2026-10');
    assert.equal(opciones.credentials, 'same-origin');
    assert.equal(opciones.cache, 'no-store');
    assert.ok(opciones.signal instanceof AbortSignal);
    return responder(200, calendario());
  } });
  assert.equal(resultado.consultorios[0].idArea, resultado.areas[0].id);
  assert.equal(resultado.turnos[0].idPlan, OTRO);
  assert.equal(resultado.profesionales[0].colegiatura, '001234');
});

test('rechaza respuestas con turnos fuera del mes o estructura inválida', async () => {
  for (const contenido of [{ ...calendario(), turnos: [{ ...turno(), fecha: '2026-11-01' }] }, { ...calendario(), consultorios: [{}] },
    { ...calendario(), profesionales: [{ ...calendario().profesionales[0], colegiatura: undefined }] }]) {
    await assert.rejects(cargarPlanificador('2026-10', { ejecutarSolicitud: async () => responder(200, contenido) }), /inválido/);
  }
});

test('crear turno envía solo datos necesarios para asignación mensual', async () => {
  const resultado = await guardarTurnoMensual({ ...turno(), idInstitucion: OTRO }, { ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, '/api/planificador/turnos');
    assert.equal(opciones.method, 'POST');
    const datos = JSON.parse(opciones.body);
    assert.equal(datos.idArea, ID);
    assert.equal(datos.idInstitucion, undefined);
    assert.equal(datos.revision, undefined);
    return responder(201, turno());
  } });
  assert.equal(resultado.id, ID);
});

test('edición y eliminación requieren revisión y envían If-Match entre comillas', async () => {
  await guardarTurnoMensual(turno(), { id: ID, revision: REVISION, ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, `/api/planificador/turnos/${ID}`);
    assert.equal(opciones.method, 'PUT');
    assert.equal(opciones.headers['If-Match'], `"${REVISION}"`);
    return responder(200, turno());
  } });
  await eliminarTurnoMensual(ID, { revision: REVISION, ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(opciones.method, 'DELETE');
    assert.equal(opciones.headers['If-Match'], `"${REVISION}"`);
    return responder(204);
  } });
  await assert.rejects(guardarTurnoMensual(turno(), { id: ID }), /Recarga/);
  assert.throws(() => eliminarTurnoMensual(ID, {}), /Recarga/);
});

test('errores de conflicto conservan mensaje y validaciones del servidor', async () => {
  await assert.rejects(guardarTurnoMensual(turno(), { ejecutarSolicitud: async () => responder(409, {
    mensaje: 'El profesional ya tiene un turno.', errores: { horaInicio: 'Hay un cruce.', secreto: 'ignorar' },
  }) }), (error) => error.estado === 409 && error.message.includes('ya tiene')
    && error.errores.horaInicio === 'Hay un cruce.' && !error.errores.secreto);
});

test('crear y vincular consultorio envían el área a las rutas atómicas', async () => {
  await crearConsultorioEnArea({ idArea: ID, codigo: 'DER-01', nombre: 'Consultorio 1', ubicacion: 'Piso 1' }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(ruta, '/api/planificador/consultorios');
      assert.equal(JSON.parse(opciones.body).idArea, ID);
      return responder(201, {});
    },
  });
  await vincularConsultorio(OTRO, ID, { ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, `/api/planificador/consultorios/${OTRO}/area`);
    assert.equal(opciones.method, 'PUT');
    assert.deepEqual(JSON.parse(opciones.body), { idArea: ID });
    return responder(200, {});
  } });
});

test('timeout y cancelación dejan un error recuperable', async () => {
  await assert.rejects(cargarPlanificador('2026-10', { ejecutarSolicitud: () => new Promise(() => {}), tiempoEspera: 5 }), /tardó demasiado/);
  const controlador = new AbortController();
  controlador.abort();
  await assert.rejects(cargarPlanificador('2026-10', { senal: controlador.signal }), /cancelada/);
});
