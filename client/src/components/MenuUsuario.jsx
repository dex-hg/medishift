import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChevronDown, LogOut, Settings, UserRound } from 'lucide-react';

export default function MenuUsuario({ sesion, cuenta, cerrando, cerrarSesion, error }) {
  const [abierto, cambiarAbierto] = useState(false);
  const contenedor = useRef(null);
  const disparador = useRef(null);
  const ubicacion = useLocation();
  const nombre = cuenta?.nombre || sesion.correo;
  const iniciales = (cuenta?.nombre ? cuenta.nombre.split(/\s+/u).slice(0, 2).map((parte) => parte[0]).join('')
    : sesion.correo.slice(0, 2)).toUpperCase();

  useEffect(() => cambiarAbierto(false), [ubicacion.pathname]);
  useEffect(() => {
    if (!abierto) return undefined;
    const cerrarFuera = (evento) => { if (!contenedor.current?.contains(evento.target)) cambiarAbierto(false); };
    const manejarTecla = (evento) => {
      if (evento.key === 'Escape') { cambiarAbierto(false); disparador.current?.focus(); }
    };
    document.addEventListener('pointerdown', cerrarFuera);
    document.addEventListener('keydown', manejarTecla);
    return () => {
      document.removeEventListener('pointerdown', cerrarFuera);
      document.removeEventListener('keydown', manejarTecla);
    };
  }, [abierto]);

  return <div className="menu-usuario" ref={contenedor} onBlur={(evento) => {
    if (!evento.currentTarget.contains(evento.relatedTarget)) cambiarAbierto(false);
  }}>
    <button ref={disparador} className="perfil perfil--boton" type="button" aria-label="Menú de usuario"
      aria-expanded={abierto} aria-controls="opciones-usuario" onClick={() => cambiarAbierto((valor) => !valor)}>
      <span className="perfil__datos"><strong>{nombre}</strong><small>{sesion.codigoInstitucion}</small></span>
      <span className="perfil__avatar" aria-hidden="true">{iniciales}</span><ChevronDown size={15} aria-hidden="true" />
    </button>
    {abierto && <nav id="opciones-usuario" className="menu-usuario__opciones" aria-label="Opciones de usuario">
      <div className="menu-usuario__cabecera"><strong>{nombre}</strong><small>{sesion.correo}</small>
        <span>{(cuenta?.esAdministrador ?? sesion.esAdministrador) ? 'Administrador' : 'Usuario'}</span></div>
      <Link to="/mi-perfil" onClick={() => cambiarAbierto(false)}><UserRound size={17} />Mi perfil</Link>
      <Link to="/configuraciones" onClick={() => cambiarAbierto(false)}><Settings size={17} />Configuraciones</Link>
      <button className="menu-usuario__salir" type="button" disabled={cerrando} onClick={cerrarSesion}>
        <LogOut size={17} />{cerrando ? 'Cerrando sesión…' : 'Cerrar sesión'}</button>
      {error && <p className="menu-usuario__error" role="alert">{error}</p>}
    </nav>}
  </div>;
}
