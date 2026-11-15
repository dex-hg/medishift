import { ErrorProgramacion } from './programacion.js';
import { periodoValido } from './planesMensuales.js';
import { fechaValida, horaValida, normalizarTurno, REVISION, UUID, validarTurno } from '../utils/validacionesProgramacion.js';
import { validarFeriados } from '../utils/feriados.js';
import { esPolitica } from './configuracion.js';
import { idsEtiquetasValidos } from '../utils/validacionesCatalogos.js';

const esObjeto = (valor) => valor !== null && typeof valor === 'object' && !Array.isArray(valor);
const esId = (valor) => typeof valor === 'string' && UUID.test(valor);
const esTexto = (valor) => typeof valor === 'string' && Boolean(valor.trim());
const camposError = ['idArea', 'idProfesional', 'idConsultorio', 'idEspecialidad', 'fecha', 'horaInicio',
  'horaFin', 'observacion', 'revision', 'codigo', 'nombre', 'ubicacion', 'periodo', 'falta', 'estado',
  'idAreaPadre', 'idsEtiquetas', 'idEtiqueta'];

function respuestaInvalida() {
  throw new ErrorProgramacion('El servicio devolvió un horario mensual inválido. Recarga para volver a intentarlo.', { tipo: 'respuesta' });
}

function lista(valor, convertir) {
  if (!Array.isArray(valor)) respuestaInvalida();
  const registros = valor.map(convertir);
  if (new Set(registros.map((registro) => registro.id)).size !== registros.length) respuestaInvalida();
  return registros;
}

function especialidad(valor) {
  if (!esObjeto(valor) || !esId(valor.id) || !esTexto(valor.nombre)) respuestaInvalida();
  if (valor.compartida !== undefined && typeof valor.compartida !== 'boolean') respuestaInvalida();
  return { id: valor.id, nombre: valor.nombre, ...(valor.compartida !== undefined ? { compartida: valor.compartida } : {}) };
}

function recurso(valor, consultorio = false) {
  if (!esObjeto(valor) || !esId(valor.id) || !esTexto(valor.nombre)
    || !['Activo', 'Inactivo'].includes(valor.estado)) respuestaInvalida();
  const resultado = { id: valor.id, nombre: valor.nombre, estado: valor.estado,
    especialidades: lista(valor.especialidades, especialidad) };
  if (valor.etiquetas !== undefined) resultado.etiquetas = lista(valor.etiquetas, especialidad);
  if (consultorio) {
    if (!esTexto(valor.codigo) || typeof valor.usoGeneral !== 'boolean'
      || (valor.idArea !== null && !esId(valor.idArea))
      || (valor.idEspecialidadArea !== null && !esId(valor.idEspecialidadArea))) respuestaInvalida();
    return { ...resultado, codigo: valor.codigo, idArea: valor.idArea, idEspecialidadArea: valor.idEspecialidadArea, usoGeneral: valor.usoGeneral };
  }
  if (typeof valor.colegiatura !== 'string') respuestaInvalida();
  return { ...resultado, colegiatura: valor.colegiatura };
}

function extraerTurno(valor) {
  const campos = ['id', 'idPlan', 'idArea', 'idProfesional', 'profesional', 'idConsultorio', 'consultorio',
    'idEspecialidad', 'especialidad', 'fecha', 'horaInicio', 'horaFin', 'estado', 'observacion', 'revision',
    'asistencia', 'revisionAsistencia'];
  if (!esObjeto(valor) || !['id', 'idPlan', 'idArea', 'idProfesional', 'idConsultorio', 'idEspecialidad'].every((campo) => esId(valor[campo]))
    || !['profesional', 'consultorio', 'especialidad'].every((campo) => esTexto(valor[campo]))
    || !fechaValida(valor.fecha) || !horaValida(valor.horaInicio) || !horaValida(valor.horaFin)
    || valor.horaInicio >= valor.horaFin || typeof valor.observacion !== 'string' || !REVISION.test(valor.revision)
    || !['Borrador', 'Aprobado', 'Pendiente', 'Cancelado', 'Sustituido'].includes(valor.estado)
    || !['Asistencia', 'Falta'].includes(valor.asistencia) || !REVISION.test(valor.revisionAsistencia)) respuestaInvalida();
  return Object.fromEntries(campos.map((campo) => [campo, valor[campo]]));
}

