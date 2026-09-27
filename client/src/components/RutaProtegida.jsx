import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import EstructuraAplicacion from './EstructuraAplicacion';
import { obtenerSesion } from '../services/sesion';

function RutaProtegida() {
  const ubicacion = useLocation();
  const ruta = ubicacion.pathname;
  const [intento, setIntento] = useState(0);
  const [acceso, setAcceso] = useState({ estado: 'verificando', sesion: null, ruta: null });

  useEffect(() => {
    let vigente = true;
    setAcceso({ estado: 'verificando', sesion: null, ruta });

    obtenerSesion()
      .then((sesion) => {
        if (vigente) setAcceso({ estado: 'autenticado', sesion, ruta });
      })
      .catch((error) => {
        if (!vigente) return;
        setAcceso({ estado: error?.estado === 401 ? 'sin-sesion' : 'error', sesion: null, ruta });
      });

    return () => { vigente = false; };
  }, [ruta, intento]);

  const estado = acceso.ruta === ruta ? acceso.estado : 'verificando';

  if (estado === 'sin-sesion') {
    const destino = `${ubicacion.pathname}${ubicacion.search}${ubicacion.hash}`;
    return <Navigate to="/iniciar-sesion" replace state={{ destino }} />;
  }

  if (estado === 'error') {
    return (
      <main className="estado-acceso" role="alert">
        <h1>No se pudo comprobar tu sesión</h1>
        <p>Comprueba la conexión con el servidor e inténtalo nuevamente.</p>
        <button className="boton boton--primario" type="button" onClick={() => setIntento((valor) => valor + 1)}>
          Reintentar
        </button>
      </main>
    );
  }

  if (estado !== 'autenticado') {
    return <main className="estado-acceso" role="status">Comprobando tu sesión…</main>;
  }

  return <EstructuraAplicacion sesion={acceso.sesion} />;
}

export default RutaProtegida;
