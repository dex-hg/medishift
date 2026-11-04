import test from 'node:test';
import assert from 'node:assert/strict';
import { gestionarAccesoDoctor } from './doctores.js';

const identificador = '1b6dcff8-9084-438c-a33c-72b95d48777b';
const revision = 'a'.repeat(64);
const acceso = { idCuenta: null, correo: 'doctor@institucion.test', habilitado: false, revision };
const responder = (estado, contenido) => new Response(JSON.stringify(contenido), { status: estado });

test('consulta acceso con cookie y extrae únicamente datos públicos', async () => {
  const resultado = await gestionarAccesoDoctor(identificador, { ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, '/api/profesionales/' + identificador + '/acceso');
    assert.equal(opciones.method, 'GET'); assert.equal(opciones.credentials, 'same-origin'); assert.equal(opciones.cache, 'no-store');
    return responder(200, { ...acceso, contrasena: 'omitida' });
  } });
  assert.deepEqual(resultado, acceso);
});

test('guarda habilitación con revisión y conserva conflictos de permisos y versiones', async () => {
  const datos = { correo: acceso.correo, contrasena: '', habilitado: true };
  for (const estado of [403, 409, 428, 503]) {
    await assert.rejects(gestionarAccesoDoctor(identificador, { datos, revision, ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(opciones.headers['If-Match'], '"' + revision + '"');
      assert.deepEqual(JSON.parse(opciones.body), datos);
      return responder(estado, { mensaje: 'Operación rechazada', errores: { revision: 'Actualiza' } });
    } }), (fallo) => fallo.estado === estado);
  }
});

test('rechaza identificadores y revisiones inválidas antes del transporte', async () => {
  let llamadas = 0;
  const ejecutarSolicitud = async () => { llamadas++; };
  await assert.rejects(gestionarAccesoDoctor('incorrecto', { ejecutarSolicitud }));
  await assert.rejects(gestionarAccesoDoctor(identificador, { datos: {}, revision: 'incorrecta', ejecutarSolicitud }));
  assert.equal(llamadas, 0);
});

test('rechaza respuestas incompletas y comunica caída de red', async () => {
  await assert.rejects(gestionarAccesoDoctor(identificador, { ejecutarSolicitud: async () => responder(200, {}) }), /inválido/);
  await assert.rejects(gestionarAccesoDoctor(identificador, { ejecutarSolicitud: async () => { throw new TypeError('Red'); } }), /conexión/);
});
