import { useMemo, useState } from 'react';
import { Pencil, Plus, Search, Trash2, UserRoundSearch } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { profesionales } from '../../data/datosDemostracion';

function Profesionales() {
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('Todos');

  const profesionalesFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLocaleLowerCase('es');
    return profesionales.filter((profesional) => {
      const coincideTexto = [
        profesional.nombreCompleto,
        profesional.colegiatura,
        profesional.especialidad,
      ].some((valor) => valor.toLocaleLowerCase('es').includes(termino));
      const coincideEstado = estado === 'Todos' || profesional.estado === estado;
      return coincideTexto && coincideEstado;
    });
  }, [busqueda, estado]);

  return (
    <>
      <EncabezadoPagina
        ruta={[{ etiqueta: 'Profesionales' }]}
        titulo="Profesionales"
        descripcion="Registro base del personal que puede recibir asignaciones de horario."
        acciones={
          <Link className="boton boton--primario" to="/profesionales/nuevo">
            <Plus size={18} />
            Nuevo profesional
          </Link>
        }
      />

      <section className="panel panel--tabla">
        <div className="barra-tabla">
          <label className="buscador">
            <Search size={17} />
            <span className="solo-lectores">Buscar profesionales</span>
            <input
              type="search"
              value={busqueda}
              onChange={(evento) => setBusqueda(evento.target.value)}
              placeholder="Nombre, CMP o especialidad"
            />
          </label>
          <label className="selector-filtro">
            <span>Estado</span>
            <select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
              <option>Todos</option>
              <option>Activo</option>
              <option>Inactivo</option>
            </select>
          </label>
          <span className="contador-resultados">{profesionalesFiltrados.length} resultados</span>
        </div>

        {profesionalesFiltrados.length > 0 ? (
          <div className="tabla-responsive">
            <table>
              <thead>
                <tr>
                  <th>Profesional</th>
                  <th>Colegiatura</th>
                  <th>Especialidad</th>
                  <th>Contacto</th>
                  <th>Estado</th>
                  <th><span className="solo-lectores">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {profesionalesFiltrados.map((profesional) => (
                  <tr key={profesional.id}>
                    <td>
                      <div className="identidad">
                        <span className={`avatar avatar--${profesional.color}`}>{profesional.iniciales}</span>
                        <span>
                          <strong>{profesional.nombreCompleto}</strong>
                          <small>{profesional.id}</small>
                        </span>
                      </div>
                    </td>
                    <td>{profesional.colegiatura}</td>
                    <td>{profesional.especialidad}</td>
                    <td>
                      <span className="dato-doble">
                        <span>{profesional.correo}</span>
                        <small>{profesional.telefono}</small>
                      </span>
                    </td>
                    <td><Estado valor={profesional.estado} /></td>
                    <td>
                      <div className="acciones-tabla">
                        <Link
                          className="boton-icono"
                          to={`/profesionales/${profesional.id}/editar`}
                          aria-label={`Editar ${profesional.nombreCompleto}`}
                        >
                          <Pencil size={16} />
                        </Link>
                        <button
                          className="boton-icono boton-icono--peligro"
                          type="button"
                          disabled
                          aria-label={`Eliminar ${profesional.nombreCompleto}`}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="sin-resultados">
            <UserRoundSearch size={32} />
            <strong>No encontramos profesionales</strong>
            <p>Prueba con otro nombre, CMP, especialidad o estado.</p>
          </div>
        )}
      </section>
    </>
  );
}

export default Profesionales;
