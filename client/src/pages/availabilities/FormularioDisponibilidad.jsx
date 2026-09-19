import { ArrowLeft, CalendarRange, Clock3 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioBase from '../../components/FormularioBase';
import { disponibilidades, profesionales } from '../../data/datosDemostracion';
import NoEncontrado from '../NoEncontrado';

function FormularioDisponibilidad({ modo }) {
  const { id } = useParams();
  const disponibilidad = modo === 'editar'
    ? disponibilidades.find((item) => item.id === id)
    : null;

  if (modo === 'editar' && !disponibilidad) {
    return <NoEncontrado />;
  }

  return (
    <>
      <EncabezadoPagina
        ruta={[
          { etiqueta: 'Disponibilidades', destino: '/disponibilidades' },
          { etiqueta: modo === 'editar' ? disponibilidad.id : 'Nueva' },
        ]}
        titulo={modo === 'editar' ? 'Editar disponibilidad' : 'Nueva disponibilidad'}
        descripcion="Define cuándo puede recibir turnos un profesional durante un periodo."
      />

      <FormularioBase
        accionesSecundarias={
          <Link className="boton boton--fantasma" to="/disponibilidades">
            <ArrowLeft size={17} /> Cancelar
          </Link>
        }
      >
        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <CalendarRange size={19} />
            <div>
              <h2>Profesional y vigencia</h2>
              <p>La disponibilidad se registra por profesional y por día de la semana.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Profesional" ancho="doble" requerido>
              <select
                name="profesionalId"
                defaultValue={disponibilidad?.profesionalId ?? ''}
                required
              >
                <option value="" disabled>Selecciona un profesional</option>
                {profesionales.filter((item) => item.estado === 'Activo').map((profesional) => (
                  <option value={profesional.id} key={profesional.id}>
                    {profesional.nombreCompleto} · {profesional.especialidad}
                  </option>
                ))}
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Inicio de vigencia" requerido>
              <input name="fechaInicio" type="date" defaultValue="2026-09-01" required />
            </CampoFormulario>
            <CampoFormulario etiqueta="Fin de vigencia" requerido>
              <input name="fechaFin" type="date" defaultValue="2026-09-30" required />
            </CampoFormulario>
          </div>
        </section>

        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <Clock3 size={19} />
            <div>
              <h2>Franja disponible</h2>
              <p>La hora final debe ser posterior a la hora inicial.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Día" requerido>
              <select name="dia" defaultValue={disponibilidad?.dia ?? ''} required>
                <option value="" disabled>Selecciona un día</option>
                <option>Lunes</option>
                <option>Martes</option>
                <option>Miércoles</option>
                <option>Jueves</option>
                <option>Viernes</option>
                <option>Sábado</option>
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Estado" requerido>
              <select name="estado" defaultValue={disponibilidad?.estado ?? 'Activa'} required>
                <option>Activa</option>
                <option>Inactiva</option>
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Hora de inicio" requerido>
              <input
                name="horaInicio"
                type="time"
                defaultValue={disponibilidad?.inicio ?? '08:00'}
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Hora de fin" requerido>
              <input
                name="horaFin"
                type="time"
                defaultValue={disponibilidad?.fin ?? '14:00'}
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Observación" ancho="doble">
              <textarea
                name="observacion"
                rows="3"
                maxLength="240"
                placeholder="Condición o excepción que deba revisar el administrador"
              />
            </CampoFormulario>
          </div>
        </section>
      </FormularioBase>
    </>
  );
}

export default FormularioDisponibilidad;
