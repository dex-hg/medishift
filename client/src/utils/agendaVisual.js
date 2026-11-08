import { partesLocales } from './validacionesAtencion.js';
import { horaValida, minutosHora } from './validacionesProgramacion.js';

/** Se calcula por instantes; nunca usa la zona horaria del equipo para decidir disponibilidad. */
export function segmentosTurno(turno, citas, { excluir = '', ahora = Date.now() } = {}) {
  const inicio = Date.parse(turno?.inicio);
  const fin = Date.parse(turno?.fin);
  if (!Number.isFinite(inicio) || !Number.isFinite(fin) || fin <= inicio || !Array.isArray(citas)) return [];
  const reservas = citas.filter((cita) => cita.id !== excluir && cita.estado !== 'cancelada' && cita.idProfesional === turno.idProfesional)
    .map((cita) => ({ inicio: Math.max(inicio, Date.parse(cita.inicio)), fin: Math.min(fin, Date.parse(cita.fin)), cita }))
    .filter((reserva) => Number.isFinite(reserva.inicio) && Number.isFinite(reserva.fin) && reserva.fin > reserva.inicio)
    .sort((primera, segunda) => primera.inicio - segunda.inicio);
  const segmentos = [];
  const redondeado = Math.ceil((ahora + 1) / 60000) * 60000;
  const agregarLibre = (desde, hasta) => {
    if (hasta <= desde) return;
    if (desde < redondeado) segmentos.push({ tipo: 'transcurrido', inicio: desde, fin: Math.min(hasta, redondeado) });
    if (hasta > redondeado) segmentos.push({ tipo: 'libre', inicio: Math.max(desde, redondeado), fin: hasta });
  };
  let cursor = inicio;
  for (const reserva of reservas) {
    agregarLibre(cursor, reserva.inicio);
    if (reserva.fin > cursor) segmentos.push({ tipo: 'ocupado', inicio: Math.max(cursor, reserva.inicio), fin: reserva.fin, cita: reserva.cita });
    cursor = Math.max(cursor, reserva.fin);
  }
  agregarLibre(cursor, fin);
  return segmentos;
}

export function espaciosLibres(turno, citas, opciones) {
  return segmentosTurno(turno, citas, opciones).filter((segmento) => segmento.tipo === 'libre' && segmento.fin - segmento.inicio >= 60000);
}

export function sumarMinutos(hora, cantidad) {
  if (!horaValida(hora) || !Number.isInteger(cantidad) || cantidad < 1) return '';
  const total = minutosHora(hora) + cantidad;
  return total < 1440 ? `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}` : '';
}

export function datosDelDia(datos, fecha, profesional = '') {
  if (!datos) return { turnos: [], citas: [] };
  const pertenece = (registro) => (!profesional || registro.idProfesional === profesional)
    && partesLocales(registro.inicio, datos.zonaHoraria).fecha === fecha;
  return { turnos: datos.turnos.filter(pertenece), citas: datos.citas.filter(pertenece) };
}

export function erroresDisponibilidad(formulario, turno, citas, zonaHoraria, excluir = '') {
  const errores = {};
  if (!turno || !horaValida(formulario.horaInicio) || !horaValida(formulario.horaFin)) return errores;
  const inicio = `${formulario.fecha}T${formulario.horaInicio}`;
  const fin = `${formulario.fecha}T${formulario.horaFin}`;
  const local = (instante) => { const partes = partesLocales(instante, zonaHoraria); return `${partes.fecha}T${partes.hora}`; };
  if (inicio < local(turno.inicio) || fin > local(turno.fin)) errores.horaInicio = 'La cita debe quedar dentro del turno seleccionado.';
  if (citas.some((cita) => cita.id !== excluir && cita.estado !== 'cancelada' && cita.idProfesional === turno.idProfesional
    && inicio < local(cita.fin) && fin > local(cita.inicio))) errores.horaInicio = 'Ese intervalo está ocupado. Elige uno de los espacios libres.';
  return errores;
}
