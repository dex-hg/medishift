import { useRef, useState } from 'react';
import { Check, Search, UserRoundPlus } from 'lucide-react';
import FormularioPaciente from '../patients/FormularioPaciente';
import { buscarPacienteDni } from '../../services/atencion';
import { useOperacionAtencion } from '../../hooks/useAtencion';

export default function SelectorPacienteCita({ paciente, seleccionar, bloqueado, ocupado, zonaHoraria }) {
  const [dni, cambiarDni] = useState(paciente?.dni || '');
  const [nuevo, cambiarNuevo] = useState(false);
  const consulta = useOperacionAtencion();
  const secuencia = useRef(0);
  const buscar = async (evento) => {
    evento.preventDefault();
    const solicitud = ++secuencia.current;
    cambiarNuevo(false);
    const resultado = await consulta.ejecutar(() => buscarPacienteDni(dni));
    if (solicitud !== secuencia.current || !resultado) return;
    if (resultado[0]) seleccionar(resultado[0]);
    else cambiarNuevo(true);
  };
  if (paciente) return <section className="reserva-paciente" aria-label="Paciente seleccionado">
    <span className="reserva-paciente__icono" aria-hidden="true"><Check size={18} /></span>
    <div><strong>{paciente.nombres} {paciente.apellidos}</strong><small>{paciente.dni ? `DNI ${paciente.dni}` : 'Paciente de la cita'}
      {!paciente.activo && ' · Inactivo'}</small></div>
    {!bloqueado && <button className="boton boton--fantasma" disabled={ocupado} type="button" onClick={() => { seleccionar(null); cambiarNuevo(false); consulta.limpiar(); }}>Cambiar</button>}
  </section>;
  return <section className="selector-paciente-cita" aria-label="Seleccionar o registrar paciente">
    {!nuevo && <form className="reserva-busqueda" onSubmit={buscar}>
      <label className="campo-planificador"><span>DNI del paciente</span><input inputMode="numeric" pattern="[0-9]{8}" maxLength={8} value={dni} required disabled={ocupado || consulta.guardando}
        onChange={(evento) => { ++secuencia.current; cambiarDni(evento.target.value); consulta.limpiar(); }} /></label>
      <button className="boton boton--secundario" disabled={ocupado || consulta.guardando || !/^[0-9]{8}$/.test(dni)}>
        <Search size={16} /> {consulta.guardando ? 'Buscando…' : 'Buscar paciente'}</button>
    </form>}
    {consulta.error && <p className="atencion-error" role="alert">{consulta.error}</p>}
    {nuevo && <><p className="reserva-nuevo"><UserRoundPlus size={17} /> No existe una ficha con este DNI. Puedes registrarla aquí.</p>
      <FormularioPaciente key={dni} dni={dni} integrado consultarAlAbrir zonaHoraria={zonaHoraria} ocupadoExterno={ocupado}
        cancelar={() => cambiarNuevo(false)} guardado={(resultado) => { cambiarNuevo(false); seleccionar(resultado); }} />
    </>}
  </section>;
}
