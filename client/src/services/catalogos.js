import {
  normalizarConsultorio, normalizarProfesional, validarConsultorio, validarProfesional,
  normalizarEspecialidad, validarEspecialidad,
} from '../utils/validacionesCatalogos.js';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const REVISION = /^[a-f0-9]{64}$/;
const CAMPOS = {
  profesionales: ['id', 'nombres', 'apellidos', 'colegiatura', 'correo', 'telefono', 'categoria', 'estado', 'especialidad'],
  consultorios: ['id', 'codigo', 'nombre', 'ubicacion', 'especialidad', 'estado'],
  especialidades: ['id', 'nombre'],
  etiquetas: ['id', 'nombre'],
};
const MENSAJES = {
  400: 'Revisa los datos ingresados.',
  401: 'Tu sesión expiró. Inicia sesión para continuar.',
  403: 'No tienes permiso para realizar esta operación.',
  404: 'Este registro ya no existe o no pertenece a tu institución.',
  409: 'El registro tiene datos duplicados o está relacionado con otros registros.',
  500: 'No se pudo completar la solicitud. Inténtalo nuevamente.',
  503: 'El servicio no está disponible. Inténtalo más tarde.',
};

export class ErrorCatalogo extends Error {
  constructor(mensaje, { estado = 0, errores = {}, tipo = 'servicio' } = {}) {
    super(mensaje);
    this.name = 'ErrorCatalogo';
    this.estado = estado;
    this.errores = errores;
    this.tipo = tipo;
  }
}

function esObjeto(valor) {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}

function validarIdentificador(id) {
  if (typeof id !== 'string' || !UUID.test(id)) {
    throw new ErrorCatalogo('El identificador del registro no es válido.', { estado: 404, tipo: 'validacion' });
  }
}

function extraerAreaConsultorio(area) {
  if (area === null) return null;
  if (!esObjeto(area) || !UUID.test(area.id) || typeof area.nombre !== 'string' || !area.nombre.trim()
    || (area.idPadre === null ? area.nombrePadre !== null : !UUID.test(area.idPadre)
      || typeof area.nombrePadre !== 'string' || !area.nombrePadre.trim())) {
    throw new ErrorCatalogo('El servicio devolvió un área inválida.', { tipo: 'respuesta' });
  }
  return { id: area.id, nombre: area.nombre, idPadre: area.idPadre, nombrePadre: area.nombrePadre,
    etiquetas: extraerContenido(area.etiquetas, 'especialidades', true) };
}

function extraerRegistro(contenido, recurso) {
  const campos = CAMPOS[recurso];
  const opcionales = recurso === 'profesionales' ? ['telefono', ...(Array.isArray(contenido?.etiquetas) ? ['especialidad'] : [])]
    : recurso === 'consultorios' ? ['especialidad'] : [];
  if (!esObjeto(contenido) || !UUID.test(contenido.id) || !campos.every((campo) =>
    typeof contenido[campo] === 'string' && (opcionales.includes(campo) || contenido[campo].trim()),
  ) || (['profesionales', 'consultorios'].includes(recurso) && !['Activo', 'Inactivo'].includes(contenido.estado))) {
    throw new ErrorCatalogo('El servicio devolvió una respuesta inválida.', { tipo: 'respuesta' });
  }
  const registro = Object.fromEntries(campos.map((campo) => [campo, contenido[campo]]));
  if (recurso === 'etiquetas') {
    if (typeof contenido.activa !== 'boolean' || !REVISION.test(contenido.revision)) {
      throw new ErrorCatalogo('El servicio devolvió una etiqueta inválida.', { tipo: 'respuesta' });
    }
    return { ...registro, activa: contenido.activa, revision: contenido.revision };
  }
  if (contenido.etiquetas !== undefined && ['profesionales', 'consultorios'].includes(recurso)) {
    if (!REVISION.test(contenido.revision) || (recurso === 'consultorios' && typeof contenido.usoGeneral !== 'boolean')) {
      throw new ErrorCatalogo('El servicio devolvió un catálogo inválido.', { tipo: 'respuesta' });
    }
    return { ...registro, etiquetas: extraerContenido(contenido.etiquetas, 'especialidades', true), revision: contenido.revision,
      ...(recurso === 'consultorios' ? { usoGeneral: contenido.usoGeneral,
        ...(contenido.area === undefined ? {} : { area: extraerAreaConsultorio(contenido.area) }) } : {}) };
  }
  return registro;
}

