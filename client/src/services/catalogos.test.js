import assert from 'node:assert/strict';
import test from 'node:test';
import {
  ErrorCatalogo, actualizarConsultorio, actualizarProfesional, crearConsultorio, crearProfesional,
  eliminarConsultorio, eliminarProfesional, obtenerConsultorio, obtenerConsultorios,
  obtenerEspecialidades, obtenerProfesional, obtenerProfesionales,
} from './catalogos.js';

const ID = 'd8f7c2b4-923a-4a20-af63-b7cff47bd83d';
const profesional = Object.freeze({
  id: ID, nombres: 'Ana María', apellidos: 'Pérez', colegiatura: 'CEP 123456',
  correo: 'ana@institucion.pe', telefono: '+51 999 888 777', categoria: 'Enfermería',
  estado: 'Activo', especialidad: 'Cuidados intensivos',
});
const consultorio = Object.freeze({
  id: ID, codigo: 'A-201', nombre: 'Consultorio norte', ubicacion: 'Piso 2',
  especialidad: '', estado: 'Activo',
});
const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });
const ejecutar = (estado, contenido) => ({ ejecutarSolicitud: async () => respuesta(estado, contenido) });
const sinId = ({ id, ...datos }) => datos;

test('crea profesional con datos normalizados, whitelist y cookie sin institution/id enviados', async () => {
  let solicitud;
  const creado = await crearProfesional({
    ...profesional, nombres: ' Ana\u00a0 Mari\u0301a ', colegiatura: ' cep 123456 ',
    correo: ' ANA@INSTITUCION.PE ', idInstitucion: 'institución ajena',
  }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      solicitud = { ruta, opciones };
      return respuesta(201, { ...profesional, ajeno: 'ignorar' });
    },
  });
  assert.deepEqual(creado, profesional);
  assert.equal(solicitud.ruta, '/api/profesionales');
  assert.equal(solicitud.opciones.method, 'POST');
  assert.equal(solicitud.opciones.credentials, 'same-origin');
  assert.equal(solicitud.opciones.cache, 'no-store');
  assert.equal(solicitud.opciones.headers['Content-Type'], 'application/json');
  assert.ok(solicitud.opciones.signal instanceof AbortSignal);
  assert.deepEqual(JSON.parse(solicitud.opciones.body), sinId(profesional));
});

test('crea consultorio general y normaliza el código sin restricciones artificiales', async () => {
  let cuerpo;
  const creado = await crearConsultorio({ ...consultorio, codigo: ' a-201 ', especialidad: ' ' }, {
    ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(ruta, '/api/consultorios');
      cuerpo = JSON.parse(opciones.body);
      return respuesta(201, consultorio);
    },
  });
  assert.deepEqual(cuerpo, sinId(consultorio));
  assert.deepEqual(creado, consultorio);
});

test('obtiene catálogos vacíos, individuales y especialidades reales', async () => {
  assert.deepEqual(await obtenerProfesionales(ejecutar(200, [])), []);
  assert.deepEqual(await obtenerConsultorios(ejecutar(200, [consultorio])), [consultorio]);
  assert.deepEqual(await obtenerEspecialidades(ejecutar(200, [{ id: ID, nombre: 'Cardiología' }])),
    [{ id: ID, nombre: 'Cardiología' }]);
  for (const [obtener, registro, recurso] of [[obtenerProfesional, profesional, 'profesionales'],
    [obtenerConsultorio, consultorio, 'consultorios']]) {
    const obtenido = await obtener(ID, {
      ejecutarSolicitud: async (ruta, opciones) => {
        assert.equal(ruta, `/api/${recurso}/${ID}`);
        assert.equal(opciones.method, 'GET');
        assert.equal('body' in opciones, false);
        return respuesta(200, registro);
      },
    });
    assert.deepEqual(obtenido, registro);
  }
});

test('actualiza ambos recursos con PUT y solo acepta DELETE 204', async () => {
  for (const [actualizar, eliminar, registro, recurso] of [
    [actualizarProfesional, eliminarProfesional, profesional, 'profesionales'],
    [actualizarConsultorio, eliminarConsultorio, consultorio, 'consultorios'],
  ]) {
    assert.deepEqual(await actualizar(ID, registro, {
      ejecutarSolicitud: async (ruta, opciones) => {
        assert.equal(ruta, `/api/${recurso}/${ID}`);
        assert.equal(opciones.method, 'PUT');
        return respuesta(200, registro);
      },
    }), registro);
    assert.equal(await eliminar(ID, {
      ejecutarSolicitud: async (ruta, opciones) => {
        assert.equal(opciones.method, 'DELETE');
        assert.equal('body' in opciones, false);
        return { status: 204, json: async () => { throw new Error('No leer 204'); } };
      },
    }), undefined);
    await assert.rejects(eliminar(ID, ejecutar(200, registro)), (error) => error.estado === 200);
  }
});

test('rechaza ID inválido antes de contactar al servidor', async () => {
  let llamadas = 0;
  for (const id of [undefined, '', 'PRO-001', '../sesion', '1-1-1-1-1', 12]) {
    await assert.rejects(obtenerProfesional(id, {
      ejecutarSolicitud: async () => { llamadas += 1; },
    }), (error) => error instanceof ErrorCatalogo && error.estado === 404);
  }
  assert.equal(llamadas, 0);
});

