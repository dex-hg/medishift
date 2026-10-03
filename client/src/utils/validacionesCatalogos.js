export const LIMITES_PROFESIONAL = Object.freeze({
  nombres: 80, apellidos: 100, colegiatura: 32, correo: 254,
  telefono: 20, categoria: 40, especialidad: 100,
});

export const LIMITES_CONSULTORIO = Object.freeze({
  codigo: 20, nombre: 100, ubicacion: 100, especialidad: 100,
});

const CAMPOS_PROFESIONAL = [...Object.keys(LIMITES_PROFESIONAL), 'estado'];
const CAMPOS_CONSULTORIO = [...Object.keys(LIMITES_CONSULTORIO), 'estado'];
const ETIQUETAS = {
  nombres: 'los nombres', apellidos: 'los apellidos', colegiatura: 'la colegiatura',
  correo: 'el correo', categoria: 'la categoría', especialidad: 'la especialidad',
  codigo: 'el código', nombre: 'el nombre', ubicacion: 'la ubicación',
};

function normalizarDatos(datos, campos) {
  const fuente = datos && typeof datos === 'object' && !Array.isArray(datos) ? datos : {};
  return Object.fromEntries(campos.map((campo) => [campo,
    typeof fuente[campo] === 'string' ? fuente[campo].normalize('NFC').replace(/[\s\p{Z}\ufeff]+/gu, ' ').trim() : '',
  ]));
}

export function normalizarProfesional(datos) {
  const normalizados = normalizarDatos(datos, CAMPOS_PROFESIONAL);
  normalizados.correo = normalizados.correo.toLowerCase();
  normalizados.colegiatura = normalizados.colegiatura.toUpperCase();
  return normalizados;
}

export function normalizarConsultorio(datos) {
  const normalizados = normalizarDatos(datos, CAMPOS_CONSULTORIO);
  normalizados.codigo = normalizados.codigo.toUpperCase();
  return normalizados;
}

function validarDatos(datos, limites, opcionales, originales) {
  const errores = {};
  for (const [campo, limite] of Object.entries(limites)) {
    const valor = datos[campo];
    const original = originales?.[campo];
    if (original !== undefined && typeof original !== 'string') {
      errores[campo] = 'Este campo debe contener texto.';
    } else if (typeof original === 'string' && /[\u0000-\u001f\u007f-\u009f]/.test(original)) {
      errores[campo] = 'Usa texto sin saltos de línea ni caracteres de control.';
    } else if (typeof original === 'string' && [...original].some((caracter) => {
      const codigo = caracter.codePointAt(0);
      return codigo >= 0xd800 && codigo <= 0xdfff;
    })) {
      errores[campo] = 'El texto contiene una secuencia Unicode no válida.';
    } else if (!valor && !opcionales.includes(campo)) {
      errores[campo] = `Ingresa ${ETIQUETAS[campo]}.`;
    } else if ([...valor].length > limite) {
      errores[campo] = `Usa hasta ${limite} caracteres.`;
    }
  }
  if (!['Activo', 'Inactivo'].includes(datos.estado)) {
    errores.estado = 'Selecciona Activo o Inactivo.';
  }
  return errores;
}

export function validarProfesional(datos) {
  const normalizados = normalizarProfesional(datos);
  const errores = validarDatos(normalizados, LIMITES_PROFESIONAL, ['telefono'], datos);
  const formatoCorreo = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;
  if (normalizados.correo && !errores.correo && !formatoCorreo.test(normalizados.correo)) {
    errores.correo = 'Ingresa un correo válido.';
  }
  const digitos = (normalizados.telefono.match(/[0-9]/g) || []).length;
  if (normalizados.telefono && !errores.telefono
    && (!/^\+?[0-9][0-9 ()-]*$/.test(normalizados.telefono) || digitos < 7 || digitos > 15)) {
    errores.telefono = 'Ingresa un teléfono con entre 7 y 15 dígitos y prefijo opcional +.';
  }
  return errores;
}

export function validarConsultorio(datos) {
  return validarDatos(normalizarConsultorio(datos), LIMITES_CONSULTORIO, ['especialidad'], datos);
}
