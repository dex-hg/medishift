import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { crearPlan, crearUnidad, listarPlanes, listarUnidades, periodoValido } from '../services/planesMensuales';
import { fechaActual } from '../utils/validacionesProgramacion';

export default function usePlanesMensuales() {
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const [unidades, setUnidades] = useState([]);
  const [planes, setPlanes] = useState([]);
  const [idUnidad, setIdUnidad] = useState('');
  const [periodo, setPeriodo] = useState('');
  const [zonaHoraria, setZonaHoraria] = useState('');
  const [cargando, setCargando] = useState(true);
  const [consultando, setConsultando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [restringido, setRestringido] = useState(false);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorPlanes, setErrorPlanes] = useState('');
  const [errorOperacion, setErrorOperacion] = useState('');
  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');
  const [intento, setIntento] = useState(0);
  const [intentoPlanes, setIntentoPlanes] = useState(0);
  const bloqueo = useRef(false);
  const vigente = useRef(true);
  const controladorGuardado = useRef(null);
  const periodoIniciado = useRef(false);
  const destino = `${ubicacion.pathname}${ubicacion.search}`;

  const manejarAcceso = useCallback((fallo) => {
    if (fallo.estado === 401) {
      navegar('/iniciar-sesion', { replace: true, state: { destino } });
      return true;
    }
    if (fallo.estado === 403) {
      setRestringido(true);
      setUnidades([]);
      setPlanes([]);
      setMensaje('');
      return true;
    }
    return false;
  }, [navegar, destino]);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controladorGuardado.current?.abort(); };
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    setCargando(true);
    setErrorCarga('');
    listarUnidades({ senal: controlador.signal }).then(({ unidades: registros, zonaHoraria: zona }) => {
      if (controlador.signal.aborted) return;
      setUnidades(registros);
      setZonaHoraria(zona);
      if (!periodoIniciado.current) {
        setPeriodo(fechaActual(zona).slice(0, 7));
        periodoIniciado.current = true;
      }
      setRestringido(false);
      setIdUnidad((anterior) => registros.some((unidad) => unidad.id === anterior) ? anterior
        : registros.find((unidad) => unidad.estado === 'Activo')?.id || registros[0]?.id || '');
    }).catch((fallo) => {
      if (!controlador.signal.aborted && !manejarAcceso(fallo)) setErrorCarga(fallo.message);
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false);
    });
    return () => controlador.abort();
  }, [intento, manejarAcceso]);

  useEffect(() => {
    const controlador = new AbortController();
    setPlanes([]);
    setErrorPlanes('');
    if (cargando || errorCarga || restringido || !idUnidad || !periodoValido(periodo)) {
      setConsultando(false);
      return () => controlador.abort();
    }
    setConsultando(true);
    listarPlanes({ idUnidad, periodo }, { senal: controlador.signal }).then((registros) => {
      if (!controlador.signal.aborted) setPlanes(registros);
    }).catch((fallo) => {
      if (!controlador.signal.aborted && !manejarAcceso(fallo)) setErrorPlanes(fallo.message);
    }).finally(() => {
      if (!controlador.signal.aborted) setConsultando(false);
    });
    return () => controlador.abort();
  }, [idUnidad, periodo, intentoPlanes, cargando, errorCarga, restringido, manejarAcceso]);

  const limpiarOperacion = () => { setErrorOperacion(''); setErrores({}); setMensaje(''); };
  const cambiarUnidad = (valor) => {
    if (bloqueo.current) return;
    limpiarOperacion();
    setIdUnidad(valor);
  };
  const cambiarPeriodo = (valor) => {
    if (bloqueo.current) return;
    limpiarOperacion();
    periodoIniciado.current = true;
    setPeriodo(valor);
  };
  const ejecutar = async (operacion, alGuardar, mensajeExito) => {
    if (bloqueo.current || cargando || errorCarga || restringido) return false;
    bloqueo.current = true;
    const controlador = new AbortController();
    controladorGuardado.current = controlador;
    setGuardando(true);
    limpiarOperacion();
    try {
      const registro = await operacion({ senal: controlador.signal });
      if (!vigente.current) return false;
      alGuardar(registro);
      setMensaje(mensajeExito);
      return true;
    } catch (fallo) {
      if (vigente.current && !manejarAcceso(fallo)) {
        setErrorOperacion(fallo.message);
        setErrores(fallo.errores || {});
        if (fallo.estado === 409) setIntentoPlanes((valor) => valor + 1);
      }
      return false;
    } finally {
      bloqueo.current = false;
      if (vigente.current) setGuardando(false);
    }
  };

  const unidad = unidades.find((registro) => registro.id === idUnidad);
  const hayBorrador = planes.some((plan) => ['Borrador', 'Pendiente', 'Aprobado'].includes(plan.estado));
  const puedeCrear = unidad?.estado === 'Activo' && periodoValido(periodo)
    && !cargando && !consultando && !guardando && !errorCarga && !errorPlanes && !restringido && !hayBorrador;
  const guardarPlan = () => {
    if (!puedeCrear) return Promise.resolve(false);
    return ejecutar((opciones) => crearPlan({ idUnidad, periodo }, opciones),
      () => setIntentoPlanes((valor) => valor + 1), 'Plan mensual creado en borrador.');
  };
  const guardarUnidad = (datos) => ejecutar((opciones) => crearUnidad(datos, opciones), (registro) => {
    setUnidades((anteriores) => [...anteriores, registro]);
    setIdUnidad(registro.id);
  }, 'Unidad registrada correctamente.');
  const recargar = () => {
    if (bloqueo.current) return;
    limpiarOperacion();
    setIntento((valor) => valor + 1);
  };

  return { unidades, planes, unidad, idUnidad, periodo, zonaHoraria, cargando, consultando, guardando,
    restringido, errorCarga, errorPlanes, errorOperacion, errores, mensaje, puedeCrear, hayBorrador,
    cambiarUnidad, cambiarPeriodo, guardarPlan, guardarUnidad, recargar };
}
