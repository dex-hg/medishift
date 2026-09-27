import assert from 'node:assert/strict';
import test from 'node:test';
import {
  LIMITES_INICIO_SESION,
  normalizarDatosInicioSesion,
  validarInicioSesion,
} from './validacionesInicioSesion.js';

const datosValidos = {
  codigoInstitucion: 'Clinica-Lima',
  correo: 'cuenta@institucion.pe',
  contrasena: ' Clave con espacios ',
};

test('acepta los tres campos del acceso sin datos de registro ni confirmación', () => {
  assert.deepEqual(validarInicioSesion(datosValidos), {});
  assert.deepEqual(Object.keys(normalizarDatosInicioSesion(datosValidos)), [
    'codigoInstitucion', 'correo', 'contrasena',
  ]);
});

test('recorta espacios públicos y conserva las mayúsculas del código', () => {
  const datos = normalizarDatosInicioSesion({
    codigoInstitucion: '\u00a0\ufeff Clinica-Lima \ufeff',
    correo: ' CUENTA@Institucion.PE\u00a0',
    contrasena: datosValidos.contrasena,
  });
  assert.deepEqual(datos, datosValidos);
});

test('preserva cada carácter y la capitalización de la contraseña', () => {
  for (const contrasena of [' Clave ', 'clave', 'CLAVE', '\u00a0Clave\ufeff', '🩺']) {
    const datos = { ...datosValidos, contrasena };
    assert.equal(normalizarDatosInicioSesion(datos).contrasena, contrasena);
    assert.deepEqual(validarInicioSesion(datos), {});
  }
});

test('rechaza campos vacíos y espacios Unicode como únicos datos', () => {
  for (const valor of ['', '   ', '\t\n\r', '\u00a0\ufeff\u3000']) {
    assert.deepEqual(Object.keys(validarInicioSesion({
      codigoInstitucion: valor, correo: valor, contrasena: valor,
    })), ['codigoInstitucion', 'correo', 'contrasena']);
  }
});

test('trata solicitudes ausentes y tipos incorrectos como errores de validación', () => {
  for (const datos of [undefined, null, {}, [], 123, false, 'texto']) {
    assert.deepEqual(Object.keys(validarInicioSesion(datos)), [
      'codigoInstitucion', 'correo', 'contrasena',
    ]);
  }
  for (const valor of [null, 123, true, [], {}]) {
    for (const campo of Object.keys(datosValidos)) {
      const errores = validarInicioSesion({ ...datosValidos, [campo]: valor });
      assert.deepEqual(Object.keys(errores), [campo]);
    }
  }
});

test('acepta 32 caracteres del código y rechaza 33', () => {
  const limite = LIMITES_INICIO_SESION.codigoInstitucion;
  assert.deepEqual(validarInicioSesion({ ...datosValidos, codigoInstitucion: 'a'.repeat(limite) }), {});
  assert.ok(validarInicioSesion({ ...datosValidos, codigoInstitucion: 'a'.repeat(limite + 1) }).codigoInstitucion);
});

test('cuenta puntos Unicode del código como PostgreSQL', () => {
  assert.deepEqual(validarInicioSesion({
    ...datosValidos, codigoInstitucion: ` ${'🩺'.repeat(32)} `,
  }), {});
  assert.ok(validarInicioSesion({
    ...datosValidos, codigoInstitucion: '🩺'.repeat(33),
  }).codigoInstitucion);
});

test('rechaza NUL y secuencias Unicode incompletas en código y contraseña', () => {
  for (const valor of ['texto\0', 'texto\uD800', '\uDC00texto']) {
    assert.ok(validarInicioSesion({ ...datosValidos, codigoInstitucion: valor }).codigoInstitucion);
    assert.ok(validarInicioSesion({ ...datosValidos, contrasena: valor }).contrasena);
  }
});

test('admite correo local y direcciones comunes, con mayúsculas normalizadas', () => {
  for (const correo of ['persona@localhost', 'nombre+turnos@institucion.pe', 'PERSONA@INSTITUCION.PE']) {
    assert.deepEqual(validarInicioSesion({ ...datosValidos, correo }), {});
    assert.equal(normalizarDatosInicioSesion({ ...datosValidos, correo }).correo, correo.toLowerCase());
  }
});

test('rechaza correo sin dominio, con espacios, múltiples arrobas o dominio inválido', () => {
  for (const correo of ['persona', '@institucion.pe', 'persona@', 'per sona@institucion.pe',
    'persona@@institucion.pe', 'persona@-institucion.pe', 'persona@institucion..pe']) {
    assert.ok(validarInicioSesion({ ...datosValidos, correo }).correo);
  }
});

test('acepta correo de 254 caracteres y rechaza 255', () => {
  const dominio = `${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(61)}`;
  const correo = `${'a'.repeat(64)}@${dominio}`;
  assert.equal(correo.length, LIMITES_INICIO_SESION.correo);
  assert.deepEqual(validarInicioSesion({ ...datosValidos, correo }), {});
  assert.ok(validarInicioSesion({ ...datosValidos, correo: `${correo}e` }).correo);
});

test('no supone que el correo identifica una cuenta fuera de su institución', () => {
  for (const codigoInstitucion of ['CLINICA-NORTE', 'CLINICA-SUR']) {
    assert.deepEqual(validarInicioSesion({ ...datosValidos, codigoInstitucion }), {});
    assert.equal(normalizarDatosInicioSesion({ ...datosValidos, codigoInstitucion }).codigoInstitucion,
      codigoInstitucion);
  }
});

test('mantiene el límite de 1024 caracteres de contraseña y admite Unicode completo', () => {
  const limite = LIMITES_INICIO_SESION.contrasena;
  for (const caracter of ['a', '🩺']) {
    assert.deepEqual(validarInicioSesion({ ...datosValidos, contrasena: caracter.repeat(limite) }), {});
    assert.ok(validarInicioSesion({ ...datosValidos, contrasena: caracter.repeat(limite + 1) }).contrasena);
  }
});

test('no añade una longitud mínima para acceder a una contraseña ya creada', () => {
  assert.deepEqual(validarInicioSesion({ ...datosValidos, contrasena: 'a' }), {});
});

test('ignora datos ajenos al acceso sin exigir nombre, zona ni confirmación', () => {
  const datos = { ...datosValidos, nombreInstitucion: '', zonaHoraria: 'inexistente',
    confirmacionContrasena: 'distinta', estado: 'active', rol: 'admin' };
  assert.deepEqual(validarInicioSesion(datos), {});
  assert.deepEqual(normalizarDatosInicioSesion(datos), datosValidos);
});

test('normaliza y valida sin modificar los datos originales', () => {
  const datos = Object.freeze({ ...datosValidos, correo: ' CUENTA@INSTITUCION.PE ' });
  const copia = { ...datos };
  normalizarDatosInicioSesion(datos);
  validarInicioSesion(datos);
  assert.deepEqual(datos, copia);
});
