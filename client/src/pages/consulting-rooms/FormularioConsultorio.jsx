import { ArrowLeft, Building2, Settings2 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioPreliminar from '../../components/FormularioPreliminar';
import { consultorios } from '../../data/datosDemostracion';
import NoEncontrado from '../NoEncontrado';

function FormularioConsultorio({ modo }) {
  const { id } = useParams();
  const consultorio = modo === 'editar' ? consultorios.find((item) => item.id === id) : null;

  if (modo === 'editar' && !consultorio) {
    return <NoEncontrado />;
  }

  return (
    <>
      <EncabezadoPagina
        ruta={[
          { etiqueta: 'Consultorios', destino: '/consultorios' },
          { etiqueta: modo === 'editar' ? consultorio.id : 'Nuevo' },
        ]}
        titulo={modo === 'editar' ? 'Editar consultorio' : 'Nuevo consultorio'}
        descripcion="Registra un ambiente físico que pueda reservarse en un turno."
      />

      <FormularioPreliminar
        accionesSecundarias={
          <Link className="boton boton--fantasma" to="/consultorios">
            <ArrowLeft size={17} /> Cancelar
          </Link>
        }
      >
        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <Building2 size={19} />
            <div>
              <h2>Identificación del ambiente</h2>
              <p>Datos visibles al momento de crear una asignación.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Código" ayuda="Usa un código único, por ejemplo A-201." requerido>
              <input
                name="codigo"
                type="text"
                defaultValue={consultorio?.codigo ?? ''}
                pattern="[A-Z0-9-]{2,12}"
                maxLength="12"
                placeholder="A-201"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Nombre descriptivo" requerido>
              <input
                name="nombre"
                type="text"
                defaultValue={consultorio?.nombre ?? ''}
                minLength="4"
                maxLength="80"
                placeholder="Consultorio cardiológico 1"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Piso o zona" requerido>
              <input
                name="ubicacion"
                type="text"
                defaultValue={consultorio?.piso ?? ''}
                minLength="2"
                maxLength="40"
                placeholder="Piso 2"
                required
              />
            </CampoFormulario>
            <CampoFormulario etiqueta="Uso principal">
              <select name="especialidad" defaultValue={consultorio?.especialidad ?? ''}>
                <option value="">Uso general</option>
                <option>Cardiología</option>
                <option>Pediatría</option>
                <option>Neurología</option>
                <option>Traumatología</option>
              </select>
            </CampoFormulario>
          </div>
        </section>

        <section className="seccion-formulario">
          <div className="seccion-formulario__titulo">
            <Settings2 size={19} />
            <div>
              <h2>Disponibilidad operativa</h2>
              <p>El estado evita ofrecer ambientes que no pueden asignarse.</p>
            </div>
          </div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Estado" requerido>
              <select name="estado" defaultValue={consultorio?.estado ?? 'Disponible'} required>
                <option>Disponible</option>
                <option>Ocupado</option>
                <option>Mantenimiento</option>
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Observación" ancho="doble">
              <textarea
                name="observacion"
                rows="3"
                maxLength="240"
                placeholder="Motivo de mantenimiento u otra nota operativa"
              />
            </CampoFormulario>
          </div>
        </section>
      </FormularioPreliminar>
    </>
  );
}

export default FormularioConsultorio;
