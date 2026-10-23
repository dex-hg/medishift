import { useEffect, useId, useRef, useState } from 'react';
import { Plus } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { crearEspecialidad } from '../services/catalogos';

/** Selección explícita y alta sin perder los datos del formulario que lo contiene. */
export default function SelectorEspecialidad({ especialidades = [], value, onChange, name = 'especialidad',
  etiqueta = 'Especialidad', requerido = true, permitirGeneral = false, usarId = false, disabled = false,
  error, ayuda, alOcupar = () => {} }) {
  const identificador = useId();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const [nuevas, setNuevas] = useState([]);
  const [nombre, setNombre] = useState('');
  const [abierto, setAbierto] = useState(false);
  const [creando, setCreando] = useState(false);
  const [fallo, setFallo] = useState('');
  const bloqueo = useRef(false);
  const vigente = useRef(true);
  const solicitud = useRef(null);
  useEffect(() => { vigente.current = true; return () => { vigente.current = false; solicitud.current?.abort(); }; }, []);
  const opciones = [...new Map([...especialidades, ...nuevas].map((registro) => [registro.id, registro])).values()]
    .sort((primero, segundo) => primero.nombre.localeCompare(segundo.nombre, 'es'));
  const cambiar = (valor) => onChange({ target: { name, value: valor } });
  const crear = async () => {
    if (disabled || bloqueo.current) return;
    bloqueo.current = true;
    solicitud.current = new AbortController();
    setCreando(true);
    alOcupar(true);
    setFallo('');
    try {
      const nueva = await crearEspecialidad({ nombre }, { senal: solicitud.current.signal });
      if (!vigente.current) return;
      setNuevas((anteriores) => [...anteriores, nueva]);
      cambiar(usarId ? nueva.id : nueva.nombre);
      setNombre('');
      setAbierto(false);
    } catch (problema) {
      if (vigente.current) {
        if (problema.estado === 401) navegar('/iniciar-sesion', { replace: true, state: { destino: ubicacion.pathname } });
        else setFallo(problema.errores?.nombre || problema.message);
      }
    } finally {
      bloqueo.current = false;
      if (vigente.current) { setCreando(false); alOcupar(false); }
    }
  };
  const estaEnLista = opciones.some((registro) => (usarId ? registro.id : registro.nombre) === value);
  return <div className={`campo campo--normal ${error ? 'campo--error' : ''}`}>
    <label htmlFor={identificador}><span>{etiqueta}{requerido && <abbr title="Obligatorio"> *</abbr>}</span></label>
    <select id={identificador} name={name} value={value} required={requerido} disabled={disabled || creando}
      aria-invalid={Boolean(error)} onChange={(evento) => cambiar(evento.target.value)}>
      <option value="">{permitirGeneral ? 'Uso general' : 'Selecciona una especialidad'}</option>
      {value && !estaEnLista && <option value={value}>{usarId ? 'Especialidad seleccionada' : value}</option>}
      {opciones.map((registro) => <option key={registro.id} value={usarId ? registro.id : registro.nombre}>{registro.nombre}</option>)}
    </select>
    {error ? <small className="campo__error" role="alert">{error}</small> : ayuda && <small>{ayuda}</small>}
    <button className="boton boton--fantasma selector-especialidad__crear" type="button" disabled={disabled || creando}
      aria-expanded={abierto} aria-controls={`${identificador}-alta`} onClick={() => { setAbierto((anterior) => !anterior); setFallo(''); }}>
      <Plus size={14} aria-hidden="true" />{abierto ? 'Cerrar nueva especialidad' : 'Crear especialidad'}</button>
    {abierto && <div id={`${identificador}-alta`} className="selector-especialidad__alta">
      <label htmlFor={`${identificador}-nombre`}>Nombre de la nueva especialidad</label>
      <input id={`${identificador}-nombre`} type="text" maxLength={100} value={nombre} disabled={disabled || creando}
        onChange={(evento) => { setNombre(evento.target.value); setFallo(''); }} onKeyDown={(evento) => {
          if (evento.key === 'Enter') { evento.preventDefault(); crear(); }
        }} />
      {fallo && <small className="campo__error" role="alert">{fallo}</small>}
      <button className="boton boton--secundario" type="button" disabled={disabled || creando || !nombre.trim()} onClick={crear}>
        {creando ? 'Creando…' : 'Crear y seleccionar'}</button>
    </div>}
  </div>;
}
