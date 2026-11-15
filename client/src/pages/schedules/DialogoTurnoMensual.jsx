import { useEffect, useRef, useState } from 'react';
import { MoveRight, Trash2 } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import VentanaPlanificador from './VentanaPlanificador';
import { diasDelMes, elegirEtiqueta, especialidadesCompatibles, etiquetasDelArea, etiquetasDisponibles, nombreMes, nombreUnidad,
  profesionalAdmitido, turnoInicial } from '../../utils/calendarioMensual';
import { duracionTurno, validarTurno } from '../../utils/validacionesProgramacion';
import { buscarFeriado } from '../../utils/feriados';
import { consultorioOcupado, reservasDelDia } from '../../utils/autoprogramacion';

function Campo({ etiqueta, campo, errores, children }) {
  return <label className="campo-planificador"><span>{etiqueta}</span>{children}
    {errores[campo] && <small className="campo-planificador__error" role="alert">{errores[campo]}</small>}</label>;
}

export default function DialogoTurnoMensual({ editor, datos, periodo, hoy, ocupado, error, errores: erroresServidor,
  guardar, eliminar, cerrar, actualizarRevision, recargar }) {
  const { cuenta } = useOutletContext();
  const [formulario, setFormulario] = useState(() => turnoInicial(editor, cuenta.preferencias));
  const [erroresLocales, setErroresLocales] = useState({});
  const [confirmaEliminar, setConfirmaEliminar] = useState(false);
  const [moviendo, cambiarMoviendo] = useState(false);
  const campoDestino = useRef(null);
  useEffect(() => { if (moviendo) campoDestino.current?.focus(); }, [moviendo]);
  const errores = { ...erroresServidor, ...erroresLocales };
  const profesional = datos.profesionales.find((registro) => registro.id === formulario.idProfesional);
  const consultorio = datos.consultorios.find((registro) => registro.id === formulario.idConsultorio);
  const areaSeleccionada = datos.areas.find((registro) => registro.id === formulario.idArea);
  const etiquetas = etiquetasDisponibles(profesional, areaSeleccionada, consultorio);
  const plan = datos.planes.find((registro) => registro.id === editor.turno?.idPlan);
  const editable = datos.permisos.puedeProgramar && (!editor.turno || editor.turno.fecha >= hoy) && (!editor.turno || editor.turno.estado === 'Borrador')
    && (!plan || plan.estado === 'Borrador') && editor.turno?.asistencia !== 'Falta';
  const turnoActual = editor.turno && datos.turnos.find((registro) => registro.id === editor.turno.id);
  const desactualizado = editor.turno && (!turnoActual || turnoActual.revision !== editor.turno.revision
    || turnoActual.revisionAsistencia !== editor.turno.revisionAsistencia);
  const esTraslado = Boolean(editor.turno && formulario.idArea !== editor.turno.idArea);
  const areasPosibles = datos.areas.filter((area) => area.id === formulario.idArea
    || (area.estado === 'Activo' && etiquetasDelArea(area).length && (!profesional || profesionalAdmitido(profesional, area))));
  const destinoBloqueado = (id) => datos.planes.some((mes) => mes.idUnidad === id && ['Aprobado', 'Pendiente'].includes(mes.estado));
  const cambiar = (campo, valor) => {
    setErroresLocales({});
    setFormulario((anterior) => {
      const siguiente = { ...anterior, [campo]: valor };
      if (campo === 'idArea') siguiente.idConsultorio = '';
      if (['idArea', 'idProfesional', 'idConsultorio'].includes(campo)) {
        const persona = datos.profesionales.find((registro) => registro.id === siguiente.idProfesional);
        const unidad = datos.areas.find((registro) => registro.id === siguiente.idArea);
        const espacio = datos.consultorios.find((registro) => registro.id === siguiente.idConsultorio);
        if (espacio && persona && !especialidadesCompatibles(persona, espacio).length) siguiente.idConsultorio = '';
        const compatibles = etiquetasDisponibles(persona, unidad, siguiente.idConsultorio ? espacio : null);
        siguiente.idEspecialidad = elegirEtiqueta(siguiente.idEspecialidad, compatibles);
      }
      return siguiente;
    });
  };
  const enviar = (evento) => {
    evento.preventDefault();
    if (!editable || desactualizado || ocupado) return;
    const validaciones = validarTurno(formulario, { recursos: datos, turnos: datos.turnos, idActual: editor.turno?.id });
    if (consultorioOcupado(formulario, datos.ocupaciones, datos.zonaHoraria, editor.turno?.id)) validaciones.idConsultorio = 'El consultorio está ocupado durante esas horas. Elige otro consultorio u otro horario.';
    if (!formulario.idArea) validaciones.idArea = 'Selecciona el área del consultorio.';
    else if (destinoBloqueado(formulario.idArea)) validaciones.idArea = 'El plan mensual de esta unidad debe estar en borrador.';
    if (!formulario.fecha.startsWith(`${periodo}-`)) validaciones.fecha = 'La fecha debe pertenecer al mes que estás organizando.';
    if (formulario.fecha < hoy) validaciones.fecha = 'Selecciona hoy o una fecha futura.';
    setErroresLocales(validaciones);
    if (!Object.keys(validaciones).length) guardar(formulario);
  };
  const duracion = duracionTurno(formulario);
  return <VentanaPlanificador titulo={moviendo ? 'Mover turno a otra área'
    : datos.permisos.esAdministrador ? (editor.turno ? 'Revisar turno mensual' : 'Asignar profesional') : (editor.turno ? 'Revisar mi turno' : 'Crear mi turno')}
    descripcion={`${nombreMes(periodo)} · Revisa el lugar y las horas antes de guardar.`} ocupado={ocupado} cerrar={cerrar}>
    <form onSubmit={enviar} className="formulario-planificador">
      {error && <div className="planificador-error" role="alert"><p>{error}</p>
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={recargar}>Actualizar calendario conservando mis entradas</button></div>}
      {desactualizado && <div className="planificador-error" role="alert"><p>Este turno cambió o se eliminó desde que lo abriste. Tus entradas se conservan.</p>
        {turnoActual ? <button type="button" className="boton boton--fantasma" onClick={() => actualizarRevision(turnoActual)}>Revisar con la versión actual</button>
          : <p>Cierra esta ventana y revisa el calendario actualizado.</p>}</div>}
      {!editable && <p className="planificador-ayuda">Este turno se muestra en modo de consulta. Solo puedes editar borradores con fecha vigente.</p>}
      {editor.turno && <p className={`asistencia-estado ${editor.turno.asistencia === 'Falta' ? 'asistencia-estado--falta' : ''}`}>
        {editor.turno.asistencia === 'Falta' ? 'Falta registrada: este turno suma 0 horas. Restablece la asistencia antes de modificarlo.' : 'Asistencia por defecto.'}
        {datos.permisos.esAdministrador && <Link className="planificador-enlace" to={`/faltas?periodo=${periodo}&buscar=${encodeURIComponent(editor.turno.profesional)}`}>Gestionar asistencia</Link>}</p>}
      <fieldset disabled={ocupado || !editable} className="formulario-planificador__campos">
        {editor.turno && !moviendo && editable && <button type="button" className="boton boton--secundario"
          onClick={() => cambiarMoviendo(true)}><MoveRight size={16} aria-hidden="true" /> Mover a otra área</button>}
        <Campo etiqueta="Profesional" campo="idProfesional" errores={errores}><select required disabled={!datos.permisos.esAdministrador} value={formulario.idProfesional} onChange={(evento) => cambiar('idProfesional', evento.target.value)}>
          <option value="">Selecciona un profesional</option>{datos.profesionales.filter((registro) => registro.id === formulario.idProfesional
            || profesionalAdmitido(registro, areaSeleccionada))
            .map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre}</option>)}
        </select></Campo>
        <div className="formulario-planificador__fila"><Campo etiqueta={moviendo ? 'Área o subárea de destino' : 'Área o subárea'} campo="idArea" errores={errores}>
          <select ref={campoDestino} required disabled={Boolean(editor.turno && !moviendo)} value={formulario.idArea}
            onChange={(evento) => cambiar('idArea', evento.target.value)}>
            {areasPosibles.map((area) => <option key={area.id} value={area.id} disabled={area.id !== formulario.idArea && destinoBloqueado(area.id)}>
              {area.nombre}{area.idAreaPadre ? ' · ' + datos.areas.find((padre) => padre.id === area.idAreaPadre)?.nombre : ''}
              {destinoBloqueado(area.id) ? ' · Plan no editable' : ''}
            </option>)}
          </select>
          {areaSeleccionada?.idAreaPadre && <small>{nombreUnidad(areaSeleccionada, datos.areas)}</small>}
        </Campo>
        <Campo etiqueta="Consultorio" campo="idConsultorio" errores={errores}><select required value={formulario.idConsultorio} onChange={(evento) => cambiar('idConsultorio', evento.target.value)}>
          <option value="">Selecciona un consultorio</option>{datos.consultorios.filter((registro) => registro.id === formulario.idConsultorio
            || (registro.idArea === formulario.idArea && registro.estado === 'Activo' && registro.especialidades.length
              && (!profesional || especialidadesCompatibles(profesional, registro).length)))
            .map((registro) => { const ocupado = consultorioOcupado({ ...formulario, idConsultorio: registro.id }, datos.ocupaciones, datos.zonaHoraria, editor.turno?.id);
              return <option key={registro.id} value={registro.id} disabled={ocupado}>{registro.nombre}{ocupado ? ' · Ocupado en estas horas' : ''}</option>; })}
        </select></Campo></div>
        <Campo etiqueta="Etiqueta de atención" campo="idEspecialidad" errores={errores}><select required value={formulario.idEspecialidad} onChange={(evento) => cambiar('idEspecialidad', evento.target.value)}>
          <option value="">{etiquetas.length ? 'Selecciona la etiqueta' : 'Sin etiquetas compatibles'}</option>
          {!editable && editor.turno && !etiquetas.some((etiqueta) => etiqueta.id === editor.turno.idEspecialidad)
            && <option value={editor.turno.idEspecialidad}>{editor.turno.especialidad}</option>}
          {etiquetas.map((etiqueta) => <option key={etiqueta.id} value={etiqueta.id}>{etiqueta.nombre}</option>)}</select></Campo>
        <div className="formulario-planificador__fila formulario-planificador__fila--horas">
          <Campo etiqueta="Día" campo="fecha" errores={errores}><input required type="date" min={hoy > `${periodo}-01` ? hoy : `${periodo}-01`}
            max={diasDelMes(periodo).at(-1)} value={formulario.fecha} onChange={(evento) => cambiar('fecha', evento.target.value)} /></Campo>
          <Campo etiqueta="Desde" campo="horaInicio" errores={errores}><input required type="time" value={formulario.horaInicio} onChange={(evento) => cambiar('horaInicio', evento.target.value)} /></Campo>
          <Campo etiqueta="Hasta" campo="horaFin" errores={errores}><input required type="time" value={formulario.horaFin} onChange={(evento) => cambiar('horaFin', evento.target.value)} /></Campo>
        </div>
        <Campo etiqueta="Nota (opcional)" campo="observacion" errores={errores}><textarea rows={2} maxLength={180} value={formulario.observacion}
          onChange={(evento) => cambiar('observacion', evento.target.value)} placeholder="Una indicación breve para este turno" /></Campo>
      </fieldset>
      {consultorioOcupado(formulario, datos.ocupaciones, datos.zonaHoraria, editor.turno?.id) && <p className="planificador-error" role="alert">El consultorio seleccionado está ocupado en esas horas. Cambia las horas o el consultorio antes de guardar.</p>}
      {reservasDelDia(datos.ocupaciones, formulario.idConsultorio, formulario.fecha, datos.zonaHoraria, [editor.turno?.id]).length > 0 && <p className="planificador-ayuda">Horas ya ocupadas: {reservasDelDia(datos.ocupaciones, formulario.idConsultorio, formulario.fecha, datos.zonaHoraria, [editor.turno?.id]).map((reserva) => reserva.horaInicio + ' a ' + reserva.horaFin).join(', ')}.</p>}
      <p className="planificador-ayuda">{duracion > 0 ? `${(duracion / 60).toLocaleString('es-PE')} ${duracion === 60 ? 'hora' : 'horas'}. ` : ''}El sistema comprueba cruces y los límites institucionales de {(datos.politica.minutosDiarios / 60).toLocaleString('es-PE')} h al día y {(datos.politica.minutosSemanales / 60).toLocaleString('es-PE')} h por semana.</p>
      {buscarFeriado(datos.feriados, formulario.fecha) && <p className="planificador-ayuda">Feriado no laborable: se acreditan {datos.feriados.minutosPorFeriado / 60} h institucionales al profesional, reemplazando las horas de sus turnos de ese día.</p>}
      {confirmaEliminar && <div className="planificador-error"><p>¿Eliminar este turno del borrador mensual?</p>
        <button className="boton boton--peligro" type="button" disabled={ocupado || desactualizado} onClick={eliminar}>Confirmar eliminación</button>
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={() => setConfirmaEliminar(false)}>Conservar turno</button></div>}
      <div className="ventana-planificador__acciones">
        {editor.turno && editable && !confirmaEliminar && <button className="boton boton--fantasma" type="button" disabled={ocupado || desactualizado}
          onClick={() => setConfirmaEliminar(true)}><Trash2 size={16} /> Eliminar</button>}
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={cerrar}>{editable ? 'Cancelar' : 'Cerrar'}</button>
        {editable && <button className="boton boton--primario" type="submit" disabled={ocupado || desactualizado || consultorioOcupado(formulario, datos.ocupaciones, datos.zonaHoraria, editor.turno?.id)}>
          {ocupado ? 'Guardando…' : esTraslado ? 'Mover turno' : editor.turno ? 'Guardar cambios' : 'Guardar turno'}
        </button>}
      </div>
    </form>
  </VentanaPlanificador>;
}
