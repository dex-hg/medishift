export const LIMITES_REGISTRO = Object.freeze({
  codigoInstitucion: 32,
  nombreInstitucion: 140,
  zonaHoraria: 64,
  correo: 254,
  contrasena: 1024,
});

export const ZONAS_HORARIAS = [
  { valor: 'America/Lima', etiqueta: 'Perú · Lima' },
  { valor: 'America/Bogota', etiqueta: 'Colombia · Bogotá' },
  { valor: 'America/Santiago', etiqueta: 'Chile · Santiago' },
  { valor: 'America/La_Paz', etiqueta: 'Bolivia · La Paz' },
  { valor: 'America/Mexico_City', etiqueta: 'México · Ciudad de México' },
  { valor: 'America/Argentina/Buenos_Aires', etiqueta: 'Argentina · Buenos Aires' },
];

// Admite el formato de los campos HTML de correo, incluidos dominios locales.
const FORMATO_CORREO = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;

function obtenerTexto(datos, campo) {
  return typeof datos?.[campo] === 'string' ? datos[campo] : '';
}

function superaLimite(texto, limite) {
  // PostgreSQL cuenta caracteres, no unidades UTF-16.
  return Array.from(texto).length > limite;
}

function contieneCaracteresInvalidos(texto) {
  return Array.from(texto).some((caracter) => {
    const codigo = caracter.codePointAt(0);
    return codigo === 0 || (codigo >= 0xD800 && codigo <= 0xDFFF);
  });
}

export function normalizarDatosRegistro(datos) {
  return {
    codigoInstitucion: obtenerTexto(datos, 'codigoInstitucion').trim(),
    nombreInstitucion: obtenerTexto(datos, 'nombreInstitucion').trim(),
    zonaHoraria: obtenerTexto(datos, 'zonaHoraria'),
    correo: obtenerTexto(datos, 'correo').trim().toLowerCase(),
    contrasena: obtenerTexto(datos, 'contrasena'),
    confirmacionContrasena: obtenerTexto(datos, 'confirmacionContrasena'),
  };
}

export function validarInstitucion(datos) {
  const { codigoInstitucion, nombreInstitucion, zonaHoraria } = normalizarDatosRegistro(datos);
  const errores = {};

  if (!codigoInstitucion) {
    errores.codigoInstitucion = 'Ingresa el código de la institución.';
  } else if (superaLimite(codigoInstitucion, LIMITES_REGISTRO.codigoInstitucion)) {
    errores.codigoInstitucion = 'El código admite hasta 32 caracteres.';
  } else if (contieneCaracteresInvalidos(codigoInstitucion)) {
    errores.codigoInstitucion = 'El código contiene caracteres no válidos.';
  }

  if (!nombreInstitucion) {
    errores.nombreInstitucion = 'Ingresa el nombre de la institución.';
  } else if (superaLimite(nombreInstitucion, LIMITES_REGISTRO.nombreInstitucion)) {
    errores.nombreInstitucion = 'El nombre admite hasta 140 caracteres.';
  } else if (contieneCaracteresInvalidos(nombreInstitucion)) {
    errores.nombreInstitucion = 'El nombre contiene caracteres no válidos.';
  }

  if (!zonaHoraria.trim()) {
    errores.zonaHoraria = 'Selecciona la zona horaria de la institución.';
  } else if (superaLimite(zonaHoraria, LIMITES_REGISTRO.zonaHoraria)) {
    errores.zonaHoraria = 'La zona horaria admite hasta 64 caracteres.';
  } else if (!ZONAS_HORARIAS.some((zona) => zona.valor === zonaHoraria)) {
    errores.zonaHoraria = 'Selecciona una zona horaria de la lista.';
  }

  return errores;
}

export function validarCuenta(datos) {
  const { correo, contrasena, confirmacionContrasena } = normalizarDatosRegistro(datos);
  const errores = {};

  if (!correo) {
    errores.correo = 'Ingresa el correo de tu cuenta.';
  } else if (superaLimite(correo, LIMITES_REGISTRO.correo)) {
    errores.correo = 'El correo admite hasta 254 caracteres.';
  } else if (!FORMATO_CORREO.test(correo)) {
    errores.correo = 'Ingresa un correo válido, por ejemplo nombre@institucion.pe.';
  }

  if (!contrasena.trim()) {
    errores.contrasena = 'Ingresa una contraseña que no esté formada solo por espacios.';
  } else if (superaLimite(contrasena, LIMITES_REGISTRO.contrasena)) {
    errores.contrasena = 'La contraseña admite hasta 1024 caracteres.';
  } else if (contieneCaracteresInvalidos(contrasena)) {
    errores.contrasena = 'La contraseña contiene caracteres no válidos.';
  }

  if (!confirmacionContrasena.trim()) {
    errores.confirmacionContrasena = 'Repite tu contraseña.';
  } else if (contieneCaracteresInvalidos(confirmacionContrasena)) {
    errores.confirmacionContrasena = 'La confirmación contiene caracteres no válidos.';
  } else if (confirmacionContrasena !== contrasena) {
    errores.confirmacionContrasena = 'Las contraseñas deben coincidir exactamente.';
  }

  return errores;
}