function extraerCalendario(valor, periodo) {
  if (!esObjeto(valor) || !esTexto(valor.zonaHoraria) || !validarFeriados(valor.feriados, periodo)
    || !esPolitica(valor.politica)) respuestaInvalida();
  try { new Intl.DateTimeFormat('es-PE', { timeZone: valor.zonaHoraria }); } catch { respuestaInvalida(); }
  const areas = lista(valor.areas, extraerArea);
  const planes = lista(valor.planes, (plan) => {
    if (!esObjeto(plan) || !esId(plan.id) || !esId(plan.idUnidad) || plan.periodo !== periodo
      || !['Borrador', 'Aprobado', 'Pendiente', 'Cancelado', 'Sustituido'].includes(plan.estado)
      || !Number.isInteger(plan.version) || plan.version < 1 || !REVISION.test(plan.revision) || plan.zonaHoraria !== valor.zonaHoraria) respuestaInvalida();
    return { id: plan.id, idUnidad: plan.idUnidad, estado: plan.estado, periodo: plan.periodo, version: plan.version,
      revision: plan.revision, zonaHoraria: plan.zonaHoraria };
  });
  const permisos = valor.permisos;
  if (!esObjeto(permisos) || typeof permisos.esAdministrador !== 'boolean' || typeof permisos.puedeProgramar !== 'boolean'
    || (permisos.idProfesional !== null && !esId(permisos.idProfesional))
    || (!permisos.esAdministrador && !esId(permisos.idProfesional))) respuestaInvalida();
  if (!Array.isArray(valor.ocupaciones)) respuestaInvalida();
  const ocupaciones = valor.ocupaciones.map((reserva) => {
    if (!esObjeto(reserva) || !esId(reserva.idTurno) || !esId(reserva.idConsultorio)
      || !Number.isFinite(Date.parse(reserva.inicio)) || !Number.isFinite(Date.parse(reserva.fin))
      || Date.parse(reserva.inicio) >= Date.parse(reserva.fin)) respuestaInvalida();
    return { idTurno: reserva.idTurno, idConsultorio: reserva.idConsultorio, inicio: reserva.inicio, fin: reserva.fin };
  });
  const turnos = lista(valor.turnos, extraerTurno);
  if (!permisos.esAdministrador && turnos.some((turno) => turno.idProfesional !== permisos.idProfesional)) respuestaInvalida();
  if (turnos.some((turno) => !turno.fecha.startsWith(`${periodo}-`))) respuestaInvalida();
  return { permisos, ocupaciones, zonaHoraria: valor.zonaHoraria, politica: valor.politica, feriados: valor.feriados, areas, planes, turnos,
    especialidades: lista(valor.etiquetas ?? valor.especialidades, especialidad),
    consultorios: lista(valor.consultorios, (valor) => recurso(valor, true)),
    profesionales: lista(valor.profesionales, (valor) => recurso(valor)) };
}

function extraerArea(area) {
  if (!esObjeto(area) || !esId(area.id) || !esTexto(area.nombre) || !esTexto(area.codigo)
    || !['Activo', 'Inactivo'].includes(area.estado) || (area.idEspecialidad !== null && !esId(area.idEspecialidad))
    || typeof area.especialidad !== 'string' || (area.idEspecialidad !== null && !esTexto(area.especialidad))) respuestaInvalida();
  const resultado = { id: area.id, nombre: area.nombre, codigo: area.codigo, estado: area.estado,
    idEspecialidad: area.idEspecialidad, especialidad: area.especialidad };
  if (area.etiquetas === undefined) return resultado;
  if ((area.idAreaPadre !== null && !esId(area.idAreaPadre)) || !REVISION.test(area.revision)) respuestaInvalida();
  return { ...resultado, idAreaPadre: area.idAreaPadre, revision: area.revision, etiquetas: lista(area.etiquetas, especialidad) };
}

