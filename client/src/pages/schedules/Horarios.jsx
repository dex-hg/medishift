import { useEffect, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, FileDown, FolderPlus, RefreshCw } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import ResumenFeriados from '../../components/ResumenFeriados';
import usePlanificadorMensual from '../../hooks/usePlanificadorMensual';
import { crearConsultorioEnArea, eliminarTurnoMensual, guardarArea, guardarTurnoMensual, vincularConsultorio } from '../../services/planificador';
import { desplazarMes, especialidadesCompatibles, etiquetasDelArea, nombreMes, nombreUnidad } from '../../utils/calendarioMensual';
import { fechaActual } from '../../utils/validacionesProgramacion';
import ArbolAreas from './ArbolAreas';
import BancoProfesionales from './BancoProfesionales';
import CalendarioMensual from './CalendarioMensual';
import DialogoTurnoMensual from './DialogoTurnoMensual';
import DialogoAreaConsultorio from './DialogoAreaConsultorio';
import DialogoReporteMensual from './DialogoReporteMensual';
import ControlAprobacionMensual from './ControlAprobacionMensual';

export default function Horarios() {
  const { sesion } = useOutletContext();
  const consulta = usePlanificadorMensual();
  const [seleccion, cambiarSeleccion] = useState({ area: '', consultorio: '' });
  const idConsultorio = seleccion.consultorio;
  const [profesionalElegido, elegirProfesional] = useState('');
  const [editor, setEditor] = useState(null);
  const [accion, setAccion] = useState(null);
  const [reporteAbierto, setReporteAbierto] = useState(false);
  const { datos, periodo, cargando, guardando } = consulta;
  const ocupado = cargando || guardando;
  const esAdministrador = datos?.permisos.esAdministrador ?? sesion.esAdministrador;
  const profesionalPropio = datos?.profesionales.find((persona) => persona.id === datos.permisos.idProfesional);
  const consultoriosVisibles = datos?.consultorios.filter((sala) => esAdministrador || (sala.idArea
    && (especialidadesCompatibles(profesionalPropio, sala).length || datos.turnos.some((turno) => turno.idConsultorio === sala.id)))) || [];
  const unidadesConSalas = new Set(consultoriosVisibles.map((sala) => sala.idArea));
  const areasVisibles = datos?.areas.filter((unidad) => esAdministrador || unidadesConSalas.has(unidad.id)
    || datos.areas.some((subarea) => subarea.idAreaPadre === unidad.id && unidadesConSalas.has(subarea.id))) || [];
  const consultorio = datos?.consultorios.find((registro) => registro.id === idConsultorio);
  const area = datos?.areas.find((registro) => registro.id === seleccion.area);
  const plan = datos?.planes.find((registro) => registro.idUnidad === area?.id && ['Borrador', 'Pendiente', 'Aprobado'].includes(registro.estado));
  const salasArea = consultoriosVisibles.filter((sala) => sala.idArea === area?.id && sala.estado === 'Activo'
    && (esAdministrador ? sala.especialidades.length : especialidadesCompatibles(profesionalPropio, sala).length));
  const salasAsignables = consultorio ? salasArea.filter((sala) => sala.id === consultorio.id) : salasArea;
  const editable = datos?.permisos.puedeProgramar && area?.estado === 'Activo' && (!plan || plan.estado === 'Borrador')
    && salasAsignables.length > 0;
  const profesionalesActivos = esAdministrador && datos ? datos.profesionales.filter((persona) => persona.estado === 'Activo') : [];
  const profesionalesCompatibles = profesionalesActivos.filter((persona) => salasAsignables.some((sala) =>
    especialidadesCompatibles(persona, sala).length > 0));
  const idProfesional = esAdministrador
    ? profesionalesCompatibles.find((persona) => persona.id === profesionalElegido)?.id || ''
    : datos?.permisos.idProfesional || '';
  const turnosVisibles = datos?.turnos.filter((turno) => idConsultorio ? turno.idConsultorio === idConsultorio : turno.idArea === area?.id) || [];
  const hoy = fechaActual(datos?.zonaHoraria);

  useEffect(() => {
    if (!datos) return;
    cambiarSeleccion((anterior) => {
      if (anterior.consultorio) {
        const sala = consultoriosVisibles.find((registro) => registro.id === anterior.consultorio && registro.idArea);
        if (sala) return { area: sala.idArea, consultorio: sala.id };
      }
      if (areasVisibles.some((unidad) => unidad.id === anterior.area)) return { area: anterior.area, consultorio: '' };
      const sala = consultoriosVisibles.find((registro) => registro.estado === 'Activo' && registro.idArea);
      return sala ? { area: sala.idArea, consultorio: sala.id } : { area: areasVisibles[0]?.id || '', consultorio: '' };
    });
  }, [datos]);

  useEffect(() => {
    if (datos && esAdministrador && profesionalElegido && !idProfesional) elegirProfesional('');
  }, [datos, esAdministrador, profesionalElegido, idProfesional]);

  const cambiarMes = (valor) => { if (!ocupado && valor) { setEditor(null); consulta.cambiarPeriodo(valor); } };
  const abrir = (fecha, turno, profesional) => {
    if (ocupado || !area) return;
    consulta.limpiarError();
    setEditor({ fecha, turno, area, consultorio: consultorio ?? datos.consultorios.find((sala) => sala.id === turno?.idConsultorio),
      profesional: profesionalPropio || profesional || datos.profesionales.find((registro) => registro.id === (turno?.idProfesional || idProfesional)) });
  };
  const soltar = (contenido, fecha) => {
    if (contenido.tipo === 'profesional') {
      const profesional = profesionalesCompatibles.find((registro) => registro.id === contenido.id);
      if (profesional) abrir(fecha, null, profesional);
    } else {
      const turno = datos.turnos.find((registro) => registro.id === contenido.id && registro.estado === 'Borrador');
      if (turno) abrir(fecha, turno);
    }
  };
  const guardarTurno = async (formulario) => {
    const exito = await consulta.ejecutar((opciones) => guardarTurnoMensual(formulario, { ...opciones,
      id: editor.turno?.id, revision: editor.turno?.revision }), editor.turno ? 'Turno actualizado en el mes.' : 'Turno guardado en el mes.');
    if (exito) {
      if (editor.turno?.idArea && editor.turno.idArea !== formulario.idArea) {
        cambiarSeleccion({ area: formulario.idArea, consultorio: formulario.idConsultorio });
      }
      setEditor(null);
    }
  };
  const borrarTurno = async () => {
    if (await consulta.ejecutar((opciones) => eliminarTurnoMensual(editor.turno.id, { ...opciones, revision: editor.turno.revision }), 'Turno eliminado del mes.')) setEditor(null);
  };
  const guardarOrganizacion = async (formulario) => {
    const operacion = accion.tipo === 'area' ? (opciones) => guardarArea(formulario, opciones)
      : accion.tipo === 'editar-area' ? (opciones) => guardarArea(formulario, { ...opciones, id: formulario.idArea, revision: formulario.revision })
      : accion.tipo === 'vincular' ? (opciones) => vincularConsultorio(formulario.idConsultorio, formulario.idArea, opciones)
        : (opciones) => crearConsultorioEnArea({ idArea: formulario.idArea, codigo: formulario.codigo.trim(),
          nombre: formulario.nombre.trim(), ubicacion: formulario.ubicacion.trim(), idsEtiquetas: formulario.idsEtiquetas }, opciones);
    if (await consulta.ejecutar(operacion, 'La configuración se guardó.')) setAccion(null);
  };

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Horarios' }]} titulo={esAdministrador ? "Horarios mensuales" : "Mis horarios"}
      descripcion={esAdministrador ? "Organiza a tus profesionales por área, consultorio y día. Todo el mes en un solo calendario." : "Elige fecha, horas y un consultorio compatible para organizar tus turnos."}
      acciones={sesion.esAdministrador && <>
        <button className="boton boton--secundario" type="button" disabled={ocupado || !datos || Boolean(consulta.errorCarga) || datos?.feriados.estado !== 'Disponible'}
          onClick={() => setReporteAbierto(true)}><FileDown size={17} /> Exportar horario</button>
        <button className="boton boton--secundario" type="button" disabled={ocupado || !datos}
          onClick={() => { consulta.limpiarError(); setAccion({ tipo: 'area' }); }}><FolderPlus size={17} /> Nueva área</button></>} />
    <div className="panel barra-mensual"><div className="barra-mensual__periodo"><span className="barra-mensual__icono"><CalendarDays size={23} /></span>
      <div><small>Planificación mensual</small><h2>{nombreMes(periodo)}</h2></div>
      <button className="boton-icono" type="button" disabled={ocupado} aria-label="Mes anterior" onClick={() => cambiarMes(desplazarMes(periodo, -1))}><ChevronLeft size={18} /></button>
      <label><span className="solo-lectores">Mes que deseas organizar</span><input type="month" min="0001-01" max="9999-12" value={periodo} disabled={ocupado}
        onChange={(evento) => cambiarMes(evento.target.value)} /></label>
      <button className="boton-icono" type="button" disabled={ocupado} aria-label="Mes siguiente" onClick={() => cambiarMes(desplazarMes(periodo, 1))}><ChevronRight size={18} /></button>
    </div><button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={consulta.recargar}><RefreshCw size={15} /> Actualizar</button></div>
    {datos && <ControlAprobacionMensual plan={plan} area={area} datos={datos} consulta={consulta} />}
    {cargando && <p className="planificador-aviso" role="status">Cargando el mes y sus consultorios…</p>}
    {consulta.errorCarga && <div className="planificador-error" role="alert"><p>{consulta.errorCarga}</p><button className="boton boton--secundario" type="button" onClick={consulta.recargar}>Volver a intentar</button></div>}
    {consulta.mensaje && <p className="planificador-exito" role="status">{consulta.mensaje}</p>}
    {datos && !consulta.errorCarga && esAdministrador && <ResumenFeriados feriados={datos.feriados} error={consulta.errorFeriados || consulta.error}
      ocupado={ocupado} actualizar={consulta.actualizarFeriados} />}
    {datos && !consulta.errorCarga && <div className="planificador" aria-busy={ocupado}>
      <aside className="planificador__lateral"><ArbolAreas areas={areasVisibles} consultorios={consultoriosVisibles}
        seleccionado={idConsultorio} idArea={area?.id}
        seleccionar={(id) => cambiarSeleccion({ area: datos.consultorios.find((sala) => sala.id === id).idArea, consultorio: id })}
        seleccionarArea={(id) => cambiarSeleccion({ area: id, consultorio: '' })} ocupado={ocupado}
        agregar={esAdministrador ? (valor) => { consulta.limpiarError(); setAccion(valor); } : undefined} />
        {esAdministrador ? <BancoProfesionales profesionales={profesionalesCompatibles} hayActivos={profesionalesActivos.length > 0}
          turnos={datos.turnos} feriados={datos.feriados} idConsultorio={idConsultorio} idArea={area?.id}
          seleccionado={idProfesional} seleccionar={elegirProfesional} ocupado={ocupado} editable={editable} />
          : <section className="panel planificador-arbol"><h2>{profesionalPropio?.nombre}</h2><p>Solo puedes modificar tus propios turnos. Los bloques ocupados de otros profesionales se muestran para elegir tus horas.</p>
            {!datos.permisos.puedeProgramar && <p className="planificador-aviso" role="status">La autoprogramación está desactivada. Puedes consultar tus horarios. Solicita los cambios al administrador.</p>}
            {!consultoriosVisibles.length && <p role="status">No hay consultorios disponibles para tus etiquetas. Solicita al administrador que revise tu perfil.</p>}</section>}</aside>
      <div className="planificador__principal"><CalendarioMensual periodo={periodo} consultorio={consultorio} area={area} hoy={hoy}
        nombreArea={area && nombreUnidad(area, datos.areas)} turnos={turnosVisibles} ocupaciones={datos.ocupaciones}
        zonaHoraria={datos.zonaHoraria} feriados={datos.feriados} seleccionado={idProfesional}
        asignar={(fecha) => abrir(fecha)} editar={(turno) => abrir(turno.fecha, turno)} soltar={soltar} ocupado={ocupado} editable={editable} />
        {esAdministrador && area && !etiquetasDelArea(area).length && <div className="planificador-error" role="status">
          <p>Configura las etiquetas de {area.nombre} para programar turnos.</p>
          <button className="boton boton--secundario" type="button" disabled={ocupado}
            onClick={() => { consulta.limpiarError(); setAccion({ tipo: 'editar-area', idArea: area.id }); }}>Configurar etiquetas</button></div>}
        {area && <p className="planificador-pie"><strong>{turnosVisibles.length} turnos en {consultorio ? 'este consultorio' : 'esta unidad'}.</strong> Pulsa un turno para ver sus datos.</p>}
        <p className="planificador-ayuda">Cada turno se guarda al confirmar. El consultorio queda reservado durante las horas del turno. Zona horaria: {datos.zonaHoraria}.</p>
      </div>
    </div>}
    {editor && datos && <DialogoTurnoMensual editor={editor} datos={datos} periodo={periodo} hoy={hoy} ocupado={guardando || cargando}
      error={consulta.error || consulta.errorCarga} errores={consulta.errores} guardar={guardarTurno} eliminar={borrarTurno} cerrar={() => setEditor(null)} recargar={consulta.recargar}
      actualizarRevision={(turno) => { setEditor((anterior) => ({ ...anterior, turno })); consulta.limpiarError(); }} />}
    {accion && datos && <DialogoAreaConsultorio accion={accion} datos={datos} ocupado={guardando || cargando} error={consulta.error || consulta.errorCarga}
      errores={consulta.errores} guardar={guardarOrganizacion} cerrar={() => setAccion(null)} recargar={consulta.recargar} />}
    {reporteAbierto && datos && <DialogoReporteMensual datos={datos} periodo={periodo} idConsultorio={idConsultorio}
      idProfesional={idProfesional} institucion={sesion.nombreInstitucion} cerrar={() => setReporteAbierto(false)} />}
  </>;
}
