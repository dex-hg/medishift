import { REVISION, UUID, fechaValida } from '../utils/validacionesProgramacion.js';
import { normalizarPaciente, validarPaciente, normalizarCita, validarCita, etiquetasEstadoCita } from '../utils/validacionesAtencion.js';
import { periodoValido } from './planesMensuales.js';

export class ErrorAtencion extends Error {
  constructor(mensaje, { estado = 0, errores = {} } = {}) {
    super(mensaje); this.name = 'ErrorAtencion'; this.estado = estado; this.errores = errores;
  }
}

const objeto = (valor) => valor !== null && typeof valor === 'object' && !Array.isArray(valor);
const texto = (valor) => typeof valor === 'string' && valor.trim().length > 0;
const instante = (valor) => typeof valor === 'string' && Number.isFinite(Date.parse(valor));
const invalida = () => { throw new ErrorAtencion('La respuesta del servidor no es válida. Recarga los datos.'); };
const idValido = (id) => { if (!UUID.test(id)) throw new ErrorAtencion('El identificador no es válido.'); return id; };

export function esResumenPaciente(valor) {
  return objeto(valor) && UUID.test(valor.id) && texto(valor.codigo) && texto(valor.nombres) && texto(valor.apellidos)
    && /^[0-9]{8}$/.test(valor.dni) && typeof valor.activo === 'boolean' && typeof valor.puedeConsultar === 'boolean';
}
export function extraerPaciente(valor) {
  if (!objeto(valor) || !UUID.test(valor.id) || !texto(valor.codigo) || !REVISION.test(valor.revision)
    || Object.keys(validarPaciente(valor)).length) invalida();
  return valor;
}
export function esCita(valor) {
  return objeto(valor) && ['id', 'idPaciente', 'idTurno', 'idProfesional', 'idConsultorio'].every((campo) => UUID.test(valor[campo]))
    && ['paciente', 'codigoPaciente', 'profesional', 'consultorio'].every((campo) => texto(valor[campo]))
    && instante(valor.inicio) && instante(valor.fin) && Date.parse(valor.fin) > Date.parse(valor.inicio)
    && Object.hasOwn(etiquetasEstadoCita, valor.estado) && REVISION.test(valor.revision);
}
export function extraerAgenda(valor) {
  if (!objeto(valor) || !fechaValida(valor.fecha) || !Array.isArray(valor.citas) || !valor.citas.every(esCita)
    || !Array.isArray(valor.turnos) || !valor.turnos.every((turno) => objeto(turno) && UUID.test(turno.id)
      && UUID.test(turno.idProfesional) && UUID.test(turno.idConsultorio) && texto(turno.profesional) && texto(turno.consultorio)
      && texto(turno.especialidad) && instante(turno.inicio) && instante(turno.fin) && Date.parse(turno.fin) > Date.parse(turno.inicio))) invalida();
  try { new Intl.DateTimeFormat('es-PE', { timeZone: valor.zonaHoraria }); } catch { invalida(); }
  if (!texto(valor.zonaHoraria)) invalida();
  return valor;
}
const extraerCita = (valor) => { if (!esCita(valor)) invalida(); return valor; };
const extraerListado = (valor) => {
  if (!objeto(valor) || !Array.isArray(valor.pacientes) || !valor.pacientes.every(esResumenPaciente)
    || !Number.isSafeInteger(valor.total) || valor.total < 0 || !Number.isInteger(valor.pagina) || valor.pagina < 0) invalida();
  return valor;
};

export async function solicitarAtencion(ruta, metodo, convertir, { datos, revision, senal, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000 } = {}) {
  if (!/^\/api\/(?:pacientes|citas|planes-mensuales)(?:\/|\?|$)/.test(ruta) || typeof ejecutarSolicitud !== 'function'
    || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0 || (senal !== undefined && !(senal instanceof AbortSignal))) throw new ErrorAtencion('No se pudo iniciar la solicitud.');
  if (revision !== undefined && !REVISION.test(revision)) throw new ErrorAtencion('Recarga el registro antes de guardar.', { estado: 428 });
  const controlador = new AbortController();
  const cancelar = () => controlador.abort();
  senal?.addEventListener('abort', cancelar, { once: true });
  if (senal?.aborted) controlador.abort();
  let temporizador;
  const vencimiento = new Promise((resolver, rechazar) => { temporizador = setTimeout(() => {
    rechazar(new ErrorAtencion('La solicitud tardó demasiado. Recarga para comprobar si se guardó antes de repetirla.'));
    controlador.abort();
  }, tiempoEspera); });
  try {
    const operacion = async () => {
      const respuesta = await ejecutarSolicitud(ruta, { method: metodo, credentials: 'same-origin', cache: 'no-store',
        signal: controlador.signal, headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json' } : {}),
          ...(revision ? { 'If-Match': `"${revision}"` } : {}) }, ...(datos ? { body: JSON.stringify(datos) } : {}) });
      let contenido;
      try { contenido = await respuesta.json(); } catch { contenido = null; }
      const esperado = metodo === 'POST' && ['/api/pacientes', '/api/citas'].includes(ruta) ? 201 : 200;
      if (respuesta.status !== esperado) throw new ErrorAtencion(texto(contenido?.mensaje) ? contenido.mensaje : 'No se pudo completar la solicitud.',
        { estado: respuesta.status, errores: objeto(contenido?.errores) ? Object.fromEntries(Object.entries(contenido.errores).filter(([, mensaje]) => typeof mensaje === 'string')) : {} });
      return convertir(contenido);
    };
    return await Promise.race([operacion(), vencimiento]);
  } catch (fallo) {
    if (fallo instanceof ErrorAtencion || fallo.name === 'AbortError') throw fallo;
    throw new ErrorAtencion('No se pudo contactar al servidor. Comprueba tu conexión.');
  } finally { clearTimeout(temporizador); senal?.removeEventListener('abort', cancelar); }
}

