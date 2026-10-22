import {
  fechaValida, horaValida, lunesDeSemana, normalizarTurno,
  REVISION, sumarDias, UUID, validarTurno,
} from '../utils/validacionesProgramacion.js';

export class ErrorProgramacion extends Error {
  constructor(mensaje, { estado = 0, errores = {}, tipo = 'servicio' } = {}) {
    super(mensaje);
    this.name = 'ErrorProgramacion';
    this.estado = estado;
    this.errores = errores;
    this.tipo = tipo;
  }
}

const CAMPOS_ERROR = ['idProfesional', 'horaInicio', 'horaFin', 'fechaInicio', 'fechaFin', 'estado',
  'observacion', 'idConsultorio', 'idEspecialidad', 'fecha', 'revision', 'idHorario',
  'cobertura', 'hora', 'zonaHoraria', 'turnos'];
const esObjeto = (valor) => valor !== null && typeof valor === 'object' && !Array.isArray(valor);
const esId = (valor) => typeof valor === 'string' && UUID.test(valor);
const esRevision = (valor) => typeof valor === 'string' && REVISION.test(valor);
const texto = (valor, vacio = false) => typeof valor === 'string' && (vacio || Boolean(valor.trim()));
const seleccionar = (valor, campos) => Object.fromEntries(campos.map((campo) => [campo, valor[campo]]));
const zonaValida = (valor) => {
  if (!texto(valor)) return false;
  try { new Intl.DateTimeFormat('es-PE', { timeZone: valor }); return true; } catch { return false; }
};

function respuestaInvalida() {
  throw new ErrorProgramacion('El servicio devolvió una respuesta de programación inválida.', { tipo: 'respuesta' });
}

function extraerTurno(valor) {
  const campos = ['id', 'idProfesional', 'profesional', 'idConsultorio', 'consultorio', 'idEspecialidad', 'especialidad',
    'fecha', 'horaInicio', 'horaFin', 'estado', 'observacion', 'revision'];
  if (!esObjeto(valor) || !['id', 'idProfesional', 'idConsultorio', 'idEspecialidad'].every((campo) => esId(valor[campo]))
    || !['profesional', 'consultorio', 'especialidad'].every((campo) => texto(valor[campo]))
    || !['Borrador', 'Aprobado', 'Cancelado', 'Sustituido', 'Pendiente'].includes(valor.estado)
    || !fechaValida(valor.fecha) || !horaValida(valor.horaInicio) || !horaValida(valor.horaFin)
    || valor.horaFin <= valor.horaInicio || !texto(valor.observacion, true) || !esRevision(valor.revision)
    || (valor.zonaHoraria !== undefined && !zonaValida(valor.zonaHoraria))) respuestaInvalida();
  return { ...seleccionar(valor, campos), ...(texto(valor.zonaHoraria) ? { zonaHoraria: valor.zonaHoraria } : {}) };
}

function extraerLista(valor, convertir) {
  if (!Array.isArray(valor)) respuestaInvalida();
  const registros = valor.map(convertir);
  if (new Set(registros.map((registro) => registro.id)).size !== registros.length) respuestaInvalida();
  return registros;
}

function extraerSemana(valor) {
  if (!esObjeto(valor) || !fechaValida(valor.fechaInicio) || lunesDeSemana(valor.fechaInicio) !== valor.fechaInicio
    || valor.fechaFin !== sumarDias(valor.fechaInicio, 6) || !zonaValida(valor.zonaHoraria)
    || (valor.idHorario !== null && !esId(valor.idHorario))
    || !Number.isInteger(valor.version) || valor.version < 0
    || !['Sin horario', 'Borrador', 'Aprobado', 'Cancelado', 'Pendiente', 'Sustituido'].includes(valor.estado) || !esRevision(valor.revision)
    || (valor.idHorario === null ? valor.estado !== 'Sin horario' || valor.version !== 0 : valor.estado === 'Sin horario' || valor.version < 1)) respuestaInvalida();
  const turnos = extraerLista(valor.turnos, extraerTurno);
  if (turnos.some((turno) => turno.fecha < valor.fechaInicio || turno.fecha > valor.fechaFin)) respuestaInvalida();
  const versionAprobada = valor.versionAprobada ?? null;
  if (versionAprobada !== null && (!Number.isInteger(versionAprobada) || versionAprobada < 1)) respuestaInvalida();
  return { ...seleccionar(valor, ['fechaInicio', 'fechaFin', 'zonaHoraria', 'idHorario', 'version', 'estado', 'revision']),
    versionAprobada, turnos };
}

