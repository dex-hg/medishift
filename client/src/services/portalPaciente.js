import { ErrorAtencion, solicitarAtencion } from './atencion.js';
import { periodoValido } from './planesMensuales.js';
import { etiquetasEstadoCita } from '../utils/validacionesAtencion.js';
import { UUID } from '../utils/validacionesProgramacion.js';

const PORTAL = '/api/portal-paciente';
const texto = (valor) => typeof valor === 'string' && Boolean(valor.trim());
const instante = (valor) => typeof valor === 'string' && Number.isFinite(Date.parse(valor));
const invalida = () => { throw new ErrorAtencion('La respuesta del servidor no es válida. Vuelve a intentarlo.'); };
const accesoFicha = (id) => {
  if (!UUID.test(id)) throw new ErrorAtencion('El identificador del paciente no es válido.');
  return '/api/pacientes/' + id + '/acceso-portal';
};

export function obtenerAccesoPaciente(id, opciones) {
  return solicitarAtencion(accesoFicha(id), 'GET', (valor) => {
    if (typeof valor?.activado !== 'boolean' || !(valor.venceEn === null || instante(valor.venceEn))) invalida();
    return valor;
  }, opciones);
}

export function emitirCodigoPaciente(id, opciones) {
  return solicitarAtencion(accesoFicha(id), 'POST', (valor) => {
    if (!/^[A-Za-z0-9_-]{22}$/.test(valor?.codigo) || !instante(valor.venceEn)) invalida();
    return valor;
  }, opciones);
}

export function iniciarSesionPaciente({ dni, contrasena }, opciones) {
  return solicitarAtencion(PORTAL + '/sesion', 'POST', () => true,
    { ...opciones, datos: { dni, contrasena }, estadoEsperado: 204 });
}

export function activarAccesoPaciente({ dni, codigo, contrasena }, opciones) {
  return solicitarAtencion(PORTAL + '/activacion', 'POST', () => true,
    { ...opciones, datos: { dni, codigo, contrasena }, estadoEsperado: 204 });
}

export function cerrarSesionPaciente(opciones) {
  return solicitarAtencion(PORTAL + '/sesion', 'DELETE', () => true, { ...opciones, estadoEsperado: 204 });
}

export function obtenerCalendarioPaciente(periodo, opciones) {
  if (!periodoValido(periodo)) throw new ErrorAtencion('Selecciona un mes válido.');
  return solicitarAtencion(PORTAL + '/calendario?periodo=' + periodo, 'GET', (valor) => {
    if (valor?.periodo !== periodo || !Array.isArray(valor.hospitales) || !Array.isArray(valor.citas)) invalida();
    const hospitales = new Set();
    for (const hospital of valor.hospitales) {
      if (!texto(hospital?.codigo) || !texto(hospital.nombre) || !texto(hospital.zonaHoraria) || hospitales.has(hospital.codigo)) invalida();
      try { new Intl.DateTimeFormat('es-PE', { timeZone: hospital.zonaHoraria }); } catch { invalida(); }
      hospitales.add(hospital.codigo);
    }
    if (!valor.citas.every((cita) => UUID.test(cita?.id) && hospitales.has(cita.codigoHospital)
      && [cita.consultorio, cita.ubicacion, cita.profesional].every(texto)
      && instante(cita.inicio) && instante(cita.fin) && Date.parse(cita.fin) > Date.parse(cita.inicio)
      && Object.hasOwn(etiquetasEstadoCita, cita.estado))) invalida();
    return valor;
  }, opciones);
}
