import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { obtenerEspecialidades } from '../services/catalogos';

function useErroresCatalogo() {
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const destino = `${ubicacion.pathname}${ubicacion.search}`;
  return useCallback((error) => {
    if (error?.estado !== 401) return false;
    navegar('/iniciar-sesion', { replace: true, state: { destino } });
    return true;
  }, [navegar, destino]);
}

export function useListadoCatalogo(obtener, eliminar) {
  const ubicacion = useLocation();
  const manejarSesion = useErroresCatalogo();
  const [registros, setRegistros] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState(ubicacion.state?.mensaje || '');
  const [seleccionado, setSeleccionado] = useState(null);
  const [eliminando, setEliminando] = useState(false);
  const [intento, setIntento] = useState(0);
  const bloqueo = useRef(false);
  const controladorEliminacion = useRef(null);
  const vigente = useRef(true);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controladorEliminacion.current?.abort(); };
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    setCargando(true);
    setError('');
    obtener({ senal: controlador.signal }).then((datos) => {
      if (!controlador.signal.aborted) setRegistros(datos);
    }).catch((fallo) => {
      if (!controlador.signal.aborted && !manejarSesion(fallo)) setError(fallo.message);
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false);
    });
    return () => controlador.abort();
  }, [obtener, manejarSesion, intento]);

  const confirmarEliminacion = async () => {
    if (bloqueo.current || !seleccionado) return;
    bloqueo.current = true;
    const controlador = new AbortController();
    controladorEliminacion.current = controlador;
    setEliminando(true);
    setError('');
    setMensaje('');
    try {
      await eliminar(seleccionado.id, { senal: controlador.signal });
      if (!vigente.current) return;
      setRegistros((anteriores) => anteriores.filter((registro) => registro.id !== seleccionado.id));
      setSeleccionado(null);
      setMensaje('El registro se eliminó correctamente.');
    } catch (fallo) {
      if (vigente.current && !manejarSesion(fallo)) setError(fallo.message);
    } finally {
      bloqueo.current = false;
      if (vigente.current) setEliminando(false);
    }
  };

  return {
    registros, cargando, error, mensaje, seleccionado, eliminando,
    seleccionar: (registro) => { if (!bloqueo.current) { setSeleccionado(registro); setError(''); setMensaje(''); } },
    cancelarEliminacion: () => { if (!bloqueo.current) setSeleccionado(null); },
    confirmarEliminacion,
    reintentar: () => { if (!bloqueo.current) { setSeleccionado(null); setIntento((valor) => valor + 1); } },
  };
}

export function useFormularioCatalogo({ modo, id, iniciales, obtener, crear, actualizar, destino, etiqueta }) {
  const navegar = useNavigate();
  const manejarSesion = useErroresCatalogo();
  const [datos, setDatos] = useState(iniciales);
  const [cargando, setCargando] = useState(modo === 'editar');
  const [errorCarga, setErrorCarga] = useState('');
  const [noEncontrado, setNoEncontrado] = useState(false);
  const [error, setError] = useState('');
  const [errores, setErrores] = useState({});
  const [guardando, setGuardando] = useState(false);
  const [especialidades, setEspecialidades] = useState([]);
  const [errorEspecialidades, setErrorEspecialidades] = useState('');
  const [intento, setIntento] = useState(0);
  const [intentoEspecialidades, setIntentoEspecialidades] = useState(0);
  const bloqueo = useRef(false);
  const controladorGuardado = useRef(null);
  const vigente = useRef(true);

  useEffect(() => {
    vigente.current = true;
    return () => { vigente.current = false; controladorGuardado.current?.abort(); };
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    setDatos(iniciales);
    setErrorCarga('');
    setNoEncontrado(false);
    setErrores({});
    setError('');
    if (modo !== 'editar') { setCargando(false); return () => controlador.abort(); }
    setCargando(true);
    obtener(id, { senal: controlador.signal }).then((registro) => {
      if (!controlador.signal.aborted) setDatos(registro);
    }).catch((fallo) => {
      if (controlador.signal.aborted || manejarSesion(fallo)) return;
      setNoEncontrado(fallo.estado === 404);
      setErrorCarga(fallo.message);
    }).finally(() => {
      if (!controlador.signal.aborted) setCargando(false);
    });
    return () => controlador.abort();
  }, [modo, id, iniciales, obtener, intento, manejarSesion]);

  useEffect(() => {
    const controlador = new AbortController();
    setErrorEspecialidades('');
    obtenerEspecialidades({ senal: controlador.signal }).then((registros) => {
      if (!controlador.signal.aborted) setEspecialidades(registros);
    }).catch((fallo) => {
      if (!controlador.signal.aborted && !manejarSesion(fallo)) {
        setErrorEspecialidades('No se pudieron cargar las sugerencias. Puedes escribir la especialidad o reintentar.');
      }
    });
    return () => controlador.abort();
  }, [intentoEspecialidades, manejarSesion]);

  const actualizarCampo = (evento) => {
    if (bloqueo.current) return;
    const { name: nombre, value: valor } = evento.target;
    setDatos((anteriores) => ({ ...anteriores, [nombre]: valor }));
    setError('');
    setErrores((anteriores) => ({ ...anteriores, [nombre]: undefined }));
  };

  const guardar = async () => {
    if (bloqueo.current) return;
    bloqueo.current = true;
    const controlador = new AbortController();
    controladorGuardado.current = controlador;
    setGuardando(true);
    setError('');
    setErrores({});
    let confirmado = false;
    try {
      if (modo === 'editar') await actualizar(id, datos, { senal: controlador.signal });
      else await crear(datos, { senal: controlador.signal });
      confirmado = true;
      if (vigente.current) navegar(destino, { replace: true, state: {
        mensaje: `${etiqueta} ${modo === 'editar' ? 'actualizado' : 'registrado'} correctamente.`,
      } });
    } catch (fallo) {
      if (vigente.current && !manejarSesion(fallo)) {
        setErrores(fallo.errores || {});
        setError(fallo.message || 'No se pudo guardar el registro.');
      }
    } finally {
      bloqueo.current = confirmado;
      if (vigente.current) setGuardando(false);
    }
  };

  return {
    datos, cargando, errorCarga, noEncontrado, error, errores, guardando, especialidades,
    errorEspecialidades, actualizarCampo, guardar,
    reintentar: () => setIntento((valor) => valor + 1),
    reintentarEspecialidades: () => setIntentoEspecialidades((valor) => valor + 1),
    evitarSalida: (evento) => { if (bloqueo.current) evento.preventDefault(); },
  };
}
