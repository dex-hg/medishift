import assert from 'node:assert/strict';
import test from 'node:test';
import {
  LIMITES_REGISTRO,
  ZONAS_HORARIAS,
  normalizarDatosRegistro,
  validarCuenta,
  validarInstitucion,
} from './validacionesRegistro.js';

const datosValidos = {
  codigoInstitucion: 'Clinica-Lima',
  nombreInstitucion: 'Clínica de Lima',
  zonaHoraria: 'America/Lima',
  correo: 'cuenta@institucion.pe',
  contrasena: 'Clave con espacios ',
  confirmacionContrasena: 'Clave con espacios ',
};

test('normaliza datos públicos y conserva código y contraseñas', () => {
  const datos = normalizarDatosRegistro({
    ...datosValidos,
    codigoInstitucion: ' Clinica-Lima ',
    nombreInstitucion: ' Clínica de Lima ',
    correo: ' CUENTA@Institucion.PE ',
  });

  assert.equal(datos.codigoInstitucion, 'Clinica-Lima');
  assert.equal(datos.nombreInstitucion, 'Clínica de Lima');
  assert.equal(datos.correo, 'cuenta@institucion.pe');
  assert.equal(datos.zonaHoraria, datosValidos.zonaHoraria);
  assert.equal(datos.contrasena, datosValidos.contrasena);
  assert.equal(datos.confirmacionContrasena, datosValidos.confirmacionContrasena);
});

test('acepta una institución y una cuenta válidas', () => {
  assert.deepEqual(validarInstitucion(datosValidos), {});
  assert.deepEqual(validarCuenta(datosValidos), {});
});

test('rechaza campos vacíos o formados solo por espacios', () => {
  const datos = Object.fromEntries(Object.keys(datosValidos).map((campo) => [campo, '   ']));
  assert.deepEqual(Object.keys(validarInstitucion(datos)), [
    'codigoInstitucion', 'nombreInstitucion', 'zonaHoraria',
  ]);
  assert.deepEqual(Object.keys(validarCuenta(datos)), [
    'correo', 'contrasena', 'confirmacionContrasena',
  ]);
});

test('maneja datos ausentes y valores con tipos incorrectos', () => {
  for (const datos of [undefined, null, {}, { codigoInstitucion: 123, correo: true }]) {
    assert.equal(Object.keys(validarInstitucion(datos)).length, 3);
    assert.equal(Object.keys(validarCuenta(datos)).length, 3);
  }
});

test('respeta los límites de código y nombre definidos en PostgreSQL', () => {
  for (const campo of ['codigoInstitucion', 'nombreInstitucion']) {
    const limite = LIMITES_REGISTRO[campo];
    assert.deepEqual(validarInstitucion({ ...datosValidos, [campo]: 'a'.repeat(limite) }), {});
    assert.ok(validarInstitucion({ ...datosValidos, [campo]: 'a'.repeat(limite + 1) })[campo]);
  }
});

test('cuenta caracteres Unicode como PostgreSQL y recorta espacios públicos', () => {
  assert.deepEqual(validarInstitucion({
    ...datosValidos,
    codigoInstitucion: ` ${'a'.repeat(32)} `,
    nombreInstitucion: '🩺'.repeat(140),
  }), {});
  assert.ok(validarInstitucion({
    ...datosValidos,
    nombreInstitucion: '🩺'.repeat(141),
  }).nombreInstitucion);
});

test('las zonas ofrecidas son IANA válidas y no utilizan offsets fijos', () => {
  for (const zona of ZONAS_HORARIAS) {
    assert.deepEqual(validarInstitucion({ ...datosValidos, zonaHoraria: zona.valor }), {});
    assert.doesNotThrow(() => new Intl.DateTimeFormat('es', { timeZone: zona.valor }));
    assert.ok(zona.etiqueta);
  }
});

test('rechaza zonas ajenas a la lista y una zona que supera el límite', () => {
  for (const zonaHoraria of ['UTC', 'America/Desconocida', ' America/Lima ', 'a'.repeat(65)]) {
    assert.ok(validarInstitucion({ ...datosValidos, zonaHoraria }).zonaHoraria);
  }
});

test('admite correo con dominio local y formatos comunes', () => {
  for (const correo of ['persona@localhost', 'persona+turnos@institucion.pe', 'PERSONA@INSTITUCION.PE']) {
    assert.deepEqual(validarCuenta({ ...datosValidos, correo }), {});
  }
});

test('rechaza correo sin dominio, con espacios, múltiples arrobas o dominio inválido', () => {
  for (const correo of ['persona', '@institucion.pe', 'persona@', 'per sona@institucion.pe',
    'persona@@institucion.pe', 'persona@-institucion.pe', 'persona@institucion..pe']) {
    assert.ok(validarCuenta({ ...datosValidos, correo }).correo);
  }
});

test('acepta un correo de 254 caracteres y rechaza 255', () => {
  const dominio = `${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(61)}`;
  const correo = `${'a'.repeat(64)}@${dominio}`;
  assert.equal(correo.length, 254);
  assert.deepEqual(validarCuenta({ ...datosValidos, correo }), {});
  assert.ok(validarCuenta({ ...datosValidos, correo: `${correo}e` }).correo);
});

test('no introduce una longitud mínima de contraseña ausente del esquema', () => {
  assert.deepEqual(validarCuenta({
    ...datosValidos,
    contrasena: 'a',
    confirmacionContrasena: 'a',
  }), {});
});

test('exige confirmación exacta sin recortar ni convertir las contraseñas', () => {
  for (const confirmacionContrasena of ['Clave con espacios', 'clave con espacios ', 'Otra clave']) {
    const errores = validarCuenta({ ...datosValidos, confirmacionContrasena });
    assert.ok(errores.confirmacionContrasena);
    assert.equal(errores.contrasena, undefined);
  }
});

test('no modifica el objeto recibido durante normalización o validación', () => {
  const datos = Object.freeze({ ...datosValidos });
  normalizarDatosRegistro(datos);
  validarInstitucion(datos);
  validarCuenta(datos);
  assert.deepEqual(datos, datosValidos);
});
