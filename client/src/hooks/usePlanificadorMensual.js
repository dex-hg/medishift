import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { cargarPlanificador } from '../services/planificador';
import { fechaActual } from '../utils/validacionesProgramacion';
import { periodoValido } from '../services/planesMensuales';

export default function usePlanificadorMensual(destino = '/horarios', periodoInicial) {
  const navegar = useNavigate();
  const [periodo, setPeriodo] = useState(() => periodoValido(periodoInicial) ? periodoInicial : fechaActual().slice(0, 7));
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [errorCarga, setErrorCarga] = useState('');
  const [error, setError] = useState('');
  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');
  const [intento, setIntento] = useState(0);
  const bloqueo = useRef(false);
  const vigente = useRef(true);
  const controladorOperacion = useRef(null);
  const secuencia = useRef(0);
  const manejarAcceso = useCallback((fallo) => {
    if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true, state: { destino } });
  }, [navegar, destino]);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controladorOperacion.current?.abort(); };
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    const solicitud = ++secuencia.current;
    setCargando(true);
    setErrorCarga('');
    cargarPlanificador(periodo, { senal: controlador.signal }).then((resultado) => {
      if (!controlador.signal.aborted && secuencia.current === solicitud) setDatos(resultado);
    }).catch((fallo) => {
      if (!controlador.signal.aborted && secuencia.current === solicitud) {
        manejarAcceso(fallo);
        setErrorCarga(fallo.message);
      }
    }).finally(() => { if (!controlador.signal.aborted && secuencia.current === solicitud) setCargando(false); });
    return () => controlador.abort();
  }, [periodo, intento, manejarAcceso]);

  const limpiarError = () => { setError(''); setErrores({}); };
  const recargar = () => { if (!bloqueo.current) setIntento((valor) => valor + 1); };
  const cambiarPeriodo = (valor) => {
    if (bloqueo.current || valor === periodo || !periodoValido(valor)) return;
    setDatos(null);
    limpiarError();
    setMensaje('');
    setPeriodo(valor);
  };
  const ejecutar = async (operacion, texto) => {
    if (bloqueo.current || cargando || errorCarga) return false;
    bloqueo.current = true;
    const controlador = new AbortController();
    controladorOperacion.current = controlador;
    setGuardando(true);
    limpiarError();
    setMensaje('');
    try {
      await operacion({ senal: controlador.signal });
      if (!vigente.current) return false;
      setMensaje(texto);
      setIntento((valor) => valor + 1);
      return true;
    } catch (fallo) {
      if (vigente.current) {
        manejarAcceso(fallo);
        setError(fallo.message);
        setErrores(fallo.errores || {});
        if (fallo.estado === 409) setIntento((valor) => valor + 1);
      }
      return false;
    } finally {
      bloqueo.current = false;
      if (vigente.current) setGuardando(false);
    }
  };
  return { periodo, datos, cargando, guardando, errorCarga, error, errores, mensaje,
    cambiarPeriodo, recargar, ejecutar, limpiarError };
}
