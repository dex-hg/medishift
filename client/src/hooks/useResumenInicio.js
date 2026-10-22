import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { obtenerResumenInicio } from '../services/resumenInicio.js';

function crearResumenInicial(estado = 'cargando') {
  return { estado, profesionales: [], consultorios: [], semana: null };
}

export function useResumenInicio() {
  const navegar = useNavigate();
  const [intento, cambiarIntento] = useState(0);
  const [resumen, actualizarResumen] = useState(crearResumenInicial);

  useEffect(() => {
    const controlador = new AbortController();
    let vigente = true;
    actualizarResumen(crearResumenInicial());

    obtenerResumenInicio({ senal: controlador.signal }).then((datos) => {
      if (vigente) actualizarResumen({ estado: 'listo', ...datos });
    }).catch((error) => {
      if (!vigente) return;
      if (error.estado === 401) {
        navegar('/iniciar-sesion', { replace: true, state: { destino: '/panel' } });
        return;
      }
      actualizarResumen({ ...crearResumenInicial('error'), mensaje: error.message });
    });

    return () => { vigente = false; controlador.abort(); };
  }, [intento, navegar]);

  return { resumen, reintentar: () => cambiarIntento((valor) => valor + 1) };
}
