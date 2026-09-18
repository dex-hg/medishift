import { ArrowLeft, BriefcaseMedical, ContactRound } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioPreliminar from '../../components/FormularioPreliminar';
import { profesionales } from '../../data/datosDemostracion';
import NoEncontrado from '../NoEncontrado';

function FormularioProfesional({ modo }) {
  const { id } = useParams();
  const profesional = modo === 'editar' ? profesionales.find((item) => item.id === id) : null;

  if (modo === 'editar' && !profesional) {
    return <NoEncontrado />;
  }

  const titulo = modo === 'editar' ? 'Editar profesional' : 'Nuevo profesional';

  return (
    <>
      <EncabezadoPagina
        ruta={[
          { etiqueta: 'Profesionales', destino: '/profesionales' },
          { etiqueta: modo === 'editar' ? profesional.id : 'Nuevo' },
        ]}
        titulo={titulo}
        descripcion="Completa los datos mínimos para identificar al profesional y asignarle turnos."
      />

      <FormularioPreliminar
        accionesSecundarias={
          <Link className="boton boton--fantasma" to="/profesionales">
            <ArrowLeft size={17} /> Cancelar
          </Link>
        }
      >
        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <ContactRound size={19} />
            <div>
              <h2>Datos personales</h2>
              <p>Información de contacto para la gestión interna.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Nombres" requerido>
              <input
                name="nombres"
                type="text"
                defaultValue={profesional?.nombres ?? ''}
                minLength="2"
                maxLength="60"
                autoComplete="given-name"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Apellidos" requerido>
              <input
                name="apellidos"
                type="text"
                defaultValue={profesional?.apellidos ?? ''}
                minLength="2"
                maxLength="80"
                autoComplete="family-name"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Correo institucional" requerido>
              <input
                name="correo"
                type="email"
                defaultValue={profesional?.correo ?? ''}
                maxLength="120"
                autoComplete="email"
                placeholder="nombre@institucion.pe"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Teléfono" ayuda="Solo se usará como dato operativo.">
              <input
                name="telefono"
                type="tel"
                defaultValue={profesional?.telefono ?? ''}
                pattern="[0-9 ]{9,12}"
                maxLength="12"
                autoComplete="tel"
                placeholder="999 999 999"
              />
            </CampoFormulario>
          </div>
        </section>

        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <BriefcaseMedical size={19} />
            <div>
              <h2>Datos laborales</h2>
              <p>Campos que relacionan al profesional con las reglas de programación.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Colegiatura" ayuda="Formato esperado: CMP 123456" requerido>
              <input
                name="colegiatura"
                type="text"
                defaultValue={profesional?.colegiatura ?? ''}
                pattern="CMP [0-9]{6}"
                placeholder="CMP 123456"
                maxLength="10"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Especialidad" requerido>
              <select name="especialidad" defaultValue={profesional?.especialidad ?? ''} required>
                <option value="" disabled>Selecciona una especialidad</option>
                <option>Cardiología</option>
                <option>Pediatría</option>
                <option>Neurología</option>
                <option>Traumatología</option>
                <option>Medicina general</option>
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Categoría profesional" requerido>
              <select name="categoria" defaultValue="Médico cirujano" required>
                <option>Médico cirujano</option>
                <option>Enfermería</option>
                <option>Tecnología médica</option>
                <option>Otra categoría</option>
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Estado" requerido>
              <select name="estado" defaultValue={profesional?.estado ?? 'Activo'} required>
                <option>Activo</option>
                <option>Inactivo</option>
              </select>
            </CampoFormulario>
          </div>
        </section>
      </FormularioPreliminar>
    </>
  );
}

export default FormularioProfesional;
