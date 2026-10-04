export const DIAS_SEMANA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
export const LIMITES_JORNADA = Object.freeze({ diarios: 360, semanales: 2160 });
export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const REVISION = /^[0-9a-f]{64}$/i;
const FORMATO_FECHA = /^\d{4}-\d{2}-\d{2}$/;
const FORMATO_HORA = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const CAMPOS_DISPONIBILIDAD = ['idProfesional', 'diaSemana', 'horaInicio', 'horaFin', 'fechaInicio', 'fechaFin', 'estado', 'observacion'];
const CAMPOS_TURNO = ['idProfesional', 'idConsultorio', 'idEspecialidad', 'fecha', 'horaInicio', 'horaFin', 'observacion'];

export function fechaValida(valor) {
  if (typeof valor !== 'string' || !FORMATO_FECHA.test(valor) || valor.slice(0, 4) === '0000') return false;
  const fecha = new Date(`${valor}T12:00:00Z`);
  return !Number.isNaN(fecha.getTime()) && fecha.toISOString().slice(0, 10) === valor;
}

export const horaValida = (valor) => typeof valor === 'string' && FORMATO_HORA.test(valor);
export const minutosHora = (valor) => horaValida(valor) ? Number(valor.slice(0, 2)) * 60 + Number(valor.slice(3)) : NaN;
export const duracionTurno = (turno) => minutosHora(turno.horaFin) - minutosHora(turno.horaInicio);

export function sumarDias(fecha, cantidad) {
  if (!fechaValida(fecha) || !Number.isInteger(cantidad)) return '';
  const resultado = new Date(`${fecha}T12:00:00Z`);
  resultado.setUTCDate(resultado.getUTCDate() + cantidad);
  return resultado.toISOString().slice(0, 10);
}

export function lunesDeSemana(fecha) {
  if (!fechaValida(fecha)) return '';
  const dia = new Date(`${fecha}T12:00:00Z`).getUTCDay() || 7;
  return sumarDias(fecha, 1 - dia);
}

export function fechaActual(zonaHoraria = 'America/Lima') {
  try {
    const partes = new Intl.DateTimeFormat('en-CA', { timeZone: zonaHoraria, year: 'numeric', month: '2-digit', day: '2-digit' })
      .formatToParts(new Date());
    const valores = Object.fromEntries(partes.map(({ type, value }) => [type, value]));
    return `${valores.year}-${valores.month}-${valores.day}`;
  } catch { return new Date().toISOString().slice(0, 10); }
}