async function solicitar(ruta, metodo, { datos, revision, senal, estadoEsperado, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000 } = {}) {
  if (typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0
    || (senal !== undefined && !(senal instanceof AbortSignal))) throw new ErrorProgramacion('No se pudo iniciar la solicitud.');
  const controlador = new AbortController();
  let temporizador;
  let rechazarCancelacion;
  const cancelacion = new Promise((resolver, rechazar) => { rechazarCancelacion = rechazar; });
  const cancelar = () => { controlador.abort(); rechazarCancelacion(new ErrorProgramacion('Solicitud cancelada.', { tipo: 'cancelado' })); };
  senal?.addEventListener('abort', cancelar, { once: true });
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      controlador.abort();
      rechazar(new ErrorProgramacion('La solicitud tardó demasiado. Recarga para comprobar si se guardó antes de reintentar.', { tipo: 'tiempo' }));
    }, tiempoEspera);
  });
  try {
    if (senal?.aborted) cancelar();
    const operacion = async () => {
      if (controlador.signal.aborted) throw new ErrorProgramacion('Solicitud cancelada.', { tipo: 'cancelado' });
      const respuesta = await ejecutarSolicitud(ruta, { method: metodo, credentials: 'same-origin', cache: 'no-store',
        signal: controlador.signal, headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json' } : {}),
          ...(revision ? { 'If-Match': `"${revision}"` } : {}) }, ...(datos ? { body: JSON.stringify(datos) } : {}) });
      let contenido = null;
      if (respuesta.status !== 204) { try { contenido = await respuesta.json(); } catch { /* Se valida abajo. */ } }
      const esperado = estadoEsperado ?? (metodo === 'DELETE' ? 204 : metodo === 'POST' ? 201 : 200);
      if (respuesta.status !== esperado) {
        const errores = esObjeto(contenido?.errores) ? Object.fromEntries(camposError.filter((campo) => esTexto(contenido.errores[campo]))
          .map((campo) => [campo, contenido.errores[campo]])) : {};
        if (errores.idEtiqueta) errores.idEspecialidad ||= errores.idEtiqueta;
        throw new ErrorProgramacion(respuesta.status === 401 ? 'Tu sesión terminó. Inicia sesión nuevamente.'
          : respuesta.status === 403 ? 'No tienes permiso para este cambio. Comprueba si la autoprogramación sigue habilitada y si el turno es tuyo.'
            : esTexto(contenido?.mensaje) ? contenido.mensaje : 'No se pudo guardar el horario.', { estado: respuesta.status, errores });
      }
      return contenido;
    };
    return await Promise.race([operacion(), vencimiento, cancelacion]);
  } catch (error) {
    if (error instanceof ErrorProgramacion) throw error;
    throw new ErrorProgramacion('No se pudo contactar al servicio. Comprueba tu conexión.', { tipo: 'red' });
  } finally { clearTimeout(temporizador); senal?.removeEventListener('abort', cancelar); }
}

export async function cargarPlanificador(periodo, opciones) {
  if (!periodoValido(periodo)) throw new ErrorProgramacion('Selecciona un mes válido.', { tipo: 'validacion' });
  return extraerCalendario(await solicitar(`/api/planificador?periodo=${encodeURIComponent(periodo)}`, 'GET', opciones), periodo);
}

export async function sincronizarFeriados(periodo, { forzar = false, ...opciones } = {}) {
  if (!periodoValido(periodo) || typeof forzar !== 'boolean') throw new ErrorProgramacion('Selecciona un mes válido.');
  const resultado = await solicitar('/api/planificador/feriados/sincronizar', 'POST', {
    ...opciones, estadoEsperado: 200, datos: { periodo, forzar } });
  if (!validarFeriados(resultado, periodo) || resultado.estado !== 'Disponible') respuestaInvalida();
  return resultado;
}

