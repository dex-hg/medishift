import {
  normalizarDatosRegistro, validarCuenta, validarInstitucion,
} from '../utils/validacionesRegistro.js';

const CAMPOS_ENVIO = ['codigoInstitucion', 'nombreInstitucion', 'zonaHoraria', 'correo', 'contrasena'];
const CAMPOS_RESULTADO = ['idInstitucion', 'idCuenta', 'codigoInstitucion', 'nombreInstitucion', 'correo'];
const MENSAJES_HTTP = {
  400: 'Revisa los datos del registro.',
  409: 'El código de institución ya está registrado.',
  503: 'El servicio no está disponible. Inténtalo nuevamente en unos momentos.',
  500: 'No se pudo completar el registro. Inténtalo nuevamente.',
};

export class ErrorRegistro extends Error {
  constructor(mensaje, { estado = 0, errores = {}, tipo = 'servicio' } = {}) {
    super(mensaje);
    this.name = 'ErrorRegistro';
    this.estado = estado;
    this.errores = errores;
    this.tipo = tipo;
  }
}

function esObjeto(valor) {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}

function obtenerErrores(contenido) {
  if (!esObjeto(contenido?.errores)) return {};
  return Object.fromEntries(CAMPOS_ENVIO
    .filter((campo) => typeof contenido.errores[campo] === 'string' && contenido.errores[campo].trim())
    .map((campo) => [campo, contenido.errores[campo]]));
}

export async function registrarInstitucion(datos, {
  ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000,
} = {}) {
  const datosNormalizados = normalizarDatosRegistro(datos);
  const errores = { ...validarInstitucion(datosNormalizados), ...validarCuenta(datosNormalizados) };
  if (Object.keys(errores).length) {
    throw new ErrorRegistro('Revisa los datos del registro.', { errores, tipo: 'validacion' });
  }
  if (typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0) {
    throw new ErrorRegistro('No se pudo iniciar la solicitud de registro.', { tipo: 'configuracion' });
  }

  const controlador = new AbortController();
  let temporizador;
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      rechazar(new ErrorRegistro(
        'La solicitud tardó demasiado. No se pudo confirmar el registro.', { tipo: 'tiempo' },
      ));
      controlador.abort();
    }, tiempoEspera);
  });

  try {
    const solicitud = async () => {
      const respuesta = await ejecutarSolicitud('/api/registro', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(Object.fromEntries(CAMPOS_ENVIO.map((campo) => [campo, datosNormalizados[campo]]))),
        signal: controlador.signal,
      });
      let contenido;
      try { contenido = await respuesta.json(); } catch { contenido = null; }

      if (respuesta.status !== 201) {
        const mensajePredeterminado = MENSAJES_HTTP[respuesta.status]
          || 'El servicio devolvió una respuesta inesperada. No se pudo confirmar el registro.';
        const mensaje = esObjeto(contenido) && typeof contenido.mensaje === 'string' && contenido.mensaje.trim()
          ? contenido.mensaje : mensajePredeterminado;
        throw new ErrorRegistro(mensaje, { estado: respuesta.status, errores: obtenerErrores(contenido) });
      }
      if (!esObjeto(contenido) || !CAMPOS_RESULTADO.every(
        (campo) => typeof contenido[campo] === 'string' && contenido[campo].trim(),
      )) {
        throw new ErrorRegistro('El servicio devolvió una respuesta inválida. No se pudo confirmar el registro.', {
          estado: 201, tipo: 'respuesta',
        });
      }
      return Object.fromEntries(CAMPOS_RESULTADO.map((campo) => [campo, contenido[campo]]));
    };
    return await Promise.race([solicitud(), vencimiento]);
  } catch (error) {
    if (error instanceof ErrorRegistro) throw error;
    throw new ErrorRegistro('No se pudo confirmar el registro. Comprueba tu conexión e inténtalo nuevamente.', {
      tipo: 'red',
    });
  } finally {
    clearTimeout(temporizador);
  }
}
