import { useState } from 'react';
import { CalendarDays, RefreshCw, Search, UserRoundCheck, UserRoundX } from 'lucide-react';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import usePlanificadorMensual from '../../hooks/usePlanificadorMensual';
import { cambiarAsistencia } from '../../services/planificador';
import { filtrarTurnosAsistencia, minutosComputables, resumirAsistencia } from '../../utils/asistencia';
import { diasDelMes, nombreMes } from '../../utils/calendarioMensual';
import { duracionReporte } from '../../utils/reportesMensuales';
import { fechaValida, mostrarFecha } from '../../utils/validacionesProgramacion';
import { buscarFeriado } from '../../utils/feriados';
import ResumenFeriados from '../../components/ResumenFeriados';

export default function Faltas() {
  const { sesion } = useOutletContext();
  const [parametros] = useSearchParams();
  const consulta = usePlanificadorMensual('/faltas', parametros.get('periodo'));
  const [busqueda, cambiarBusqueda] = useState(() => parametros.get('buscar') || '');
  const [fecha, cambiarFecha] = useState('');
  const [idArea, cambiarArea] = useState('');
  const [idConsultorio, cambiarConsultorio] = useState('');
  const [estado, cambiarEstado] = useState('');
  const { datos, periodo, cargando, guardando } = consulta;
  const ocupado = cargando || guardando;
  const visibles = filtrarTurnosAsistencia(datos?.turnos || [], { busqueda, fecha, idArea, idConsultorio, estado });
  const resumen = resumirAsistencia(visibles, datos?.feriados);
  const nombresAreas = new Map((datos?.areas || []).map((area) => [area.id, area.nombre]));
  const consultorios = (datos?.consultorios || []).filter((consultorio) => !idArea || consultorio.idArea === idArea);

  const seleccionarMes = (valor) => {
    consulta.cambiarPeriodo(valor);
    cambiarFecha('');
  };
  const actualizarAsistencia = (turno) => consulta.ejecutar(
    (opciones) => cambiarAsistencia(turno.id, turno.asistencia !== 'Falta', { ...opciones, revision: turno.revisionAsistencia }),
    turno.asistencia === 'Falta' ? 'Asistencia restablecida. Sus horas vuelven a contar.' : 'Falta registrada. Las horas de este turno ya no cuentan.',
  );

  return <>
    <EncabezadoPagina titulo="Asistencia y faltas" ruta={[{ etiqueta: 'Faltas' }]}
      descripcion="Registra faltas por turno, consultorio y día. Cada turno cuenta como asistencia hasta que lo cambies."
      acciones={<Link className="boton boton--secundario" to="/horarios"><CalendarDays size={17} /> Ver horarios</Link>} />
    {!sesion.esAdministrador ? <p className="planificador-error" role="alert">Tu cuenta necesita el permiso de administrador para gestionar faltas.</p> : <>
      <section className="panel filtros-faltas" aria-label="Filtros de asistencia">
        <div className="filtros-faltas__periodo"><div><p className="sobrelinea">Control por turno</p><h2>{nombreMes(periodo)}</h2></div>
          <label>Mes<input type="month" min="0001-01" max="9999-12" value={periodo} disabled={ocupado}
            onChange={(evento) => seleccionarMes(evento.target.value)} /></label>
          <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={consulta.recargar}><RefreshCw size={16} /> Actualizar</button></div>
        <div className="filtros-faltas__campos">
          <label className="filtros-faltas__busqueda">Especialista<span className="buscador"><Search size={17} aria-hidden="true" />
            <input type="search" placeholder="Buscar por nombre o apellido" maxLength={200} value={busqueda}
              onChange={(evento) => cambiarBusqueda(evento.target.value)} /></span></label>
          <label>Día<input type="date" value={fecha} min={`${periodo}-01`} max={diasDelMes(periodo).at(-1)}
            disabled={ocupado} onChange={(evento) => {
              const valor = evento.target.value;
              if (!valor || (fechaValida(valor) && valor.startsWith(`${periodo}-`))) cambiarFecha(valor);
            }} /></label>
          <label>Área<select value={idArea} disabled={ocupado} onChange={(evento) => { cambiarArea(evento.target.value); cambiarConsultorio(''); }}>
            <option value="">Todas las áreas</option>{datos?.areas.map((area) => <option key={area.id} value={area.id}>{area.nombre}</option>)}</select></label>
          <label>Consultorio<select value={idConsultorio} disabled={ocupado} onChange={(evento) => cambiarConsultorio(evento.target.value)}>
            <option value="">Todos los consultorios</option>{consultorios.map((consultorio) => <option key={consultorio.id} value={consultorio.id}>{consultorio.codigo} · {consultorio.nombre}</option>)}</select></label>
          <label>Asistencia<select value={estado} disabled={ocupado} onChange={(evento) => cambiarEstado(evento.target.value)}>
            <option value="">Todos los turnos</option><option value="Falta">Solo faltas</option><option value="Asistencia">Solo asistencias</option></select></label>
        </div>
        <p className="planificador-ayuda">Puedes registrar faltas en fechas pasadas, en el día de hoy y en fechas futuras. El cambio afecta únicamente al turno seleccionado.</p>
      </section>
      {datos && !consulta.errorCarga && <ResumenFeriados feriados={datos.feriados} error={consulta.errorFeriados}
        ocupado={ocupado} actualizar={consulta.actualizarFeriados} />}
      {cargando && <p role="status">Cargando los turnos y sus asistencias…</p>}
      {consulta.errorCarga && <div className="planificador-error" role="alert"><p>{consulta.errorCarga}</p>
        <button type="button" className="boton boton--secundario" onClick={consulta.recargar}>Volver a intentar</button></div>}
      {consulta.error && <p className="planificador-error" role="alert">{consulta.error}</p>}
      {consulta.mensaje && <p className="planificador-exito" role="status">{consulta.mensaje}</p>}
      {datos && !consulta.errorCarga && <section className="panel panel--tabla faltas-tabla" aria-busy={ocupado}>
        <div className="faltas-tabla__resumen"><h2>Turnos encontrados <span>{resumen.cantidad}</span></h2>
          <p><strong>{resumen.faltas}</strong> {resumen.faltas === 1 ? 'falta' : 'faltas'} · <strong>{duracionReporte(resumen.minutos)}</strong> de turnos</p></div>
        <div className="tabla-responsive"><table><caption className="solo-lectores">Asistencia de los turnos mensuales según los filtros seleccionados</caption>
          <thead><tr><th scope="col">Día y horario</th><th scope="col">Especialista</th><th scope="col">Área y consultorio</th>
            <th scope="col">Asistencia</th><th scope="col">Horas</th><th scope="col">Acción</th></tr></thead>
          <tbody>{visibles.map((turno) => <tr key={turno.id} className={turno.asistencia === 'Falta' ? 'faltas-tabla__falta' : ''}>
            <td><strong>{mostrarFecha(turno.fecha)}</strong><small>{turno.horaInicio} a {turno.horaFin}</small></td>
            <td><strong>{turno.profesional}</strong><small>{turno.especialidad}</small></td>
            <td><strong>{nombresAreas.get(turno.idArea)}</strong><small>{turno.consultorio}</small></td>
            <td><span className={`asistencia-estado ${turno.asistencia === 'Falta' ? 'asistencia-estado--falta' : ''}`}>
              {turno.asistencia === 'Falta' ? <UserRoundX size={15} /> : <UserRoundCheck size={15} />}{turno.asistencia}</span></td>
            <td>{duracionReporte(minutosComputables(turno, datos.feriados))}
              {buscarFeriado(datos.feriados, turno.fecha) && <small>Feriado: abono institucional</small>}</td>
            <td><button type="button" className={`boton ${turno.asistencia === 'Falta' ? 'boton--secundario' : 'boton--fantasma'}`}
              disabled={ocupado} onClick={() => actualizarAsistencia(turno)}
              aria-label={`${turno.asistencia === 'Falta' ? 'Restablecer asistencia' : 'Marcar falta'} de ${turno.profesional} el ${mostrarFecha(turno.fecha)}, ${turno.horaInicio} a ${turno.horaFin}, ${turno.consultorio}`}>
              {turno.asistencia === 'Falta' ? 'Restablecer asistencia' : 'Marcar falta'}</button></td>
          </tr>)}</tbody>
        </table></div>
        {!visibles.length && <div className="faltas-vacio"><UserRoundCheck size={32} /><h3>No hay turnos para esta selección</h3>
          <p>{datos.turnos.length ? 'Ajusta los filtros o busca otro especialista.' : 'Consulta otro mes o registra turnos en Horarios.'}</p></div>}
      </section>}
    </>}
  </>;
}
