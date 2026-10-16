import test from 'node:test';
import assert from 'node:assert/strict';
import {
  crearPlan, crearUnidad, ErrorPlanMensual, listarPlanes, listarUnidades, obtenerPlan, periodoValido,
} from './planesMensuales.js';

const ID_UNIDAD = '10000000-0000-0000-0000-000000000001';
const ID_PLAN = '10000000-0000-0000-0000-000000000002';
const ID_ACTOR = '10000000-0000-0000-0000-000000000003';
const REVISION = 'a'.repeat(64);
const unidad = () => ({ id: ID_UNIDAD, codigo: 'CAR', nombre: 'Cardiología', tipo: 'Servicio', estado: 'Activo' });
const plan = () => ({ id: ID_PLAN, idUnidad: ID_UNIDAD, periodo: '2026-10', fechaInicio: '2026-10-01',
  fechaFin: '2026-10-31', zonaHoraria: 'America/Lima', version: 1, estado: 'Borrador',
  idCreador: ID_ACTOR, creadoEn: '2026-10-16T18:00:00Z', idAprobador: null, aprobadoEn: null, revision: REVISION });
const entrada = () => ({ idUnidad: ID_UNIDAD, periodo: '2026-10' });
const responder = (status, datos) => ({ status, json: async () => datos });
const devolver = (status, datos) => ({ ejecutarSolicitud: async () => responder(status, datos) });

test('unidades obtiene zona institucional y lista blanca con cookie y sin caché', async () => {
  const datos = await listarUnidades({ ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, '/api/unidades-planificacion');
    assert.equal(opciones.credentials, 'same-origin');
    assert.equal(opciones.cache, 'no-store');
    assert.equal(opciones.method, 'GET');
    assert.ok(opciones.signal instanceof AbortSignal);
    return responder(200, { zonaHoraria: 'America/Lima', unidades: [{ ...unidad(), secreto: 'omitido' }], interno: true });
  } });
  assert.deepEqual(datos, { zonaHoraria: 'America/Lima', unidades: [unidad()] });
});

test('unidades inactivas permanecen consultables', async () => {
  const inactiva = { ...unidad(), estado: 'Inactivo' };
  assert.deepEqual(await listarUnidades(devolver(200, { zonaHoraria: 'America/Lima', unidades: [inactiva] })),
    { zonaHoraria: 'America/Lima', unidades: [inactiva] });
});

test('alta unidad recorta texto y envía solo código nombre tipo', async () => {
  const resultado = await crearUnidad({ ...unidad(), codigo: ' CAR ', nombre: ' Cardiología ', idInstitucion: ID_ACTOR }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(ruta, '/api/unidades-planificacion');
      assert.equal(opciones.method, 'POST');
      assert.deepEqual(JSON.parse(opciones.body), { codigo: 'CAR', nombre: 'Cardiología', tipo: 'Servicio' });
      return responder(201, unidad());
    },
  });
  assert.deepEqual(resultado, unidad());
});

test('alta unidad rechaza vacíos tipos y límites antes de solicitar', () => {
  let solicitudes = 0;
  for (const datos of [{ ...unidad(), codigo: ' ' }, { ...unidad(), codigo: 'a'.repeat(33) },
    { ...unidad(), nombre: 'a'.repeat(141) }, { ...unidad(), nombre: 'Texto\ninválido' },
    { ...unidad(), tipo: 'hospital' }, null]) {
    assert.throws(() => crearUnidad(datos, { ejecutarSolicitud: () => { solicitudes += 1; } }),
      (error) => error.tipo === 'validacion' && error.estado === 400);
  }
  assert.equal(solicitudes, 0);
});

test('consulta mensual verifica alcance ordena versiones y omite campos extras', async () => {
  const segunda = { ...plan(), id: ID_ACTOR, version: 2, secreto: 'omitido' };
  const resultado = await listarPlanes(entrada(), { ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, `/api/planes-mensuales?unidad=${ID_UNIDAD}&periodo=2026-10`);
    assert.equal(opciones.method, 'GET');
    return responder(200, { planes: [plan(), segunda] });
  } });
  assert.equal(resultado[0].version, 2);
  assert.equal(Object.hasOwn(resultado[0], 'secreto'), false);
});

test('alta mensual excluye actor institución estado revisión y versión de la entrada', async () => {
  await crearPlan({ ...plan(), idInstitucion: ID_ACTOR }, { ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, '/api/planes-mensuales');
    assert.equal(opciones.method, 'POST');
    assert.deepEqual(JSON.parse(opciones.body), entrada());
    return responder(201, plan());
  } });
});

test('periodos e identificadores inválidos se rechazan antes de solicitar', () => {
  for (const periodo of ['0000-01', '2026-00', '2026-13', '2026-1', '2026-10-01', '', null]) {
    assert.equal(periodoValido(periodo), false);
    assert.throws(() => listarPlanes({ idUnidad: ID_UNIDAD, periodo }), (error) => Boolean(error.errores.periodo));
  }
  assert.equal(periodoValido('0001-01'), true);
  assert.equal(periodoValido('9999-12'), true);
  assert.throws(() => crearPlan({ ...entrada(), idUnidad: 'ajena' }), (error) => Boolean(error.errores.idUnidad));
  assert.throws(() => obtenerPlan('inválido'), (error) => error.estado === 404);
});

