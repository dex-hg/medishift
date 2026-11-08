import { useCallback, useRef, useState } from 'react';
import { useLocation, useOutletContext } from 'react-router-dom';
import { Plus, RefreshCw } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import VentanaPlanificador from '../schedules/VentanaPlanificador';
import TablaCitas from './TablaCitas';
import CalendarioCitas from './CalendarioCitas';
import HorariosDiaCitas from './HorariosDiaCitas';
import PanelReservaCita from './PanelReservaCita';
import { useConsultaAtencion, useOperacionAtencion } from '../../hooks/useAtencion';
import { cambiarEstadoCita, guardarCita, obtenerCalendarioCitas } from '../../services/atencion';
import { etiquetasEstadoCita, partesLocales } from '../../utils/validacionesAtencion';
import { fechaActual } from '../../utils/validacionesProgramacion';
import { datosDelDia } from '../../utils/agendaVisual';

const reservaVacia = (paciente = null) => ({ paciente, cita: null, idTurno: '', horaInicio: '', horaFin: '' });

export default function AgendaCitas() {
  const { configuracion, sesion } = useOutletContext();
  const ubicacion = useLocation();
  const [fecha, cambiarFecha] = useState(() => fechaActual(configuracion.zonaHoraria));
  const [reserva, cambiarReserva] = useState(() => reservaVacia(ubicacion.state?.paciente || null));
  const [profesional, cambiarProfesional] = useState('');
  const [detalle, cambiarDetalle] = useState(null);
  const panel = useRef(null);
  const [confirmacion, cambiarConfirmacion] = useState(null);
  const [mensaje, cambiarMensaje] = useState('');
  const periodo = fecha.slice(0, 7);
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerCalendarioCitas(periodo, opciones), [periodo]));
  const operacion = useOperacionAtencion();
  const dia = datosDelDia(consulta.datos, fecha, profesional);
  const medicos = [...new Map((consulta.datos?.turnos || []).map((turno) => [turno.idProfesional,
    { id: turno.idProfesional, nombre: turno.profesional }])).values()];
  const zona = consulta.datos?.zonaHoraria || configuracion.zonaHoraria;
  const enfocarPanel = () => {
    if (window.matchMedia('(max-width: 980px)').matches) panel.current?.scrollIntoView({ block: 'start' });
    panel.current?.querySelector('h2')?.focus({ preventScroll: true });
  };
  const nueva = () => { cambiarReserva(reservaVacia()); operacion.limpiar(); cambiarDetalle(null); };
  const elegirFecha = (valor) => {
    if (operacion.guardando || valor === fecha) return;
    cambiarFecha(valor); cambiarMensaje(''); operacion.limpiar();
    cambiarReserva((anterior) => ({ ...anterior, idTurno: '', horaInicio: '', horaFin: '' }));
  };
  const elegirEspacio = (turno, espacio) => {
    operacion.limpiar(); cambiarMensaje('');
    cambiarReserva((anterior) => ({ ...anterior, idTurno: turno.id, horaInicio: partesLocales(espacio.inicio, zona).hora, horaFin: '' }));
    enfocarPanel();
  };
  const guardar = async (datos) => {
    const resultado = await operacion.ejecutar(() => guardarCita(datos, { id: reserva.cita?.id, revision: reserva.cita?.revision }));
    if (resultado) { cambiarReserva(reservaVacia()); cambiarMensaje('La cita se guardó y ya aparece en el calendario.'); }
    consulta.recargar();
  };
  const actuar = (cita, accion) => {
    operacion.limpiar(); cambiarMensaje('');
    if (accion === 'reprogramar') {
      cambiarFecha(partesLocales(cita.inicio, zona).fecha); cambiarProfesional(''); cambiarDetalle(null);
      cambiarReserva({ cita, paciente: { id: cita.idPaciente, nombres: cita.paciente, apellidos: '', activo: true },
        idTurno: cita.idTurno, horaInicio: partesLocales(cita.inicio, zona).hora, horaFin: partesLocales(cita.fin, zona).hora });
      enfocarPanel();
    }
    else cambiarConfirmacion({ cita, estado: accion });
  };
  const confirmar = async () => {
    const resultado = await operacion.ejecutar(() => cambiarEstadoCita(confirmacion.cita.id, confirmacion.estado, { revision: confirmacion.cita.revision }));
    if (resultado) { cambiarConfirmacion(null); cambiarMensaje('El estado de la cita se actualizó.'); consulta.recargar(); }
  };
  const recargar = () => { operacion.limpiar(); cambiarMensaje(''); consulta.recargar(); };
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Citas' }]} titulo={sesion.esAdministrador ? 'Agenda de citas' : 'Mi agenda de citas'}
      descripcion="Elige el día, revisa sus espacios libres y registra la cita sin salir del calendario."
      acciones={<button className="boton boton--primario" disabled={operacion.guardando} onClick={() => { nueva(); enfocarPanel(); }}><Plus size={18} /> Nueva cita</button>} />
    <div className="agenda-herramientas">
      {sesion.esAdministrador && <label className="campo-planificador"><span>Médico</span><select value={profesional} disabled={operacion.guardando}
        onChange={(evento) => { cambiarProfesional(evento.target.value); cambiarReserva((anterior) => ({ ...anterior, idTurno: '', horaInicio: '', horaFin: '' })); }}>
        <option value="">Todos los médicos</option>{medicos.map((medico) => <option value={medico.id} key={medico.id}>{medico.nombre}</option>)}</select></label>}
      <p className="atencion-ayuda">Zona horaria: {zona}</p>
        <button className="boton boton--fantasma" disabled={consulta.cargando || operacion.guardando} onClick={recargar}><RefreshCw size={16} /> Actualizar</button></div>
    {mensaje && <p className="agenda-resultado" role="status">{mensaje}</p>}
    {consulta.cargando && <p className="atencion-ayuda" role="status">Cargando turnos y citas del mes…</p>}
    {consulta.error && <div className="atencion-aviso" role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" onClick={recargar}>Reintentar</button></div>}
    <div className="agenda-distribucion"><div className="agenda-distribucion__calendario">
      <CalendarioCitas datos={consulta.datos} periodo={periodo} fecha={fecha} hoy={fechaActual(zona)} profesional={profesional}
        seleccionar={elegirFecha} cambiarMes={(mes) => elegirFecha(`${mes}-01`)} ocupado={operacion.guardando} cargando={consulta.cargando} excluir={reserva.cita?.id} />
      {!consulta.cargando && consulta.datos && <HorariosDiaCitas turnos={dia.turnos} citas={consulta.datos.citas} fecha={fecha} zonaHoraria={zona}
        elegir={elegirEspacio} ocupado={operacion.guardando} seleccionado={reserva.idTurno} excluir={reserva.cita?.id}
        verCita={(cita) => cita.estado === 'programada' ? actuar(cita, 'reprogramar') : cambiarDetalle(cita)} />}
      {consulta.datos && <details className="panel agenda-listado"><summary>Ver citas del día ({dia.citas.length})</summary>
        <TablaCitas citas={dia.citas} zonaHoraria={zona} actuar={actuar} ocupado={operacion.guardando} /></details>}
    </div><PanelReservaCita referencia={panel} reserva={reserva} cambiar={(valor) => { cambiarReserva(valor); operacion.limpiar(); }} fecha={fecha}
      turnos={dia.turnos} citas={consulta.datos?.citas || []} zonaHoraria={zona} ocupado={operacion.guardando} cargando={consulta.cargando || Boolean(consulta.error)}
      error={operacion.error} errores={operacion.errores} guardar={guardar} nueva={nueva} />
    </div>
    {detalle && <VentanaPlanificador titulo="Detalle de la cita" descripcion={detalle.paciente} cerrar={() => cambiarDetalle(null)}>
      <p>{detalle.profesional} · {detalle.consultorio}</p><p>{partesLocales(detalle.inicio, zona).hora} a {partesLocales(detalle.fin, zona).hora} · {etiquetasEstadoCita[detalle.estado]}</p>
    </VentanaPlanificador>}
    {confirmacion && <VentanaPlanificador titulo="Actualizar estado de la cita" descripcion={`${confirmacion.cita.paciente} · ${etiquetasEstadoCita[confirmacion.estado]}`} ocupado={operacion.guardando} cerrar={() => cambiarConfirmacion(null)}>
      <p>El estado final quedará en el historial de citas del paciente.</p>
      {operacion.error && <p role="alert" className="atencion-error">{operacion.error}</p>}
      <div className="ventana-planificador__acciones"><button className="boton boton--secundario" disabled={operacion.guardando} onClick={() => cambiarConfirmacion(null)}>Volver</button>
        <button className="boton boton--primario" disabled={operacion.guardando} onClick={confirmar}>{operacion.guardando ? 'Guardando…' : 'Confirmar estado'}</button></div>
    </VentanaPlanificador>}
  </>;
}
