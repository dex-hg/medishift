import { Building2, ChevronRight, FolderOpen, MapPin, Pencil, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import Estado from '../../components/Estado';

const SIN_AREA = 'Sin área asignada';
const compararNombres = new Intl.Collator('es', { numeric: true, sensitivity: 'base' }).compare;

export const etiquetasDeConsultorio = (consultorio) => (consultorio.usoGeneral
  ? consultorio.area?.etiquetas : consultorio.etiquetas) || [];

function IdentidadConsultorio({ consultorio }) {
  return <div className="identidad">
    <span className="avatar avatar--edificio" aria-hidden="true"><Building2 size={18} /></span>
    <span><strong>{consultorio.codigo}</strong><small>{consultorio.nombre}</small></span>
  </div>;
}

function UbicacionConsultorio({ consultorio }) {
  return <span className="dato-con-icono"><MapPin size={15} aria-hidden="true" /> {consultorio.ubicacion}</span>;
}

function EtiquetasConsultorio({ consultorio }) {
  const etiquetas = etiquetasDeConsultorio(consultorio);
  return <div className="consultorios-etiquetas">
    {consultorio.usoGeneral && <small>Uso general</small>}
    {etiquetas.length > 0 && <ul className="etiquetas-elegidas" aria-label="Etiquetas admitidas">
      {etiquetas.map((etiqueta) => <li key={etiqueta.id}>{etiqueta.nombre}</li>)}
    </ul>}
    {!consultorio.usoGeneral && !etiquetas.length && <small>Sin etiquetas activas</small>}
  </div>;
}

function AccionesConsultorio({ consultorio, seleccionar, eliminando }) {
  return <div className="acciones-tabla">
    <Link className="boton-icono" to={`/consultorios/${consultorio.id}/editar`}
      aria-label={`Editar consultorio ${consultorio.codigo}`} aria-disabled={eliminando || undefined}
      onClick={(evento) => { if (eliminando) evento.preventDefault(); }}>
      <Pencil size={16} aria-hidden="true" />
    </Link>
    <button className="boton-icono boton-icono--peligro" type="button" disabled={eliminando}
      onClick={() => seleccionar(consultorio)} aria-label={`Eliminar consultorio ${consultorio.codigo}`}>
      <Trash2 size={16} aria-hidden="true" />
    </button>
  </div>;
}

export function ListaConsultorios({ consultorios, seleccionar, eliminando }) {
  return <div className="tabla-responsive"><table>
    <thead><tr><th>Consultorio</th><th>Ubicación</th><th>Área</th><th>Etiquetas admitidas</th><th>Estado</th>
      <th><span className="solo-lectores">Acciones</span></th></tr></thead>
    <tbody>{consultorios.map((consultorio) => <tr key={consultorio.id}>
      <td><IdentidadConsultorio consultorio={consultorio} /></td>
      <td><UbicacionConsultorio consultorio={consultorio} /></td>
      <td><span className="dato-doble"><span>{consultorio.area?.nombrePadre || consultorio.area?.nombre || SIN_AREA}</span>
        {consultorio.area?.idPadre && <small>{consultorio.area.nombre}</small>}</span></td>
      <td><EtiquetasConsultorio consultorio={consultorio} /></td>
      <td><Estado valor={consultorio.estado} /></td>
      <td><AccionesConsultorio consultorio={consultorio} seleccionar={seleccionar} eliminando={eliminando} /></td>
    </tr>)}</tbody>
  </table></div>;
}

function agruparPorAreas(consultorios) {
  const areas = new Map();
  const sinArea = [];
  for (const consultorio of consultorios) {
    const { area } = consultorio;
    if (!area) { sinArea.push(consultorio); continue; }
    const idRaiz = area.idPadre || area.id;
    if (!areas.has(idRaiz)) {
      areas.set(idRaiz, { id: idRaiz, nombre: area.nombrePadre || area.nombre, salas: [], subareas: new Map() });
    }
    const raiz = areas.get(idRaiz);
    if (!area.idPadre) { raiz.salas.push(consultorio); continue; }
    if (!raiz.subareas.has(area.id)) raiz.subareas.set(area.id, { id: area.id, nombre: area.nombre, salas: [] });
    raiz.subareas.get(area.id).salas.push(consultorio);
  }
  return { areas: ordenarAreas(areas), sinArea };
}

function ordenarAreas(areas) {
  return Array.from(areas.values()).sort((primera, segunda) => compararNombres(primera.nombre, segunda.nombre));
}

function RamaConsultorios({ nombre, cantidad, children }) {
  return <details className="consultorios-rama" open>
    <summary><ChevronRight size={16} className="consultorios-rama__flecha" aria-hidden="true" />
      <FolderOpen size={16} aria-hidden="true" /><strong>{nombre}</strong>
      <small>{cantidad} {cantidad === 1 ? 'consultorio' : 'consultorios'}</small></summary>
    <div className="consultorios-rama__contenido">{children}</div>
  </details>;
}

function SalasDelArbol({ salas, seleccionar, eliminando }) {
  if (!salas.length) return null;
  return <ul className="consultorios-salas">
    {salas.map((consultorio) => <li key={consultorio.id} className="consultorios-sala">
      <IdentidadConsultorio consultorio={consultorio} />
      <UbicacionConsultorio consultorio={consultorio} />
      <EtiquetasConsultorio consultorio={consultorio} />
      <Estado valor={consultorio.estado} />
      <AccionesConsultorio consultorio={consultorio} seleccionar={seleccionar} eliminando={eliminando} />
    </li>)}
  </ul>;
}

export function ArbolConsultorios({ consultorios, seleccionar, eliminando }) {
  const { areas, sinArea } = agruparPorAreas(consultorios);
  const acciones = { seleccionar, eliminando };
  return <div className="consultorios-arbol">
    {areas.map((area) => {
      const subareas = ordenarAreas(area.subareas);
      const cantidad = subareas.reduce((total, subarea) => total + subarea.salas.length, area.salas.length);
      return <RamaConsultorios key={area.id} nombre={area.nombre} cantidad={cantidad}>
        <SalasDelArbol salas={area.salas} {...acciones} />
        {subareas.map((subarea) => <RamaConsultorios key={subarea.id} nombre={subarea.nombre} cantidad={subarea.salas.length}>
          <SalasDelArbol salas={subarea.salas} {...acciones} />
        </RamaConsultorios>)}
      </RamaConsultorios>;
    })}
    {sinArea.length > 0 && <RamaConsultorios nombre={SIN_AREA} cantidad={sinArea.length}>
      <SalasDelArbol salas={sinArea} {...acciones} />
    </RamaConsultorios>}
  </div>;
}
