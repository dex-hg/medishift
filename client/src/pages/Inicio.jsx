import { useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router-dom';
import { ArrowRight, Building2, CalendarClock, Plus, Search, UserRoundX, UsersRound } from 'lucide-react';
import EncabezadoPagina from '../components/EncabezadoPagina';
import { useResumenInicio } from '../hooks/useResumenInicio';

function Inicio() {
  const { resumen, reintentar } = useResumenInicio();
  const { sesion } = useOutletContext();
  const navegar = useNavigate();
  const [busquedaFaltas, cambiarBusquedaFaltas] = useState('');

  const { profesionales, consultorios } = resumen;
  const accesos = [
    { titulo: 'Profesionales', descripcion: 'Datos laborales, etiquetas y estado del personal.',
      cantidad: profesionales.length, destino: '/profesionales', icono: UsersRound, color: 'turquesa' },
    { titulo: 'Consultorios', descripcion: 'Ambientes registrados para la atención.',
      cantidad: consultorios.length, destino: '/consultorios', icono: Building2, color: 'azul' },
  ];

  return (
    <>
      <EncabezadoPagina titulo="Panel operativo" descripcion="Registros y programación real de tu institución."
        acciones={<Link className="boton boton--primario" to="/horarios"><CalendarClock size={18} /> Organizar el mes</Link>} />

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
          </section>

          <section className="panel panel--principal">
            <div className="panel__encabezado"><h2>Catálogos de atención</h2></div>
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
        </>
      )}

      {sesion.esAdministrador && <section className="panel acceso-faltas" aria-label="Acceso rápido a faltas">
        <span className="tarjeta-metrica__icono tarjeta-metrica__icono--turquesa"><UserRoundX size={24} /></span>
        <div><p className="sobrelinea">Asistencia del personal</p><h2>Asistencia y faltas</h2>
          <p>Busca un especialista y registra faltas por día y consultorio. Sus horas se actualizan en los totales y reportes.</p>
          <Link className="planificador-enlace" to="/faltas">Abrir módulo de faltas <ArrowRight size={14} /></Link></div>
        <form onSubmit={(evento) => { evento.preventDefault(); navegar(`/faltas?buscar=${encodeURIComponent(busquedaFaltas.trim())}`); }}>
          <label className="buscador"><Search size={17} aria-hidden="true" /><span className="solo-lectores">Buscar especialista para registrar faltas</span>
            <input type="search" placeholder="Nombre o apellido del especialista" maxLength={200} value={busquedaFaltas}
              onChange={(evento) => cambiarBusquedaFaltas(evento.target.value)} /></label>
          <button className="boton boton--primario" type="submit">Buscar especialista</button>
        </form>
      </section>}

      <section className="panel panel--acciones">
        <div><p className="sobrelinea">Acciones rápidas</p><h2>Crear un registro</h2></div>
        <div className="acciones-rapidas">
          <Link to="/profesionales/nuevo"><Plus size={16} /> Profesional</Link>
          <Link to="/consultorios/nuevo"><Plus size={16} /> Consultorio</Link>
          <Link to="/horarios"><Plus size={16} /> Turno mensual</Link>
        </div>
      </section>
    </>
  );
}

export default Inicio;
