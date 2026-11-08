import { useEffect, useState } from 'react';
import { Clock3, CalendarCheck2 } from 'lucide-react';
import SelectorPacienteCita from './SelectorPacienteCita';
import { partesLocales, validarCita } from '../../utils/validacionesAtencion';
import { erroresDisponibilidad, sumarMinutos } from '../../utils/agendaVisual';
import { mostrarFecha } from '../../utils/validacionesProgramacion';

export default function PanelReservaCita({ reserva, cambiar, fecha, turnos, citas, zonaHoraria, ocupado, cargando,
  error, errores, guardar, nueva, referencia }) {
  const [erroresLocales, cambiarErrores] = useState({});
  const turno = turnos.find((registro) => registro.id === reserva.idTurno);
  const validaciones = { ...errores, ...erroresLocales };
  const mensajeValidacion = validaciones.horaInicio || validaciones.horaFin || validaciones.idPaciente || validaciones.fecha;
  useEffect(() => { cambiarErrores({}); }, [fecha, reserva.idTurno, reserva.horaInicio, reserva.horaFin]);
  const cambiarCampo = (campo, valor) => { cambiarErrores({}); cambiar((anterior) => ({ ...anterior, [campo]: valor })); };
  const enviar = (evento) => {
    evento.preventDefault();
    if (ocupado || cargando) return;
    const datos = { idPaciente: reserva.paciente?.id, idTurno: reserva.idTurno, fecha,
      horaInicio: reserva.horaInicio, horaFin: reserva.horaFin };
    const fallos = { ...validarCita(datos), ...erroresDisponibilidad(datos, turno, citas, zonaHoraria, reserva.cita?.id) };
    cambiarErrores(fallos);
    if (!Object.keys(fallos).length) guardar(datos);
  };
  const duracionPermitida = (minutos) => {
    const final = sumarMinutos(reserva.horaInicio, minutos);
    return turno && final && !Object.keys(erroresDisponibilidad({ fecha, horaInicio: reserva.horaInicio, horaFin: final },
      turno, citas, zonaHoraria, reserva.cita?.id)).length;
  };
  return <aside ref={referencia} className="panel reserva-cita" aria-labelledby="titulo-reserva-cita">
    <header className="reserva-cita__cabecera"><div><small>3. Completa la reserva</small><h2 id="titulo-reserva-cita" tabIndex={-1}>
      {reserva.cita ? 'Reprogramar cita' : 'Agendar cita'}</h2>
      <p className="reserva-cita__fecha"><Clock3 size={16} /><time dateTime={fecha}>{mostrarFecha(fecha)}</time></p>
    </div><CalendarCheck2 size={23} aria-hidden="true" /></header>
    <SelectorPacienteCita paciente={reserva.paciente} seleccionar={(paciente) => cambiarCampo('paciente', paciente)}
      bloqueado={Boolean(reserva.cita)} ocupado={ocupado} zonaHoraria={zonaHoraria} />
    <form className="reserva-cita__formulario" onSubmit={enviar}>
      <fieldset disabled={ocupado || cargando} className="atencion-campos">
        <label className="campo-planificador"><span>Turno aprobado</span><select value={reserva.idTurno} required
          onChange={(evento) => { const idTurno = evento.target.value; cambiarErrores({}); cambiar((anterior) => ({ ...anterior, idTurno, horaInicio: '', horaFin: '' })); }}>
          <option value="">Elige un espacio en el horario</option>{turnos.map((registro) => <option key={registro.id} value={registro.id}>
            {registro.profesional} · {partesLocales(registro.inicio, zonaHoraria).hora} a {partesLocales(registro.fin, zonaHoraria).hora}</option>)}</select>
          {validaciones.idTurno && <small className="atencion-error" role="alert">{validaciones.idTurno}</small>}
        </label>
        {turno && <p className="reserva-cita__consultorio">{turno.consultorio} · {turno.especialidad}</p>}
        <div className="reserva-cita__horas">
          <label className="campo-planificador"><span>Hora de inicio</span><input type="time" required value={reserva.horaInicio} onChange={(evento) => cambiarCampo('horaInicio', evento.target.value)} /></label>
          <label className="campo-planificador"><span>Hora de fin</span><input type="time" required value={reserva.horaFin} onChange={(evento) => cambiarCampo('horaFin', evento.target.value)} /></label>
        </div>
        <div className="reserva-duraciones" aria-label="Duraciones rápidas"><span>Completar duración</span>
          <div>{[15, 30, 45, 60].map((minutos) => <button type="button" className="boton boton--fantasma" key={minutos}
            disabled={!duracionPermitida(minutos)} onClick={() => cambiarCampo('horaFin', sumarMinutos(reserva.horaInicio, minutos))}>{minutos} min</button>)}</div>
        </div>
      </fieldset>
      {mensajeValidacion && <p className="atencion-error" role="alert">{mensajeValidacion}</p>}
      {error && error !== mensajeValidacion && <p className="atencion-error" role="alert">{error}</p>}
      {!reserva.idTurno && <p className="reserva-cita__ayuda">Selecciona un espacio libre del día. El paciente y los datos que completes se conservarán aquí.</p>}
      <p className="reserva-cita__ayuda">Puedes ajustar las horas libremente dentro del turno. Solo se reservan horas futuras. Zona horaria: {zonaHoraria}.</p>
      <button type="submit" className="boton boton--primario reserva-cita__guardar" disabled={ocupado || cargando || !turno || !reserva.paciente?.activo}>
        {ocupado ? 'Guardando…' : reserva.cita ? 'Guardar reprogramación' : 'Guardar cita'}</button>
      {reserva.cita && <button className="boton boton--secundario reserva-cita__guardar" type="button" disabled={ocupado} onClick={nueva}>Terminar reprogramación</button>}
    </form>
  </aside>;
}
