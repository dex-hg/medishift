import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { consultarIdentidadDni } from '../services/atencion';

/** Cancela una consulta anterior para que su resultado no complete un DNI diferente. */
export default function useIdentidadDni() {
  const navegar = useNavigate();
  const controlador = useRef(null);
  const vigente = useRef(true);
  const [consultando, cambiarConsultando] = useState(false);
  const [aviso, cambiarAviso] = useState('');
  useEffect(() => { vigente.current = true; return () => { vigente.current = false; controlador.current?.abort(); }; }, []);
  const cancelar = () => { controlador.current?.abort(); controlador.current = null; cambiarConsultando(false); cambiarAviso(''); };
  const consultar = async (dni) => {
    controlador.current?.abort();
    const solicitud = new AbortController();
    controlador.current = solicitud;
    cambiarConsultando(true); cambiarAviso('');
    try {
      const resultado = await consultarIdentidadDni(dni, { senal: solicitud.signal });
      if (!vigente.current || solicitud.signal.aborted || controlador.current !== solicitud) return null;
      cambiarAviso('Nombres y apellidos obtenidos. Revisa los datos y completa el nacimiento.');
      return resultado;
    } catch (fallo) {
      if (vigente.current && !solicitud.signal.aborted && controlador.current === solicitud) {
        if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
        cambiarAviso(fallo.message);
      }
      return null;
    } finally {
      if (vigente.current && controlador.current === solicitud) cambiarConsultando(false);
    }
  };
  return { consultando, aviso, consultar, cancelar };
}
