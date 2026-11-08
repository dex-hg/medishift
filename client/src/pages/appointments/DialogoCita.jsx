import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import VentanaPlanificador from '../schedules/VentanaPlanificador';
import { useConsultaAtencion, useOperacionAtencion } from '../../hooks/useAtencion';
import { buscarPacienteDni, obtenerAgenda } from '../../services/atencion';
import { partesLocales, validarCita } from '../../utils/validacionesAtencion';

export default function DialogoCita({ editor, zonaHoraria, ocupado, error, errores, guardar, cerrar }) {
  const cita = editor.cita;
  const [fecha, cambiarFecha] = useState(cita ? partesLocales(cita.inicio, zonaHoraria).fecha : editor.fecha);
  const [paciente, cambiarPaciente] = useState(editor.paciente || (cita ? { id: cita.idPaciente, nombres: cita.paciente, apellidos: '', activo: true } : null));
  const [dni, cambiarDni] = useState('');
  const [buscado, cambiarBuscado] = useState(false);
  const [idTurno, cambiarTurno] = useState(cita?.idTurno || '');
  const [horaInicio, cambiarInicio] = useState(cita ? partesLocales(cita.inicio, zonaHoraria).hora : '');
  const [horaFin, cambiarFin] = useState(cita ? partesLocales(cita.fin, zonaHoraria).hora : '');
  const [erroresLocales, cambiarErrores] = useState({});
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerAgenda(fecha, opciones), [fecha]));
  const busqueda = useOperacionAtencion();
  const validaciones = { ...errores, ...erroresLocales };
  const turnos = consulta.datos?.turnos.filter((turno) => Date.parse(turno.fin) > Date.now()) || [];
  const seleccionado = turnos.find((turno) => turno.id === idTurno);
  const reservas = consulta.datos?.citas.filter((reserva) => reserva.idTurno === idTurno && reserva.estado !== 'cancelada' && reserva.id !== cita?.id) || [];
  const buscar = async (evento) => {
    evento.preventDefault(); cambiarBuscado(false); cambiarPaciente(null);
    const resultado = await busqueda.ejecutar(() => buscarPacienteDni(dni));
    if (resultado) { cambiarBuscado(true); cambiarPaciente(resultado[0] || null); }
  };
  const enviar = (evento) => {
    evento.preventDefault();
    if (ocupado || consulta.cargando || !seleccionado) return;
    const datos = { idPaciente: paciente?.id, idTurno, fecha, horaInicio, horaFin };
    const fallos = validarCita(datos);
    cambiarErrores(fallos);
    if (!Object.keys(fallos).length) guardar(datos);
  };
  return <VentanaPlanificador titulo={cita ? 'Reprogramar cita' : 'Agendar cita'} descripcion="Selecciona un turno aprobado y define las horas de la cita." ocupado={ocupado || busqueda.guardando} cerrar={cerrar}>
    {!cita && <form className="atencion-filtros atencion-buscador-cita" onSubmit={buscar}>
      <label className="campo-planificador"><span>Buscar paciente por DNI</span><input value={dni} inputMode="numeric" pattern="[0-9]{8}" maxLength={8} required disabled={ocupado || busqueda.guardando}
        onChange={(evento) => { cambiarDni(evento.target.value); cambiarPaciente(null); cambiarBuscado(false); busqueda.limpiar(); }} /></label>
      <button className="boton boton--secundario" disabled={ocupado || busqueda.guardando || !/^[0-9]{8}$/.test(dni)}>Buscar</button>
    </form>}
    {busqueda.error && <p className="atencion-error" role="alert">{busqueda.error}</p>}
    {buscado && !paciente && <p role="status">No existe una ficha con ese DNI. <Link className="planificador-enlace" to="/pacientes/nuevo" state={{ dni }}>Registrar paciente</Link></p>}
    {paciente && <p className="atencion-paciente" role="status"><strong>{paciente.nombres} {paciente.apellidos}</strong>{!paciente.activo && ' · Paciente inactivo'}</p>}
    <form onSubmit={enviar} className="formulario-planificador">
      <fieldset disabled={ocupado} className="atencion-campos"><div className="rejilla-formulario">
        <label className="campo-planificador"><span>Fecha de la cita</span><input type="date" required min="0001-01-01" max="9999-12-31" value={fecha}
          onChange={(evento) => { cambiarFecha(evento.target.value); cambiarTurno(''); cambiarInicio(''); cambiarFin(''); }} /></label>
        <label className="campo-planificador"><span>Turno aprobado</span><select value={idTurno} required disabled={consulta.cargando || !consulta.datos}
          onChange={(evento) => { cambiarTurno(evento.target.value); cambiarInicio(''); cambiarFin(''); }}>
          <option value="">Selecciona un turno</option>{turnos.map((turno) => <option key={turno.id} value={turno.id}>
            {turno.profesional} · {turno.consultorio} · {partesLocales(turno.inicio, zonaHoraria).hora} a {partesLocales(turno.fin, zonaHoraria).hora}</option>)}</select>
          {validaciones.idTurno && <small role="alert">{validaciones.idTurno}</small>}</label>
        <label className="campo-planificador"><span>Hora de inicio</span><input type="time" value={horaInicio} required onChange={(evento) => cambiarInicio(evento.target.value)} />
          {validaciones.horaInicio && <small role="alert">{validaciones.horaInicio}</small>}</label>
        <label className="campo-planificador"><span>Hora de fin</span><input type="time" value={horaFin} required onChange={(evento) => cambiarFin(evento.target.value)} />
          {validaciones.horaFin && <small role="alert">{validaciones.horaFin}</small>}</label>
      </div></fieldset>
      {consulta.cargando && <p role="status">Consultando turnos aprobados…</p>}
      {consulta.error && <div role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" type="button" onClick={consulta.recargar}>Reintentar</button></div>}
      {consulta.datos && !turnos.length && <p>No hay turnos aprobados disponibles para esta fecha. El administrador puede aprobarlos desde Horarios.</p>}
      {seleccionado && <p className="atencion-ayuda">{seleccionado.especialidad} · Turno de {partesLocales(seleccionado.inicio, zonaHoraria).hora} a {partesLocales(seleccionado.fin, zonaHoraria).hora}.
        {reservas.length ? ` Citas ocupadas: ${reservas.map((reserva) => `${partesLocales(reserva.inicio, zonaHoraria).hora} a ${partesLocales(reserva.fin, zonaHoraria).hora}`).join(', ')}.` : ' Sin citas ocupadas en este turno.'}</p>}
      {(error || validaciones.idPaciente || validaciones.fecha) && <p className="atencion-error" role="alert">{error || validaciones.idPaciente || validaciones.fecha}</p>}
      <p className="atencion-ayuda">Zona horaria: {zonaHoraria}. La duración se define con las horas elegidas.</p>
      <div className="ventana-planificador__acciones"><button className="boton boton--secundario" type="button" disabled={ocupado} onClick={cerrar}>Volver</button>
        <button className="boton boton--primario" disabled={ocupado || consulta.cargando || !seleccionado || !paciente?.activo}>{ocupado ? 'Guardando…' : 'Guardar cita'}</button></div>
    </form>
  </VentanaPlanificador>;
}
