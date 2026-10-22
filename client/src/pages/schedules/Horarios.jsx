import { useEffect, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, FolderPlus, RefreshCw } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import usePlanificadorMensual from '../../hooks/usePlanificadorMensual';
import { crearUnidad } from '../../services/planesMensuales';
import { crearConsultorioEnArea, eliminarTurnoMensual, guardarTurnoMensual, vincularConsultorio } from '../../services/planificador';
import { desplazarMes, nombreMes } from '../../utils/calendarioMensual';
import { fechaActual } from '../../utils/validacionesProgramacion';
import ArbolAreas from './ArbolAreas';
import BancoProfesionales from './BancoProfesionales';
import CalendarioMensual from './CalendarioMensual';
import DialogoTurnoMensual from './DialogoTurnoMensual';
import DialogoAreaConsultorio from './DialogoAreaConsultorio';

export default function Horarios() {
  const { sesion } = useOutletContext();
  const consulta = usePlanificadorMensual();
  const [idConsultorio, setIdConsultorio] = useState('');
  const [idProfesional, setIdProfesional] = useState('');
  const [editor, setEditor] = useState(null);
  const [accion, setAccion] = useState(null);
  const { datos, periodo, cargando, guardando } = consulta;
  const ocupado = cargando || guardando;
  const consultorio = datos?.consultorios.find((registro) => registro.id === idConsultorio);
  const area = datos?.areas.find((registro) => registro.id === consultorio?.idArea);
  const plan = datos?.planes.find((registro) => registro.idUnidad === area?.id && ['Borrador', 'Pendiente', 'Aprobado'].includes(registro.estado));
  const editable = consultorio?.estado === 'Activo' && area?.estado === 'Activo' && (!plan || plan.estado === 'Borrador');
  const hoy = fechaActual(datos?.zonaHoraria);

  useEffect(() => {
    if (!datos) return;
    setIdConsultorio((anterior) => datos.consultorios.some((registro) => registro.id === anterior && registro.idArea)
      ? anterior : datos.consultorios.find((registro) => registro.estado === 'Activo' && registro.idArea)?.id || '');
  }, [datos]);

  const cambiarMes = (valor) => { if (!ocupado && valor) { setEditor(null); consulta.cambiarPeriodo(valor); } };
  const abrir = (fecha, turno, profesional) => {
    if (ocupado || !consultorio) return;
    consulta.limpiarError();
    setEditor({ fecha, turno, consultorio, profesional: profesional || datos.profesionales.find((registro) => registro.id === (turno?.idProfesional || idProfesional)) });
  };
  const soltar = (contenido, fecha) => {
    if (contenido.tipo === 'profesional') {
      const profesional = datos.profesionales.find((registro) => registro.id === contenido.id && registro.estado === 'Activo');
      if (profesional) abrir(fecha, null, profesional);
    } else {
      const turno = datos.turnos.find((registro) => registro.id === contenido.id && registro.estado === 'Borrador');
      if (turno) abrir(fecha, turno);
    }
  };
  const guardarTurno = async (formulario) => {
    const exito = await consulta.ejecutar((opciones) => guardarTurnoMensual(formulario, { ...opciones,
      id: editor.turno?.id, revision: editor.turno?.revision }), editor.turno ? 'Turno actualizado en el mes.' : 'Turno guardado en el mes.');
    if (exito) setEditor(null);
  };
  const borrarTurno = async () => {
    if (await consulta.ejecutar((opciones) => eliminarTurnoMensual(editor.turno.id, { ...opciones, revision: editor.turno.revision }), 'Turno eliminado del mes.')) setEditor(null);
  };
  const guardarOrganizacion = async (formulario) => {
    const operacion = accion.tipo === 'area' ? (opciones) => crearUnidad({ codigo: formulario.codigo, nombre: formulario.nombre, tipo: 'Servicio' }, opciones)
      : accion.tipo === 'vincular' ? (opciones) => vincularConsultorio(formulario.idConsultorio, formulario.idArea, opciones)
        : (opciones) => crearConsultorioEnArea({ idArea: formulario.idArea, codigo: formulario.codigo.trim(), nombre: formulario.nombre.trim(), ubicacion: formulario.ubicacion.trim() }, opciones);
    if (await consulta.ejecutar(operacion, 'La organización de consultorios se guardó.')) setAccion(null);
  };

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Horarios' }]} titulo="Horarios mensuales"
      descripcion="Organiza a tus profesionales por área, consultorio y día. Todo el mes en un solo calendario."
      acciones={sesion.esAdministrador && <button className="boton boton--secundario" type="button" disabled={ocupado || !datos}
        onClick={() => { consulta.limpiarError(); setAccion({ tipo: 'area' }); }}><FolderPlus size={17} /> Nueva área</button>} />
    <div className="panel barra-mensual"><div className="barra-mensual__periodo"><span className="barra-mensual__icono"><CalendarDays size={23} /></span>
      <div><small>Planificación mensual</small><h2>{nombreMes(periodo)}</h2></div>
      <button className="boton-icono" type="button" disabled={ocupado} aria-label="Mes anterior" onClick={() => cambiarMes(desplazarMes(periodo, -1))}><ChevronLeft size={18} /></button>
      <label><span className="solo-lectores">Mes que deseas organizar</span><input type="month" min="0001-01" max="9999-12" value={periodo} disabled={ocupado}
        onChange={(evento) => cambiarMes(evento.target.value)} /></label>
      <button className="boton-icono" type="button" disabled={ocupado} aria-label="Mes siguiente" onClick={() => cambiarMes(desplazarMes(periodo, 1))}><ChevronRight size={18} /></button>
    </div><button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={consulta.recargar}><RefreshCw size={15} /> Actualizar</button></div>
    {cargando && <p className="planificador-aviso" role="status">Cargando el mes y sus consultorios…</p>}
    {consulta.errorCarga && <div className="planificador-error" role="alert"><p>{consulta.errorCarga}</p><button className="boton boton--secundario" type="button" onClick={consulta.recargar}>Volver a intentar</button></div>}
    {consulta.mensaje && <p className="planificador-exito" role="status">{consulta.mensaje} Los cambios se conservan como borrador mensual.</p>}
    {datos && !consulta.errorCarga && sesion.esAdministrador && <div className="planificador" aria-busy={ocupado}>
      <aside className="planificador__lateral"><ArbolAreas areas={datos.areas} consultorios={datos.consultorios}
        seleccionado={idConsultorio} seleccionar={(valor) => { setIdConsultorio(valor); setIdProfesional(''); }} ocupado={ocupado}
        agregar={(valor) => { consulta.limpiarError(); setAccion(valor); }} />
        <BancoProfesionales profesionales={datos.profesionales} turnos={datos.turnos} consultorio={consultorio}
          seleccionado={idProfesional} seleccionar={setIdProfesional} ocupado={ocupado || !editable} /></aside>
      <div className="planificador__principal"><CalendarioMensual periodo={periodo} consultorio={consultorio} area={area} hoy={hoy}
        turnos={datos.turnos.filter((turno) => turno.idConsultorio === idConsultorio)} seleccionado={idProfesional}
        asignar={(fecha) => abrir(fecha)} editar={(turno) => abrir(turno.fecha, turno)} soltar={soltar} ocupado={ocupado} editable={editable} />
        <p className="planificador-pie"><strong>{datos.turnos.filter((turno) => turno.idConsultorio === idConsultorio).length} turnos en este consultorio.</strong> Arrastra un turno a otro día para moverlo; pulsa sobre él para editarlo o eliminarlo.</p>
        <p className="planificador-ayuda">Cada turno se guarda al confirmar. La aprobación mensual todavía está pendiente. Zona horaria: {datos.zonaHoraria}.</p>
      </div>
    </div>}
    {!sesion.esAdministrador && <p className="planificador-error">Tu cuenta necesita el permiso de administrador para organizar los horarios mensuales.</p>}
    <details className="planificador-legado"><summary>Consultar programación semanal anterior</summary><p>Los horarios semanales anteriores son independientes del calendario mensual.</p>
      <Link className="planificador-enlace" to="/horarios/semanales">Abrir historial semanal</Link></details>
    {editor && datos && <DialogoTurnoMensual editor={editor} datos={datos} periodo={periodo} hoy={hoy} ocupado={guardando || cargando}
      error={consulta.error || consulta.errorCarga} errores={consulta.errores} guardar={guardarTurno} eliminar={borrarTurno} cerrar={() => setEditor(null)} recargar={consulta.recargar}
      actualizarRevision={(turno) => { setEditor((anterior) => ({ ...anterior, turno })); consulta.limpiarError(); }} />}
    {accion && datos && <DialogoAreaConsultorio accion={accion} datos={datos} ocupado={guardando || cargando} error={consulta.error || consulta.errorCarga}
      errores={consulta.errores} guardar={guardarOrganizacion} cerrar={() => setAccion(null)} recargar={consulta.recargar} />}
  </>;
}