function extraerRecursos(valor) {
  if (!esObjeto(valor) || !zonaValida(valor.zonaHoraria)) respuestaInvalida();
  const especialidad = (registro) => {
    if (!esObjeto(registro) || !esId(registro.id) || !texto(registro.nombre)) respuestaInvalida();
    return seleccionar(registro, ['id', 'nombre']);
  };
  const convertir = (registro, consultorio) => {
    if (!esObjeto(registro) || !esId(registro.id) || !texto(registro.nombre) || !['Activo', 'Inactivo'].includes(registro.estado)
      || (consultorio && (!texto(registro.codigo) || typeof registro.usoGeneral !== 'boolean'))) respuestaInvalida();
    return { ...seleccionar(registro, consultorio ? ['id', 'nombre', 'codigo', 'estado', 'usoGeneral'] : ['id', 'nombre', 'estado']),
      especialidades: extraerLista(registro.especialidades, especialidad) };
  };
  return { zonaHoraria: valor.zonaHoraria,
    profesionales: extraerLista(valor.profesionales, (registro) => convertir(registro, false)),
    consultorios: extraerLista(valor.consultorios, (registro) => convertir(registro, true)) };
}

async function solicitar(ruta, metodo, convertir, {
  datos, revision, senal, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000,
} = {}) {
  if (typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0
    || (senal !== undefined && !(senal instanceof AbortSignal))) {
    throw new ErrorProgramacion('No se pudo iniciar la solicitud.', { tipo: 'configuracion' });
  }
  if (revision !== undefined && !esRevision(revision)) {
    throw new ErrorProgramacion('Recarga el registro antes de modificarlo.', { estado: 409, tipo: 'validacion' });
  }
  const controlador = new AbortController();
  let temporizador;
  let rechazarCancelacion;
  const cancelacion = new Promise((resolver, rechazar) => { rechazarCancelacion = rechazar; });
  const cancelar = () => {
    controlador.abort();
    rechazarCancelacion(new ErrorProgramacion('La solicitud se canceló.', { tipo: 'cancelado' }));
  };
  senal?.addEventListener('abort', cancelar, { once: true });
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      controlador.abort();
      rechazar(new ErrorProgramacion('La solicitud tardó demasiado. Comprueba el estado del registro antes de reintentar.', { tipo: 'tiempo' }));
    }, tiempoEspera);
  });
  try {
    if (senal?.aborted) cancelar();
    const operacion = async () => {
      if (controlador.signal.aborted) throw new ErrorProgramacion('La solicitud se canceló.', { tipo: 'cancelado' });
      const respuesta = await ejecutarSolicitud(ruta, {
        method: metodo, credentials: 'same-origin', cache: 'no-store', signal: controlador.signal,
        headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json' } : {}),
          ...(revision ? { 'If-Match': `"${revision}"` } : {}) },
        ...(datos ? { body: JSON.stringify(datos) } : {}),
      });
      let contenido = null;
      if (respuesta.status !== 204) { try { contenido = await respuesta.json(); } catch { contenido = null; } }
      const esperado = metodo === 'DELETE' ? 204 : metodo === 'POST' && !ruta.match(/\/(aprobar|reabrir|cancelar)$/) ? 201 : 200;
      if (respuesta.status !== esperado) {
        const errores = esObjeto(contenido?.errores) ? Object.fromEntries(CAMPOS_ERROR.filter((campo) => texto(contenido.errores[campo]))
          .map((campo) => [campo, contenido.errores[campo]])) : {};
        const mensaje = respuesta.status === 401 ? 'Tu sesión terminó. Inicia sesión nuevamente.'
          : texto(contenido?.mensaje) ? contenido.mensaje : 'No se pudo completar la solicitud de programación.';
        throw new ErrorProgramacion(mensaje, { estado: respuesta.status, errores });
      }
      if (metodo === 'DELETE') return undefined;
      return convertir(contenido);
    };
    return await Promise.race([operacion(), vencimiento, cancelacion]);
  } catch (error) {
    if (error instanceof ErrorProgramacion) throw error;
    throw new ErrorProgramacion('No se pudo contactar al servicio. Comprueba tu conexión.', { tipo: 'red' });
  } finally {
    clearTimeout(temporizador);
    senal?.removeEventListener('abort', cancelar);
  }
}

