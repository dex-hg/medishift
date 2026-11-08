import { useCallback, useState } from 'react';
import VentanaPlanificador from './VentanaPlanificador';
import { cambiarAprobacionMensual, obtenerHistorialMensual } from '../../services/aprobacionMensual';
import { useConsultaAtencion } from '../../hooks/useAtencion';

function HistorialMensual({ plan, cerrar }) {
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerHistorialMensual(plan.id, opciones), [plan.id]));
  const mostrar = (valor) => new Intl.DateTimeFormat('es-PE', { timeZone: plan.zonaHoraria, dateStyle: 'short', timeStyle: 'short' }).format(new Date(valor));
  return <VentanaPlanificador titulo="Versiones aprobadas anteriores" descripcion="Las copias conservan los turnos tal como estaban antes de reabrir el mes." cerrar={cerrar}>
    {consulta.cargando && <p role="status">Cargando versiones…</p>}
    {consulta.error && <div role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" onClick={consulta.recargar}>Reintentar</button></div>}
    {consulta.datos && (consulta.datos.length ? consulta.datos.map((version) => <details className="atencion-version" key={version.version}>
      <summary>Versión {version.version} · Archivada {mostrar(version.archivadoEn)} · {version.contenido.turnos.length} turnos</summary>
      <div className="tabla-responsive"><table><thead><tr><th>Inicio</th><th>Fin</th><th>Estado</th></tr></thead>
        <tbody>{version.contenido.turnos.map((turno) => <tr key={turno.id_shift}><td>{mostrar(turno.start_at_shift)}</td><td>{mostrar(turno.end_at_shift)}</td><td>{turno.status_shift === 'approved' ? 'Aprobado' : 'Cancelado'}</td></tr>)}</tbody></table></div>
    </details>) : <p>El mes aún no tiene versiones archivadas por reapertura.</p>)}
  </VentanaPlanificador>;
}

export default function ControlAprobacionMensual({ plan, area, datos, consulta }) {
  const [confirmar, cambiarConfirmar] = useState(false);
  const [historial, cambiarHistorial] = useState(false);
  if (!plan || !area || !datos.permisos.esAdministrador) return null;
  const reabrir = plan.estado === 'Aprobado';
  const turnos = datos.turnos.filter((turno) => turno.idPlan === plan.id && !['Cancelado', 'Sustituido'].includes(turno.estado));
  const ejecutar = async () => {
    const exito = await consulta.ejecutar((opciones) => cambiarAprobacionMensual(plan, reabrir, opciones),
      reabrir ? 'Mes reabierto. Se archivó la versión aprobada y se conservaron las citas.' : 'Mes aprobado. Sus turnos ya permiten agendar citas.');
    if (exito) cambiarConfirmar(false);
  };
  return <>
    <div className="panel atencion-aprobacion"><div><strong>{area.nombre} · Versión {plan.version} · {plan.estado}</strong>
      <p>La aprobación abarca todos los consultorios del área en este mes.</p></div>
      <div className="atencion-acciones"><button className="boton boton--secundario" disabled={consulta.cargando || consulta.guardando} onClick={() => cambiarHistorial(true)}>Ver versiones</button>
        {['Borrador', 'Aprobado'].includes(plan.estado) && <button className="boton boton--primario" disabled={consulta.cargando || consulta.guardando || (!reabrir && !turnos.length)}
          onClick={() => { consulta.limpiarError(); cambiarConfirmar(true); }}>{reabrir ? 'Reabrir mes del área' : 'Aprobar mes del área'}</button>}</div>
    </div>
    {confirmar && <VentanaPlanificador titulo={reabrir ? 'Reabrir mes aprobado' : 'Aprobar programación mensual'} descripcion={`${area.nombre} · ${plan.periodo} · ${turnos.length} turnos`} ocupado={consulta.guardando} cerrar={() => cambiarConfirmar(false)}>
      <p>{reabrir ? 'Se archivará la versión aprobada. Los turnos y sus citas conservarán su identidad. No se podrán agendar nuevas citas hasta volver a aprobar el mes; cualquier cambio que afecte una cita deberá resolverse antes.'
        : 'Se comprobarán los cruces, los recursos, los permisos del personal y los límites institucionales de todos los turnos del área.'}</p>
      <div className="tabla-responsive"><table><thead><tr><th>Fecha</th><th>Profesional</th><th>Consultorio</th><th>Horas</th></tr></thead><tbody>
        {turnos.map((turno) => <tr key={turno.id}><td>{turno.fecha}</td><td>{turno.profesional}</td><td>{turno.consultorio}</td><td>{turno.horaInicio} a {turno.horaFin}</td></tr>)}</tbody></table></div>
      {consulta.error && <p className="atencion-error" role="alert">{consulta.error}</p>}
      <div className="ventana-planificador__acciones"><button className="boton boton--secundario" disabled={consulta.guardando} onClick={() => cambiarConfirmar(false)}>Volver</button>
        <button className="boton boton--primario" disabled={consulta.guardando || consulta.cargando} onClick={ejecutar}>{consulta.guardando ? 'Guardando…' : reabrir ? 'Confirmar reapertura' : 'Confirmar aprobación'}</button></div>
    </VentanaPlanificador>}
    {historial && <HistorialMensual plan={plan} cerrar={() => cambiarHistorial(false)} />}
  </>;
}