function extraerContenido(contenido, recurso, listado) {
  if (!listado) return extraerRegistro(contenido, recurso);
  if (!Array.isArray(contenido)) {
    throw new ErrorCatalogo('El servicio devolvió una lista inválida.', { tipo: 'respuesta' });
  }
  const registros = contenido.map((registro) => extraerRegistro(registro, recurso));
  if (new Set(registros.map((registro) => registro.id)).size !== registros.length) {
    throw new ErrorCatalogo('El servicio devolvió registros duplicados.', { tipo: 'respuesta' });
  }
  return registros;
}

async function solicitarCatalogo(recurso, metodo, {
  id, datos, revision, senal, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000,
} = {}) {
  if (typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0
    || (senal !== undefined && !(senal instanceof AbortSignal))) {
    throw new ErrorCatalogo('No se pudo iniciar la solicitud.', { tipo: 'configuracion' });
  }
  if (id !== undefined) validarIdentificador(id);
  if (revision !== undefined && !REVISION.test(revision)) {
    throw new ErrorCatalogo('Recarga el registro antes de editarlo.', { tipo: 'validacion', estado: 409 });
  }
  const controlador = new AbortController();
  const cancelar = () => controlador.abort();
  let temporizador;
  let rechazarCancelacion;
  const cancelacion = new Promise((resolver, rechazar) => { rechazarCancelacion = rechazar; });
  const alCancelar = () => {
    rechazarCancelacion(new ErrorCatalogo('La solicitud se canceló.', { tipo: 'cancelado' }));
    cancelar();
  };
  senal?.addEventListener('abort', alCancelar, { once: true });
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      rechazar(new ErrorCatalogo('La solicitud tardó demasiado. Inténtalo nuevamente.', { tipo: 'tiempo' }));
      cancelar();
    }, tiempoEspera);
  });

  try {
    if (senal?.aborted) alCancelar();
    const solicitud = async () => {
      if (controlador.signal.aborted) throw new ErrorCatalogo('La solicitud se canceló.', { tipo: 'cancelado' });
      const respuesta = await ejecutarSolicitud(`/api/${recurso}${id ? `/${id}` : ''}`, {
        method: metodo, credentials: 'same-origin', cache: 'no-store', signal: controlador.signal,
        headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json' } : {}),
          ...(revision ? { 'If-Match': `"${revision}"` } : {}) },
        ...(datos ? { body: JSON.stringify(datos) } : {}),
      });
      let contenido = null;
      if (respuesta.status !== 204) {
        try { contenido = await respuesta.json(); } catch { contenido = null; }
      }
      const estadoEsperado = metodo === 'POST' ? 201 : metodo === 'DELETE' ? 204 : 200;
      if (respuesta.status !== estadoEsperado) {
        const camposAcceso = recurso === 'profesionales' ? ['correoAcceso', 'contrasenaAcceso'] : [];
        const errores = esObjeto(contenido?.errores) ? Object.fromEntries([...CAMPOS[recurso], ...camposAcceso, 'idsEtiquetas', 'activa', 'revision']
          .filter((campo) => campo !== 'id' && typeof contenido.errores[campo] === 'string')
          .map((campo) => [campo, contenido.errores[campo]])) : {};
        const mensaje = respuesta.status === 401 ? MENSAJES[401]
          : esObjeto(contenido) && typeof contenido.mensaje === 'string' && contenido.mensaje.trim()
            ? contenido.mensaje : MENSAJES[respuesta.status] || 'El servicio devolvió una respuesta inesperada.';
        throw new ErrorCatalogo(mensaje, { estado: respuesta.status, errores });
      }
      if (metodo === 'DELETE') return undefined;
      try { return extraerContenido(contenido, recurso, metodo === 'GET' && !id); } catch (error) {
        error.estado = respuesta.status;
        throw error;
      }
    };
    return await Promise.race([solicitud(), vencimiento, cancelacion]);
  } catch (error) {
    if (error instanceof ErrorCatalogo) throw error;
    if (senal?.aborted) throw new ErrorCatalogo('La solicitud se canceló.', { tipo: 'cancelado' });
    throw new ErrorCatalogo('No se pudo contactar al servicio. Comprueba tu conexión e inténtalo nuevamente.', {
      tipo: 'red',
    });
  } finally {
    clearTimeout(temporizador);
    senal?.removeEventListener('abort', alCancelar);
  }
}

