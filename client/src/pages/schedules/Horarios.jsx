import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarClock, ChevronLeft, ChevronRight, Pencil, Plus, Trash2 } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import ConfirmacionProgramacion from '../../components/ConfirmacionProgramacion';
import { useConsultaProgramacion } from '../../hooks/useProgramacion';
import { aprobarSemana, cancelarSemana, eliminarTurno, listarSemana, listarSemanaActual, reabrirSemana } from '../../services/programacion';
import { DIAS_SEMANA, duracionTurno, fechaActual, lunesDeSemana, mostrarFecha, sumarDias } from '../../utils/validacionesProgramacion';

function Horarios() {
  const [parametros, setParametros] = useSearchParams();
  const fechaSolicitada = lunesDeSemana(parametros.get('fechaInicio'));
  const cargar = useCallback((opciones) => fechaSolicitada ? listarSemana(fechaSolicitada, opciones) : listarSemanaActual(opciones), [fechaSolicitada]);
  const consulta = useConsultaProgramacion(cargar);
  const [confirmacion, setConfirmacion] = useState(null);
  const semana = consulta.datos;
  const fechaInicio = fechaSolicitada || semana?.fechaInicio || '';
  const editable = semana && ['Sin horario', 'Borrador', 'Cancelado'].includes(semana.estado);
  const habilitado = Boolean(semana && semana.fechaInicio === fechaInicio && !consulta.cargando && !consulta.ocupado && !consulta.error);
  const hoyInstitucional = semana ? fechaActual(semana.zonaHoraria) : '';
  const fechaNuevoTurno = hoyInstitucional >= fechaInicio && hoyInstitucional <= semana?.fechaFin ? hoyInstitucional : fechaInicio;
  useEffect(() => { setConfirmacion(null); }, [fechaInicio]);
  const grupos = useMemo(() => {
    const agrupados = new Map();
    for (const turno of semana?.turnos || []) {
      if (!agrupados.has(turno.idProfesional)) agrupados.set(turno.idProfesional, { id: turno.idProfesional, nombre: turno.profesional, turnos: [] });
      agrupados.get(turno.idProfesional).turnos.push(turno);
    }
    return [...agrupados.values()].sort((primero, segundo) => primero.nombre.localeCompare(segundo.nombre, 'es'));
  }, [semana]);
  const cambiarSemana = (fecha) => {
    const lunes = lunesDeSemana(fecha);
    if (lunes && habilitado) { setConfirmacion(null); setParametros({ fechaInicio: lunes }); }
  };
  const recargar = () => { setConfirmacion(null); consulta.recargar(); };
  const solicitarAccion = (accion, turno) => {
    const periodo = `${mostrarFecha(fechaInicio)} al ${mostrarFecha(sumarDias(fechaInicio, 6))}`;
    const descripciones = {
      aprobar: `Se aprobará la versión ${semana.version} del ${periodo}, con ${semana.turnos.length} turnos. Si hay una aprobación anterior, será reemplazada en una sola operación.`,
      reabrir: `Se creará una nueva versión en borrador del ${periodo}. La versión aprobada ${semana.version} seguirá vigente hasta aprobar el reemplazo.`,
      cancelar: `Se cancelará la programación del ${periodo}, incluido el borrador y cualquier versión aprobada vigente. Se conservará el historial.`,
      eliminar: turno ? `Se eliminará el turno de ${turno.profesional} del ${mostrarFecha(turno.fecha)}, de ${turno.horaInicio} a ${turno.horaFin}, en ${turno.consultorio}.` : '',
    };
    setConfirmacion({ accion, turno, descripcion: descripciones[accion],
      semana: { fechaInicio: semana.fechaInicio, idHorario: semana.idHorario, revision: semana.revision } });
  };
  const confirmar = async () => {
    if (!confirmacion || confirmacion.semana.fechaInicio !== fechaInicio) { setConfirmacion(null); return; }
    const acciones = { aprobar: aprobarSemana, reabrir: reabrirSemana, cancelar: cancelarSemana };
    const exito = await consulta.ejecutar((opciones) => confirmacion.accion === 'eliminar'
      ? eliminarTurno(confirmacion.turno.id, { ...opciones, revision: confirmacion.turno.revision })
      : acciones[confirmacion.accion](confirmacion.semana, opciones), 'La operación se completó correctamente.');
    if (exito) setConfirmacion(null);
  };

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Horarios' }]} titulo="Horarios de atención"
      descripcion="Programación semanal. Cada profesional puede atender hasta 6 horas diarias y 36 semanales."
      acciones={editable && habilitado ? <Link className="boton boton--primario" to={`/horarios/nuevo?fecha=${fechaNuevoTurno}`}><Plus size={18} /> Nuevo turno</Link> : null} />
    <section className="panel panel--horario" aria-busy={consulta.cargando || consulta.ocupado}>
      <div className="controles-horario"><div className="periodo-horario">
        <button className="boton-icono" type="button" disabled={!habilitado} aria-label="Semana anterior" onClick={() => cambiarSemana(sumarDias(fechaInicio, -7))}><ChevronLeft size={18} /></button>
        <label className="selector-filtro"><span>Semana del lunes</span><input type="date" value={fechaInicio} disabled={!habilitado} onChange={(evento) => cambiarSemana(evento.target.value)} /></label>
        <button className="boton-icono" type="button" disabled={!habilitado} aria-label="Semana siguiente" onClick={() => cambiarSemana(sumarDias(fechaInicio, 7))}><ChevronRight size={18} /></button>
      </div><button className="boton boton--fantasma" type="button" disabled={consulta.ocupado || consulta.cargando} onClick={recargar}>Recargar</button></div>
      <AvisoCatalogo cargando={consulta.cargando} error={consulta.error} mensaje={consulta.mensaje} reintentar={recargar} />
      {consulta.ocupado && <p className="nota-programacion" role="status">Procesando la operación de programación…</p>}
      {semana && habilitado && <>
        <div className="resumen-programacion"><div><Estado valor={semana.estado} /><span>{semana.version ? `Versión ${semana.version}` : 'Sin versión registrada'}</span>
          <span>Zona horaria: {semana.zonaHoraria}</span><span>{mostrarFecha(semana.fechaInicio)} al {mostrarFecha(semana.fechaFin)}</span></div>
          <div className="acciones-tabla">
            {semana.estado === 'Borrador' && <button className="boton boton--primario" type="button" disabled={!semana.turnos.length} onClick={() => solicitarAccion('aprobar')}>Aprobar semana</button>}
            {semana.estado === 'Aprobado' && <button className="boton boton--secundario" type="button" onClick={() => solicitarAccion('reabrir')}>Reabrir para editar</button>}
            {['Borrador', 'Aprobado'].includes(semana.estado) && <button className="boton boton--fantasma" type="button" onClick={() => solicitarAccion('cancelar')}>Cancelar semana</button>}
          </div>
        </div>
        {semana.estado === 'Borrador' && semana.versionAprobada && <p className="nota-programacion">La versión aprobada {semana.versionAprobada} permanece vigente hasta aprobar este borrador.</p>}
        {confirmacion && <ConfirmacionProgramacion titulo={confirmacion.accion === 'eliminar' ? 'Eliminar turno' : `${confirmacion.accion[0].toUpperCase()}${confirmacion.accion.slice(1)} semana`}
          descripcion={confirmacion.descripcion} ocupado={consulta.ocupado} confirmar={confirmar} cancelar={() => setConfirmacion(null)} />}
        {grupos.length ? <div className="tabla-responsive"><div className="calendario-programacion">
          <div className="calendario-programacion__fila calendario-programacion__cabecera"><strong>Profesional</strong>{DIAS_SEMANA.map((dia, indice) => <strong key={dia}>{dia}<small>{mostrarFecha(sumarDias(fechaInicio, indice))}</small></strong>)}</div>
          {grupos.map((grupo) => <div className="calendario-programacion__fila" key={grupo.id}>
            <div className="calendario-programacion__persona"><strong>{grupo.nombre}</strong><small>{(grupo.turnos.reduce((total, turno) => total + duracionTurno(turno), 0) / 60).toLocaleString('es-PE')} horas esta versión</small></div>
            {DIAS_SEMANA.map((dia, indice) => <div className="calendario-programacion__dia" key={dia}>
              {grupo.turnos.filter((turno) => turno.fecha === sumarDias(fechaInicio, indice)).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio)).map((turno) => <article className="tarjeta-turno" key={turno.id}>
                <strong>{turno.horaInicio} a {turno.horaFin}</strong><span>{turno.consultorio}</span><small>{turno.especialidad}</small><Estado valor={turno.estado} />
                {semana.estado === 'Borrador' && turno.estado === 'Borrador' && <div className="acciones-tabla"><Link className="boton-icono" to={`/horarios/${turno.id}/editar`} aria-label={`Editar turno de ${turno.profesional} del ${mostrarFecha(turno.fecha)}`}><Pencil size={15} /></Link>
                  <button className="boton-icono boton-icono--peligro" type="button" onClick={() => solicitarAccion('eliminar', turno)} aria-label={`Eliminar turno de ${turno.profesional} del ${mostrarFecha(turno.fecha)}`}><Trash2 size={15} /></button></div>}
              </article>)}
            </div>)}
          </div>)}
        </div></div> : <div className="sin-resultados"><CalendarClock size={30} /><strong>No hay turnos en esta semana</strong><p>Registra disponibilidades y crea el primer turno para comenzar el borrador.</p></div>}
      </>}
      {consulta.conflicto && <p className="nota-programacion">La versión vigente cambió o hay una restricción de programación. Recarga para revisar el estado actual antes de continuar.</p>}
    </section>
  </>;
}

export default Horarios;
