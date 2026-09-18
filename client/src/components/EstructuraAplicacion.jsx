import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  Building2,
  CalendarDays,
  ChevronLeft,
  Clock3,
  LayoutDashboard,
  Menu,
  Sparkles,
  Stethoscope,
  UsersRound,
  X,
} from 'lucide-react';

const gruposNavegacion = [
  {
    titulo: 'Principal',
    enlaces: [
      { destino: '/', etiqueta: 'Inicio', icono: LayoutDashboard, fin: true },
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

function EstructuraAplicacion() {
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [barraCompacta, setBarraCompacta] = useState(false);

  const cerrarMenu = () => setMenuAbierto(false);

  return (
    <div className={`aplicacion ${barraCompacta ? 'aplicacion--compacta' : ''}`}>
      <aside className={`barra-lateral ${menuAbierto ? 'barra-lateral--abierta' : ''}`}>
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
          <div className="etiqueta-prototipo">
            <span className="etiqueta-prototipo__punto" />
            <span>Vista preliminar</span>
          </div>
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
            onClick={() => setMenuAbierto(true)}
          >
            <Menu size={21} />
          </button>
          <div className="barra-superior__contexto">
            <Stethoscope size={18} />
            <span>Clínica demostrativa</span>
          </div>
          <div className="perfil">
            <span className="perfil__datos">
              <strong>Administración</strong>
              <small>Datos de demostración</small>
            </span>
            <span className="perfil__avatar" aria-hidden="true">AD</span>
          </div>
        </header>

        <main className="contenido-principal">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default EstructuraAplicacion;

