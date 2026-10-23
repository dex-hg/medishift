import test from 'node:test';
import assert from 'node:assert/strict';
import { crearEspecialidad, obtenerEspecialidades } from './catalogos.js';

const ID = '10000000-0000-4000-8000-000000000001';
const respuesta = (status, contenido) => ({ status, json: async () => contenido });

test('crear especialidad normaliza el nombre y envía solo el dato autorizado con sesión y sin caché', async () => {
  const resultado = await crearEspecialidad({ nombre: '  Medicina\u00a0  familiar  ', idInstitucion: 'ajena', id: 'inventado' }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(ruta, '/api/especialidades');
      assert.equal(opciones.method, 'POST');
      assert.equal(opciones.credentials, 'same-origin');
      assert.equal(opciones.cache, 'no-store');
      assert.deepEqual(JSON.parse(opciones.body), { nombre: 'Medicina familiar' });
      return respuesta(201, { id: ID, nombre: 'Medicina familiar', campoPrivado: 'ignorar' });
    },
  });
  assert.deepEqual(resultado, { id: ID, nombre: 'Medicina familiar' });
});

test('los nombres inválidos se rechazan antes de contactar al servidor', async () => {
  for (const nombre of ['', ' '.repeat(3), null, 12, 'a'.repeat(101), 'Nombre\ninválido', '\ud800']) {
    await assert.rejects(crearEspecialidad({ nombre }, { ejecutarSolicitud: () => assert.fail('No debe solicitar') }),
      (fallo) => fallo.tipo === 'validacion' && Boolean(fallo.errores.nombre));
  }
});

test('duplicados y permisos se comunican sin anunciar una creación', async () => {
  await assert.rejects(crearEspecialidad({ nombre: 'Dermatología' }, { ejecutarSolicitud: async () => respuesta(409, {
    mensaje: 'Ya existe esa especialidad.', errores: { nombre: 'Selecciona la existente.', idInstitucion: 'ignorar' },
  }) }), (fallo) => fallo.estado === 409 && fallo.errores.nombre === 'Selecciona la existente.' && !fallo.errores.idInstitucion);
  await assert.rejects(crearEspecialidad({ nombre: 'Dermatología' }, { ejecutarSolicitud: async () => respuesta(401, {}) }),
    (fallo) => fallo.estado === 401);
  await assert.rejects(crearEspecialidad({ nombre: 'Dermatología' }, { ejecutarSolicitud: async () => respuesta(200, { id: ID, nombre: 'Dermatología' }) }));
});

test('el listado acepta un catálogo vacío y rechaza datos incompletos', async () => {
  assert.deepEqual(await obtenerEspecialidades({ ejecutarSolicitud: async () => respuesta(200, []) }), []);
  await assert.rejects(obtenerEspecialidades({ ejecutarSolicitud: async () => respuesta(200, [{ id: ID }]) }), /inválida/);
});
