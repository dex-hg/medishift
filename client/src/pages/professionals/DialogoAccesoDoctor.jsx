import { useEffect, useRef, useState } from 'react';
import VentanaPlanificador from '../schedules/VentanaPlanificador';
import { gestionarAccesoDoctor } from '../../services/doctores';
import { LIMITES_ACCESO } from '../../utils/validacionesCatalogos';

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
  const tieneCuenta = Boolean(acceso?.idCuenta);
  const puedeEditar = Boolean(acceso?.credencialesEditables);

  useEffect(() => {
    vigente.current = true;
    const controlador = new AbortController();
    cambiarOcupado(true);
    cambiarError('');
    gestionarAccesoDoctor(profesional.id, { senal: controlador.signal }).then((resultado) => {
      if (!controlador.signal.aborted) { cambiarAcceso(resultado); cambiarCorreo(resultado.correo); cambiarContrasena(''); }
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
    const soloRol = tieneCuenta && habilitado !== undefined;
    const datos = soloRol ? { correo: acceso.correo, contrasena: '', habilitado }
      : { correo, contrasena, ...(habilitado !== undefined ? { habilitado } : {}) };
    try {
      const resultado = await gestionarAccesoDoctor(profesional.id, { revision: acceso.revision, senal: controlador.signal, datos });
      if (vigente.current) {
        cambiarAcceso(resultado);
        if (!soloRol) { cambiarCorreo(resultado.correo); cambiarContrasena(''); }
        cambiarMensaje(habilitado === undefined ? 'Credenciales actualizadas.' : habilitado
          ? 'Acceso de doctor habilitado. Puede iniciar sesión con el código institucional y su correo.' : 'Rol de doctor revocado.');
      }
    } catch (fallo) {
      if (vigente.current) {
        const mensajes = new Set([fallo.message, fallo.errores?.contrasena, fallo.errores?.correo, fallo.errores?.habilitado].filter(Boolean));
        cambiarError(Array.from(mensajes).join(' '));
      }
    } finally {
      bloqueo.current = false;
      if (vigente.current) cambiarOcupado(false);
    }
  };

  return <VentanaPlanificador titulo="Acceso del doctor" descripcion={profesional.nombres + ' ' + profesional.apellidos}
    ocupado={ocupado} cerrar={cerrar}>
    <form className="formulario-planificador" onSubmit={(evento) => guardar(evento, tieneCuenta ? undefined : true)}>
      <p>Esta cuenta permite consultar los turnos propios. Para crearlos o cambiarlos, habilita también la autoprogramación médica en Configuraciones.</p>
      {error && <div className="planificador-error" role="alert"><p>{error}</p><button type="button" className="boton boton--fantasma"
        disabled={ocupado} onClick={() => cambiarIntento((valor) => valor + 1)}>Actualizar acceso</button></div>}
      {mensaje && <p className="planificador-exito" role="status">{mensaje}</p>}
      {ocupado && <p role="status">Consultando o guardando el acceso…</p>}
      {acceso && <fieldset disabled={ocupado} className="formulario-planificador__campos">
        <label className="campo-planificador"><span>Correo de acceso</span><input required type="email" maxLength={LIMITES_ACCESO.correo}
          value={correo} readOnly={!puedeEditar} onChange={(evento) => cambiarCorreo(evento.target.value)} autoComplete="off" /></label>
        {puedeEditar && <label className="campo-planificador"><span>{tieneCuenta ? 'Nueva contraseña' : 'Contraseña inicial'}</span>
          <input required={!tieneCuenta} type="password" minLength={LIMITES_ACCESO.contrasenaMinima}
            maxLength={LIMITES_ACCESO.contrasenaMaxima} value={contrasena} onChange={(evento) => cambiarContrasena(evento.target.value)} autoComplete="new-password" />
          <span className="planificador-ayuda">{tieneCuenta ? 'Opcional. Usa entre 12 y 128 caracteres para cambiarla.'
            : 'Entre 12 y 128 caracteres. El doctor puede cambiarla en Mi perfil.'}</span></label>}
        {tieneCuenta && <p>Estado de acceso <strong>{acceso.habilitado ? 'Habilitado' : 'Sin acceso de doctor'}</strong>.</p>}
        {!puedeEditar && <p className="planificador-ayuda">Esta cuenta tiene otros roles institucionales. El titular puede cambiar su contraseña en Mi perfil.</p>}
      </fieldset>}
      <div className="ventana-planificador__acciones">
        {acceso?.habilitado && <button type="button" className="boton boton--peligro" disabled={ocupado} onClick={(evento) => guardar(evento, false)}>Revocar rol de doctor</button>}
        <button type="button" className="boton boton--fantasma" disabled={ocupado} onClick={cerrar}>Cerrar</button>
        {acceso && !tieneCuenta && <button type="submit" className="boton boton--primario" disabled={ocupado}>Habilitar acceso</button>}
        {tieneCuenta && !acceso.habilitado && <button type="button" className="boton boton--secundario" disabled={ocupado}
          onClick={(evento) => guardar(evento, true)}>Habilitar acceso</button>}
        {tieneCuenta && puedeEditar && <button type="submit" className="boton boton--primario"
          disabled={ocupado || (correo.trim().toLowerCase() === acceso.correo.toLowerCase() && !contrasena)}>Guardar credenciales</button>}
      </div>
    </form>
  </VentanaPlanificador>;
}
