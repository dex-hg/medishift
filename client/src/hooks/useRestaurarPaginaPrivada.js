import { useEffect } from 'react';

export default function useRestaurarPaginaPrivada() {
  useEffect(() => {
    const raiz = document.getElementById('root');

    const suspender = (evento) => {
      if (evento.persisted && raiz) raiz.hidden = true;
    };
    const restaurar = (evento) => {
      if (evento.persisted) window.location.reload();
    };

    // Evita que el historial restaure la pantalla con datos de una sesión anterior.
    window.addEventListener('pagehide', suspender);
    window.addEventListener('pageshow', restaurar);
    return () => {
      window.removeEventListener('pagehide', suspender);
      window.removeEventListener('pageshow', restaurar);
      if (raiz) raiz.hidden = false;
    };
  }, []);
}
