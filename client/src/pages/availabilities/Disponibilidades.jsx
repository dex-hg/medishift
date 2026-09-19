import { useMemo, useState } from 'react';
import { CalendarClock, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { disponibilidades } from '../../data/datosDemostracion';

function Disponibilidades() {
  const [busqueda, setBusqueda] = useState('');
  const [dia, setDia] = useState('Todos');

  const disponibilidadesFiltradas = useMemo(() => {
    const termino = busqueda.trim().toLocaleLowerCase('es');
    return disponibilidades.filter((disponibilidad) => {
      const coincideTexto = disponibilidad.profesional.toLocaleLowerCase('es').includes(termino);
      const coincideDia = dia === 'Todos' || disponibilidad.dia === dia;
      return coincideTexto && coincideDia;
    });
  }, [busqueda, dia]);

  return (
    <>
      <EncabezadoPagina
        ruta={[{ etiqueta: 'Disponibilidades' }]}
        titulo="Disponibilidades"
        descripcion="Franjas declaradas por el personal antes de crear una asignación."
        acciones={
          <Link className="boton boton--primario" to="/disponibilidades/nueva">
            <Plus size={18} />
            Nueva disponibilidad
          </Link>
        }
      />

      <section className="panel panel--tabla">
        <div className="barra-tabla">
          <label className="buscador">
            <Search size={17} />
            <span className="solo-lectores">Buscar disponibilidades</span>
            <input
              type="search"
              value={busqueda}
              onChange={(evento) => setBusqueda(evento.target.value)}
              placeholder="Buscar profesional"
            />
          </label>
          <label className="selector-filtro">
            <span>Día</span>
            <select value={dia} onChange={(evento) => setDia(evento.target.value)}>
              <option>Todos</option>
              <option>Lunes</option>
              <option>Martes</option>
              <option>Miércoles</option>
              <option>Jueves</option>
              <option>Viernes</option>
              <option>Sábado</option>
            </select>
          </label>
          <span className="contador-resultados">{disponibilidadesFiltradas.length} resultados</span>
        </div>

        {disponibilidadesFiltradas.length > 0 ? (
          <div className="tabla-responsive">
            <table>
              <thead>
                <tr>
                  <th>Profesional</th>
                  <th>Día</th>
                  <th>Franja</th>
                  <th>Vigencia</th>
                  <th>Estado</th>
                  <th><span className="solo-lectores">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {disponibilidadesFiltradas.map((disponibilidad) => (
                  <tr key={disponibilidad.id}>
                    <td>
                      <span className="dato-doble">
                        <strong>{disponibilidad.profesional}</strong>
                        <small>{disponibilidad.id}</small>
                      </span>
                    </td>
                    <td>{disponibilidad.dia}</td>
                    <td><span className="franja-horaria">{disponibilidad.inicio} - {disponibilidad.fin}</span></td>
                    <td>{disponibilidad.vigencia}</td>
                    <td><Estado valor={disponibilidad.estado} /></td>
                    <td>
                      <div className="acciones-tabla">
                        <Link
                          className="boton-icono"
                          to={`/disponibilidades/${disponibilidad.id}/editar`}
                          aria-label={`Editar disponibilidad de ${disponibilidad.profesional}`}
                        >
                          <Pencil size={16} />
                        </Link>
                        <button
                          className="boton-icono boton-icono--peligro"
                          type="button"
                          disabled
                          aria-label={`Eliminar disponibilidad de ${disponibilidad.profesional}`}
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
            <CalendarClock size={32} />
            <strong>No encontramos disponibilidades</strong>
            <p>Cambia el profesional buscado o selecciona otro día.</p>
          </div>
        )}
      </section>
    </>
  );
}

export default Disponibilidades;
