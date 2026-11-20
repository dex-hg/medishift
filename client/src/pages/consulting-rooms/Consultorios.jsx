import { useMemo, useState } from 'react';
import { Building2, FolderTree, List, Plus, Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import { AvisoCatalogo, ConfirmacionEliminar } from '../../components/AvisoCatalogo';
import { useListadoCatalogo } from '../../hooks/useCatalogos';
import { eliminarConsultorio, obtenerConsultorios } from '../../services/catalogos';
import { ArbolConsultorios, ListaConsultorios, etiquetasDeConsultorio } from './VistasConsultorios';

const normalizarBusqueda = (texto) => texto.normalize('NFD').replace(/\p{M}/gu, '').trim().toLocaleLowerCase('es');

function Consultorios() {
  const listado = useListadoCatalogo(obtenerConsultorios, eliminarConsultorio);
  const [busqueda, cambiarBusqueda] = useState('');
  const [estado, cambiarEstado] = useState('Todos');
  const [vista, cambiarVista] = useState('lista');
  const consultoriosFiltrados = useMemo(() => {
    const termino = normalizarBusqueda(busqueda);
    return listado.registros.filter((consultorio) => {
      const coincideTexto = [consultorio.codigo, consultorio.nombre, consultorio.ubicacion,
        consultorio.area?.nombre || 'Sin área asignada', consultorio.area?.nombrePadre || '',
        consultorio.usoGeneral ? 'Uso general' : '', ...etiquetasDeConsultorio(consultorio).map((etiqueta) => etiqueta.nombre)]
        .some((valor) => normalizarBusqueda(valor).includes(termino));
      return coincideTexto && (estado === 'Todos' || consultorio.estado === estado);
    });
  }, [listado.registros, busqueda, estado]);
  const VistaConsultorios = vista === 'arbol' ? ArbolConsultorios : ListaConsultorios;

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
            <input type="search" value={busqueda} onChange={(evento) => cambiarBusqueda(evento.target.value)}
              placeholder="Código, nombre, ubicación, área o etiqueta" />
          </label>
          <label className="selector-filtro"><span>Estado</span>
            <select value={estado} onChange={(evento) => cambiarEstado(evento.target.value)}>
              <option>Todos</option><option>Activo</option><option>Inactivo</option>
            </select>
          </label>
          <div className="consultorios-vista" role="group" aria-label="Vista de consultorios">
            <button type="button" aria-pressed={vista === 'lista'} onClick={() => cambiarVista('lista')}>
              <List size={16} aria-hidden="true" /> Lista
            </button>
            <button type="button" aria-pressed={vista === 'arbol'} onClick={() => cambiarVista('arbol')}>
              <FolderTree size={16} aria-hidden="true" /> Árbol
            </button>
          </div>
          {!listado.cargando && <span className="contador-resultados">{consultoriosFiltrados.length} resultados</span>}
        </div>
        <AvisoCatalogo cargando={listado.cargando} error={listado.error} mensaje={listado.mensaje}
          reintentar={listado.reintentar} />
        {listado.seleccionado && <ConfirmacionEliminar
          descripcion={`el consultorio ${listado.seleccionado.codigo}: ${listado.seleccionado.nombre}`}
          eliminando={listado.eliminando} confirmar={listado.confirmarEliminacion} cancelar={listado.cancelarEliminacion} />}
        {!listado.cargando && !listado.error && (consultoriosFiltrados.length ? (
          <VistaConsultorios consultorios={consultoriosFiltrados} seleccionar={listado.seleccionar} eliminando={listado.eliminando} />
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
