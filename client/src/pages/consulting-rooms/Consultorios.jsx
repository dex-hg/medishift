import { useMemo, useState } from 'react';
import { Building2, MapPin, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { consultorios } from '../../data/datosDemostracion';

function Consultorios() {
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('Todos');

  const consultoriosFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLocaleLowerCase('es');
    return consultorios.filter((consultorio) => {
      const coincideTexto = [consultorio.codigo, consultorio.nombre, consultorio.especialidad].some(
        (valor) => valor.toLocaleLowerCase('es').includes(termino),
      );
      const coincideEstado = estado === 'Todos' || consultorio.estado === estado;
      return coincideTexto && coincideEstado;
    });
  }, [busqueda, estado]);

  return (
    <>
      <EncabezadoPagina
        ruta={[{ etiqueta: 'Consultorios' }]}
        titulo="Consultorios"
        descripcion="Ambientes que pueden reservarse durante la programación de turnos."
        acciones={
          <Link className="boton boton--primario" to="/consultorios/nuevo">
            <Plus size={18} />
            Nuevo consultorio
          </Link>
        }
      />

      <section className="panel panel--tabla">
        <div className="barra-tabla">
          <label className="buscador">
            <Search size={17} />
            <span className="solo-lectores">Buscar consultorios</span>
            <input
              type="search"
              value={busqueda}
              onChange={(evento) => setBusqueda(evento.target.value)}
              placeholder="Código, nombre o especialidad"
            />
          </label>
          <label className="selector-filtro">
            <span>Estado</span>
            <select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
              <option>Todos</option>
              <option>Disponible</option>
              <option>Ocupado</option>
              <option>Mantenimiento</option>
            </select>
          </label>
          <span className="contador-resultados">{consultoriosFiltrados.length} resultados</span>
        </div>

        {consultoriosFiltrados.length > 0 ? (
          <div className="tabla-responsive">
            <table>
              <thead>
                <tr>
                  <th>Consultorio</th>
                  <th>Ubicación</th>
                  <th>Uso principal</th>
                  <th>Estado</th>
                  <th><span className="solo-lectores">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {consultoriosFiltrados.map((consultorio) => (
                  <tr key={consultorio.id}>
                    <td>
                      <div className="identidad">
                        <span className="avatar avatar--edificio"><Building2 size={18} /></span>
                        <span>
                          <strong>{consultorio.codigo}</strong>
                          <small>{consultorio.nombre}</small>
                        </span>
                      </div>
                    </td>
                    <td><span className="dato-con-icono"><MapPin size={15} /> {consultorio.piso}</span></td>
                    <td>{consultorio.especialidad}</td>
                    <td><Estado valor={consultorio.estado} /></td>
                    <td>
                      <div className="acciones-tabla">
                        <Link
                          className="boton-icono"
                          to={`/consultorios/${consultorio.id}/editar`}
                          aria-label={`Editar consultorio ${consultorio.codigo}`}
                        >
                          <Pencil size={16} />
                        </Link>
                        <button
                          className="boton-icono boton-icono--peligro"
                          type="button"
                          disabled
                          aria-label={`Eliminar consultorio ${consultorio.codigo}`}
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
            <Building2 size={32} />
            <strong>No encontramos consultorios</strong>
            <p>Cambia el texto de búsqueda o el filtro de estado.</p>
          </div>
        )}
      </section>
    </>
  );
}

export default Consultorios;