async function guardarRegistro(recurso, metodo, datos, opciones) {
  const normalizados = recurso === 'profesionales' ? normalizarProfesional(datos) : normalizarConsultorio(datos);
  const errores = recurso === 'profesionales' ? validarProfesional(datos) : validarConsultorio(datos);
  if (Object.keys(errores).length) {
    throw new ErrorCatalogo('Revisa los datos ingresados.', { errores, tipo: 'validacion' });
  }
  return solicitarCatalogo(recurso, metodo, { ...opciones, datos: normalizados });
}

export const obtenerProfesionales = (opciones) => solicitarCatalogo('profesionales', 'GET', opciones);
export const obtenerConsultorios = (opciones) => solicitarCatalogo('consultorios', 'GET', opciones);
export const obtenerEspecialidades = (opciones) => solicitarCatalogo('especialidades', 'GET', opciones);
export function crearEspecialidad(datos, opciones) {
  const errores = validarEspecialidad(datos);
  if (Object.keys(errores).length) return Promise.reject(new ErrorCatalogo('Revisa el nombre de la especialidad.', { errores, tipo: 'validacion' }));
  return solicitarCatalogo('especialidades', 'POST', { ...opciones, datos: normalizarEspecialidad(datos) });
}
export const obtenerEtiquetas = (opciones) => solicitarCatalogo('etiquetas', 'GET', opciones);

export function crearEtiqueta(datos, opciones) {
  return guardarEtiqueta('POST', datos, opciones);
}

export function actualizarEtiqueta(id, datos, opciones) {
  return guardarEtiqueta('PUT', datos, { ...opciones, id: id ?? '' });
}

function guardarEtiqueta(metodo, datos, opciones) {
  const errores = validarEspecialidad(datos);
  if (metodo === 'PUT' && typeof datos?.activa !== 'boolean') errores.activa = 'Indica el estado de la etiqueta.';
  if (Object.keys(errores).length) return Promise.reject(new ErrorCatalogo('Revisa la etiqueta.', { errores, tipo: 'validacion' }));
  return solicitarCatalogo('etiquetas', metodo, { ...opciones, datos: {
    ...normalizarEspecialidad(datos), ...(metodo === 'PUT' ? { activa: datos.activa } : {}),
  } });
}
export const obtenerProfesional = (id, opciones) => solicitarCatalogo('profesionales', 'GET', { ...opciones, id: id ?? '' });
export const obtenerConsultorio = (id, opciones) => solicitarCatalogo('consultorios', 'GET', { ...opciones, id: id ?? '' });
export const crearProfesional = (datos, opciones) => guardarRegistro('profesionales', 'POST', datos, opciones);
export const crearConsultorio = (datos, opciones) => guardarRegistro('consultorios', 'POST', datos, opciones);
export const actualizarProfesional = (id, datos, opciones) => guardarRegistro('profesionales', 'PUT', datos, { ...opciones, id: id ?? '' });
export const actualizarConsultorio = (id, datos, opciones) => guardarRegistro('consultorios', 'PUT', datos, { ...opciones, id: id ?? '' });
export const eliminarProfesional = (id, opciones) => solicitarCatalogo('profesionales', 'DELETE', { ...opciones, id: id ?? '' });
export const eliminarConsultorio = (id, opciones) => solicitarCatalogo('consultorios', 'DELETE', { ...opciones, id: id ?? '' });
