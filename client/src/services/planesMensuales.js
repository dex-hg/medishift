import { fechaValida, REVISION, UUID } from '../utils/validacionesProgramacion.js';

const CAMPOS_ERROR = ['codigo', 'nombre', 'tipo', 'idUnidad', 'unidad', 'periodo', 'revision'];
const esObjeto = (valor) => valor !== null && typeof valor === 'object' && !Array.isArray(valor);
const esId = (valor) => typeof valor === 'string' && UUID.test(valor);
const esTexto = (valor) => typeof valor === 'string' && Boolean(valor.trim());
const seleccionar = (valor, campos) => Object.fromEntries(campos.map((campo) => [campo, valor[campo]]));

export class ErrorPlanMensual extends Error {
  constructor(mensaje, { estado = 0, errores = {}, tipo = 'servicio' } = {}) {
    super(mensaje);
    this.name = 'ErrorPlanMensual';
    this.estado = estado;
    this.errores = errores;
    this.tipo = tipo;
  }
}

export const periodoValido = (valor) => typeof valor === 'string'
  && /^(?!0000)\d{4}-(?:0[1-9]|1[0-2])$/.test(valor);

function finDelMes(periodo) {
  const [anio, mes] = periodo.split('-').map(Number);
  const bisiesto = anio % 4 === 0 && (anio % 100 !== 0 || anio % 400 === 0);
  return `${periodo}-${mes === 2 ? (bisiesto ? 29 : 28) : [4, 6, 9, 11].includes(mes) ? 30 : 31}`;
}

function respuestaInvalida() {
  throw new ErrorPlanMensual('El servicio devolvió una respuesta mensual inválida.', { tipo: 'respuesta' });
}

function extraerUnidad(valor) {
  if (!esObjeto(valor) || !esId(valor.id) || !esTexto(valor.codigo) || !esTexto(valor.nombre)
    || !['Departamento', 'Servicio'].includes(valor.tipo) || !['Activo', 'Inactivo'].includes(valor.estado)) respuestaInvalida();
  return seleccionar(valor, ['id', 'codigo', 'nombre', 'tipo', 'estado']);
}

function instanteValido(valor) {
  return esTexto(valor) && /^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(valor)
    && Number.isFinite(Date.parse(valor));
}

function zonaValida(valor) {
  if (!esTexto(valor)) return false;
  try { new Intl.DateTimeFormat('es-PE', { timeZone: valor }); return true; } catch { return false; }
}

function extraerPlan(valor) {
  if (!esObjeto(valor) || !esId(valor.id) || !esId(valor.idUnidad) || !periodoValido(valor.periodo)
    || !fechaValida(valor.fechaInicio) || valor.fechaInicio !== `${valor.periodo}-01`
    || valor.fechaFin !== finDelMes(valor.periodo) || !zonaValida(valor.zonaHoraria)
    || !Number.isInteger(valor.version) || valor.version < 1
    || !['Borrador', 'Pendiente', 'Aprobado', 'Sustituido', 'Cancelado'].includes(valor.estado)
    || !esId(valor.idCreador) || !instanteValido(valor.creadoEn)
    || (valor.idAprobador !== null && !esId(valor.idAprobador))
    || (valor.aprobadoEn !== null && !instanteValido(valor.aprobadoEn))
    || ((valor.idAprobador === null) !== (valor.aprobadoEn === null))
    || typeof valor.revision !== 'string' || !REVISION.test(valor.revision)) respuestaInvalida();
  return seleccionar(valor, ['id', 'idUnidad', 'periodo', 'fechaInicio', 'fechaFin', 'zonaHoraria',
    'version', 'estado', 'idCreador', 'creadoEn', 'idAprobador', 'aprobadoEn', 'revision']);
}

function extraerLista(contenido, campo, convertir) {
  if (!esObjeto(contenido) || !Array.isArray(contenido[campo])) respuestaInvalida();
  const registros = contenido[campo].map(convertir);
  if (new Set(registros.map((registro) => registro.id)).size !== registros.length) respuestaInvalida();
  return registros;
}

function validarPlan(datos) {
  const errores = {};
  if (!esId(datos?.idUnidad)) errores.idUnidad = 'Selecciona una unidad válida.';
  if (!periodoValido(datos?.periodo)) errores.periodo = 'Selecciona un mes válido.';
  if (Object.keys(errores).length) throw new ErrorPlanMensual('Revisa los campos del formulario.', { estado: 400, errores, tipo: 'validacion' });
  return seleccionar(datos, ['idUnidad', 'periodo']);
}

function validarUnidad(datos) {
  const normalizados = Object.fromEntries(['codigo', 'nombre', 'tipo'].map((campo) => [campo,
    typeof datos?.[campo] === 'string' ? datos[campo].trim() : '']));
  const errores = {};
  for (const [campo, limite] of [['codigo', 32], ['nombre', 140]]) {
    if (!normalizados[campo] || [...normalizados[campo]].length > limite
      || /[\u0000-\u001f\u007f]/u.test(normalizados[campo])) errores[campo] = `Ingresa texto válido de hasta ${limite} caracteres.`;
  }
  if (!['Departamento', 'Servicio'].includes(normalizados.tipo)) errores.tipo = 'Selecciona Departamento o Servicio.';
  if (Object.keys(errores).length) throw new ErrorPlanMensual('Revisa los campos de la unidad.', { estado: 400, errores, tipo: 'validacion' });
  return normalizados;
}

