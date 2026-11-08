import { fechaValida, horaValida, UUID } from './validacionesProgramacion.js';

const camposPaciente = ['nombres', 'apellidos', 'dni', 'fechaNacimiento', 'correo', 'telefono'];
const camposCita = ['idPaciente', 'idTurno', 'fecha', 'horaInicio', 'horaFin'];
const normalizar = (valor) => typeof valor === 'string' ? valor.normalize('NFC').replace(/[\s\p{Z}]+/gu, ' ').trim() : valor;

export function normalizarPaciente(datos = {}) {
  return { ...Object.fromEntries(camposPaciente.map((campo) => [campo, normalizar(datos[campo] ?? '')])),
    correo: typeof datos.correo === 'string' ? normalizar(datos.correo).toLowerCase() : datos.correo ?? '', activo: datos.activo };
}

export function validarPaciente(datos, hoy) {
  const errores = {};
  for (const [campo, limite] of Object.entries({ nombres: 80, apellidos: 100, dni: 8, correo: 254, telefono: 20 })) {
    const valor = datos?.[campo];
    if (typeof valor !== 'string' || [...valor].length > limite || /[\p{Cc}\p{Cs}]/u.test(valor)
      || (['nombres', 'apellidos'].includes(campo) && !valor.trim())) errores[campo] = `Completa el campo con un máximo de ${limite} caracteres.`;
  }
  if (!/^[0-9]{8}$/.test(datos?.dni)) errores.dni = 'El DNI debe contener exactamente 8 dígitos.';
  if (!fechaValida(datos?.fechaNacimiento) || (hoy && datos.fechaNacimiento > hoy)) errores.fechaNacimiento = 'Selecciona una fecha de nacimiento válida que no sea futura.';
  if (datos?.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(datos.correo)) errores.correo = 'Ingresa un correo válido.';
  if (datos?.telefono && (!/^\+?[0-9][0-9 ()-]*$/.test(datos.telefono) || datos.telefono.replace(/[^0-9]/g, '').length < 7
    || datos.telefono.replace(/[^0-9]/g, '').length > 15)) errores.telefono = 'Ingresa un teléfono de 7 a 15 dígitos.';
  if (typeof datos?.activo !== 'boolean') errores.activo = 'Selecciona el estado del paciente.';
  return errores;
}

export const normalizarCita = (datos = {}) => Object.fromEntries(camposCita.map((campo) => [campo, typeof datos[campo] === 'string' ? datos[campo].trim() : datos[campo]]));

export function validarCita(datos) {
  const errores = {};
  for (const campo of ['idPaciente', 'idTurno']) if (!UUID.test(datos?.[campo])) errores[campo] = 'Selecciona un registro válido.';
  if (!fechaValida(datos?.fecha)) errores.fecha = 'Selecciona una fecha válida.';
  for (const campo of ['horaInicio', 'horaFin']) if (!horaValida(datos?.[campo])) errores[campo] = 'Ingresa una hora válida.';
  if (horaValida(datos?.horaInicio) && horaValida(datos?.horaFin) && datos.horaFin <= datos.horaInicio) errores.horaFin = 'El final debe ser posterior al inicio.';
  return errores;
}

export const etiquetasEstadoCita = Object.freeze({ programada: 'Programada', atendida: 'Atendida', cancelada: 'Cancelada', no_asistio: 'No asistió' });

export function partesLocales(instante, zonaHoraria) {
  const valores = Object.fromEntries(new Intl.DateTimeFormat('sv-SE', { timeZone: zonaHoraria,
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(new Date(instante)).map((parte) => [parte.type, parte.value]));
  return { fecha: `${valores.year}-${valores.month}-${valores.day}`, hora: `${valores.hour}:${valores.minute}` };
}

export function accionesCita(cita, ahora = Date.now()) {
  if (cita.estado !== 'programada') return [];
  return ['reprogramar', 'cancelada', ...(ahora >= Date.parse(cita.inicio) ? ['atendida'] : []),
    ...(ahora >= Date.parse(cita.fin) ? ['no_asistio'] : [])];
}
