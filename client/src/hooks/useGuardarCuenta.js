import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function useGuardarCuenta(actualizar) {
  const navegar = useNavigate();
  const [guardando, cambiarGuardando] = useState(false);
  const [errores, cambiarErrores] = useState({});
  const [error, cambiarError] = useState('');
  const [mensaje, cambiarMensaje] = useState('');
  const [desactualizado, cambiarDesactualizado] = useState(false);
  const bloqueo = useRef(false);
  const controlador = useRef(null);
  const vigente = useRef(true);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controlador.current?.abort(); };
  }, []);

  const guardar = async (operacion) => {
    if (bloqueo.current || desactualizado) return false;
    bloqueo.current = true;
    controlador.current = new AbortController();
    cambiarGuardando(true);
    cambiarError(''); cambiarErrores({}); cambiarMensaje('');
    try {
      const resultado = await operacion(controlador.current.signal);
      if (!vigente.current) return false;
      actualizar(resultado);
      cambiarMensaje('Cambios guardados correctamente.');
      return true;
    } catch (fallo) {
      if (!vigente.current) return false;
      if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
      else {
        cambiarError(fallo.message);
        cambiarErrores(fallo.errores || {});
        cambiarDesactualizado(Boolean(fallo.errores?.revision) || fallo.estado === 428 || !fallo.estado);
      }
      return false;
    } finally {
      bloqueo.current = false;
      if (vigente.current) cambiarGuardando(false);
    }
  };
  return { guardar, guardando, errores, error, mensaje, desactualizado };
}
