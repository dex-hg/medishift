import { CalendarDays, ChevronDown, DoorOpen, Folder, FolderOpen, FolderPlus, Plus, Settings2 } from 'lucide-react';
import { useId, useState } from 'react';
import { etiquetasDelArea } from '../../utils/calendarioMensual';

export default function ArbolAreas({ areas, consultorios, seleccionado, idArea, seleccionar, seleccionarArea, agregar, ocupado }) {
  const [cerradas, cambiarCerradas] = useState(new Set());
  const prefijo = useId();
  const alternar = (id) => cambiarCerradas((anteriores) => {
    const siguientes = new Set(anteriores);
    if (siguientes.has(id)) siguientes.delete(id); else siguientes.add(id);
    return siguientes;
  });
  const sinArea = consultorios.filter((consultorio) => !consultorio.idArea);
  const mostrarArea = (area) => {
    const salas = consultorios.filter((consultorio) => consultorio.idArea === area.id);
    const subareas = areas.filter((unidad) => unidad.idAreaPadre === area.id);
    const etiquetas = etiquetasDelArea(area);
    const abierta = !cerradas.has(area.id);
    const seccion = `${prefijo}-${area.id}`;
    return <li key={area.id}>
      <div className="arbol-areas__carpeta">
        <button type="button" aria-expanded={abierta} aria-controls={seccion} onClick={() => alternar(area.id)}>
          <ChevronDown size={14} className={!abierta ? 'arbol-areas__cerrado' : ''} aria-hidden="true" />
          {abierta ? <FolderOpen size={18} aria-hidden="true" /> : <Folder size={18} aria-hidden="true" />}
          <span>{area.nombre}</span>{area.estado === 'Inactivo' && <small>Inactiva</small>}
        </button>
        <button type="button" className="boton-icono" aria-label={`Ver calendario de ${area.nombre}`}
          aria-pressed={idArea === area.id && !seleccionado} disabled={ocupado} onClick={() => seleccionarArea(area.id)}>
          <CalendarDays size={15} aria-hidden="true" />
        </button>
        {agregar && <button className="boton-icono" type="button" disabled={ocupado} onClick={() => agregar({ tipo: 'editar-area', idArea: area.id })}
          aria-label={`Configurar ${area.nombre}`}><Settings2 size={15} aria-hidden="true" /></button>}
      </div>
      <p className="arbol-areas__especialidad">{etiquetas.map((etiqueta) => etiqueta.nombre).join(', ') || 'Sin etiquetas'}</p>
      <ul id={seccion} hidden={!abierta}>
        {salas.map((consultorio) => <li key={consultorio.id}>
          <button className={`arbol-areas__consultorio ${seleccionado === consultorio.id ? 'seleccionado' : ''}`}
            aria-pressed={seleccionado === consultorio.id} type="button" disabled={ocupado} onClick={() => seleccionar(consultorio.id)}>
            <DoorOpen size={16} aria-hidden="true" /><span>{consultorio.codigo} · {consultorio.nombre}</span>
            {consultorio.estado !== 'Activo' && <small>Inactivo</small>}
          </button>
        </li>)}
        {subareas.map(mostrarArea)}
        {!salas.length && !subareas.length && <li className="arbol-areas__sin-hijos">Sin consultorios</li>}
        {agregar && area.estado === 'Activo' && <li className="arbol-areas__acciones">
          {etiquetas.length > 0 && <button className="boton boton--fantasma" type="button" disabled={ocupado}
            aria-label={`Añadir consultorio a ${area.nombre}`} onClick={() => agregar({ tipo: 'consultorio', idArea: area.id })}>
            <Plus size={14} aria-hidden="true" /> Consultorio</button>}
          {!area.idAreaPadre && <button className="boton boton--fantasma" type="button" disabled={ocupado}
            aria-label={`Crear subárea de ${area.nombre}`} onClick={() => agregar({ tipo: 'area', idAreaPadre: area.id })}>
            <FolderPlus size={14} aria-hidden="true" /> Subárea</button>}
        </li>}
      </ul>
    </li>;
  };

  return <section className="panel planificador-arbol" aria-label="Áreas, subáreas y consultorios">
    <div className="planificador-titulo"><div><span className="planificador-paso">1</span><h2>Áreas y consultorios</h2></div></div>
    {!areas.length && <p className="planificador-ayuda">{agregar ? 'Crea un área para organizar sus consultorios y turnos.' : 'No hay áreas disponibles.'}</p>}
    <ul className="arbol-areas">{areas.filter((area) => !area.idAreaPadre).map(mostrarArea)}</ul>
    {sinArea.length > 0 && agregar && <div className="arbol-areas__pendientes"><strong>{sinArea.length} consultorios sin área</strong>
      <button className="boton boton--fantasma" type="button" disabled={ocupado || !areas.some((area) => area.estado === 'Activo' && etiquetasDelArea(area).length)}
        onClick={() => agregar({ tipo: 'vincular', idArea })}>Vincular consultorios</button></div>}
  </section>;
}
