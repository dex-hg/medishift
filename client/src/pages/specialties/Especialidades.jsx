import { useEffect, useRef, useState } from 'react';
import { Plus, Search, Tags } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useListadoCatalogo } from '../../hooks/useCatalogos';
import { crearEspecialidad, obtenerEspecialidades } from '../../services/catalogos';

export default function Especialidades() {
  const listado = useListadoCatalogo(obtenerEspecialidades);
  const navegar = useNavigate();
  const [nombre, setNombre] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const bloqueo = useRef(false);
  const vigente = useRef(true);
  const controlador = useRef(null);
  useEffect(() => { vigente.current = true; return () => { vigente.current = false; controlador.current?.abort(); }; }, []);
  const guardar = async (evento) => {
    evento.preventDefault();
    if (bloqueo.current) return;
    bloqueo.current = true;
    controlador.current = new AbortController();
    setGuardando(true);
    setError('');
    setMensaje('');
    try {
      const especialidad = await crearEspecialidad({ nombre }, { senal: controlador.current.signal });
      if (!vigente.current) return;
      setNombre('');
      setMensaje(`Especialidad creada: ${especialidad.nombre}. Ya puedes seleccionarla en profesionales y áreas.`);
      listado.reintentar();
    } catch (fallo) {
      if (vigente.current) {
        if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true, state: { destino: '/especialidades' } });
        else setError(fallo.errores?.nombre || fallo.message);
      }
    } finally { bloqueo.current = false; if (vigente.current) setGuardando(false); }
  };
  const registros = listado.registros.filter((registro) => registro.nombre.toLocaleLowerCase('es').includes(busqueda.trim().toLocaleLowerCase('es')));
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Especialidades' }]} titulo="Especialidades"
      descripcion="Crea las especialidades que utiliza tu institución y selecciónalas al registrar profesionales, consultorios y áreas." />
    <section className="panel especialidades-alta" aria-label="Nueva especialidad">
      <h2><Tags size={19} aria-hidden="true" /> Nueva especialidad</h2>
      <form onSubmit={guardar}><label className="campo-planificador"><span>Nombre de la especialidad</span>
        <input type="text" required maxLength={100} value={nombre} disabled={guardando}
          onChange={(evento) => { setNombre(evento.target.value); setError(''); setMensaje(''); }} placeholder="Por ejemplo: Dermatología" /></label>
        <button className="boton boton--primario" type="submit" disabled={guardando || !nombre.trim()}>
          <Plus size={16} aria-hidden="true" />{guardando ? 'Creando…' : 'Crear especialidad'}</button></form>
      <AvisoCatalogo error={error} mensaje={mensaje} />
    </section>
    <section className="panel panel--tabla" aria-label="Catálogo de especialidades" aria-busy={listado.cargando}>
      <div className="barra-tabla"><label className="buscador"><Search size={17} aria-hidden="true" />
        <span className="solo-lectores">Buscar especialidades</span><input type="search" value={busqueda}
          onChange={(evento) => setBusqueda(evento.target.value)} placeholder="Nombre de la especialidad" /></label>
        <span className="contador-resultados">{registros.length} especialidades</span></div>
      <AvisoCatalogo cargando={listado.cargando} error={listado.error} reintentar={listado.reintentar} />
      {!listado.cargando && !listado.error && (registros.length ? <div className="tabla-responsive"><table>
        <thead><tr><th>Especialidad</th></tr></thead><tbody>{registros.map((registro) => <tr key={registro.id}><td>{registro.nombre}</td></tr>)}</tbody>
      </table></div> : <p className="sin-resultados">{busqueda ? 'No hay coincidencias.' : 'Crea la primera especialidad de tu institución.'}</p>)}
    </section>
  </>;
}
