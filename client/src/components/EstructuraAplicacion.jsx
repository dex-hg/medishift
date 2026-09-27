import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Building2,
  CalendarDays,
  ChevronLeft,
  Clock3,
  LayoutDashboard,
  LogOut,
  Menu,
  Sparkles,
  Stethoscope,
  UsersRound,
  X,
} from 'lucide-react';
import { cerrarSesion, obtenerSesion } from '../services/sesion';

const gruposNavegacion = [
  {
    titulo: 'Principal',
    enlaces: [
      { destino: '/panel', etiqueta: 'Inicio', icono: LayoutDashboard, fin: true },
      { destino: '/horarios', etiqueta: 'Horarios', icono: CalendarDays },
    ],
  },
  {
    titulo: 'Datos operativos',
    enlaces: [
      { destino: '/profesionales', etiqueta: 'Profesionales', icono: UsersRound },
      { destino: '/consultorios', etiqueta: 'Consultorios', icono: Building2 },
      { destino: '/disponibilidades', etiqueta: 'Disponibilidades', icono: Clock3 },
    ],
  },
];

function EstructuraAplicacion({ sesion }) {
  const navegar = useNavigate();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [barraCompacta, setBarraCompacta] = useState(false);
  const [cerrando, setCerrando] = useState(false);
  const [errorSalida, setErrorSalida] = useState('');

  const cerrarMenu = () => setMenuAbierto(false);
  const cerrarAcceso = async () => {
    if (cerrando) return;
    setCerrando(true);
    setErrorSalida('');
    try {
      await cerrarSesion();
      navegar('/iniciar-sesion', { replace: true });
    } catch {
      try {
        await obtenerSesion();
        setErrorSalida('No se pudo cerrar la sesión. Inténtalo nuevamente.');
      } catch (error) {
        if (error?.estado === 401) {
          navegar('/iniciar-sesion', { replace: true });
          return;
        }
        setErrorSalida('No se pudo confirmar el cierre de sesión. Comprueba la conexión e inténtalo nuevamente.');
      }
      setCerrando(false);
    }
  };

  const iniciales = sesion.correo.slice(0, 2).toUpperCase();

  return (
    <div className={`aplicacion ${barraCompacta ? 'aplicacion--compacta' : ''}`}>
      <aside id="navegacion-operativa" className={`barra-lateral ${menuAbierto ? 'barra-lateral--abierta' : ''}`}>
        <div className="marca">
          <span className="marca__simbolo" aria-hidden="true">
            <Sparkles size={20} strokeWidth={2.4} />
          </span>
          <span className="marca__texto">
            <strong>MediShift</strong>
            <small>Gestión operativa</small>
          </span>
          <button
            className="boton-icono marca__cerrar"
            type="button"
            aria-label="Cerrar menú"
            onClick={cerrarMenu}
          >
            <X size={20} />
          </button>
        </div>

        <nav className="navegacion" aria-label="Navegación principal">
          {gruposNavegacion.map((grupo) => (
            <div className="navegacion__grupo" key={grupo.titulo}>
              <p className="navegacion__titulo">{grupo.titulo}</p>
              {grupo.enlaces.map(({ destino, etiqueta, icono: Icono, fin }) => (
                <NavLink
                  key={destino}
                  to={destino}
                  end={fin}
                  aria-label={etiqueta}
                  className={({ isActive }) =>
                    `navegacion__enlace ${isActive ? 'navegacion__enlace--activo' : ''}`
                  }
                  onClick={cerrarMenu}
                >
                  <Icono size={19} />
                  <span>{etiqueta}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="barra-lateral__pie">
          {errorSalida && <p className="barra-lateral__error" role="alert">{errorSalida}</p>}
          <button className="boton-compactar boton-salir" type="button" onClick={cerrarAcceso}
            disabled={cerrando} aria-label="Cerrar sesión">
            <LogOut size={17} aria-hidden="true" />
            <span>{cerrando ? 'Cerrando sesión…' : 'Cerrar sesión'}</span>
          </button>
          <button
            className="boton-compactar"
            type="button"
            onClick={() => setBarraCompacta((valor) => !valor)}
            aria-label={barraCompacta ? 'Expandir barra lateral' : 'Compactar barra lateral'}
          >
            <ChevronLeft size={17} />
            <span>Compactar</span>
          </button>
        </div>
      </aside>

      {menuAbierto && (
        <button
          className="fondo-menu"
          type="button"
          aria-label="Cerrar menú"
          onClick={cerrarMenu}
        />
      )}

      <div className="area-principal">
        <header className="barra-superior">
          <button
            className="boton-icono barra-superior__menu"
            type="button"
            aria-label="Abrir menú"
            aria-controls="navegacion-operativa"
            aria-expanded={menuAbierto}
            onClick={() => setMenuAbierto(true)}
          >
            <Menu size={21} />
          </button>
          <div className="barra-superior__contexto">
            <Stethoscope size={18} />
            <span>{sesion.nombreInstitucion}</span>
          </div>
          <div className="perfil">
            <span className="perfil__datos">
              <strong>{sesion.correo}</strong>
              <small>{sesion.codigoInstitucion}</small>
            </span>
            <span className="perfil__avatar" aria-hidden="true">{iniciales}</span>
          </div>
        </header>

        <main className="contenido-principal">
          <p className="aviso-demostracion">
            Los profesionales, consultorios y horarios de este panel son datos de demostración.
          </p>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default EstructuraAplicacion;
