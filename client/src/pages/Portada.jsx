import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Building2,
  CalendarCheck2,
  CheckCircle2,
  Clock3,
  Sparkles,
  UsersRound,
} from 'lucide-react';

const beneficios = [
  {
    icono: CalendarCheck2,
    titulo: 'Horarios organizados',
    descripcion: 'Consulta la programación semanal por profesional, especialidad y consultorio.',
  },
  {
    icono: Building2,
    titulo: 'Recursos centralizados',
    descripcion: 'Mantén profesionales, ambientes y disponibilidades en un mismo espacio.',
  },
  {
    icono: Clock3,
    titulo: 'Jornadas visibles',
    descripcion: 'Revisa las franjas asignadas y la distribución del trabajo durante la semana.',
  },
];

const pasos = [
  'Registra profesionales y consultorios.',
  'Define disponibilidades por día y franja.',
  'Construye y revisa el horario semanal.',
];

function Marca() {
  return (
    <Link className="portada-marca" to="/" aria-label="MediShift, página principal">
      <span className="portada-marca__simbolo" aria-hidden="true">
        <Sparkles size={20} strokeWidth={2.4} />
      </span>
      <span>
        <strong>MediShift</strong>
        <small>Gestión operativa</small>
      </span>
    </Link>
  );
}

function Portada() {
  return (
    <div className="portada">
      <header className="portada__encabezado">
        <Marca />
        <nav className="portada__navegacion" aria-label="Navegación de la página principal">
          <a href="#caracteristicas">Características</a>
          <a href="#funcionamiento">Cómo funciona</a>
        </nav>
        <div className="portada__acceso">
          <button className="boton-registro" type="button">Registrarse</button>
          <Link className="boton-acceso" to="/panel">Iniciar sesión</Link>
        </div>
      </header>

      <main>
        <section className="portada-hero">
          <div className="portada-hero__contenido">
            <span className="portada-hero__etiqueta">
              <CalendarCheck2 size={15} />
              Gestión de horarios médicos
            </span>
            <h1>Organiza turnos médicos con una visión clara de cada recurso</h1>
            <p>
              Centraliza profesionales, consultorios y disponibilidades para preparar horarios
              semanales ordenados y fáciles de consultar.
            </p>
            <div className="portada-hero__acciones">
              <Link className="boton-acceso boton-acceso--grande" to="/panel">
                Iniciar sesión
                <ArrowRight size={18} />
              </Link>
              <button className="boton-registro boton-registro--grande" type="button">
                Crear una cuenta
              </button>
            </div>
            <div className="portada-hero__ventajas" aria-label="Ventajas principales">
              <span><CheckCircle2 size={15} /> Programación semanal</span>
              <span><CheckCircle2 size={15} /> Control de consultorios</span>
              <span><CheckCircle2 size={15} /> Disponibilidad médica</span>
            </div>
          </div>

          <div className="muestra-horario" aria-label="Ejemplo de horario semanal">
            <div className="muestra-horario__encabezado">
              <div>
                <span>Semana actual</span>
                <strong>Horarios de atención</strong>
              </div>
              <span className="muestra-horario__estado">Organizado</span>
            </div>
            <div className="muestra-horario__dias">
              <span>Profesional</span><span>Lun</span><span>Mar</span><span>Mié</span><span>Jue</span>
            </div>
            <div className="muestra-horario__fila">
              <div className="muestra-profesional">
                <span>EV</span>
                <div><strong>Elena Vargas</strong><small>Cardiología</small></div>
              </div>
              <span className="muestra-turno">08:00<small>13:00</small></span>
              <span className="muestra-turno">14:00<small>18:00</small></span>
              <span className="muestra-turno">08:00<small>13:00</small></span>
              <span className="muestra-turno">14:00<small>18:00</small></span>
            </div>
            <div className="muestra-horario__fila">
              <div className="muestra-profesional">
                <span>SM</span>
                <div><strong>Sofía Mendoza</strong><small>Pediatría</small></div>
              </div>
              <span className="muestra-turno">08:00<small>14:00</small></span>
              <span className="muestra-turno">08:00<small>14:00</small></span>
              <span className="muestra-turno muestra-turno--libre">Libre</span>
              <span className="muestra-turno">08:00<small>14:00</small></span>
            </div>
            <div className="muestra-horario__pie">
              <UsersRound size={17} />
              <span>Profesionales y consultorios en una sola vista</span>
            </div>
          </div>
        </section>

        <section className="portada-caracteristicas" id="caracteristicas">
          <div className="portada-seccion__titulo">
            <span>Organización operativa</span>
            <h2>La información necesaria para preparar cada horario</h2>
          </div>
          <div className="portada-caracteristicas__rejilla">
            {beneficios.map(({ icono: Icono, titulo, descripcion }) => (
              <article key={titulo}>
                <span><Icono size={22} /></span>
                <h3>{titulo}</h3>
                <p>{descripcion}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="portada-flujo" id="funcionamiento">
          <div className="portada-flujo__texto">
            <span>Flujo de trabajo</span>
            <h2>De los datos básicos al horario semanal</h2>
            <p>La plataforma reúne cada paso de la programación en una secuencia sencilla.</p>
          </div>
          <ol>
            {pasos.map((paso, indice) => (
              <li key={paso}><span>{indice + 1}</span><p>{paso}</p></li>
            ))}
          </ol>
        </section>
      </main>

      <footer className="portada__pie">
        <Marca />
        <p>Gestión de profesionales, consultorios y horarios de atención.</p>
      </footer>
    </div>
  );
}

export default Portada;

