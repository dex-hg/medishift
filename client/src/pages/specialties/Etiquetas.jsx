import { useEffect, useRef, useState } from 'react';
import { Pencil, Plus, Search, Tags } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useListadoCatalogo } from '../../hooks/useCatalogos';
import { actualizarEtiqueta, crearEtiqueta, obtenerEtiquetas } from '../../services/catalogos';

export default function Etiquetas() {
  const listado = useListadoCatalogo(obtenerEtiquetas);
  const navegar = useNavigate();
  const [edicion, cambiarEdicion] = useState(null);
  const [nombre, cambiarNombre] = useState('');
  const [activa, cambiarActiva] = useState(true);
  const [busqueda, cambiarBusqueda] = useState('');
  const [guardando, cambiarGuardando] = useState(false);
  const [error, cambiarError] = useState('');
  const [mensaje, cambiarMensaje] = useState('');
  const solicitud = useRef(null);
  const campoNombre = useRef(null);
  useEffect(() => () => solicitud.current?.abort(), []);

  const preparar = (registro) => {
    cambiarEdicion(registro);
    cambiarNombre(registro?.nombre || '');
    cambiarActiva(registro?.activa ?? true);
    cambiarError('');
    cambiarMensaje('');
    if (registro) campoNombre.current?.focus();
  };

  const guardar = async (evento) => {
    evento.preventDefault();
    if (solicitud.current) return;
    const controlador = new AbortController();
    solicitud.current = controlador;
    cambiarGuardando(true);
    cambiarError('');
    cambiarMensaje('');
    try {
      const opciones = { senal: controlador.signal, revision: edicion?.revision };
      const guardada = edicion ? await actualizarEtiqueta(edicion.id, { nombre, activa }, opciones)
        : await crearEtiqueta({ nombre }, opciones);
      preparar(null);
      cambiarMensaje(`Etiqueta guardada: ${guardada.nombre}.`);
      listado.reintentar();
    } catch (fallo) {
      if (controlador.signal.aborted) return;
      if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true, state: { destino: '/etiquetas' } });
      else cambiarError(fallo.errores?.nombre || fallo.message);
    } finally {
      solicitud.current = null;
      if (!controlador.signal.aborted) cambiarGuardando(false);
    }
  };
  const registros = listado.registros.filter((registro) => registro.nombre.toLocaleLowerCase('es')
    .includes(busqueda.trim().toLocaleLowerCase('es')));
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Etiquetas' }]} titulo="Etiquetas"
      descripcion="Administra las etiquetas de tu clínica y asígnalas a profesionales, áreas y consultorios." />
    <section className="panel especialidades-alta" aria-label={edicion ? 'Editar etiqueta' : 'Nueva etiqueta'}>
      <h2><Tags size={19} aria-hidden="true" /> {edicion ? 'Editar etiqueta' : 'Nueva etiqueta'}</h2>
      <form onSubmit={guardar}>
        <label className="campo-planificador"><span>Nombre de la etiqueta</span>
          <input ref={campoNombre} required maxLength={100} value={nombre} disabled={guardando}
            onChange={(evento) => cambiarNombre(evento.target.value)} placeholder="Por ejemplo, Dermatología" />
        </label>
        {edicion && <label className="campo-planificador"><span>Estado</span>
          <select value={activa ? 'activa' : 'archivada'} disabled={guardando} onChange={(evento) => cambiarActiva(evento.target.value === 'activa')}>
            <option value="activa">Activa</option><option value="archivada">Archivada</option>
          </select>
        </label>}
        <button className="boton boton--primario" type="submit" disabled={guardando || !nombre.trim()}>
          {edicion ? <Pencil size={16} aria-hidden="true" /> : <Plus size={16} aria-hidden="true" />}
          {guardando ? 'Guardando…' : edicion ? 'Guardar etiqueta' : 'Crear etiqueta'}
        </button>
        {edicion && <button type="button" className="boton boton--fantasma" disabled={guardando} onClick={() => preparar(null)}>Cancelar</button>}
      </form>
      {edicion && !activa && <p className="planificador-ayuda">Las etiquetas archivadas dejan de estar disponibles para nuevas asignaciones.</p>}
      <AvisoCatalogo error={error} mensaje={mensaje} />
    </section>
    <section className="panel panel--tabla" aria-label="Catálogo de etiquetas" aria-busy={listado.cargando}>
      <div className="barra-tabla"><label className="buscador"><Search size={17} aria-hidden="true" />
        <span className="solo-lectores">Buscar etiquetas</span><input type="search" value={busqueda}
          onChange={(evento) => cambiarBusqueda(evento.target.value)} placeholder="Buscar por nombre" /></label>
        <span className="contador-resultados">{registros.length} etiquetas</span></div>
      <AvisoCatalogo cargando={listado.cargando} error={listado.error} reintentar={listado.reintentar} />
      {!listado.cargando && !listado.error && (registros.length ? <div className="tabla-responsive"><table>
        <thead><tr><th>Etiqueta</th><th>Estado</th><th><span className="solo-lectores">Acciones</span></th></tr></thead>
        <tbody>{registros.map((registro) => <tr key={registro.id}>
          <td>{registro.nombre}</td><td>{registro.activa ? 'Activa' : 'Archivada'}</td>
          <td><button type="button" className="boton-icono"
            aria-label={`Editar etiqueta ${registro.nombre}`} disabled={guardando} onClick={() => preparar(registro)}>
            <Pencil size={16} aria-hidden="true" /></button></td>
        </tr>)}</tbody>
      </table></div> : <p className="sin-resultados">{busqueda ? 'No hay coincidencias.' : 'Crea la primera etiqueta de tu institución.'}</p>)}
    </section>
  </>;
}
