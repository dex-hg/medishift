import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Building2, CalendarClock, Plus, UsersRound } from 'lucide-react';
import EncabezadoPagina from '../components/EncabezadoPagina';
import { obtenerConsultorios, obtenerProfesionales } from '../services/catalogos';
import { listarDisponibilidades, listarRecursosHorario, listarSemana } from '../services/programacion';
import { fechaActual, lunesDeSemana, mostrarFecha } from '../utils/validacionesProgramacion';

function Inicio() {
  const navegar = useNavigate();
  const [intento, setIntento] = useState(0);
  const [resumen, setResumen] = useState({ estado: 'cargando', profesionales: [], consultorios: [], disponibilidades: [], semana: null });

  useEffect(() => {
    const controlador = new AbortController();
    let vigente = true;
    setResumen({ estado: 'cargando', profesionales: [], consultorios: [], disponibilidades: [], semana: null });
    Promise.all([
      obtenerProfesionales({ senal: controlador.signal }),
      obtenerConsultorios({ senal: controlador.signal }),
      listarDisponibilidades({ senal: controlador.signal }),
      listarRecursosHorario({ senal: controlador.signal }),
    ]).then(async ([profesionales, consultorios, disponibilidades, recursos]) => {
      const semana = await listarSemana(lunesDeSemana(fechaActual(recursos.zonaHoraria)), { senal: controlador.signal });
      if (vigente) setResumen({ estado: 'listo', profesionales, consultorios, disponibilidades, semana });
    }).catch((error) => {
      if (!vigente) return;
      if (error.estado === 401) {
        navegar('/iniciar-sesion', { replace: true, state: { destino: '/panel' } });
        return;
      }
      setResumen({ estado: 'error', profesionales: [], consultorios: [], disponibilidades: [], semana: null, mensaje: error.message });
    });
    return () => { vigente = false; controlador.abort(); };
  }, [intento, navegar]);

  const { profesionales, consultorios, disponibilidades, semana } = resumen;
  const accesos = [
    { titulo: 'Profesionales', descripcion: 'Datos laborales, especialidad y estado del personal.',
      cantidad: profesionales.length, destino: '/profesionales', icono: UsersRound, color: 'turquesa' },
    { titulo: 'Consultorios', descripcion: 'Ambientes registrados para la atención.',
      cantidad: consultorios.length, destino: '/consultorios', icono: Building2, color: 'azul' },
    { titulo: 'Disponibilidades', descripcion: 'Franjas semanales y fechas de vigencia del personal.',
      cantidad: disponibilidades.length, destino: '/disponibilidades', icono: CalendarClock, color: 'turquesa' },
    { titulo: 'Horarios', descripcion: `Semana actual: ${semana?.estado || 'cargando'}.`,
      cantidad: semana?.turnos.length || 0, destino: `/horarios?fechaInicio=${semana?.fechaInicio || lunesDeSemana(fechaActual())}`, icono: CalendarClock, color: 'azul' },
  ];

  return (
    <>
      <EncabezadoPagina titulo="Panel operativo" descripcion="Registros y programación real de tu institución."
        acciones={<Link className="boton boton--primario" to="/profesionales/nuevo"><Plus size={18} /> Nuevo profesional</Link>} />

      {resumen.estado === 'cargando' && <p role="status">Cargando los registros de tu institución…</p>}
      {resumen.estado === 'error' && (
        <div className="aviso" role="alert">
          <p>{resumen.mensaje || 'No se pudieron cargar los registros.'}</p>
          <button className="boton boton--secundario" type="button" onClick={() => setIntento((valor) => valor + 1)}>Reintentar</button>
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
              <span className="tarjeta-metrica__icono tarjeta-metrica__icono--verde"><CalendarClock size={21} /></span>
              <div><span>Disponibilidades vigentes</span>
                <strong>{disponibilidades.filter((item) => item.estado === 'Activo' && item.fechaInicio <= fechaActual(semana.zonaHoraria)
                  && item.fechaFin >= fechaActual(semana.zonaHoraria)).length}</strong>
                <small>de {disponibilidades.length} franjas registradas</small></div>
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
                <div><strong>No hay turnos registrados</strong><small>Declara disponibilidades y prepara el borrador semanal.</small></div></div>}</div>
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
          <Link to="/disponibilidades/nueva"><Plus size={16} /> Disponibilidad</Link>
          <Link to="/horarios/nuevo"><Plus size={16} /> Turno</Link>
        </div>
      </section>
    </>
  );
}

export default Inicio;
