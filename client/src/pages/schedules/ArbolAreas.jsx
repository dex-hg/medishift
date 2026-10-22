import { ChevronDown, DoorOpen, Folder, FolderOpen, Plus } from 'lucide-react';
import { useState } from 'react';

export default function ArbolAreas({ areas, consultorios, seleccionado, seleccionar, agregar, ocupado }) {
  const [cerradas, setCerradas] = useState(new Set());
  const alternar = (id) => setCerradas((anteriores) => {
    const siguientes = new Set(anteriores);
    if (siguientes.has(id)) siguientes.delete(id); else siguientes.add(id);
    return siguientes;
  });
  const sinArea = consultorios.filter((consultorio) => !consultorio.idArea);
  return <section className="panel planificador-arbol" aria-label="Áreas y consultorios">
    <div className="planificador-titulo"><div><span className="planificador-paso">1</span><h2>Elige un consultorio</h2></div>
      <button className="boton-icono" type="button" disabled={ocupado} onClick={() => agregar({ tipo: 'area' })}
        aria-label="Crear área"><Plus size={18} /></button></div>
    <p className="planificador-ayuda">Cada área es una carpeta con sus consultorios.</p>
    {!areas.length && <div className="planificador-vacio"><Folder size={25} /><p>Comienza creando un área, como Dermatología.</p>
      <button className="boton boton--secundario" type="button" disabled={ocupado} onClick={() => agregar({ tipo: 'area' })}>Crear primera área</button></div>}
    <ul className="arbol-areas">
      {areas.map((area) => {
        const hijos = consultorios.filter((consultorio) => consultorio.idArea === area.id);
        const abierta = !cerradas.has(area.id);
        return <li key={area.id}>
          <div className="arbol-areas__carpeta"><button type="button" aria-expanded={abierta} onClick={() => alternar(area.id)}>
            <ChevronDown size={14} className={!abierta ? 'arbol-areas__cerrado' : ''} />
            {abierta ? <FolderOpen size={18} /> : <Folder size={18} />}<span>{area.nombre}</span><small>{hijos.length}</small>
          </button><button className="boton-icono" type="button" disabled={ocupado} onClick={() => agregar({ tipo: 'consultorio', idArea: area.id })}
            aria-label={`Añadir consultorio a ${area.nombre}`}><Plus size={15} /></button></div>
          {abierta && <ul>{hijos.map((consultorio) => <li key={consultorio.id}>
            <button className={`arbol-areas__consultorio ${seleccionado === consultorio.id ? 'seleccionado' : ''}`}
              aria-pressed={seleccionado === consultorio.id} type="button" disabled={ocupado} onClick={() => seleccionar(consultorio.id)}>
              <DoorOpen size={16} /><span>{consultorio.nombre}</span>{consultorio.estado !== 'Activo' && <small>Inactivo</small>}
            </button></li>)}
            {!hijos.length && <li className="arbol-areas__sin-hijos">Todavía no hay consultorios</li>}</ul>}
        </li>;
      })}
    </ul>
    {sinArea.length > 0 && <div className="arbol-areas__pendientes"><strong>{sinArea.length} consultorios sin área</strong>
      <p>Asócialos para empezar a programar.</p><button className="boton boton--fantasma" type="button"
        disabled={ocupado || !areas.length} onClick={() => agregar({ tipo: 'vincular', idArea: areas[0]?.id })}>Organizar consultorios</button></div>}
  </section>;
}
