import { useMemo, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Pencil, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { diasSemana, turnos } from '../../data/datosDemostracion';

function Horarios() {
  const [especialidad, setEspecialidad] = useState('Todas');
  const especialidades = ['Todas', ...new Set(turnos.map((turno) => turno.especialidad))];

  const turnosFiltrados = useMemo(
    () => turnos.filter((turno) => especialidad === 'Todas' || turno.especialidad === especialidad),
    [especialidad],
  );

  return (
    <>
      <EncabezadoPagina
        ruta={[{ etiqueta: 'Horarios' }]}
        titulo="Horarios de atención"
        descripcion="Vista semanal de las asignaciones manuales del periodo seleccionado."
        acciones={
          <Link className="boton boton--primario" to="/horarios/nuevo">
            <Plus size={18} />
            Nuevo turno
          </Link>
        }
      />

      <section className="panel panel--horario">
        <div className="controles-horario">
          <div className="selector-especialidad" role="group" aria-label="Filtrar por especialidad">
            {especialidades.map((opcion) => (
              <button
                type="button"
                key={opcion}
                className={especialidad === opcion ? 'activo' : ''}
                onClick={() => setEspecialidad(opcion)}
              >
                {opcion}
              </button>
            ))}
          </div>
          <div className="periodo-horario">
            <button className="boton-icono" type="button" disabled aria-label="Semana anterior">
              <ChevronLeft size={17} />
            </button>
            <span>
              <small>Semana</small>
              31 ago - 5 sep 2026
            </span>
            <button className="boton-icono" type="button" disabled aria-label="Semana siguiente">
              <ChevronRight size={17} />
            </button>
          </div>
        </div>

        {turnosFiltrados.length > 0 ? (
          <div className="horario-semanal">
            <div className="horario-semanal__cabecera">
              <span>Profesional</span>
              {diasSemana.map((dia) => <span key={dia.clave}>{dia.etiqueta}</span>)}
              <span className="solo-lectores">Acciones</span>
            </div>
            {turnosFiltrados.map((turno) => (
              <article className="fila-horario" key={turno.id}>
                <div className="fila-horario__profesional">
                  <span className="avatar avatar--turquesa">
                    {turno.profesional.split(' ').slice(-2).map((parte) => parte[0]).join('')}
                  </span>
                  <span>
                    <strong>{turno.profesional}</strong>
                    <small>{turno.especialidad} · {turno.consultorio}</small>
                    <Estado valor={turno.estado} />
                  </span>
                </div>
                {diasSemana.map((dia) => {
                  const bloque = turno.bloques[dia.clave];
                  return (
                    <div className={`bloque-turno ${bloque ? 'bloque-turno--asignado' : ''}`} key={dia.clave}>
                      <span className="bloque-turno__dia">{dia.etiqueta}</span>
                      {bloque ? (
                        <>
                          <strong>{bloque[0]}</strong>
                          <small>hasta {bloque[1]}</small>
                        </>
                      ) : (
                        <span aria-label="Sin turno">—</span>
                      )}
                    </div>
                  );
                })}
                <Link
                  className="boton-icono fila-horario__editar"
                  to={`/horarios/${turno.id}/editar`}
                  aria-label={`Editar horario de ${turno.profesional}`}
                >
                  <Pencil size={16} />
                </Link>
              </article>
            ))}
          </div>
        ) : (
          <div className="sin-resultados">
            <CalendarDays size={32} />
            <strong>No hay horarios para esta especialidad</strong>
            <p>Selecciona otra especialidad para continuar.</p>
          </div>
        )}
      </section>
    </>
  );
}

export default Horarios;
