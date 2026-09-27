import { normalizarDatosInicioSesion, validarInicioSesion } from '../utils/validacionesInicioSesion.js';

const CAMPOS_SOLICITUD = ['codigoInstitucion', 'correo', 'contrasena'];
const CAMPOS_CUENTA = ['idCuenta', 'idInstitucion', 'codigoInstitucion', 'nombreInstitucion', 'correo'];
const MENSAJES_HTTP = {
  400: 'Revisa los datos de acceso.',
  401: 'El código, el correo o la contraseña no son correctos.',
  503: 'El servicio no está disponible. Inténtalo más tarde.',
  500: 'No se pudo completar la solicitud. Inténtalo nuevamente.',
};

export class ErrorSesion extends Error {
  constructor(mensaje, { estado = 0, errores = {}, tipo = 'servicio' } = {}) {
    super(mensaje);
    this.name = 'ErrorSesion';
    this.estado = estado;
    this.errores = errores;
    this.tipo = tipo;
  }
}

function esObjeto(valor) {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}

function extraerErrores(contenido) {
  if (!esObjeto(contenido?.errores)) return {};
  return Object.fromEntries(CAMPOS_SOLICITUD
    .filter((campo) => typeof contenido.errores[campo] === 'string' && contenido.errores[campo].trim())
    .map((campo) => [campo, contenido.errores[campo]]));
}

function extraerCuenta(contenido) {
  if (!esObjeto(contenido) || !CAMPOS_CUENTA.every(
    (campo) => typeof contenido[campo] === 'string' && contenido[campo].trim(),
  )) {
    throw new ErrorSesion('El servicio devolvió una respuesta inválida.', { tipo: 'respuesta' });
  }
  return Object.fromEntries(CAMPOS_CUENTA.map((campo) => [campo, contenido[campo]]));
}

async function solicitarSesion(metodo, { datos, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000 } = {}) {
  if (typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0) {
    throw new ErrorSesion('No se pudo iniciar la solicitud.', { tipo: 'configuracion' });
  }
  const controlador = new AbortController();
  let temporizador;
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      rechazar(new ErrorSesion('La solicitud tardó demasiado. Inténtalo nuevamente.', { tipo: 'tiempo' }));
      controlador.abort();
    }, tiempoEspera);
  });

  try {
    const solicitud = async () => {
      const respuesta = await ejecutarSolicitud('/api/sesion', {
        method: metodo,
        credentials: 'same-origin',
        cache: 'no-store',
        headers: datos ? { 'Content-Type': 'application/json', Accept: 'application/json' }
          : { Accept: 'application/json' },
        ...(datos ? { body: JSON.stringify(datos) } : {}),
        signal: controlador.signal,
      });
      let contenido = null;
      if (respuesta.status !== 204) {
        try { contenido = await respuesta.json(); } catch { contenido = null; }
      }
      const estadoEsperado = metodo === 'POST' || metodo === 'GET' ? 200 : 204;
      if (respuesta.status !== estadoEsperado) {
        const mensaje = respuesta.status === 401 && metodo === 'POST'
          ? MENSAJES_HTTP[401]
          : esObjeto(contenido) && typeof contenido.mensaje === 'string' && contenido.mensaje.trim()
            ? contenido.mensaje : MENSAJES_HTTP[respuesta.status]
              || 'El servicio devolvió una respuesta inesperada.';
        const errores = respuesta.status === 401 ? {} : extraerErrores(contenido);
        throw new ErrorSesion(mensaje, { estado: respuesta.status, errores });
      }
      if (metodo === 'DELETE') return undefined;
      try { return extraerCuenta(contenido); } catch (error) {
        error.estado = respuesta.status;
        throw error;
      }
    };
    return await Promise.race([solicitud(), vencimiento]);
  } catch (error) {
    if (error instanceof ErrorSesion) throw error;
    throw new ErrorSesion('No se pudo contactar al servicio. Comprueba tu conexión e inténtalo nuevamente.', {
      tipo: 'red',
    });
  } finally {
    clearTimeout(temporizador);
  }
}

export async function iniciarSesion(datos, opciones = {}) {
  const datosNormalizados = normalizarDatosInicioSesion(datos);
  const errores = validarInicioSesion(datosNormalizados);
  if (Object.keys(errores).length) {
    throw new ErrorSesion('Revisa los datos de acceso.', { errores, tipo: 'validacion' });
  }
  return solicitarSesion('POST', {
    ...opciones,
    datos: Object.fromEntries(CAMPOS_SOLICITUD.map((campo) => [campo, datosNormalizados[campo]])),
  });
}

export function obtenerSesion(opciones = {}) {
  return solicitarSesion('GET', opciones);
}

export function cerrarSesion(opciones = {}) {
  return solicitarSesion('DELETE', opciones);
}
