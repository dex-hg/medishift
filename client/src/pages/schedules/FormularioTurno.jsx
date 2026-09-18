import { ArrowLeft, CalendarPlus, Info, Link2 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioPreliminar from '../../components/FormularioPreliminar';
import { consultorios, profesionales, turnos } from '../../data/datosDemostracion';
import NoEncontrado from '../NoEncontrado';

function FormularioTurno({ modo }) {
  const { id } = useParams();
  const turno = modo === 'editar' ? turnos.find((item) => item.id === id) : null;

  if (modo === 'editar' && !turno) {
    return <NoEncontrado />;
  }

  const profesionalSeleccionado = turno?.profesionalId ?? '';
  const consultorioSeleccionado = consultorios.find(
    (consultorio) => consultorio.codigo === turno?.consultorio,
  )?.id ?? '';

  return (
    <>
      <EncabezadoPagina
        ruta={[
          { etiqueta: 'Horarios', destino: '/horarios' },
          { etiqueta: modo === 'editar' ? turno.id : 'Nuevo turno' },
        ]}
        titulo={modo === 'editar' ? 'Editar turno' : 'Nuevo turno'}
        descripcion="Asocia un profesional, un consultorio y un intervalo de atención."
      />

      <FormularioPreliminar
        accionesSecundarias={
          <Link className="boton boton--fantasma" to="/horarios">
            <ArrowLeft size={17} /> Cancelar
          </Link>
        }
      >
        <div className="aviso aviso--restriccion">
          <Info size={18} aria-hidden="true" />
          <p>
            La futura API deberá comprobar disponibilidad, jornada y superposición antes de guardar.
            Esta vista solo valida que el intervalo tenga un orden correcto.
          </p>
        </div>

        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <Link2 size={19} />
            <div>
              <h2>Recursos de la asignación</h2>
              <p>Selecciona los dos recursos que quedarán reservados durante el intervalo.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Profesional" requerido>
              <select name="profesionalId" defaultValue={profesionalSeleccionado} required>
                <option value="" disabled>Selecciona un profesional</option>
                {profesionales.filter((item) => item.estado === 'Activo').map((profesional) => (
                  <option value={profesional.id} key={profesional.id}>
                    {profesional.nombreCompleto} · {profesional.especialidad}
                  </option>
                ))}
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Consultorio" requerido>
              <select name="consultorioId" defaultValue={consultorioSeleccionado} required>
                <option value="" disabled>Selecciona un consultorio</option>
                {consultorios.filter((item) => item.estado !== 'Mantenimiento').map((consultorio) => (
                  <option value={consultorio.id} key={consultorio.id}>
                    {consultorio.codigo} · {consultorio.nombre}
                  </option>
                ))}
              </select>
            </CampoFormulario>
          </div>
        </section>

        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <CalendarPlus size={19} />
            <div>
              <h2>Intervalo y estado</h2>
              <p>El intervalo usa límite inicial incluido y límite final excluido.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Inicio" requerido>
              <input
                name="fechaInicio"
                type="datetime-local"
                defaultValue={modo === 'editar' ? '2026-09-01T08:00' : ''}
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Fin" requerido>
              <input
                name="fechaFin"
                type="datetime-local"
                defaultValue={modo === 'editar' ? '2026-09-01T13:00' : ''}
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Estado" requerido>
              <select name="estado" defaultValue={turno?.estado ?? 'Borrador'} required>
                <option>Borrador</option>
                <option>Pendiente</option>
                <option>Aprobado</option>
                <option>Cancelado</option>
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Observación">
              <input
                name="observacion"
                type="text"
                maxLength="180"
                placeholder="Motivo o indicación operativa"
              />
            </CampoFormulario>
          </div>
        </section>
      </FormularioPreliminar>
    </>
  );
}

export default FormularioTurno;
