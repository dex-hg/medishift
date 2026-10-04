import { useMemo, useState } from 'react';
import { CalendarClock, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { AvisoCatalogo, ConfirmacionEliminar } from '../../components/AvisoCatalogo';
import { useConsultaProgramacion } from '../../hooks/useProgramacion';
import { eliminarDisponibilidad, listarDisponibilidades } from '../../services/programacion';
import { DIAS_SEMANA, mostrarFecha } from '../../utils/validacionesProgramacion';

function Disponibilidades() {
  const listado = useConsultaProgramacion(listarDisponibilidades);
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('Todos');
  const [seleccionada, setSeleccionada] = useState(null);
  const registros = listado.datos || [];
  const filtrados = useMemo(() => registros.filter((item) =>
    item.profesional.toLocaleLowerCase('es').includes(busqueda.trim().toLocaleLowerCase('es'))
    && (estado === 'Todos' || item.estado === estado)), [registros, busqueda, estado]);
  const recargar = () => { setSeleccionada(null); listado.recargar(); };
  const confirmar = async () => {
    const exito = await listado.ejecutar((opciones) => eliminarDisponibilidad(seleccionada.id,
      { ...opciones, revision: seleccionada.revision }), 'La disponibilidad se eliminó correctamente.');
    if (exito) setSeleccionada(null);
  };

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Disponibilidades' }]} titulo="Disponibilidades"
      descripcion="Franjas semanales de atención y su intervalo de vigencia. Una disponibilidad puede superar 6 horas; los límites se aplican a los turnos."
      acciones={<Link className="boton boton--primario" to="/disponibilidades/nueva"><Plus size={18} /> Nueva disponibilidad</Link>} />
    <section className="panel panel--tabla" aria-busy={listado.cargando || listado.ocupado}>
      <div className="barra-tabla">
        <label className="buscador"><Search size={17} aria-hidden="true" />
          <span className="solo-lectores">Buscar profesional</span>
          <input type="search" value={busqueda} onChange={(evento) => setBusqueda(evento.target.value)} placeholder="Nombre del profesional" />
        </label>
        <label className="selector-filtro"><span>Estado</span><select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
          <option>Todos</option><option>Activo</option><option>Inactivo</option>
        </select></label>
        {!listado.cargando && <span className="contador-resultados">{filtrados.length} franjas</span>}
      </div>
      <AvisoCatalogo cargando={listado.cargando} error={listado.error} mensaje={listado.mensaje} reintentar={recargar} />
      {listado.conflicto && <p className="nota-programacion">Los datos cambiaron o están vinculados a un horario. Recarga antes de volver a modificar.</p>}
      {seleccionada && !listado.error && <ConfirmacionEliminar
        descripcion={`la disponibilidad de ${seleccionada.profesional}, ${DIAS_SEMANA[seleccionada.diaSemana - 1]} de ${seleccionada.horaInicio} a ${seleccionada.horaFin}, vigente del ${mostrarFecha(seleccionada.fechaInicio)} al ${mostrarFecha(seleccionada.fechaFin)}`}
        eliminando={listado.ocupado} confirmar={confirmar} cancelar={() => setSeleccionada(null)} />}
      {!listado.cargando && !listado.error && (filtrados.length ? <div className="tabla-responsive"><table>
        <thead><tr><th>Profesional</th><th>Día y franja</th><th>Vigencia</th><th>Observación</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>{filtrados.map((item) => <tr key={item.id}>
          <td><strong>{item.profesional}</strong></td>
          <td><span className="dato-doble"><strong>{DIAS_SEMANA[item.diaSemana - 1]}</strong><small>{item.horaInicio} a {item.horaFin}</small></span></td>
          <td><span className="dato-doble"><span>{mostrarFecha(item.fechaInicio)}</span><small>hasta {mostrarFecha(item.fechaFin)}</small></span></td>
          <td>{item.observacion || 'Sin observación'}</td><td><Estado valor={item.estado} /></td>
          <td><div className="acciones-tabla"><Link className="boton-icono" to={`/disponibilidades/${item.id}/editar`}
            aria-label={`Editar disponibilidad de ${item.profesional}`} onClick={(evento) => { if (listado.ocupado) evento.preventDefault(); }}>
            <Pencil size={16} /></Link>
          <button className="boton-icono boton-icono--peligro" type="button" disabled={listado.ocupado}
            aria-label={`Eliminar disponibilidad de ${item.profesional}`} onClick={() => setSeleccionada(item)}><Trash2 size={16} /></button></div></td>
        </tr>)}</tbody>
      </table></div> : <div className="sin-resultados"><CalendarClock size={30} /><strong>{registros.length ? 'No encontramos coincidencias' : 'Aún no hay disponibilidades'}</strong>
        <p>Registra las franjas del profesional antes de asignarle turnos.</p></div>)}
    </section>
  </>;
}

export default Disponibilidades;
