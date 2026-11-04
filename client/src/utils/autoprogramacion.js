import { fechaValida, horaValida } from './validacionesProgramacion.js';

function fechaHoraLocal(instante, zonaHoraria) {
  if (!Number.isFinite(Date.parse(instante))) return '';
  try {
    const partes = Object.fromEntries(new Intl.DateTimeFormat('sv-SE', {
      timeZone: zonaHoraria, year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    }).formatToParts(new Date(instante)).map((parte) => [parte.type, parte.value]));
    return partes.year + '-' + partes.month + '-' + partes.day + 'T' + partes.hour + ':' + partes.minute;
  } catch { return ''; }
}

export function reservasDelDia(ocupaciones, idConsultorio, fecha, zonaHoraria, excluir = []) {
  if (!Array.isArray(ocupaciones) || !fechaValida(fecha) || !idConsultorio) return [];
  const inicioDia = fecha + 'T00:00';
  const finDia = fecha + 'T24:00';
  return ocupaciones.filter((reserva) => reserva.idConsultorio === idConsultorio && !excluir.includes(reserva.idTurno))
    .map((reserva) => ({ ...reserva, inicioLocal: fechaHoraLocal(reserva.inicio, zonaHoraria),
      finLocal: fechaHoraLocal(reserva.fin, zonaHoraria) }))
    .filter((reserva) => reserva.inicioLocal && reserva.finLocal && reserva.inicioLocal < finDia && reserva.finLocal > inicioDia)
    .map((reserva) => ({ ...reserva, horaInicio: reserva.inicioLocal < inicioDia ? '00:00' : reserva.inicioLocal.slice(11),
      horaFin: reserva.finLocal >= finDia ? '24:00' : reserva.finLocal.slice(11) }))
    .sort((primera, segunda) => primera.horaInicio.localeCompare(segunda.horaInicio));
}

export function consultorioOcupado(formulario, ocupaciones, zonaHoraria, idActual) {
  if (!horaValida(formulario.horaInicio) || !horaValida(formulario.horaFin)
    || formulario.horaInicio >= formulario.horaFin) return false;
  return reservasDelDia(ocupaciones, formulario.idConsultorio, formulario.fecha, zonaHoraria, [idActual])
    .some((reserva) => formulario.horaInicio < reserva.horaFin && formulario.horaFin > reserva.horaInicio);
}

export function rutaPermitida(sesion, ruta) {
  if (sesion.esAdministrador) return true;
  return Boolean(sesion.esDoctor) && ['/horarios', '/mi-perfil', '/configuraciones'].includes(ruta);
}