test('cabeceras comprueban mes completo bisiesto y metadatos', async () => {
  const febrero = { ...plan(), periodo: '2028-02', fechaInicio: '2028-02-01', fechaFin: '2028-02-29' };
  assert.deepEqual(await obtenerPlan(ID_PLAN, devolver(200, febrero)), febrero);
  for (const datos of [{ ...febrero, fechaFin: '2028-02-28' }, { ...plan(), fechaInicio: '2026-10-02' },
    { ...plan(), zonaHoraria: 'Zona/Inventada' }, { ...plan(), revision: '' },
    { ...plan(), creadoEn: '2026-10-16' }, { ...plan(), aprobadoEn: '2026-10-16T18:00:00Z' },
    { ...plan(), estado: 'Inventado' }, { ...plan(), version: 0 }]) {
    await assert.rejects(obtenerPlan(ID_PLAN, devolver(200, datos)), (error) => error.tipo === 'respuesta');
  }
});

test('rechaza unidades o planes duplicados y respuestas fuera del alcance solicitado', async () => {
  for (const unidades of [[unidad(), unidad()], [{ ...unidad(), tipo: 'service' }], null]) {
    await assert.rejects(listarUnidades(devolver(200, { zonaHoraria: 'America/Lima', unidades })),
      (error) => error.tipo === 'respuesta');
  }
  for (const planes of [[plan(), plan()], [{ ...plan(), idUnidad: ID_ACTOR }],
    [plan(), { ...plan(), id: ID_ACTOR }]]) {
    await assert.rejects(listarPlanes(entrada(), devolver(200, { planes })), (error) => error.tipo === 'respuesta');
  }
  await assert.rejects(crearPlan(entrada(), devolver(201, { ...plan(), estado: 'Aprobado' })),
    (error) => error.tipo === 'respuesta');
  await assert.rejects(crearUnidad(unidad(), devolver(201, { ...unidad(), estado: 'Inactivo' })),
    (error) => error.tipo === 'respuesta');
  await assert.rejects(obtenerPlan(ID_UNIDAD, devolver(200, plan())), (error) => error.tipo === 'respuesta');
});

test('403 informa acceso restringido y 401 obliga a renovar sesión sin filtrar mensaje interno', async () => {
  for (const estado of [401, 403]) {
    await assert.rejects(listarUnidades(devolver(estado, { mensaje: 'Detalle interno' })),
      (error) => error instanceof ErrorPlanMensual && error.estado === estado
        && !error.message.includes('Detalle interno') && error.message.includes(estado === 403 ? 'administrador' : 'sesión'));
  }
});

test('409 conserva errores permitidos y un POST inesperado nunca declara éxito', async () => {
  await assert.rejects(crearPlan(entrada(), devolver(409, {
    mensaje: 'Existe un borrador.', errores: { periodo: 'Mes ocupado', interno: 'omitido' },
  })), (error) => error.estado === 409 && error.errores.periodo === 'Mes ocupado'
    && !Object.hasOwn(error.errores, 'interno'));
  await assert.rejects(crearPlan(entrada(), devolver(200, plan())), (error) => error.estado === 200);
  await assert.rejects(listarUnidades(devolver(503, null)), (error) => error.estado === 503);
});

test('timeout incluye lectura JSON y aborta la solicitud', async () => {
  let senal;
  await assert.rejects(listarUnidades({ tiempoEspera: 10, ejecutarSolicitud: async (ruta, opciones) => {
    senal = opciones.signal;
    return { status: 200, json: () => new Promise(() => {}) };
  } }), (error) => error.tipo === 'tiempo');
  assert.equal(senal.aborted, true);
});

test('cancelación previa impide solicitud y cancelación en curso descarta respuesta', async () => {
  const previo = new AbortController();
  previo.abort();
  let solicitudes = 0;
  await assert.rejects(listarUnidades({ senal: previo.signal, ejecutarSolicitud: async () => { solicitudes += 1; } }),
    (error) => error.tipo === 'cancelado');
  assert.equal(solicitudes, 0);
  const controlador = new AbortController();
  const pendiente = listarUnidades({ senal: controlador.signal, ejecutarSolicitud: () => new Promise(() => {}) });
  controlador.abort();
  await assert.rejects(pendiente, (error) => error.tipo === 'cancelado');
});

test('errores de red y configuración se comunican sin declarar éxito', async () => {
  await assert.rejects(listarUnidades({ ejecutarSolicitud: async () => { throw new TypeError('red'); } }),
    (error) => error.tipo === 'red');
  await assert.rejects(listarUnidades({ tiempoEspera: 0 }), (error) => error.tipo === 'configuracion');
});
