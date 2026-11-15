import { useId, useRef, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { crearEtiqueta } from '../services/catalogos';

export default function SelectorEtiquetas({ etiqueta = 'Etiquetas', opciones, valor, cambiar, error, ayuda,
  deshabilitado, permitirAlta = false, alOcupar, ancho = false }) {
  const id = useId();
  const navegar = useNavigate();
  const [buscar, cambiarBuscar] = useState('');
  const [nuevas, cambiarNuevas] = useState([]);
  const [alta, cambiarAlta] = useState(false);
  const [nombre, cambiarNombre] = useState('');
  const [guardando, cambiarGuardando] = useState(false);
  const [errorAlta, cambiarErrorAlta] = useState('');
  const bloqueo = useRef(false);
  const disponibles = [...opciones, ...nuevas.filter((nueva) => !opciones.some((opcion) => opcion.id === nueva.id))];
  const elegidas = disponibles.filter((opcion) => valor.includes(opcion.id));
  const frecuencia = new Map();
  const claveNombre = (opcion) => opcion.nombre.normalize('NFC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('es');
  for (const opcion of disponibles) frecuencia.set(claveNombre(opcion), (frecuencia.get(claveNombre(opcion)) ?? 0) + 1);
  const nombreVisible = (opcion) => frecuencia.get(claveNombre(opcion)) > 1
    ? `${opcion.nombre} · ${opcion.compartida ? 'Compartida' : 'Institucional'}` : opcion.nombre;
  const termino = buscar.trim().toLocaleLowerCase('es');
  const visibles = disponibles.filter((opcion) => opcion.nombre.toLocaleLowerCase('es').includes(termino));

  const agregar = async () => {
    if (bloqueo.current || !nombre.trim()) return;
    bloqueo.current = true;
    cambiarGuardando(true);
    alOcupar?.(true);
    cambiarErrorAlta('');
    try {
      const creada = await crearEtiqueta({ nombre });
      cambiarNuevas((anteriores) => [...anteriores, creada]);
      cambiar([...valor, creada.id]);
      cambiarNombre('');
      cambiarBuscar('');
      cambiarAlta(false);
    } catch (fallo) {
      if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
      else cambiarErrorAlta(fallo.errores?.nombre || fallo.message);
    } finally {
      bloqueo.current = false;
      cambiarGuardando(false);
      alOcupar?.(false);
    }
  };

  return <fieldset className={`selector-etiquetas${ancho ? ' selector-etiquetas--ancho' : ''}`} disabled={deshabilitado || guardando}
    aria-describedby={ayuda ? `${id}-ayuda` : undefined}>
    <legend>{etiqueta}</legend>
    {ayuda && <small id={`${id}-ayuda`}>{ayuda}</small>}
    {elegidas.length > 0 && <ul className="etiquetas-elegidas" aria-label="Etiquetas seleccionadas">
      {elegidas.map((opcion) => <li key={opcion.id}>{nombreVisible(opcion)}</li>)}
    </ul>}
    {disponibles.length > 6 && <label className="planificador-busqueda">
      <Search size={16} aria-hidden="true" /><span className="solo-lectores">Buscar etiquetas</span>
      <input type="search" value={buscar} placeholder="Buscar etiquetas" onChange={(evento) => cambiarBuscar(evento.target.value)} />
    </label>}
    <div className="selector-etiquetas__lista">
      {visibles.map((opcion) => <label key={opcion.id}>
        <input type="checkbox" name="idsEtiquetas" value={opcion.id} checked={valor.includes(opcion.id)}
          onChange={(evento) => cambiar(evento.target.checked ? [...valor, opcion.id] : valor.filter((idEtiqueta) => idEtiqueta !== opcion.id))} />
        <span>{nombreVisible(opcion)}</span>
      </label>)}
      {!visibles.length && <small role="status">{termino ? 'Sin coincidencias.' : 'Todavía no hay etiquetas disponibles.'}</small>}
    </div>
    {error && <small className="campo-planificador__error" role="alert">{error}</small>}
    {permitirAlta && (alta ? <div className="selector-etiquetas__alta">
      <label className="campo-planificador"><span>Nombre de la nueva etiqueta</span><input type="text" maxLength={100} value={nombre}
        onChange={(evento) => cambiarNombre(evento.target.value)} onKeyDown={(evento) => {
          if (evento.key === 'Enter') { evento.preventDefault(); agregar(); }
        }} /></label>
      {errorAlta && <small className="campo-planificador__error" role="alert">{errorAlta}</small>}
      <div><button type="button" className="boton boton--secundario" disabled={!nombre.trim()} onClick={agregar}>Crear y seleccionar</button>
        <button type="button" className="boton boton--fantasma" onClick={() => { cambiarAlta(false); cambiarErrorAlta(''); }}>Cerrar alta</button></div>
    </div> : <button className="boton boton--fantasma selector-etiquetas__crear" type="button" onClick={() => cambiarAlta(true)}>
      <Plus size={15} aria-hidden="true" /> Crear etiqueta
    </button>)}
  </fieldset>;
}
