import { useCallback, useState } from 'react';
import { useLocation, useOutletContext } from 'react-router-dom';
import { Plus, RefreshCw } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import VentanaPlanificador from '../schedules/VentanaPlanificador';
import TablaCitas from './TablaCitas';
import DialogoCita from './DialogoCita';
import { useConsultaAtencion, useOperacionAtencion } from '../../hooks/useAtencion';
import { cambiarEstadoCita, guardarCita, obtenerAgenda } from '../../services/atencion';
import { etiquetasEstadoCita } from '../../utils/validacionesAtencion';
import { fechaActual } from '../../utils/validacionesProgramacion';

export default function AgendaCitas() {
  const { configuracion, sesion } = useOutletContext();
  const ubicacion = useLocation();
  const [fecha, cambiarFecha] = useState(() => fechaActual(configuracion.zonaHoraria));
  const [editor, cambiarEditor] = useState(() => ubicacion.state?.paciente ? { paciente: ubicacion.state.paciente, fecha: fechaActual(configuracion.zonaHoraria) } : null);
  const [confirmacion, cambiarConfirmacion] = useState(null);
  const [mensaje, cambiarMensaje] = useState('');
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerAgenda(fecha, opciones), [fecha]));
  const operacion = useOperacionAtencion();
  const guardar = async (datos) => {
    const resultado = await operacion.ejecutar(() => guardarCita(datos, { id: editor.cita?.id, revision: editor.cita?.revision }));
    if (resultado) { cambiarEditor(null); cambiarMensaje('La cita se guardó.'); cambiarFecha(datos.fecha); consulta.recargar(); }
  };
  const actuar = (cita, accion) => {
    operacion.limpiar(); cambiarMensaje('');
    if (accion === 'reprogramar') cambiarEditor({ cita });
    else cambiarConfirmacion({ cita, estado: accion });
  };
  const confirmar = async () => {
    const resultado = await operacion.ejecutar(() => cambiarEstadoCita(confirmacion.cita.id, confirmacion.estado, { revision: confirmacion.cita.revision }));
    if (resultado) { cambiarConfirmacion(null); cambiarMensaje('El estado de la cita se actualizó.'); consulta.recargar(); }
  };
  const recargar = () => { operacion.limpiar(); cambiarMensaje(''); consulta.recargar(); };
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Citas' }]} titulo={sesion.esAdministrador ? 'Agenda de citas' : 'Mi agenda de citas'}
      descripcion="Programa la atención de tus pacientes dentro de turnos aprobados."
      acciones={<button className="boton boton--primario" disabled={operacion.guardando} onClick={() => { operacion.limpiar(); cambiarEditor({ fecha }); }}><Plus size={18} /> Agendar cita</button>} />
    <section className="panel panel--tabla" aria-busy={consulta.cargando}>
      <div className="barra-tabla atencion-filtros"><label className="campo-planificador"><span>Fecha de la agenda</span><input type="date" value={fecha} min="0001-01-01" max="9999-12-31" disabled={operacion.guardando}
        onChange={(evento) => { if (evento.target.value) { cambiarFecha(evento.target.value); cambiarMensaje(''); } }} /></label>
        <p className="atencion-ayuda">Zona horaria: {configuracion.zonaHoraria}</p>
        <button className="boton boton--fantasma" disabled={consulta.cargando || operacion.guardando} onClick={recargar}><RefreshCw size={16} /> Actualizar</button></div>
      {mensaje && <p className="atencion-aviso" role="status">{mensaje}</p>}
      {consulta.cargando && <p className="atencion-aviso" role="status">Cargando agenda…</p>}
      {consulta.error && <div className="atencion-aviso" role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" onClick={recargar}>Reintentar</button></div>}
      {consulta.datos && <TablaCitas citas={consulta.datos.citas} zonaHoraria={consulta.datos.zonaHoraria} actuar={actuar} ocupado={operacion.guardando} />}
    </section>
    {editor && <DialogoCita editor={editor} zonaHoraria={configuracion.zonaHoraria} ocupado={operacion.guardando} error={operacion.error} errores={operacion.errores}
      guardar={guardar} cerrar={() => { cambiarEditor(null); operacion.limpiar(); consulta.recargar(); }} />}
    {confirmacion && <VentanaPlanificador titulo="Actualizar estado de la cita" descripcion={`${confirmacion.cita.paciente} · ${etiquetasEstadoCita[confirmacion.estado]}`} ocupado={operacion.guardando} cerrar={() => cambiarConfirmacion(null)}>
      <p>El estado final quedará en el historial de citas del paciente.</p>
      {operacion.error && <p role="alert" className="atencion-error">{operacion.error}</p>}
      <div className="ventana-planificador__acciones"><button className="boton boton--secundario" disabled={operacion.guardando} onClick={() => cambiarConfirmacion(null)}>Volver</button>
        <button className="boton boton--primario" disabled={operacion.guardando} onClick={confirmar}>{operacion.guardando ? 'Guardando…' : 'Confirmar estado'}</button></div>
    </VentanaPlanificador>}
  </>;
}