async function solicitar(ruta, metodo, convertir, {
  datos, senal, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000,
} = {}) {
  if (typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0
    || (senal !== undefined && !(senal instanceof AbortSignal))) {
    throw new ErrorPlanMensual('No se pudo iniciar la solicitud.', { tipo: 'configuracion' });
  }
  const controlador = new AbortController();
  let temporizador;
  let rechazarCancelacion;
  const cancelacion = new Promise((resolver, rechazar) => { rechazarCancelacion = rechazar; });
  const cancelar = () => {
    controlador.abort();
    rechazarCancelacion(new ErrorPlanMensual('La solicitud se canceló.', { tipo: 'cancelado' }));
  };
  senal?.addEventListener('abort', cancelar, { once: true });
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      controlador.abort();
      rechazar(new ErrorPlanMensual('La solicitud tardó demasiado. Recarga para comprobar si se guardó antes de reintentar.', { tipo: 'tiempo' }));
    }, tiempoEspera);
  });
  try {
    if (senal?.aborted) cancelar();
    const operacion = async () => {
      if (controlador.signal.aborted) throw new ErrorPlanMensual('La solicitud se canceló.', { tipo: 'cancelado' });
      const respuesta = await ejecutarSolicitud(ruta, {
        method: metodo, credentials: 'same-origin', cache: 'no-store', signal: controlador.signal,
        headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json' } : {}) },
        ...(datos ? { body: JSON.stringify(datos) } : {}),
      });
      let contenido;
      try { contenido = await respuesta.json(); } catch { contenido = null; }
      if (respuesta.status !== (metodo === 'POST' ? 201 : 200)) {
        const errores = esObjeto(contenido?.errores) ? Object.fromEntries(CAMPOS_ERROR
          .filter((campo) => esTexto(contenido.errores[campo])).map((campo) => [campo, contenido.errores[campo]])) : {};
        const mensaje = respuesta.status === 401 ? 'Tu sesión terminó. Inicia sesión nuevamente.'
          : respuesta.status === 403 ? 'Solo un administrador puede acceder a la planificación mensual.'
            : esTexto(contenido?.mensaje) ? contenido.mensaje : 'No se pudo completar la solicitud mensual.';
        throw new ErrorPlanMensual(mensaje, { estado: respuesta.status, errores });
      }
      return convertir(contenido);
    };
    return await Promise.race([operacion(), vencimiento, cancelacion]);
  } catch (error) {
    if (error instanceof ErrorPlanMensual) throw error;
    throw new ErrorPlanMensual('No se pudo contactar al servicio. Comprueba tu conexión.', { tipo: 'red' });
  } finally {
    clearTimeout(temporizador);
    senal?.removeEventListener('abort', cancelar);
  }
}

export const listarUnidades = (opciones) => solicitar('/api/unidades-planificacion', 'GET', (contenido) => {
  if (!zonaValida(contenido?.zonaHoraria)) respuestaInvalida();
  return { zonaHoraria: contenido.zonaHoraria, unidades: extraerLista(contenido, 'unidades', extraerUnidad) };
}, opciones);
export const crearUnidad = (datos, opciones) => solicitar('/api/unidades-planificacion', 'POST', (contenido) => {
  const unidad = extraerUnidad(contenido);
  if (unidad.estado !== 'Activo') respuestaInvalida();
  return unidad;
}, { ...opciones, datos: validarUnidad(datos) });
export const listarPlanes = (datos, opciones) => {
  const { idUnidad, periodo } = validarPlan(datos);
  return solicitar(`/api/planes-mensuales?unidad=${encodeURIComponent(idUnidad)}&periodo=${encodeURIComponent(periodo)}`,
    'GET', (contenido) => {
      const planes = extraerLista(contenido, 'planes', extraerPlan);
      if (planes.some((plan) => plan.idUnidad !== idUnidad || plan.periodo !== periodo)
        || new Set(planes.map((plan) => plan.version)).size !== planes.length) respuestaInvalida();
      return planes.sort((primero, segundo) => segundo.version - primero.version);
    }, opciones);
};
export const crearPlan = (datos, opciones) => {
  const entrada = validarPlan(datos);
  return solicitar('/api/planes-mensuales', 'POST', (contenido) => {
    const plan = extraerPlan(contenido);
    if (plan.idUnidad !== entrada.idUnidad || plan.periodo !== entrada.periodo || plan.estado !== 'Borrador') respuestaInvalida();
    return plan;
  }, { ...opciones, datos: entrada });
};
export const obtenerPlan = (id, opciones) => {
  if (!esId(id)) throw new ErrorPlanMensual('El identificador no es válido.', { estado: 404, tipo: 'validacion' });
  return solicitar(`/api/planes-mensuales/${id}`, 'GET', (contenido) => {
    const plan = extraerPlan(contenido);
    if (plan.id !== id) respuestaInvalida();
    return plan;
  }, opciones);
};