export function mostrarFecha(fecha) {
  if (!fechaValida(fecha)) return '';
  return new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${fecha}T12:00:00Z`));
}

export function normalizarDisponibilidad(datos = {}) {
  return Object.fromEntries(CAMPOS_DISPONIBILIDAD.map((campo) => [campo,
    campo === 'diaSemana' ? Number(datos[campo]) : typeof datos[campo] === 'string' ? datos[campo].trim() : datos[campo] ?? '',
  ]));
}

export function normalizarTurno(datos = {}) {
  return Object.fromEntries(CAMPOS_TURNO.map((campo) => [campo,
    typeof datos[campo] === 'string' ? datos[campo].trim() : datos[campo] ?? '',
  ]));
}

function validarBase(datos, campos, longitudObservacion) {
  const errores = {};
  for (const campo of campos) {
    if (campo === 'diaSemana') continue;
    if (typeof datos?.[campo] !== 'string') errores[campo] = 'Este campo debe contener texto.';
  }
  for (const campo of campos.filter((nombre) => nombre.startsWith('id'))) {
    if (!UUID.test(datos?.[campo])) errores[campo] = 'Selecciona un registro válido.';
  }
  for (const campo of campos.filter((nombre) => nombre.startsWith('fecha'))) {
    if (!fechaValida(datos?.[campo])) errores[campo] = 'Ingresa una fecha válida.';
  }
  for (const campo of ['horaInicio', 'horaFin']) {
    if (!horaValida(datos?.[campo])) errores[campo] = 'Ingresa la hora con formato HH:mm.';
  }
  if (horaValida(datos?.horaInicio) && horaValida(datos?.horaFin) && datos.horaFin <= datos.horaInicio) {
    errores.horaFin = 'La hora final debe ser posterior a la inicial, dentro del mismo día.';
  }
  if (typeof datos?.observacion === 'string') {
    const contenido = datos.observacion.trim();
    if ([...contenido].length > longitudObservacion) errores.observacion = `Admite hasta ${longitudObservacion} caracteres.`;
    if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(contenido)
      || [...contenido].some((caracter) => caracter.length === 1 && /[\ud800-\udfff]/u.test(caracter))) {
      errores.observacion = 'La observación contiene un carácter no permitido.';
    }
  }
  return errores;
}

export function validarDisponibilidad(datos) {
  const normalizados = normalizarDisponibilidad(datos);
  const errores = validarBase(normalizados, CAMPOS_DISPONIBILIDAD, 240);
  if (!['number', 'string'].includes(typeof datos?.diaSemana) || !/^[1-7]$/.test(String(datos?.diaSemana))) {
    errores.diaSemana = 'Selecciona un día entre lunes y domingo.';
  }
  if (!['Activo', 'Inactivo'].includes(normalizados.estado)) errores.estado = 'Selecciona Activo o Inactivo.';
  if (fechaValida(normalizados.fechaInicio) && fechaValida(normalizados.fechaFin) && normalizados.fechaFin < normalizados.fechaInicio) {
    errores.fechaFin = 'La fecha final debe ser igual o posterior a la inicial.';
  }
  if (!errores.diaSemana && !errores.fechaInicio && !errores.fechaFin) {
    const primerDia = new Date(`${normalizados.fechaInicio}T12:00:00Z`).getUTCDay() || 7;
    const primerOcurrencia = sumarDias(normalizados.fechaInicio, (normalizados.diaSemana - primerDia + 7) % 7);
    if (primerOcurrencia > normalizados.fechaFin) errores.diaSemana = 'El intervalo de fechas no contiene el día seleccionado.';
  }
  return errores;
}

export function validarTurno(datos, { recursos, turnos = [], idActual } = {}) {
  const normalizados = normalizarTurno(datos);
  const errores = validarBase(normalizados, CAMPOS_TURNO, 180);
  const duracion = duracionTurno(normalizados);
  if (duracion > LIMITES_JORNADA.diarios) errores.horaFin = 'El turno admite hasta 6 horas diarias por profesional.';
  if (recursos) {
    const profesional = recursos.profesionales.find((registro) => registro.id === normalizados.idProfesional && registro.estado === 'Activo');
    const consultorio = recursos.consultorios.find((registro) => registro.id === normalizados.idConsultorio && registro.estado === 'Activo');
    if (!profesional) errores.idProfesional = 'Selecciona un profesional activo.';
    if (!consultorio) errores.idConsultorio = 'Selecciona un consultorio activo.';
    if (profesional && !profesional.especialidades.some((registro) => registro.id === normalizados.idEspecialidad)) {
      errores.idEspecialidad = 'Selecciona una especialidad del profesional.';
    }
    if (consultorio && !consultorio.usoGeneral && !consultorio.especialidades.some((registro) => registro.id === normalizados.idEspecialidad)) {
      errores.idConsultorio = 'El consultorio no admite la especialidad seleccionada.';
    }
  }
  if (duracion > 0 && fechaValida(normalizados.fecha)) {
    const otros = turnos.filter((turno) => turno.id !== idActual && turno.estado !== 'Cancelado');
    const profesional = otros.filter((turno) => turno.idProfesional === normalizados.idProfesional);
    const delDia = profesional.filter((turno) => turno.fecha === normalizados.fecha);
    if (delDia.reduce((total, turno) => total + duracionTurno(turno), duracion) > LIMITES_JORNADA.diarios) {
      errores.horaFin = 'El profesional superaría 6 horas asignadas en este día.';
    }
    const lunes = lunesDeSemana(normalizados.fecha);
    const semana = profesional.filter((turno) => lunesDeSemana(turno.fecha) === lunes);
    if (semana.reduce((total, turno) => total + duracionTurno(turno), duracion) > LIMITES_JORNADA.semanales) {
      errores.fecha = 'El profesional superaría 36 horas asignadas esta semana.';
    }
    for (const turno of otros.filter((item) => item.fecha === normalizados.fecha)) {
      if (turno.horaInicio < normalizados.horaFin && normalizados.horaInicio < turno.horaFin) {
        if (turno.idProfesional === normalizados.idProfesional) errores.idProfesional = 'El profesional ya tiene un turno que se cruza con esta franja.';
        if (turno.idConsultorio === normalizados.idConsultorio) errores.idConsultorio = 'El consultorio ya está ocupado durante esta franja.';
      }
    }
  }
  return errores;
}
