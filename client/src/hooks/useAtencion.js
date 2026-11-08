import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

export function useConsultaAtencion(cargar) {
  const navegar = useNavigate();
  const [datos, cambiarDatos] = useState(null);
  const [cargando, cambiarCargando] = useState(true);
  const [error, cambiarError] = useState('');
  const [intento, cambiarIntento] = useState(0);
  useEffect(() => {
    const controlador = new AbortController();
    cambiarCargando(true); cambiarError(''); cambiarDatos(null);
    Promise.resolve().then(() => cargar({ senal: controlador.signal })).then((resultado) => {
      if (!controlador.signal.aborted) cambiarDatos(resultado);
    }).catch((fallo) => {
      if (controlador.signal.aborted) return;
      if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
      cambiarError(fallo.message);
    }).finally(() => { if (!controlador.signal.aborted) cambiarCargando(false); });
    return () => controlador.abort();
  }, [cargar, intento, navegar]);
  return { datos, cargando, error, recargar: useCallback(() => cambiarIntento((valor) => valor + 1), []) };
}

export function useOperacionAtencion() {
  const navegar = useNavigate();
  const bloqueo = useRef(false);
  const vigente = useRef(true);
  const [guardando, cambiarGuardando] = useState(false);
  const [error, cambiarError] = useState('');
  const [errores, cambiarErrores] = useState({});
  useEffect(() => { vigente.current = true; return () => { vigente.current = false; }; }, []);
  const limpiar = () => { cambiarError(''); cambiarErrores({}); };
  const ejecutar = async (operacion) => {
    if (bloqueo.current) return null;
    bloqueo.current = true; cambiarGuardando(true); limpiar();
    try {
      const resultado = await operacion();
      return vigente.current ? resultado : null;
    } catch (fallo) {
      if (vigente.current) {
        if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
        cambiarError(fallo.message); cambiarErrores(fallo.errores || {});
      }
      return null;
    } finally { bloqueo.current = false; if (vigente.current) cambiarGuardando(false); }
  };
  return { guardando, error, errores, ejecutar, limpiar };
}
