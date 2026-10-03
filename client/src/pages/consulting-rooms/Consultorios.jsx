import { useMemo, useState } from 'react';
import { Building2, MapPin, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { AvisoCatalogo, ConfirmacionEliminar } from '../../components/AvisoCatalogo';
import { useListadoCatalogo } from '../../hooks/useCatalogos';
import { eliminarConsultorio, obtenerConsultorios } from '../../services/catalogos';

function Consultorios() {
  const listado = useListadoCatalogo(obtenerConsultorios, eliminarConsultorio);
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('Todos');
  const consultoriosFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLocaleLowerCase('es');
    return listado.registros.filter((consultorio) => {
      const coincideTexto = [consultorio.codigo, consultorio.nombre, consultorio.ubicacion,
        consultorio.especialidad || 'Uso general'].some((valor) => valor.toLocaleLowerCase('es').includes(termino));
      return coincideTexto && (estado === 'Todos' || consultorio.estado === estado);
    });
  }, [listado.registros, busqueda, estado]);

  return (
    <>
      <EncabezadoPagina ruta={[{ etiqueta: 'Consultorios' }]} titulo="Consultorios"
        descripcion="Ambientes de tu institución que podrán asignarse a los turnos de atención."
        acciones={<Link className="boton boton--primario" to="/consultorios/nuevo">
          <Plus size={18} aria-hidden="true" /> Nuevo consultorio
        </Link>} />
      <section className="panel panel--tabla" aria-busy={listado.cargando || listado.eliminando}>
        <div className="barra-tabla">
          <label className="buscador"><Search size={17} aria-hidden="true" />
            <span className="solo-lectores">Buscar consultorios</span>
            <input type="search" value={busqueda} onChange={(evento) => setBusqueda(evento.target.value)}
              placeholder="Código, nombre, ubicación o especialidad" />
          </label>
          <label className="selector-filtro"><span>Estado</span>
            <select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
              <option>Todos</option><option>Activo</option><option>Inactivo</option>
            </select>
          </label>
          {!listado.cargando && <span className="contador-resultados">{consultoriosFiltrados.length} resultados</span>}
        </div>
        <AvisoCatalogo cargando={listado.cargando} error={listado.error} mensaje={listado.mensaje}
          reintentar={listado.reintentar} />
        {listado.seleccionado && <ConfirmacionEliminar
          descripcion={`el consultorio ${listado.seleccionado.codigo}: ${listado.seleccionado.nombre}`}
          eliminando={listado.eliminando} confirmar={listado.confirmarEliminacion} cancelar={listado.cancelarEliminacion} />}
        {!listado.cargando && !listado.error && (consultoriosFiltrados.length ? (
          <div className="tabla-responsive"><table>
            <thead><tr><th>Consultorio</th><th>Ubicación</th><th>Uso principal</th><th>Estado</th>
              <th><span className="solo-lectores">Acciones</span></th></tr></thead>
            <tbody>{consultoriosFiltrados.map((consultorio) => <tr key={consultorio.id}>
              <td><div className="identidad"><span className="avatar avatar--edificio" aria-hidden="true"><Building2 size={18} /></span>
                <span><strong>{consultorio.codigo}</strong><small>{consultorio.nombre}</small></span></div></td>
              <td><span className="dato-con-icono"><MapPin size={15} aria-hidden="true" /> {consultorio.ubicacion}</span></td>
              <td>{consultorio.especialidad || 'Uso general'}</td><td><Estado valor={consultorio.estado} /></td>
              <td><div className="acciones-tabla">
                <Link className="boton-icono" to={`/consultorios/${consultorio.id}/editar`}
                  aria-label={`Editar consultorio ${consultorio.codigo}`} aria-disabled={listado.eliminando || undefined}
                  onClick={(evento) => { if (listado.eliminando) evento.preventDefault(); }}>
                  <Pencil size={16} aria-hidden="true" />
                </Link>
                <button className="boton-icono boton-icono--peligro" type="button" disabled={listado.eliminando}
                  onClick={() => listado.seleccionar(consultorio)} aria-label={`Eliminar consultorio ${consultorio.codigo}`}>
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </div></td>
            </tr>)}</tbody>
          </table></div>
        ) : (
          <div className="sin-resultados"><Building2 size={32} aria-hidden="true" />
            <strong>{listado.registros.length ? 'No encontramos coincidencias' : 'Aún no hay consultorios registrados'}</strong>
            <p>{listado.registros.length ? 'Cambia el texto de búsqueda o el filtro de estado.'
              : 'Registra el primer consultorio de tu institución.'}</p>
          </div>
        ))}
      </section>
    </>
  );
}

export default Consultorios;