test('valida campos requeridos, teléfonos, estados y límites sin enviar solicitud', async () => {
  let llamadas = 0;
  const opciones = { ejecutarSolicitud: async () => { llamadas += 1; } };
  for (const cambio of [{ nombres: ' ' }, { apellidos: 'A'.repeat(101) }, { colegiatura: 'X'.repeat(33) },
    { correo: 'persona@localhost' }, { telefono: '+++++++' }, { telefono: '123456' },
    { telefono: '1234567890123456' }, { telefono: '123.4567' }, { categoria: '' },
    { especialidad: '' }, { estado: 'Ocupado' }, { nombres: 'Ana\nMaría' }, { nombres: '\ud800' }]) {
    await assert.rejects(crearProfesional({ ...profesional, ...cambio }, opciones),
      (error) => error.tipo === 'validacion' && Boolean(error.errores[Object.keys(cambio)[0]]));
  }
  for (const cambio of [{ codigo: 'X'.repeat(21) }, { nombre: '' }, { ubicacion: '' },
    { especialidad: 'X'.repeat(101) }, { estado: 'Mantenimiento' }, { nombre: null }]) {
    await assert.rejects(crearConsultorio({ ...consultorio, ...cambio }, opciones),
      (error) => error.tipo === 'validacion' && Boolean(error.errores[Object.keys(cambio)[0]]));
  }
  assert.equal(llamadas, 0);
});

test('acepta límites de caracteres Unicode y teléfono opcional', async () => {
  assert.deepEqual(await crearProfesional({ ...profesional, nombres: '😀'.repeat(80), telefono: '' },
    ejecutar(201, profesional)), profesional);
  await assert.rejects(crearProfesional({ ...profesional, nombres: '😀'.repeat(81) }, ejecutar(201, profesional)),
    (error) => Boolean(error.errores.nombres));
  await assert.rejects(crearConsultorio({ ...consultorio, codigo: 'ß'.repeat(11) }, ejecutar(201, consultorio)),
    (error) => Boolean(error.errores.codigo));
});

test('expone errores por campo permitidos y estados 401,404,409,503', async () => {
  await assert.rejects(crearProfesional(profesional, ejecutar(409, {
    mensaje: 'La colegiatura ya existe.', errores: { colegiatura: 'Duplicada.', idInstitucion: 'ignorar', correo: 2 },
  })), (error) => {
    assert.equal(error.estado, 409);
    assert.deepEqual(error.errores, { colegiatura: 'Duplicada.' });
    return true;
  });
  for (const estado of [401, 404, 409, 503]) {
    await assert.rejects(obtenerProfesional(ID, ejecutar(estado, null)),
      (error) => error.estado === estado && Boolean(error.message));
  }
});

test('no anuncia éxito ante JSON incompleto, lista duplicada ni estados inesperados', async () => {
  for (const contenido of [null, {}, [], { ...profesional, id: 'PRO-001' },
    { ...profesional, nombres: '' }, { ...profesional, estado: 'Ocupado' }]) {
    await assert.rejects(crearProfesional(profesional, ejecutar(201, contenido)),
      (error) => error.tipo === 'respuesta' && error.estado === 201);
  }
  for (const contenido of [{}, [profesional, profesional], [null]]) {
    await assert.rejects(obtenerProfesionales(ejecutar(200, contenido)), (error) => error.tipo === 'respuesta');
  }
  await assert.rejects(crearConsultorio(consultorio, ejecutar(200, consultorio)), (error) => error.estado === 200);
});

test('controla red, timeout de lectura y configuración inválida', async () => {
  await assert.rejects(obtenerConsultorios({ ejecutarSolicitud: async () => { throw new Error('secreto interno'); } }),
    (error) => error.tipo === 'red' && !error.message.includes('secreto'));
  let senal;
  await assert.rejects(obtenerConsultorios({
    tiempoEspera: 5, ejecutarSolicitud: async (ruta, opciones) => {
      senal = opciones.signal;
      return { status: 200, json: async () => new Promise(() => {}) };
    },
  }), (error) => error.tipo === 'tiempo');
  assert.equal(senal.aborted, true);
  for (const tiempoEspera of [0, -1, NaN, Infinity]) {
    await assert.rejects(obtenerConsultorios({ tiempoEspera }), (error) => error.tipo === 'configuracion');
  }
  await assert.rejects(obtenerConsultorios({ ejecutarSolicitud: null }), (error) => error.tipo === 'configuracion');
});

test('cancela al desmontar aun cuando fetch ignora signal y evita solicitudes ya abortadas', async () => {
  const controlador = new AbortController();
  let senal;
  const solicitud = obtenerConsultorios({
    senal: controlador.signal, ejecutarSolicitud: async (ruta, opciones) => {
      senal = opciones.signal;
      return new Promise(() => {});
    },
  });
  controlador.abort();
  await assert.rejects(solicitud, (error) => error.tipo === 'cancelado');
  assert.equal(senal.aborted, true);
  let llamadas = 0;
  await assert.rejects(obtenerConsultorios({
    senal: controlador.signal, ejecutarSolicitud: async () => { llamadas += 1; },
  }), (error) => error.tipo === 'cancelado');
  assert.equal(llamadas, 0);
});
