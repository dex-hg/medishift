import assert from 'node:assert/strict';
import test from 'node:test';
import { ErrorSesion, cerrarSesion, iniciarSesion, obtenerSesion } from './sesion.js';

const datosValidos = Object.freeze({
  codigoInstitucion: ' CLINICA-SUR ', correo: ' PERSONA@INSTITUCION.PE ',
  contrasena: 'Clave personal ',
});
const cuenta = Object.freeze({
  idCuenta: '0575aa71-daf6-425a-aec1-9d93a0df068d',
  idInstitucion: '532b3f90-eb0e-4f45-84cf-7cf3dc419ef7',
  codigoInstitucion: 'CLINICA-SUR', nombreInstitucion: 'Clínica Sur',
  correo: 'persona@institucion.pe',
});
const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });

test('envía solo las tres credenciales normalizadas con cookie del mismo origen', async () => {
  let solicitud;
  const creada = await iniciarSesion({ ...datosValidos, campoAjeno: 'no enviar' }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      solicitud = { ruta, opciones };
      return respuesta(200, { ...cuenta, contrasena: 'no devolver' });
    },
  });
  assert.equal(solicitud.ruta, '/api/sesion');
  assert.equal(solicitud.opciones.method, 'POST');
  assert.equal(solicitud.opciones.credentials, 'same-origin');
  assert.equal(solicitud.opciones.cache, 'no-store');
  assert.equal(solicitud.opciones.headers['Content-Type'], 'application/json');
  assert.ok(solicitud.opciones.signal instanceof AbortSignal);
  assert.deepEqual(JSON.parse(solicitud.opciones.body), {
    codigoInstitucion: 'CLINICA-SUR', correo: 'persona@institucion.pe', contrasena: 'Clave personal ',
  });
  assert.deepEqual(creada, cuenta);
  assert.equal(datosValidos.contrasena, 'Clave personal ');
});

test('valida entradas antes de enviar cualquier solicitud', async () => {
  let llamadas = 0;
  await assert.rejects(iniciarSesion({ ...datosValidos, contrasena: ' ' }, {
    ejecutarSolicitud: async () => { llamadas += 1; },
  }), (error) => error instanceof ErrorSesion && error.tipo === 'validacion'
    && Boolean(error.errores.contrasena));
  assert.equal(llamadas, 0);
});

test('rechaza éxito de login con estado diferente de 200', async () => {
  await assert.rejects(iniciarSesion(datosValidos, {
    ejecutarSolicitud: async () => respuesta(201, cuenta),
  }), (error) => error.estado === 201);
});

test('rechaza cuerpos de éxito incompletos o inválidos', async () => {
  for (const contenido of [null, [], {}, { ...cuenta, idCuenta: 5 }, { ...cuenta, correo: ' ' }]) {
    await assert.rejects(iniciarSesion(datosValidos, {
      ejecutarSolicitud: async () => respuesta(200, contenido),
    }), (error) => error.estado === 200 && error.tipo === 'respuesta');
  }
  await assert.rejects(iniciarSesion(datosValidos, {
    ejecutarSolicitud: async () => ({ status: 200, json: async () => { throw new SyntaxError('HTML'); } }),
  }), (error) => error.tipo === 'respuesta');
});

test('expone errores 400 permitidos por campo sin datos inesperados', async () => {
  await assert.rejects(iniciarSesion(datosValidos, {
    ejecutarSolicitud: async () => respuesta(400, {
      mensaje: 'Revisa los datos.', errores: { correo: 'Correo inválido.', ajeno: 'ignorar', contrasena: 7 },
    }),
  }), (error) => {
    assert.equal(error.estado, 400);
    assert.equal(error.message, 'Revisa los datos.');
    assert.deepEqual(error.errores, { correo: 'Correo inválido.' });
    return true;
  });
});

test('maneja credenciales inválidas 401 sin indicar cuál dato falló', async () => {
  await assert.rejects(iniciarSesion(datosValidos, {
    ejecutarSolicitud: async () => respuesta(401, {
      mensaje: 'La contraseña era incorrecta.', errores: { contrasena: 'Incorrecta.' },
    }),
  }), (error) => error.estado === 401 && error.message.includes('no son correctos')
    && !error.message.includes('contraseña era') && Object.keys(error.errores).length === 0);
});

test('maneja indisponibilidad 503 y fallo 500 con respuesta no JSON', async () => {
  for (const estado of [503, 500]) {
    await assert.rejects(iniciarSesion(datosValidos, {
      ejecutarSolicitud: async () => ({ status: estado, json: async () => { throw new SyntaxError('HTML'); } }),
    }), (error) => error.estado === estado && Boolean(error.message));
  }
});

test('convierte fallos de red en error controlado sin mostrar detalles internos', async () => {
  await assert.rejects(iniciarSesion(datosValidos, {
    ejecutarSolicitud: async () => { throw new Error('detalle interno'); },
  }), (error) => error.tipo === 'red' && !error.message.includes('detalle interno'));
});

test('aborta la solicitud que supera el tiempo de espera', async () => {
  let senal;
  await assert.rejects(iniciarSesion(datosValidos, {
    tiempoEspera: 5,
    ejecutarSolicitud: async (ruta, opciones) => {
      senal = opciones.signal;
      return new Promise(() => {});
    },
  }), (error) => error.tipo === 'tiempo');
  assert.equal(senal.aborted, true);
});

test('rechaza configuración de solicitud inválida', async () => {
  for (const tiempoEspera of [0, -1, NaN, Infinity]) {
    await assert.rejects(iniciarSesion(datosValidos, { tiempoEspera }),
      (error) => error.tipo === 'configuracion');
  }
  await assert.rejects(iniciarSesion(datosValidos, { ejecutarSolicitud: null }),
    (error) => error.tipo === 'configuracion');
});

test('consulta la sesión con GET, cookie y sin cuerpo', async () => {
  let solicitud;
  const actual = await obtenerSesion({
    ejecutarSolicitud: async (ruta, opciones) => {
      solicitud = { ruta, opciones };
      return respuesta(200, cuenta);
    },
  });
  assert.deepEqual(actual, cuenta);
  assert.equal(solicitud.ruta, '/api/sesion');
  assert.equal(solicitud.opciones.method, 'GET');
  assert.equal(solicitud.opciones.credentials, 'same-origin');
  assert.equal('body' in solicitud.opciones, false);
});

test('GET sin sesión reporta 401 para la ruta protegida', async () => {
  await assert.rejects(obtenerSesion({
    ejecutarSolicitud: async () => respuesta(401, { mensaje: 'Sin sesión.', errores: {} }),
  }), (error) => error.estado === 401);
});

test('DELETE 204 cierra sesión sin cuerpo y rechaza otros estados', async () => {
  let solicitud;
  const resultado = await cerrarSesion({
    ejecutarSolicitud: async (ruta, opciones) => {
      solicitud = { ruta, opciones };
      return { status: 204, json: async () => { throw new Error('No debería leerse'); } };
    },
  });
  assert.equal(resultado, undefined);
  assert.equal(solicitud.opciones.method, 'DELETE');
  assert.equal(solicitud.opciones.credentials, 'same-origin');
  assert.equal('body' in solicitud.opciones, false);
  await assert.rejects(cerrarSesion({
    ejecutarSolicitud: async () => respuesta(200, cuenta),
  }), (error) => error.estado === 200);
});
