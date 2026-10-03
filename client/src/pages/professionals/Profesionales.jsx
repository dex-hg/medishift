import { useMemo, useState } from 'react';
import { Pencil, Plus, Search, Trash2, UserRoundSearch } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { AvisoCatalogo, ConfirmacionEliminar } from '../../components/AvisoCatalogo';
import { useListadoCatalogo } from '../../hooks/useCatalogos';
import { eliminarProfesional, obtenerProfesionales } from '../../services/catalogos';

function Profesionales() {
  const listado = useListadoCatalogo(obtenerProfesionales, eliminarProfesional);
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('Todos');
  const profesionalesFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLocaleLowerCase('es');
    return listado.registros.filter((profesional) => {
      const coincideTexto = [profesional.nombres, profesional.apellidos,
        `${profesional.nombres} ${profesional.apellidos}`, profesional.colegiatura,
        profesional.especialidad, profesional.categoria, profesional.correo]
        .some((valor) => valor.toLocaleLowerCase('es').includes(termino));
      return coincideTexto && (estado === 'Todos' || profesional.estado === estado);
    });
  }, [listado.registros, busqueda, estado]);

  return (
    <>
      <EncabezadoPagina ruta={[{ etiqueta: 'Profesionales' }]} titulo="Profesionales"
        descripcion="Personal de tu institución disponible para la programación de horarios."
        acciones={<Link className="boton boton--primario" to="/profesionales/nuevo">
          <Plus size={18} aria-hidden="true" /> Nuevo profesional
        </Link>} />
      <section className="panel panel--tabla" aria-busy={listado.cargando || listado.eliminando}>
        <div className="barra-tabla">
          <label className="buscador"><Search size={17} aria-hidden="true" />
            <span className="solo-lectores">Buscar profesionales</span>
            <input type="search" value={busqueda} onChange={(evento) => setBusqueda(evento.target.value)}
              placeholder="Nombre, colegiatura o especialidad" />
          </label>
          <label className="selector-filtro"><span>Estado</span>
            <select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
              <option>Todos</option><option>Activo</option><option>Inactivo</option>
            </select>
          </label>
          {!listado.cargando && <span className="contador-resultados">{profesionalesFiltrados.length} resultados</span>}
        </div>
        <AvisoCatalogo cargando={listado.cargando} error={listado.error} mensaje={listado.mensaje}
          reintentar={listado.reintentar} />
        {listado.seleccionado && <ConfirmacionEliminar
          descripcion={`al profesional ${listado.seleccionado.nombres} ${listado.seleccionado.apellidos}, colegiatura ${listado.seleccionado.colegiatura}`}
          eliminando={listado.eliminando} confirmar={listado.confirmarEliminacion} cancelar={listado.cancelarEliminacion} />}
        {!listado.cargando && !listado.error && (profesionalesFiltrados.length ? (
          <div className="tabla-responsive"><table>
            <thead><tr><th>Profesional</th><th>Colegiatura</th><th>Especialidad</th><th>Contacto</th><th>Estado</th>
              <th><span className="solo-lectores">Acciones</span></th></tr></thead>
            <tbody>{profesionalesFiltrados.map((profesional) => {
              const nombreCompleto = `${profesional.nombres} ${profesional.apellidos}`;
              const iniciales = `${profesional.nombres[0]}${profesional.apellidos[0]}`.toLocaleUpperCase('es');
              return <tr key={profesional.id}>
                <td><div className="identidad"><span className="avatar avatar--turquesa" aria-hidden="true">{iniciales}</span>
                  <span><strong>{nombreCompleto}</strong><small>{profesional.categoria}</small></span>
                </div></td>
                <td>{profesional.colegiatura}</td><td>{profesional.especialidad}</td>
                <td><span className="dato-doble"><span>{profesional.correo}</span>
                  <small>{profesional.telefono || 'Sin teléfono'}</small></span></td>
                <td><Estado valor={profesional.estado} /></td>
                <td><div className="acciones-tabla">
                  <Link className="boton-icono" to={`/profesionales/${profesional.id}/editar`}
                    aria-label={`Editar ${nombreCompleto}`} aria-disabled={listado.eliminando || undefined}
                    onClick={(evento) => { if (listado.eliminando) evento.preventDefault(); }}>
                    <Pencil size={16} aria-hidden="true" />
                  </Link>
                  <button className="boton-icono boton-icono--peligro" type="button" disabled={listado.eliminando}
                    onClick={() => listado.seleccionar(profesional)} aria-label={`Eliminar ${nombreCompleto}`}>
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                </div></td>
              </tr>;
            })}</tbody>
          </table></div>
        ) : (
          <div className="sin-resultados"><UserRoundSearch size={32} aria-hidden="true" />
            <strong>{listado.registros.length ? 'No encontramos coincidencias' : 'Aún no hay profesionales registrados'}</strong>
            <p>{listado.registros.length ? 'Prueba con otro nombre, colegiatura, especialidad o estado.'
              : 'Registra el primer profesional de tu institución.'}</p>
          </div>
        ))}
      </section>
    </>
  );
}

export default Profesionales;
