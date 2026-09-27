import assert from 'node:assert/strict';
import test from 'node:test';
import { ErrorRegistro, registrarInstitucion } from './registro.js';

const datosValidos = Object.freeze({
  codigoInstitucion: ' CLINICA-SUR ', nombreInstitucion: ' Clínica Sur ', zonaHoraria: 'America/Lima',
  correo: ' CUENTA@INSTITUCION.PE ', contrasena: 'Clave personal ', confirmacionContrasena: 'Clave personal ',
});
const resultado = Object.freeze({
  idInstitucion: '532b3f90-eb0e-4f45-84cf-7cf3dc419ef7',
  idCuenta: '0575aa71-daf6-425a-aec1-9d93a0df068d',
  codigoInstitucion: 'CLINICA-SUR', nombreInstitucion: 'Clínica Sur', correo: 'cuenta@institucion.pe',
});
const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });

test('envía el contrato normalizado y excluye confirmación y datos adicionales', async () => {
  let solicitud;
  const creado = await registrarInstitucion({ ...datosValidos, secretoAjeno: 'no enviar' }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      solicitud = { ruta, opciones };
      return respuesta(201, { ...resultado, campoAjeno: 'no devolver' });
    },
  });
  assert.equal(solicitud.ruta, '/api/registro');
  assert.equal(solicitud.opciones.method, 'POST');
  assert.equal(solicitud.opciones.headers['Content-Type'], 'application/json');
  assert.ok(solicitud.opciones.signal instanceof AbortSignal);
  assert.deepEqual(JSON.parse(solicitud.opciones.body), {
    codigoInstitucion: 'CLINICA-SUR', nombreInstitucion: 'Clínica Sur', zonaHoraria: 'America/Lima',
    correo: 'cuenta@institucion.pe', contrasena: 'Clave personal ',
  });
  assert.deepEqual(creado, resultado);
  assert.equal(datosValidos.codigoInstitucion, ' CLINICA-SUR ');
});

test('no llama al servicio cuando los datos locales son inválidos', async () => {
  let solicitudes = 0;
  await assert.rejects(registrarInstitucion({ ...datosValidos, contrasena: '' }, {
    ejecutarSolicitud: async () => { solicitudes += 1; },
  }), (error) => error instanceof ErrorRegistro && error.tipo === 'validacion' && Boolean(error.errores.contrasena));
  assert.equal(solicitudes, 0);
});

test('no acepta una respuesta 200 como creación confirmada', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => respuesta(200, resultado),
  }), (error) => error.estado === 200 && error.message.includes('No se pudo confirmar'));
});

test('rechaza JSON de éxito incompleto, nulo, arreglo o con tipos inválidos', async () => {
  for (const contenido of [null, [], {}, { ...resultado, idCuenta: 123 }, { ...resultado, correo: ' ' }]) {
    await assert.rejects(registrarInstitucion(datosValidos, {
      ejecutarSolicitud: async () => respuesta(201, contenido),
    }), (error) => error.tipo === 'respuesta' && error.estado === 201);
  }
});

test('rechaza una respuesta de éxito que no se puede interpretar como JSON', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => ({ status: 201, json: async () => { throw new SyntaxError('no JSON'); } }),
  }), (error) => error.tipo === 'respuesta');
});

test('conserva errores 400 por campo y descarta propiedades ajenas al contrato', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => respuesta(400, {
      mensaje: 'Revisa los datos.', errores: { correo: 'Correo inválido.', ajeno: 'ignorar', contrasena: 1 },
    }),
  }), (error) => {
    assert.equal(error.estado, 400);
    assert.equal(error.message, 'Revisa los datos.');
    assert.deepEqual(error.errores, { correo: 'Correo inválido.' });
    return true;
  });
});

test('identifica el conflicto 409 de código institucional', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => respuesta(409, {
      mensaje: 'Código registrado.', errores: { codigoInstitucion: 'Elige otro código.' },
    }),
  }), (error) => error.estado === 409 && error.errores.codigoInstitucion === 'Elige otro código.');
});

test('maneja respuestas 503 y 500 sin un mensaje utilizable', async () => {
  for (const estado of [503, 500]) {
    await assert.rejects(registrarInstitucion(datosValidos, {
      ejecutarSolicitud: async () => respuesta(estado, { mensaje: '', errores: null }),
    }), (error) => error.estado === estado && Boolean(error.message) && Object.keys(error.errores).length === 0);
  }
});

test('mantiene un mensaje comprensible cuando el error HTTP no contiene JSON', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => ({ status: 503, json: async () => { throw new SyntaxError('HTML'); } }),
  }), (error) => error.estado === 503 && error.message.includes('no está disponible'));
});

test('maneja el fallo de red sin propagar detalles de la solicitud', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => { throw new Error('detalle sensible'); },
  }), (error) => error.tipo === 'red' && !error.message.includes('detalle sensible'));
});

test('cancela la solicitud al vencer el tiempo y no confirma un resultado desconocido', async () => {
  let senal;
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async (ruta, opciones) => {
      senal = opciones.signal;
      return new Promise(() => {});
    },
    tiempoEspera: 5,
  }), (error) => error.tipo === 'tiempo' && error.message.includes('No se pudo confirmar'));
  assert.equal(senal.aborted, true);
});

test('el tiempo de espera también cubre la lectura del cuerpo de respuesta', async () => {
  await assert.rejects(registrarInstitucion(datosValidos, {
    ejecutarSolicitud: async () => ({ status: 201, json: () => new Promise(() => {}) }),
    tiempoEspera: 5,
  }), (error) => error.tipo === 'tiempo');
});

test('valida opciones incorrectas antes de enviar', async () => {
  for (const tiempoEspera of [0, -1, Infinity, NaN]) {
    await assert.rejects(registrarInstitucion(datosValidos, { tiempoEspera }),
      (error) => error.tipo === 'configuracion');
  }
  await assert.rejects(registrarInstitucion(datosValidos, { ejecutarSolicitud: null }),
    (error) => error.tipo === 'configuracion');
});
