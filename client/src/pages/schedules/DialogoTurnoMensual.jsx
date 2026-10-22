import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import VentanaPlanificador from './VentanaPlanificador';
import { diasDelMes, especialidadesCompatibles, nombreMes, turnoInicial } from '../../utils/calendarioMensual';
import { duracionTurno, validarTurno } from '../../utils/validacionesProgramacion';

function Campo({ etiqueta, campo, errores, children }) {
  return <label className="campo-planificador"><span>{etiqueta}</span>{children}
    {errores[campo] && <small className="campo-planificador__error" role="alert">{errores[campo]}</small>}</label>;
}

export default function DialogoTurnoMensual({ editor, datos, periodo, hoy, ocupado, error, errores: erroresServidor,
  guardar, eliminar, cerrar, actualizarRevision, recargar }) {
  const [formulario, setFormulario] = useState(() => turnoInicial(editor));
  const [erroresLocales, setErroresLocales] = useState({});
  const [confirmaEliminar, setConfirmaEliminar] = useState(false);
  const errores = { ...erroresServidor, ...erroresLocales };
  const profesional = datos.profesionales.find((registro) => registro.id === formulario.idProfesional);
  const consultorio = datos.consultorios.find((registro) => registro.id === formulario.idConsultorio);
  const especialidades = especialidadesCompatibles(profesional, consultorio);
  const plan = datos.planes.find((registro) => registro.id === editor.turno?.idPlan);
  const editable = (!editor.turno || editor.turno.fecha >= hoy) && (!editor.turno || editor.turno.estado === 'Borrador')
    && (!plan || plan.estado === 'Borrador');
  const turnoActual = editor.turno && datos.turnos.find((registro) => registro.id === editor.turno.id);
  const desactualizado = editor.turno && (!turnoActual || turnoActual.revision !== editor.turno.revision);
  const cambiar = (campo, valor) => {
    setErroresLocales({});
    setFormulario((anterior) => {
      const siguiente = { ...anterior, [campo]: valor };
      if (campo === 'idArea') { siguiente.idConsultorio = ''; siguiente.idEspecialidad = ''; }
      if (['idProfesional', 'idConsultorio'].includes(campo)) {
        const persona = datos.profesionales.find((registro) => registro.id === siguiente.idProfesional);
        const espacio = datos.consultorios.find((registro) => registro.id === siguiente.idConsultorio);
        const compatibles = especialidadesCompatibles(persona, espacio);
        siguiente.idEspecialidad = compatibles.some((registro) => registro.id === siguiente.idEspecialidad)
          ? siguiente.idEspecialidad : compatibles.length === 1 ? compatibles[0].id : '';
      }
      return siguiente;
    });
  };
  const enviar = (evento) => {
    evento.preventDefault();
    if (!editable || desactualizado || ocupado) return;
    const validaciones = validarTurno(formulario, { recursos: datos, turnos: datos.turnos, idActual: editor.turno?.id });
    if (!formulario.idArea) validaciones.idArea = 'Selecciona el área del consultorio.';
    if (!formulario.fecha.startsWith(`${periodo}-`)) validaciones.fecha = 'La fecha debe pertenecer al mes que estás organizando.';
    if (formulario.fecha < hoy) validaciones.fecha = 'Selecciona hoy o una fecha futura.';
    setErroresLocales(validaciones);
    if (!Object.keys(validaciones).length) guardar(formulario);
  };
  const duracion = duracionTurno(formulario);
  return <VentanaPlanificador titulo={editor.turno ? 'Revisar turno mensual' : 'Asignar profesional'}
    descripcion={`${nombreMes(periodo)} · Revisa el lugar y las horas antes de guardar.`} ocupado={ocupado} cerrar={cerrar}>
    <form onSubmit={enviar} className="formulario-planificador">
      {error && <div className="planificador-error" role="alert"><p>{error}</p>
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={recargar}>Actualizar calendario conservando mis entradas</button></div>}
      {desactualizado && <div className="planificador-error" role="alert"><p>Este turno cambió o se eliminó desde que lo abriste. Tus entradas se conservan.</p>
        {turnoActual ? <button type="button" className="boton boton--fantasma" onClick={() => actualizarRevision(turnoActual)}>Revisar con la versión actual</button>
          : <p>Cierra esta ventana y revisa el calendario actualizado.</p>}</div>}
      {!editable && <p className="planificador-ayuda">Este turno se muestra en modo de consulta. Solo puedes editar borradores con fecha vigente.</p>}
      <fieldset disabled={ocupado || !editable} className="formulario-planificador__campos">
        <Campo etiqueta="Profesional" campo="idProfesional" errores={errores}><select required value={formulario.idProfesional} onChange={(evento) => cambiar('idProfesional', evento.target.value)}>
          <option value="">Selecciona un profesional</option>{datos.profesionales.filter((registro) => registro.estado === 'Activo' || registro.id === formulario.idProfesional)
            .map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre}</option>)}
        </select></Campo>
        <div className="formulario-planificador__fila"><Campo etiqueta="Área o servicio" campo="idArea" errores={errores}><select required value={formulario.idArea}
          onChange={(evento) => cambiar('idArea', evento.target.value)}>{datos.areas.filter((registro) => registro.estado === 'Activo' || registro.id === formulario.idArea).map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre}</option>)}</select></Campo>
        <Campo etiqueta="Consultorio" campo="idConsultorio" errores={errores}><select required value={formulario.idConsultorio} onChange={(evento) => cambiar('idConsultorio', evento.target.value)}>
          <option value="">Selecciona un consultorio</option>{datos.consultorios.filter((registro) => registro.idArea === formulario.idArea && (registro.estado === 'Activo' || registro.id === formulario.idConsultorio))
            .map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre}</option>)}
        </select></Campo></div>
        <Campo etiqueta="Especialidad" campo="idEspecialidad" errores={errores}><select required value={formulario.idEspecialidad} onChange={(evento) => cambiar('idEspecialidad', evento.target.value)}>
          <option value="">{especialidades.length ? 'Selecciona la especialidad' : 'Sin especialidades compatibles'}</option>
          {especialidades.map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre}</option>)}</select></Campo>
        <div className="formulario-planificador__fila formulario-planificador__fila--horas">
          <Campo etiqueta="Día" campo="fecha" errores={errores}><input required type="date" min={hoy > `${periodo}-01` ? hoy : `${periodo}-01`}
            max={diasDelMes(periodo).at(-1)} value={formulario.fecha} onChange={(evento) => cambiar('fecha', evento.target.value)} /></Campo>
          <Campo etiqueta="Desde" campo="horaInicio" errores={errores}><input required type="time" value={formulario.horaInicio} onChange={(evento) => cambiar('horaInicio', evento.target.value)} /></Campo>
          <Campo etiqueta="Hasta" campo="horaFin" errores={errores}><input required type="time" value={formulario.horaFin} onChange={(evento) => cambiar('horaFin', evento.target.value)} /></Campo>
        </div>
        <Campo etiqueta="Nota (opcional)" campo="observacion" errores={errores}><textarea rows={2} maxLength={180} value={formulario.observacion}
          onChange={(evento) => cambiar('observacion', evento.target.value)} placeholder="Una indicación breve para este turno" /></Campo>
      </fieldset>
      <p className="planificador-ayuda">{duracion > 0 ? `${(duracion / 60).toLocaleString('es-PE')} horas. ` : ''}El sistema comprueba cruces y los límites actuales de 6 h al día y 36 h por semana.</p>
      {confirmaEliminar && <div className="planificador-error"><p>¿Eliminar este turno del borrador mensual?</p>
        <button className="boton boton--peligro" type="button" disabled={ocupado || desactualizado} onClick={eliminar}>Confirmar eliminación</button>
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={() => setConfirmaEliminar(false)}>Conservar turno</button></div>}
      <div className="ventana-planificador__acciones">
        {editor.turno && editable && !confirmaEliminar && <button className="boton boton--fantasma" type="button" disabled={ocupado || desactualizado}
          onClick={() => setConfirmaEliminar(true)}><Trash2 size={16} /> Eliminar</button>}
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={cerrar}>{editable ? 'Cancelar' : 'Cerrar'}</button>
        {editable && <button className="boton boton--primario" type="submit" disabled={ocupado || desactualizado}>{ocupado ? 'Guardando…' : editor.turno ? 'Guardar cambios' : 'Guardar turno'}</button>}
      </div>
    </form>
  </VentanaPlanificador>;
}
