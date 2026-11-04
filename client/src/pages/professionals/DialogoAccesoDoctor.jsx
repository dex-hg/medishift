import { useEffect, useRef, useState } from 'react';
import VentanaPlanificador from '../schedules/VentanaPlanificador';
import { gestionarAccesoDoctor } from '../../services/doctores';

export default function DialogoAccesoDoctor({ profesional, cerrar }) {
  const [acceso, cambiarAcceso] = useState(null);
  const [correo, cambiarCorreo] = useState('');
  const [contrasena, cambiarContrasena] = useState('');
  const [error, cambiarError] = useState('');
  const [mensaje, cambiarMensaje] = useState('');
  const [ocupado, cambiarOcupado] = useState(true);
  const [intento, cambiarIntento] = useState(0);
  const bloqueo = useRef(false);
  const vigente = useRef(true);
  const operacion = useRef(null);

  useEffect(() => {
    vigente.current = true;
    const controlador = new AbortController();
    cambiarOcupado(true);
    cambiarError('');
    gestionarAccesoDoctor(profesional.id, { senal: controlador.signal }).then((resultado) => {
      if (!controlador.signal.aborted) { cambiarAcceso(resultado); cambiarCorreo(resultado.correo); }
    }).catch((fallo) => { if (!controlador.signal.aborted) cambiarError(fallo.message); })
      .finally(() => { if (!controlador.signal.aborted) cambiarOcupado(false); });
    return () => { vigente.current = false; controlador.abort(); operacion.current?.abort(); };
  }, [profesional.id, intento]);

  const guardar = async (evento, habilitado) => {
    evento.preventDefault();
    if (!acceso || bloqueo.current) return;
    bloqueo.current = true;
    cambiarOcupado(true); cambiarError(''); cambiarMensaje('');
    const controlador = new AbortController();
    operacion.current = controlador;
    try {
      const resultado = await gestionarAccesoDoctor(profesional.id, { revision: acceso.revision, senal: controlador.signal,
        datos: { correo, contrasena: acceso.idCuenta ? '' : contrasena, habilitado } });
      if (vigente.current) {
        cambiarAcceso(resultado); cambiarContrasena('');
        cambiarMensaje(habilitado ? 'Acceso de doctor habilitado. Puede iniciar sesión con el código institucional y su correo.' : 'Rol de doctor revocado. Sus turnos se conservan.');
      }
    } catch (fallo) {
      if (vigente.current) cambiarError(fallo.message + (fallo.errores?.contrasena ? ' ' + fallo.errores.contrasena : '')
        + (fallo.errores?.correo ? ' ' + fallo.errores.correo : ''));
    } finally {
      bloqueo.current = false;
      if (vigente.current) cambiarOcupado(false);
    }
  };

  return <VentanaPlanificador titulo="Acceso del doctor" descripcion={profesional.nombres + ' ' + profesional.apellidos}
    ocupado={ocupado} cerrar={cerrar}>
    <form className="formulario-planificador" onSubmit={(evento) => guardar(evento, true)}>
      <p>Esta cuenta permite consultar los turnos propios. Para crearlos o cambiarlos, habilita también la autoprogramación médica en Configuraciones.</p>
      {error && <div className="planificador-error" role="alert"><p>{error}</p><button type="button" className="boton boton--fantasma"
        disabled={ocupado} onClick={() => cambiarIntento((valor) => valor + 1)}>Actualizar acceso</button></div>}
      {mensaje && <p className="planificador-exito" role="status">{mensaje}</p>}
      {ocupado && <p role="status">Consultando o guardando el acceso…</p>}
      {acceso && <fieldset disabled={ocupado} className="formulario-planificador__campos">
        <label className="campo-planificador"><span>Correo de acceso</span><input required type="email" maxLength={254}
          value={correo} readOnly={Boolean(acceso.idCuenta)} onChange={(evento) => cambiarCorreo(evento.target.value)} autoComplete="off" /></label>
        {!acceso.idCuenta && <label className="campo-planificador"><span>Contraseña inicial</span><input required type="password" minLength={12}
          maxLength={128} value={contrasena} onChange={(evento) => cambiarContrasena(evento.target.value)} autoComplete="new-password" />
          <span className="planificador-ayuda">Entre 12 y 128 caracteres. El doctor puede cambiarla en Mi perfil.</span></label>}
        {acceso.idCuenta && <p>Estado: <strong>{acceso.habilitado ? 'Habilitado' : 'Sin acceso de doctor'}</strong>.</p>}
      </fieldset>}
      <div className="ventana-planificador__acciones">
        {acceso?.habilitado && <button type="button" className="boton boton--peligro" disabled={ocupado} onClick={(evento) => guardar(evento, false)}>Revocar rol de doctor</button>}
        <button type="button" className="boton boton--fantasma" disabled={ocupado} onClick={cerrar}>Cerrar</button>
        {acceso && !acceso.habilitado && <button type="submit" className="boton boton--primario" disabled={ocupado}>Habilitar acceso</button>}
      </div>
    </form>
  </VentanaPlanificador>;
}
