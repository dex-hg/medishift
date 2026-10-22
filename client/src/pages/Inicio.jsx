import { Link } from 'react-router-dom';
import { ArrowRight, Building2, CalendarClock, Plus, UsersRound } from 'lucide-react';
import EncabezadoPagina from '../components/EncabezadoPagina';
import { useResumenInicio } from '../hooks/useResumenInicio';
import { mostrarFecha } from '../utils/validacionesProgramacion';

function Inicio() {
  const { resumen, reintentar } = useResumenInicio();

  const { profesionales, consultorios, semana } = resumen;
  const accesos = [
    { titulo: 'Profesionales', descripcion: 'Datos laborales, especialidad y estado del personal.',
      cantidad: profesionales.length, destino: '/profesionales', icono: UsersRound, color: 'turquesa' },
    { titulo: 'Consultorios', descripcion: 'Ambientes registrados para la atención.',
      cantidad: consultorios.length, destino: '/consultorios', icono: Building2, color: 'azul' },
    { titulo: 'Horarios', descripcion: `Semana actual: ${semana?.estado || 'cargando'}.`,
      cantidad: semana?.turnos.length || 0, destino: '/horarios', icono: CalendarClock, color: 'azul' },
  ];

  return (
    <>
      <EncabezadoPagina titulo="Panel operativo" descripcion="Registros y programación real de tu institución."
        acciones={<Link className="boton boton--primario" to="/profesionales/nuevo"><Plus size={18} /> Nuevo profesional</Link>} />

      {resumen.estado === 'cargando' && <p role="status">Cargando los registros de tu institución…</p>}
      {resumen.estado === 'error' && (
        <div className="aviso" role="alert">
          <p>{resumen.mensaje || 'No se pudieron cargar los registros.'}</p>
          <button className="boton boton--secundario" type="button" onClick={reintentar}>Reintentar</button>
        </div>
      )}

      {resumen.estado === 'listo' && (
        <>
          <section className="resumen" aria-label="Resumen de registros">
            <article className="tarjeta-metrica">
              <span className="tarjeta-metrica__icono tarjeta-metrica__icono--turquesa"><UsersRound size={21} /></span>
              <div><span>Profesionales activos</span>
                <strong>{profesionales.filter((item) => item.estado === 'Activo').length}</strong>
                <small>de {profesionales.length} registrados</small></div>
            </article>
            <article className="tarjeta-metrica">
              <span className="tarjeta-metrica__icono tarjeta-metrica__icono--azul"><Building2 size={21} /></span>
              <div><span>Consultorios activos</span>
                <strong>{consultorios.filter((item) => item.estado === 'Activo').length}</strong>
                <small>de {consultorios.length} registrados</small></div>
            </article>
            <article className="tarjeta-metrica">
              <span className="tarjeta-metrica__icono tarjeta-metrica__icono--azul"><CalendarClock size={21} /></span>
              <div><span>Turnos de la semana actual</span><strong>{semana.turnos.length}</strong>
                <small>{semana.estado}{semana.version ? ` · Versión ${semana.version}` : ''}</small></div>
            </article>
          </section>

          <div className="rejilla-inicio">
            <section className="panel panel--principal">
              <div className="panel__encabezado"><div><p className="sobrelinea">Gestión de atención</p><h2>Registros y programación</h2></div></div>
              <div className="accesos-crud">
                {accesos.map(({ titulo, descripcion, cantidad, destino, icono: Icono, color }) => (
                  <Link className="acceso-crud" to={destino} key={titulo}>
                    <span className={`acceso-crud__icono acceso-crud__icono--${color}`}><Icono size={20} /></span>
                    <span className="acceso-crud__contenido"><strong>{titulo}</strong><small>{descripcion}</small></span>
                    <span className="acceso-crud__cantidad"><strong>{cantidad}</strong><small>registros</small></span>
                    <ArrowRight className="acceso-crud__flecha" size={18} />
                  </Link>
                ))}
              </div>
            </section>
            <aside className="panel panel--agenda">
              <p className="sobrelinea">Semana actual</p><h2>{semana.estado}</h2>
              <p className="nota-programacion nota-programacion--compacta">{mostrarFecha(semana.fechaInicio)} al {mostrarFecha(semana.fechaFin)}. Zona: {semana.zonaHoraria}.</p>
              {semana.estado === 'Borrador' && semana.versionAprobada && <p className="nota-programacion nota-programacion--compacta">
                La versión aprobada {semana.versionAprobada} sigue vigente; los turnos mostrados pertenecen al borrador.</p>}
              <div className="lista-turnos">{semana.turnos.slice(0, 4).map((turno) => <div className="turno-resumen" key={turno.id}>
                <span><CalendarClock size={17} /></span><div><strong>{turno.profesional}</strong>
                  <small>{mostrarFecha(turno.fecha)} · {turno.horaInicio} a {turno.horaFin}</small><small>{turno.consultorio} · {turno.estado}</small></div>
              </div>)}{!semana.turnos.length && <div className="turno-resumen"><span><CalendarClock size={17} /></span>
                <div><strong>No hay turnos registrados</strong><small>Selecciona un médico y un consultorio para crear el primer turno.</small></div></div>}</div>
              <Link className="boton boton--secundario" to={`/horarios?fechaInicio=${semana.fechaInicio}`}>Ver semana</Link>
            </aside>
          </div>
        </>
      )}

      <section className="panel panel--acciones">
        <div><p className="sobrelinea">Acciones rápidas</p><h2>Crear un registro</h2></div>
        <div className="acciones-rapidas">
          <Link to="/profesionales/nuevo"><Plus size={16} /> Profesional</Link>
          <Link to="/consultorios/nuevo"><Plus size={16} /> Consultorio</Link>
          <Link to="/horarios/nuevo"><Plus size={16} /> Turno</Link>
        </div>
      </section>
    </>
  );
}

export default Inicio;
