import { useState } from 'react';
import { Link } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import FormularioBase from '../../components/FormularioBase';
import { guardarPaciente } from '../../services/atencion';
import { useOperacionAtencion } from '../../hooks/useAtencion';
import { normalizarPaciente, validarPaciente } from '../../utils/validacionesAtencion';
import { fechaActual } from '../../utils/validacionesProgramacion';

export default function FormularioPaciente({ paciente, dni = '', zonaHoraria, guardado, recargar }) {
  const operacion = useOperacionAtencion();
  const [erroresLocales, cambiarErrores] = useState({});
  const [mensaje, cambiarMensaje] = useState('');
  const errores = { ...operacion.errores, ...erroresLocales };
  const hoy = fechaActual(zonaHoraria);
  const guardar = async (datos) => {
    cambiarMensaje('');
    const normalizados = normalizarPaciente({ ...datos, activo: datos.activo === 'true' });
    const validaciones = validarPaciente(normalizados, hoy);
    cambiarErrores(validaciones);
    if (Object.keys(validaciones).length) return;
    const resultado = await operacion.ejecutar(() => guardarPaciente(normalizados, { id: paciente?.id, revision: paciente?.revision }));
    if (resultado) { cambiarMensaje('La ficha del paciente se guardó.'); guardado(resultado); }
  };
  return <section className="panel">
    <FormularioBase onGuardar={guardar} guardando={operacion.guardando} error={operacion.error} errores={errores}
      accionesSecundarias={<><Link className="boton boton--secundario" to="/pacientes">Volver a pacientes</Link>
        {recargar && <button className="boton boton--fantasma" type="button" disabled={operacion.guardando} onClick={recargar}>Recargar ficha</button>}</>}>
      <div className="seccion-formulario"><div className="seccion-formulario__titulo"><div><h2>Datos del paciente</h2>
        <p>El código interno se asigna al registrar la ficha.</p></div></div>
        <div className="rejilla-formulario">
          <CampoFormulario etiqueta="Nombres" requerido error={errores.nombres}><input name="nombres" autoComplete="given-name" maxLength={80} required defaultValue={paciente?.nombres || ''} /></CampoFormulario>
          <CampoFormulario etiqueta="Apellidos" requerido error={errores.apellidos}><input name="apellidos" autoComplete="family-name" maxLength={100} required defaultValue={paciente?.apellidos || ''} /></CampoFormulario>
          <CampoFormulario etiqueta="DNI" requerido error={errores.dni}><input name="dni" inputMode="numeric" pattern="[0-9]{8}" maxLength={8} required defaultValue={paciente?.dni || dni} /></CampoFormulario>
          <CampoFormulario etiqueta="Fecha de nacimiento" requerido error={errores.fechaNacimiento}><input name="fechaNacimiento" type="date" min="0001-01-01" max={hoy} required defaultValue={paciente?.fechaNacimiento || ''} /></CampoFormulario>
          <CampoFormulario etiqueta="Correo" error={errores.correo}><input name="correo" type="email" autoComplete="email" maxLength={254} defaultValue={paciente?.correo || ''} /></CampoFormulario>
          <CampoFormulario etiqueta="Teléfono" error={errores.telefono}><input name="telefono" type="tel" autoComplete="tel" maxLength={20} defaultValue={paciente?.telefono || ''} /></CampoFormulario>
          <CampoFormulario etiqueta="Estado" error={errores.activo} ayuda="La desactivación conserva la ficha y sus citas; impide agendar nuevas citas.">
            <select name="activo" defaultValue={String(paciente?.activo ?? true)}><option value="true">Activo</option><option value="false">Inactivo</option></select>
          </CampoFormulario>
        </div>
      </div>
    </FormularioBase>
    {mensaje && <p className="atencion-aviso" role="status">{mensaje}</p>}
  </section>;
}
