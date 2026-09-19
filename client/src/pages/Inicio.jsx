import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Building2,
  CalendarCheck2,
  CalendarClock,
  CalendarPlus,
  Clock3,
  Plus,
  UsersRound,
} from 'lucide-react';
import EncabezadoPagina from '../components/EncabezadoPagina';
import { consultorios, disponibilidades, profesionales, turnos } from '../data/datosDemostracion';

const accesos = [
  {
    titulo: 'Profesionales',
    descripcion: 'Datos laborales, especialidad y estado del personal médico.',
    cantidad: profesionales.length,
    etiqueta: 'registros',
    destino: '/profesionales',
    icono: UsersRound,
    color: 'turquesa',
  },
  {
    titulo: 'Consultorios',
    descripcion: 'Ambientes disponibles para las asignaciones de atención.',
    cantidad: consultorios.length,
    etiqueta: 'ambientes',
    destino: '/consultorios',
    icono: Building2,
    color: 'azul',
  },
  {
    titulo: 'Disponibilidades',
    descripcion: 'Franjas declaradas antes de preparar cada horario.',
    cantidad: disponibilidades.length,
    etiqueta: 'franjas',
    destino: '/disponibilidades',
    icono: Clock3,
    color: 'ambar',
  },
];

function Inicio() {
  const turnosAprobados = turnos.filter((turno) => turno.estado === 'Aprobado').length;
  const consultoriosDisponibles = consultorios.filter(
    (consultorio) => consultorio.estado === 'Disponible',
  ).length;

  return (
    <>
      <EncabezadoPagina
        titulo="Panel operativo"
        descripcion="Resumen de profesionales, consultorios y horarios de atención."
        acciones={
          <Link className="boton boton--primario" to="/horarios/nuevo">
            <CalendarPlus size={18} />
            Nuevo turno
          </Link>
        }
      />

      <section className="resumen" aria-label="Resumen del periodo">
        <article className="tarjeta-metrica">
          <span className="tarjeta-metrica__icono tarjeta-metrica__icono--turquesa">
            <UsersRound size={21} />
          </span>
          <div>
            <span>Profesionales activos</span>
            <strong>{profesionales.filter((item) => item.estado === 'Activo').length}</strong>
            <small>de {profesionales.length} registrados</small>
          </div>
        </article>
        <article className="tarjeta-metrica">
          <span className="tarjeta-metrica__icono tarjeta-metrica__icono--azul">
            <Building2 size={21} />
          </span>
          <div>
            <span>Consultorios disponibles</span>
            <strong>{consultoriosDisponibles}</strong>
            <small>para nuevas asignaciones</small>
          </div>
        </article>
        <article className="tarjeta-metrica">
          <span className="tarjeta-metrica__icono tarjeta-metrica__icono--verde">
            <CalendarCheck2 size={21} />
          </span>
          <div>
            <span>Horarios aprobados</span>
            <strong>{turnosAprobados}</strong>
            <small>semana del 31 de agosto</small>
          </div>
        </article>
      </section>

      <div className="rejilla-inicio">
        <section className="panel panel--principal">
          <div className="panel__encabezado">
            <div>
              <p className="sobrelinea">Datos maestros</p>
              <h2>Preparar la programación</h2>
            </div>
          </div>

          <div className="accesos-crud">
            {accesos.map(({ titulo, descripcion, cantidad, etiqueta, destino, icono: Icono, color }) => (
              <Link className="acceso-crud" to={destino} key={titulo}>
                <span className={`acceso-crud__icono acceso-crud__icono--${color}`}>
                  <Icono size={20} />
                </span>
                <span className="acceso-crud__contenido">
                  <strong>{titulo}</strong>
                  <small>{descripcion}</small>
                </span>
                <span className="acceso-crud__cantidad">
                  <strong>{cantidad}</strong>
                  <small>{etiqueta}</small>
                </span>
                <ArrowRight className="acceso-crud__flecha" size={18} />
              </Link>
            ))}
          </div>
        </section>

        <aside className="panel panel--agenda">
          <p className="sobrelinea">Agenda semanal</p>
          <h2>Próximos turnos</h2>
          <div className="lista-turnos">
            {turnos.slice(0, 3).map((turno) => {
              const primerBloque = Object.entries(turno.bloques)[0];
              return (
                <div className="turno-resumen" key={turno.id}>
                  <span><CalendarClock size={17} /></span>
                  <div>
                    <strong>{turno.profesional}</strong>
                    <small>{primerBloque[0]} · {primerBloque[1][0]} · {turno.consultorio}</small>
                  </div>
                </div>
              );
            })}
          </div>
          <Link className="boton boton--secundario boton--ancho" to="/horarios">
            Ver horario semanal
            <ArrowRight size={17} />
          </Link>
        </aside>
      </div>

      <section className="panel panel--acciones">
        <div>
          <p className="sobrelinea">Acciones rápidas</p>
          <h2>Crear un registro</h2>
        </div>
        <div className="acciones-rapidas">
          <Link to="/profesionales/nuevo"><Plus size={16} /> Profesional</Link>
          <Link to="/consultorios/nuevo"><Plus size={16} /> Consultorio</Link>
          <Link to="/disponibilidades/nueva"><Plus size={16} /> Disponibilidad</Link>
        </div>
      </section>
    </>
  );
}

export default Inicio;