export const listarPacientes = (buscar, pagina, opciones) => solicitarAtencion(`/api/pacientes?buscar=${encodeURIComponent(buscar)}&pagina=${pagina}`, 'GET', extraerListado, opciones);
export const buscarPacienteDni = (dni, opciones) => {
  if (!/^[0-9]{8}$/.test(dni)) throw new ErrorAtencion('Ingresa un DNI de 8 dígitos.');
  return solicitarAtencion(`/api/pacientes/buscar-dni?dni=${dni}`, 'GET', (valor) => {
    if (!Array.isArray(valor) || !valor.every(esResumenPaciente)) invalida(); return valor;
  }, opciones);
};
export const obtenerPaciente = (id, opciones) => solicitarAtencion(`/api/pacientes/${idValido(id)}`, 'GET', extraerPaciente, opciones);
export const guardarPaciente = (datos, { id, ...opciones } = {}) => {
  const normalizados = normalizarPaciente(datos);
  const errores = validarPaciente(normalizados);
  if (Object.keys(errores).length) throw new ErrorAtencion('Revisa los campos indicados.', { errores });
  return solicitarAtencion(`/api/pacientes${id ? `/${idValido(id)}` : ''}`, id ? 'PUT' : 'POST', extraerPaciente, { ...opciones, datos: normalizados });
};
export const obtenerAgenda = (fecha, opciones) => {
  if (!fechaValida(fecha)) throw new ErrorAtencion('Selecciona una fecha válida.');
  return solicitarAtencion(`/api/citas?fecha=${fecha}`, 'GET', extraerAgenda, opciones);
};
export const obtenerCalendarioCitas = (periodo, opciones) => {
  if (!periodoValido(periodo)) throw new ErrorAtencion('Selecciona un mes válido.');
  return solicitarAtencion(`/api/citas/calendario?periodo=${periodo}`, 'GET', (valor) => {
    if (!objeto(valor) || valor.periodo !== periodo) invalida();
    extraerAgenda({ ...valor, fecha: `${periodo}-01` });
    return valor;
  }, opciones);
};
export const consultarIdentidadDni = (dni, opciones) => {
  if (!/^[0-9]{8}$/.test(dni)) throw new ErrorAtencion('Ingresa un DNI de 8 dígitos.');
  return solicitarAtencion('/api/pacientes/consultar-dni', 'POST', (valor) => {
    if (!objeto(valor) || valor.dni !== dni || !texto(valor.nombres) || !texto(valor.apellidos)
      || [...valor.nombres].length > 80 || [...valor.apellidos].length > 100) invalida();
    return { dni: valor.dni, nombres: valor.nombres, apellidos: valor.apellidos };
  }, { ...opciones, datos: { dni } });
};
export const guardarCita = (datos, { id, ...opciones } = {}) => {
  const normalizados = normalizarCita(datos);
  const errores = validarCita(normalizados);
  if (Object.keys(errores).length) throw new ErrorAtencion('Revisa los campos indicados.', { errores });
  return solicitarAtencion(`/api/citas${id ? `/${idValido(id)}` : ''}`, id ? 'PUT' : 'POST', extraerCita, { ...opciones, datos: normalizados });
};
export const cambiarEstadoCita = (id, estado, opciones) => {
  if (!['atendida', 'cancelada', 'no_asistio'].includes(estado)) throw new ErrorAtencion('Selecciona un estado final válido.');
  return solicitarAtencion(`/api/citas/${idValido(id)}/estado`, 'PUT', extraerCita, { ...opciones, datos: { estado } });
};
export const obtenerHistorialCitas = (id, pagina, opciones) => solicitarAtencion(`/api/pacientes/${idValido(id)}/citas?pagina=${pagina}`, 'GET', (valor) => {
  if (!objeto(valor) || !Array.isArray(valor.citas) || !valor.citas.every(esCita) || !Number.isSafeInteger(valor.total) || valor.total < 0
    || !Number.isInteger(valor.pagina) || valor.pagina < 0 || !texto(valor.zonaHoraria)) invalida(); return valor;
}, opciones);