export async function guardarTurnoMensual(datos, { id, revision, ...opciones } = {}) {
  const errores = validarTurno(datos, { aplicarLimites: false });
  if (!esId(datos?.idArea)) errores.idArea = 'Selecciona un área válida.';
  if (Object.keys(errores).length) throw new ErrorProgramacion('Revisa los campos del turno.', { errores, tipo: 'validacion' });
  if (id && (!esId(id) || !REVISION.test(revision))) throw new ErrorProgramacion('Recarga el turno antes de modificarlo.', { estado: 409 });
  const { idEspecialidad: idEtiqueta, ...turno } = normalizarTurno(datos);
  return extraerTurno(await solicitar(`/api/planificador/turnos${id ? `/${id}` : ''}`, id ? 'PUT' : 'POST', {
    ...opciones, revision, datos: { ...turno, idEtiqueta, idArea: datos.idArea } }));
}

export function eliminarTurnoMensual(id, { revision, ...opciones } = {}) {
  if (!esId(id) || !REVISION.test(revision)) throw new ErrorProgramacion('Recarga el turno antes de eliminarlo.', { estado: 409 });
  return solicitar(`/api/planificador/turnos/${id}`, 'DELETE', { ...opciones, revision });
}

export async function cambiarAsistencia(id, falta, { revision, ...opciones } = {}) {
  if (!esId(id) || !REVISION.test(revision) || typeof falta !== 'boolean') {
    throw new ErrorProgramacion('Actualiza el turno antes de cambiar su asistencia.', { tipo: 'validacion' });
  }
  const resultado = await solicitar(`/api/planificador/turnos/${id}/asistencia`, 'PUT', {
    ...opciones, revision, datos: { falta } });
  if (!esObjeto(resultado) || resultado.idTurno !== id || resultado.asistencia !== (falta ? 'Falta' : 'Asistencia')
    || !REVISION.test(resultado.revisionAsistencia)) respuestaInvalida();
  return { idTurno: resultado.idTurno, asistencia: resultado.asistencia, revisionAsistencia: resultado.revisionAsistencia };
}

export function crearConsultorioEnArea(datos, opciones) {
  return solicitar('/api/planificador/consultorios', 'POST', { ...opciones, datos });
}

export function vincularConsultorio(id, idArea, opciones) {
  if (!esId(id) || !esId(idArea)) throw new ErrorProgramacion('Selecciona un consultorio y un área válidos.');
  return solicitar(`/api/planificador/consultorios/${id}/area`, 'PUT', { ...opciones, datos: { idArea } });
}

export function crearAreaEspecializada(datos, opciones) {
  if (!esId(datos?.idEspecialidad)) return Promise.reject(new ErrorProgramacion('Selecciona una especialidad.', { errores: { idEspecialidad: 'Selecciona una especialidad.' } }));
  return solicitar('/api/planificador/areas', 'POST', { ...opciones, datos: {
    codigo: typeof datos.codigo === 'string' ? datos.codigo.trim() : '', nombre: typeof datos.nombre === 'string' ? datos.nombre.trim() : '', idEspecialidad: datos.idEspecialidad,
  } });
}

export function configurarEspecialidadArea(id, idEspecialidad, opciones) {
  if (!esId(id) || !esId(idEspecialidad)) return Promise.reject(new ErrorProgramacion('Selecciona un área y una especialidad válidas.'));
  return solicitar(`/api/planificador/areas/${id}/especialidad`, 'PUT', { ...opciones, datos: { idEspecialidad } });
}

export async function guardarArea(datos, { id, revision, ...opciones } = {}) {
  if (!idsEtiquetasValidos(datos?.idsEtiquetas) || (datos.idAreaPadre && !esId(datos.idAreaPadre))) {
    throw new ErrorProgramacion('Selecciona el área padre y las etiquetas del catálogo.');
  }
  if (id && (!esId(id) || !REVISION.test(revision))) throw new ErrorProgramacion('Recarga el área antes de editarla.');
  return extraerArea(await solicitar(`/api/planificador/areas${id ? `/${id}` : ''}`, id ? 'PUT' : 'POST', {
    ...opciones, revision, datos: { nombre: datos.nombre.trim(), idAreaPadre: datos.idAreaPadre || null,
      idsEtiquetas: datos.idsEtiquetas, ...(id ? { estado: datos.estado } : { codigo: datos.codigo.trim() }) },
  }));
}
