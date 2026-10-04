import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ErrorProgramacion } from '../services/programacion';

function useSesionProgramacion() {
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const destino = `${ubicacion.pathname}${ubicacion.search}`;
  return useCallback((error) => {
    if (error?.estado !== 401) return false;
    navegar('/iniciar-sesion', { replace: true, state: { destino } });
    return true;
  }, [navegar, destino]);
}

export function useConsultaProgramacion(cargar) {
  const manejarSesion = useSesionProgramacion();
  const ubicacion = useLocation();
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState(ubicacion.state?.mensaje || '');
  const [conflicto, setConflicto] = useState(false);
  const [intento, setIntento] = useState(0);
  const bloqueo = useRef(false);
  const controladorMutacion = useRef(null);
  const vigente = useRef(true);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controladorMutacion.current?.abort(); };
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    setCargando(true);
    setError('');
    setConflicto(false);
    cargar({ senal: controlador.signal }).then((respuesta) => {
      if (!controlador.signal.aborted) setDatos(respuesta);
    }).catch((fallo) => {
      if (!controlador.signal.aborted && !manejarSesion(fallo)) setError(fallo.message);
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false);
    });
    return () => controlador.abort();
  }, [cargar, intento, manejarSesion]);

  const ejecutar = async (operacion, mensajeExito) => {
    if (bloqueo.current || cargando || error) return false;
    bloqueo.current = true;
    const controlador = new AbortController();
    controladorMutacion.current = controlador;
    setOcupado(true);
    setError('');
    setMensaje('');
    try {
      await operacion({ senal: controlador.signal });
      if (!vigente.current) return false;
      setMensaje(mensajeExito);
      setIntento((valor) => valor + 1);
      return true;
    } catch (fallo) {
      if (vigente.current && !manejarSesion(fallo)) {
        setError(fallo.message);
        setConflicto(fallo.estado === 409 || fallo.estado === 428);
      }
      return false;
    } finally {
      bloqueo.current = false;
      if (vigente.current) setOcupado(false);
    }
  };

  return { datos, cargando, ocupado, error, mensaje, conflicto, ejecutar,
    recargar: () => { if (!bloqueo.current) setIntento((valor) => valor + 1); } };
}

export function useFormularioProgramacion({ modo, id, iniciales, obtener, recursos: cargarRecursos,
  crear, actualizar, validarAntes, ajustarIniciales, destino, etiqueta }) {
  const navegar = useNavigate();
  const manejarSesion = useSesionProgramacion();
  const [datos, setDatos] = useState(iniciales);
  const [registro, setRegistro] = useState(null);
  const [recursos, setRecursos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [error, setError] = useState('');
  const [errores, setErrores] = useState({});
  const [guardando, setGuardando] = useState(false);
  const [conflicto, setConflicto] = useState(false);
  const [puedeRecargar, setPuedeRecargar] = useState(false);
  const [avisoRecarga, setAvisoRecarga] = useState('');
  const [intento, setIntento] = useState(0);
  const conservarEntradas = useRef(false);
  const objetivo = useRef(`${modo}:${id || ''}`);
  const bloqueo = useRef(false);
  const controladorGuardado = useRef(null);
  const vigente = useRef(true);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controladorGuardado.current?.abort(); };
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    const objetivoActual = `${modo}:${id || ''}`;
    if (objetivo.current !== objetivoActual) {
      conservarEntradas.current = false;
      objetivo.current = objetivoActual;
      setAvisoRecarga('');
    }
    setCargando(true);
    setErrorCarga('');
    Promise.all([cargarRecursos({ senal: controlador.signal }),
      modo === 'editar' ? obtener(id, { senal: controlador.signal }) : Promise.resolve(null),
    ]).then(([opciones, existente]) => {
      if (controlador.signal.aborted) return;
      setRecursos(opciones);
      setRegistro(existente);
      if (!conservarEntradas.current) setDatos(existente || (ajustarIniciales ? ajustarIniciales(iniciales, opciones) : iniciales));
      else setAvisoRecarga('Se recargó la revisión vigente. Tus entradas se conservaron; revísalas antes de guardar.');
      setConflicto(false);
      setPuedeRecargar(false);
      setError('');
      setErrores({});
    }).catch((fallo) => {
      if (!controlador.signal.aborted && !manejarSesion(fallo)) setErrorCarga(fallo.message);
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false);
    });
    return () => controlador.abort();
  }, [modo, id, iniciales, obtener, cargarRecursos, ajustarIniciales, intento, manejarSesion]);

  const actualizarCampo = (evento) => {
    if (bloqueo.current) return;
    const { name: campo, value: valor } = evento.target;
    setDatos((anteriores) => ({ ...anteriores, [campo]: valor }));
    setErrores((anteriores) => ({ ...anteriores, [campo]: undefined }));
    setAvisoRecarga('');
  };

  const guardar = async () => {
    if (bloqueo.current || cargando || conflicto) return;
    bloqueo.current = true;
    const controlador = new AbortController();
    controladorGuardado.current = controlador;
    setGuardando(true);
    setError('');
    setErrores({});
    setPuedeRecargar(false);
    try {
      const erroresValidacion = validarAntes ? await validarAntes(datos, { recursos, registro, senal: controlador.signal }) : {};
      if (Object.keys(erroresValidacion).length) throw new ErrorProgramacion('Revisa los campos del formulario.', { errores: erroresValidacion });
      const opciones = { senal: controlador.signal, revision: registro?.revision };
      const respuesta = modo === 'editar' ? await actualizar(id, datos, opciones) : await crear(datos, opciones);
      const resultado = modo === 'editar' ? 'actualizado' : 'registrado';
      const participio = etiqueta === 'Disponibilidad' ? `${resultado.slice(0, -1)}a` : resultado;
      if (vigente.current) navegar(typeof destino === 'function' ? destino(respuesta) : destino, { replace: true,
        state: { mensaje: `${etiqueta} ${participio} correctamente.` } });
    } catch (fallo) {
      if (vigente.current && !manejarSesion(fallo)) {
        setError(fallo.message);
        setErrores(fallo.errores || {});
        setConflicto(fallo.estado === 428 || (fallo.estado === 409 && Boolean(fallo.errores?.revision)));
        setPuedeRecargar(fallo.estado === 409 || fallo.estado === 428);
      }
    } finally {
      bloqueo.current = false;
      if (vigente.current) setGuardando(false);
    }
  };

  return { datos, registro, recursos, cargando, errorCarga, error, errores, guardando, conflicto, puedeRecargar, avisoRecarga,
    actualizarCampo, setDatos, guardar,
    recargar: () => {
      if (!bloqueo.current) {
        conservarEntradas.current = Boolean(recursos) && (modo !== 'editar' || Boolean(registro));
        setIntento((valor) => valor + 1);
      }
    },
    evitarSalida: (evento) => { if (bloqueo.current) evento.preventDefault(); } };
}
