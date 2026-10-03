import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Building2, CalendarClock, Plus, UsersRound } from 'lucide-react';
import EncabezadoPagina from '../components/EncabezadoPagina';
import { obtenerConsultorios, obtenerProfesionales } from '../services/catalogos';

function Inicio() {
  const navegar = useNavigate();
  const [intento, setIntento] = useState(0);
  const [resumen, setResumen] = useState({ estado: 'cargando', profesionales: [], consultorios: [] });

  useEffect(() => {
    const controlador = new AbortController();
    let vigente = true;
    setResumen({ estado: 'cargando', profesionales: [], consultorios: [] });
    Promise.all([
      obtenerProfesionales({ senal: controlador.signal }),
      obtenerConsultorios({ senal: controlador.signal }),
    ]).then(([profesionales, consultorios]) => {
      if (vigente) setResumen({ estado: 'listo', profesionales, consultorios });
    }).catch((error) => {
      if (!vigente) return;
      if (error.estado === 401) {
        navegar('/iniciar-sesion', { replace: true, state: { destino: '/panel' } });
        return;
      }
      setResumen({ estado: 'error', profesionales: [], consultorios: [], mensaje: error.message });
    });
    return () => { vigente = false; controlador.abort(); };
  }, [intento, navegar]);

  const { profesionales, consultorios } = resumen;
  const accesos = [
    { titulo: 'Profesionales', descripcion: 'Datos laborales, especialidad y estado del personal.',
      cantidad: profesionales.length, destino: '/profesionales', icono: UsersRound, color: 'turquesa' },
    { titulo: 'Consultorios', descripcion: 'Ambientes registrados para la atención.',
      cantidad: consultorios.length, destino: '/consultorios', icono: Building2, color: 'azul' },
  ];

  return (
    <>
      <EncabezadoPagina titulo="Panel operativo" descripcion="Profesionales y consultorios de tu institución."
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
          </section>

          <div className="rejilla-inicio">
            <section className="panel panel--principal">
              <div className="panel__encabezado"><div><p className="sobrelinea">Datos maestros</p><h2>Preparar la programación</h2></div></div>
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
              <p className="sobrelinea">Próxima etapa</p><h2>Disponibilidades y horarios</h2>
              <div className="turno-resumen"><span><CalendarClock size={17} /></span>
                <div><strong>Programación pendiente</strong><small>Registra primero el personal y los ambientes.</small></div></div>
            </aside>
          </div>
        </>
      )}

      <section className="panel panel--acciones">
        <div><p className="sobrelinea">Acciones rápidas</p><h2>Crear un registro</h2></div>
        <div className="acciones-rapidas">
          <Link to="/profesionales/nuevo"><Plus size={16} /> Profesional</Link>
          <Link to="/consultorios/nuevo"><Plus size={16} /> Consultorio</Link>
        </div>
      </section>
    </>
  );
}

export default Inicio;
