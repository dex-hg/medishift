import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import FormularioBase from '../../components/FormularioBase';
import { buscarPacienteDni, guardarPaciente } from '../../services/atencion';
import { useOperacionAtencion } from '../../hooks/useAtencion';
import useIdentidadDni from '../../hooks/useIdentidadDni';
import { normalizarPaciente, validarPaciente } from '../../utils/validacionesAtencion';
import { fechaActual } from '../../utils/validacionesProgramacion';

export default function FormularioPaciente({ paciente, dni = '', zonaHoraria, guardado, recargar,
  integrado = false, cancelar, consultarAlAbrir = false, ocupadoExterno = false }) {
  const operacion = useOperacionAtencion();
  const identidad = useIdentidadDni();
  const [datos, cambiarDatos] = useState(() => ({ nombres: paciente?.nombres || '', apellidos: paciente?.apellidos || '',
    dni: paciente?.dni || dni, fechaNacimiento: paciente?.fechaNacimiento || '', correo: paciente?.correo || '',
    telefono: paciente?.telefono || '', activo: paciente?.activo ?? true }));
  const [erroresLocales, cambiarErrores] = useState({});
  const [mensaje, cambiarMensaje] = useState('');
  const [contacto, cambiarContacto] = useState(!integrado || Boolean(paciente?.correo || paciente?.telefono));
  const errores = { ...operacion.errores, ...erroresLocales };
  const hoy = fechaActual(zonaHoraria);
  const ocupado = operacion.guardando || identidad.consultando || ocupadoExterno;
  const consultar = async (documento) => {
    const resultado = await identidad.consultar(documento);
    if (resultado) cambiarDatos((anteriores) => anteriores.dni === documento
      ? { ...anteriores, nombres: resultado.nombres, apellidos: resultado.apellidos } : anteriores);
  };
  useEffect(() => {
    if (consultarAlAbrir && !paciente && /^[0-9]{8}$/.test(dni)) void consultar(dni);
  }, [consultarAlAbrir, dni, paciente]);
  const cambiar = (campo, valor) => {
    if (campo === 'dni') identidad.cancelar();
    cambiarDatos((anteriores) => ({ ...anteriores, [campo]: valor,
      ...(!paciente && campo === 'dni' ? { nombres: '', apellidos: '' } : {}) }));
    cambiarErrores({}); cambiarMensaje(''); operacion.limpiar();
  };
  const guardar = async () => {
    cambiarMensaje('');
    const normalizados = normalizarPaciente(datos);
    const validaciones = validarPaciente(normalizados, hoy);
    cambiarErrores(validaciones);
    if (Object.keys(validaciones).length) return;
    const resultado = await operacion.ejecutar(async () => {
      try { return await guardarPaciente(normalizados, { id: paciente?.id, revision: paciente?.revision }); }
      catch (fallo) {
        if (integrado && !paciente && fallo.estado === 409 && fallo.errores?.dni) {
          const existentes = await buscarPacienteDni(datos.dni);
          if (existentes[0]) return existentes[0];
        }
        throw fallo;
      }
    });
    if (resultado) { cambiarMensaje('La ficha del paciente se guardó.'); guardado(resultado); }
  };
  return <section className={integrado ? 'registro-paciente-integrado' : 'panel'} aria-busy={ocupado}>
    <FormularioBase onGuardar={guardar} guardando={operacion.guardando} bloquearGuardado={identidad.consultando || ocupadoExterno}
      error={operacion.error} errores={errores} etiquetaGuardar={integrado ? 'Registrar paciente y continuar' : 'Guardar'}
      accionesSecundarias={integrado
        ? <button className="boton boton--secundario" type="button" disabled={ocupado} onClick={cancelar}>Volver a buscar</button>
        : <><Link className="boton boton--secundario" to="/pacientes">Volver a pacientes</Link>
          {recargar && <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={recargar}>Recargar ficha</button>}</>}>
      <div className="seccion-formulario"><div className="seccion-formulario__titulo"><div>
        <h2>{integrado ? 'Registrar este paciente' : 'Datos del paciente'}</h2>
        <p>{integrado ? 'Completa el nacimiento para continuar con la cita.' : 'Consulta el DNI para completar los nombres. El nacimiento se registra manualmente.'}</p>
      </div></div>
        <div className="rejilla-formulario">
          <div className="consulta-dni-fila campo--doble">
            <CampoFormulario etiqueta="DNI" requerido error={errores.dni}><input name="dni" inputMode="numeric" pattern="[0-9]{8}" maxLength={8} required
              value={datos.dni} readOnly={integrado} onChange={(evento) => cambiar('dni', evento.target.value)} /></CampoFormulario>
            {!paciente && <button className="boton boton--secundario" type="button" disabled={ocupado || !/^[0-9]{8}$/.test(datos.dni)}
              onClick={() => consultar(datos.dni)}>{identidad.consultando ? 'Consultando…' : 'Consultar DNI'}</button>}
          </div>
          {identidad.aviso && <p className="consulta-dni-aviso campo--doble" role="status">{identidad.aviso}</p>}
          <CampoFormulario etiqueta="Nombres" requerido error={errores.nombres}><input name="nombres" autoComplete="off" maxLength={80} required
            value={datos.nombres} disabled={identidad.consultando} onChange={(evento) => cambiar('nombres', evento.target.value)} /></CampoFormulario>
          <CampoFormulario etiqueta="Apellidos" requerido error={errores.apellidos}><input name="apellidos" autoComplete="off" maxLength={100} required
            value={datos.apellidos} disabled={identidad.consultando} onChange={(evento) => cambiar('apellidos', evento.target.value)} /></CampoFormulario>
          <CampoFormulario etiqueta="Fecha de nacimiento" requerido error={errores.fechaNacimiento}><input name="fechaNacimiento" type="date" min="0001-01-01" max={hoy} required
            value={datos.fechaNacimiento} onChange={(evento) => cambiar('fechaNacimiento', evento.target.value)} /></CampoFormulario>
          {!integrado && <CampoFormulario etiqueta="Estado" error={errores.activo} ayuda="La desactivación conserva la ficha y sus citas.">
            <select name="activo" value={String(datos.activo)} onChange={(evento) => cambiar('activo', evento.target.value === 'true')}>
              <option value="true">Activo</option><option value="false">Inactivo</option></select></CampoFormulario>}
          {integrado && <button type="button" className="boton boton--fantasma campo--doble" aria-expanded={contacto} onClick={() => cambiarContacto(!contacto)}>
            {contacto ? 'Ocultar contacto opcional' : 'Agregar contacto opcional'}</button>}
          {contacto && <>
            <CampoFormulario etiqueta="Correo" error={errores.correo}><input name="correo" type="email" maxLength={254} value={datos.correo}
              onChange={(evento) => cambiar('correo', evento.target.value)} /></CampoFormulario>
            <CampoFormulario etiqueta="Teléfono" error={errores.telefono}><input name="telefono" type="tel" maxLength={20} value={datos.telefono}
              onChange={(evento) => cambiar('telefono', evento.target.value)} /></CampoFormulario>
          </>}
        </div>
      </div>
    </FormularioBase>
    {mensaje && <p className="atencion-aviso" role="status">{mensaje}</p>}
  </section>;
}
