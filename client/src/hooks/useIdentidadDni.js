import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { consultarIdentidadDni } from '../services/atencion';

/** Cancela una consulta anterior para que su resultado no complete un DNI diferente. */
export default function useIdentidadDni() {
  const navegar = useNavigate();
  const activa = useRef(null);
  const temporizador = useRef(null);
  const vigente = useRef(true);
  const [consultando, cambiarConsultando] = useState(false);
  const [aviso, cambiarAviso] = useState('');
  const [espera, cambiarEspera] = useState(0);

  useEffect(() => {
    vigente.current = true;
    return () => {
      vigente.current = false;
      activa.current?.controlador.abort();
      activa.current = null;
      clearTimeout(temporizador.current);
      temporizador.current = null;
    };
  }, []);

  const cancelar = () => {
    activa.current?.controlador.abort();
    activa.current = null;
    cambiarConsultando(false);
    cambiarAviso('');
  };

  const consultar = async (dni) => {
    if (temporizador.current !== null || activa.current?.dni === dni) return null;
    activa.current?.controlador.abort();
    const controlador = new AbortController();
    const solicitud = { dni, controlador };
    activa.current = solicitud;
    cambiarConsultando(true);
    cambiarAviso('');
    try {
      const resultado = await consultarIdentidadDni(dni, { senal: controlador.signal });
      if (!vigente.current || controlador.signal.aborted || activa.current !== solicitud) return null;
      cambiarAviso('Nombres y apellidos obtenidos. Revisa los datos y completa el nacimiento.');
      return resultado;
    } catch (fallo) {
      if (vigente.current && !controlador.signal.aborted && activa.current === solicitud) {
        if (fallo.estado === 401) navegar('/iniciar-sesion', { replace: true });
        if (fallo.reintentarEn > 0) {
          cambiarEspera(fallo.reintentarEn);
          temporizador.current = setTimeout(() => {
            temporizador.current = null;
            cambiarEspera(0);
          }, fallo.reintentarEn * 1000);
        } else {
          cambiarAviso(fallo.message);
        }
      }
      return null;
    } finally {
      if (vigente.current && activa.current === solicitud) {
        activa.current = null;
        cambiarConsultando(false);
      }
    }
  };

  return {
    consultando, consultar, cancelar, enPausa: espera > 0,
    aviso: espera > 0 ? `Espera ${espera} s antes de volver a consultar el DNI. Puedes completar los nombres manualmente.` : aviso,
  };
}
