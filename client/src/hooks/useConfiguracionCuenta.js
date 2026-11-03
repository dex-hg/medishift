import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { obtenerConfiguracion, obtenerCuenta } from '../services/configuracion';

export default function useConfiguracionCuenta() {
  const navegar = useNavigate();
  const [cuenta, actualizarCuenta] = useState(null);
  const [configuracion, actualizarConfiguracion] = useState(null);
  const [error, cambiarError] = useState('');
  const [cargando, cambiarCargando] = useState(true);
  const [intento, cambiarIntento] = useState(0);
  const recargarConfiguracion = useCallback(() => cambiarIntento((valor) => valor + 1), []);

  useEffect(() => {
    const controlador = new AbortController();
    cambiarCargando(true);
    cambiarError('');
    Promise.all([obtenerCuenta({ senal: controlador.signal }), obtenerConfiguracion({ senal: controlador.signal })])
      .then(([perfil, institucion]) => {
        if (controlador.signal.aborted) return;
        actualizarCuenta(perfil);
        actualizarConfiguracion(institucion);
      }).catch((fallo) => {
        if (controlador.signal.aborted) return;
        if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
        else cambiarError(fallo.message);
      }).finally(() => { if (!controlador.signal.aborted) cambiarCargando(false); });
    return () => controlador.abort();
  }, [intento, navegar]);

  return { cuenta, configuracion, actualizarCuenta, actualizarConfiguracion, recargarConfiguracion, cargando, error };
}