function rutaTurno(id) {
  if (!esId(id)) throw new ErrorProgramacion('El identificador no es válido.', { estado: 404, tipo: 'validacion' });
  return `/api/horarios/${id}`;
}

async function guardarTurno(metodo, datos, opciones = {}) {
  const errores = validarTurno(datos);
  if (Object.keys(errores).length) throw new ErrorProgramacion('Revisa los campos del formulario.', { errores, tipo: 'validacion' });
  if (metodo === 'PUT' && !esRevision(opciones.revision)) {
    throw new ErrorProgramacion('Recarga el registro antes de modificarlo.', { estado: 409, tipo: 'validacion' });
  }
  return solicitar(metodo === 'PUT' ? rutaTurno(opciones.id) : '/api/horarios', metodo,
    extraerTurno, { ...opciones, datos: normalizarTurno(datos) });
}

export function eliminarTurno(id, opciones = {}) {
  if (!esRevision(opciones.revision)) throw new ErrorProgramacion('Recarga el registro antes de eliminarlo.', { estado: 409 });
  return solicitar(rutaTurno(id), 'DELETE', undefined, opciones);
}

export const listarRecursosHorario = (opciones) => solicitar('/api/horarios/recursos', 'GET', extraerRecursos, opciones);
export const obtenerTurno = (id, opciones) => solicitar(rutaTurno(id), 'GET', extraerTurno, opciones);
export const crearTurno = (datos, opciones) => guardarTurno('POST', datos, opciones);
export const actualizarTurno = (id, datos, opciones) => guardarTurno('PUT', datos, { ...opciones, id });
export const listarSemanaActual = (opciones) => solicitar('/api/horarios', 'GET', extraerSemana, opciones);
export const listarSemana = (fechaInicio, opciones) => {
  if (!fechaValida(fechaInicio) || lunesDeSemana(fechaInicio) !== fechaInicio) {
    throw new ErrorProgramacion('Selecciona el lunes de la semana.', { estado: 400, tipo: 'validacion' });
  }
  return solicitar(`/api/horarios?fechaInicio=${encodeURIComponent(fechaInicio)}`, 'GET', extraerSemana, opciones);
};

function gestionarSemana(accion, semana, opciones) {
  if (!esObjeto(semana) || !esId(semana.idHorario) || !esRevision(semana.revision)
    || !fechaValida(semana.fechaInicio) || lunesDeSemana(semana.fechaInicio) !== semana.fechaInicio) {
    throw new ErrorProgramacion('Recarga el horario antes de continuar.', { estado: 409, tipo: 'validacion' });
  }
  return solicitar(`/api/horarios/${accion}`, 'POST', extraerSemana, { ...opciones,
    datos: seleccionar(semana, ['fechaInicio', 'idHorario', 'revision']) });
}

export const aprobarSemana = (semana, opciones) => gestionarSemana('aprobar', semana, opciones);
export const reabrirSemana = (semana, opciones) => gestionarSemana('reabrir', semana, opciones);
export const cancelarSemana = (semana, opciones) => gestionarSemana('cancelar', semana, opciones);
