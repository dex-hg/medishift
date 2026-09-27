import {
  LIMITES_REGISTRO,
  normalizarDatosRegistro,
  validarCuenta,
  validarInstitucion,
} from './validacionesRegistro.js';

export const LIMITES_INICIO_SESION = Object.freeze({
  codigoInstitucion: LIMITES_REGISTRO.codigoInstitucion,
  correo: LIMITES_REGISTRO.correo,
  contrasena: LIMITES_REGISTRO.contrasena,
});

export function normalizarDatosInicioSesion(datos) {
  const { codigoInstitucion, correo, contrasena } = normalizarDatosRegistro(datos);
  return { codigoInstitucion, correo, contrasena };
}

export function validarInicioSesion(datos) {
  const datosNormalizados = normalizarDatosInicioSesion(datos);
  const erroresInstitucion = validarInstitucion(datosNormalizados);
  const erroresCuenta = validarCuenta(datosNormalizados);
  const errores = {};

  // Comparte las reglas del registro sin exigir sus demás campos.
  if (erroresInstitucion.codigoInstitucion) {
    errores.codigoInstitucion = erroresInstitucion.codigoInstitucion;
  }
  if (erroresCuenta.correo) {
    errores.correo = erroresCuenta.correo;
  }
  if (erroresCuenta.contrasena) {
    errores.contrasena = erroresCuenta.contrasena;
  }

  return errores;
}
