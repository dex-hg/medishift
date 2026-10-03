import { useId } from 'react';
import { ArrowLeft, BriefcaseMedical, ContactRound } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioBase from '../../components/FormularioBase';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useFormularioCatalogo } from '../../hooks/useCatalogos';
import { actualizarProfesional, crearProfesional, obtenerProfesional } from '../../services/catalogos';
import { LIMITES_PROFESIONAL } from '../../utils/validacionesCatalogos';

const DATOS_INICIALES = Object.freeze({
  nombres: '', apellidos: '', colegiatura: '', correo: '', telefono: '',
  categoria: '', estado: 'Activo', especialidad: '',
});

function FormularioProfesional({ modo }) {
  const { id } = useParams();
  const identificador = useId();
  const formulario = useFormularioCatalogo({
    modo, id, iniciales: DATOS_INICIALES, obtener: obtenerProfesional,
    crear: crearProfesional, actualizar: actualizarProfesional,
    destino: '/profesionales', etiqueta: 'Profesional',
  });
  const campo = (nombre) => ({
    id: `${identificador}-${nombre}`, name: nombre, value: formulario.datos[nombre],
    onChange: formulario.actualizarCampo, maxLength: LIMITES_PROFESIONAL[nombre],
    'aria-invalid': Boolean(formulario.errores[nombre]),
  });

  return (
    <>
      <EncabezadoPagina ruta={[{ etiqueta: 'Profesionales', destino: '/profesionales' },
        { etiqueta: modo === 'editar' ? 'Editar' : 'Nuevo' }]}
        titulo={modo === 'editar' ? 'Editar profesional' : 'Nuevo profesional'}
        descripcion="Completa los datos del profesional para registrarlo en tu institución." />
      {formulario.cargando || formulario.errorCarga ? (
        <section className="panel">
          <AvisoCatalogo cargando={formulario.cargando} error={formulario.errorCarga}
            reintentar={formulario.noEncontrado ? undefined : formulario.reintentar} />
          <div className="formulario__acciones"><Link className="boton boton--fantasma" to="/profesionales">
            <ArrowLeft size={17} aria-hidden="true" /> Volver a profesionales
          </Link></div>
        </section>
      ) : (
        <FormularioBase onGuardar={formulario.guardar} guardando={formulario.guardando}
          error={formulario.error} errores={formulario.errores}
          accionesSecundarias={<Link className="boton boton--fantasma" to="/profesionales"
            onClick={formulario.evitarSalida} aria-disabled={formulario.guardando || undefined}>
            <ArrowLeft size={17} aria-hidden="true" /> Cancelar
          </Link>}>
          <section className="seccion-formulario">
            <div className="seccion-formulario__titulo"><ContactRound size={19} aria-hidden="true" /><div>
              <h2>Datos personales</h2><p>Información de contacto para la gestión interna.</p>
            </div></div>
            <div className="rejilla-formulario">
              <CampoFormulario etiqueta="Nombres" requerido error={formulario.errores.nombres}>
                <input {...campo('nombres')} type="text" autoComplete="given-name" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Apellidos" requerido error={formulario.errores.apellidos}>
                <input {...campo('apellidos')} type="text" autoComplete="family-name" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Correo institucional" requerido error={formulario.errores.correo}>
                <input {...campo('correo')} type="email" autoComplete="email" placeholder="nombre@institucion.pe" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Teléfono" ayuda="Opcional. Entre 7 y 15 dígitos, con prefijo + si corresponde."
                error={formulario.errores.telefono}>
                <input {...campo('telefono')} type="tel" autoComplete="tel" placeholder="+51 999 999 999" />
              </CampoFormulario>
            </div>
          </section>
          <section className="seccion-formulario">
            <div className="seccion-formulario__titulo"><BriefcaseMedical size={19} aria-hidden="true" /><div>
              <h2>Datos laborales</h2><p>Identificación laboral y especialidad del profesional.</p>
            </div></div>
            <div className="rejilla-formulario">
              <CampoFormulario etiqueta="Colegiatura" requerido ayuda="Ingresa el código de su colegio profesional."
                error={formulario.errores.colegiatura}>
                <input {...campo('colegiatura')} type="text" placeholder="Código de colegiatura" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Especialidad" requerido ayuda="Selecciona una sugerencia o escribe una nueva especialidad."
                error={formulario.errores.especialidad}>
                <input {...campo('especialidad')} type="text" list={`${identificador}-especialidades`} required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Categoría profesional" requerido error={formulario.errores.categoria}>
                <input {...campo('categoria')} type="text" placeholder="Categoría del profesional" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Estado" requerido error={formulario.errores.estado}>
                <select {...campo('estado')} required><option>Activo</option><option>Inactivo</option></select>
              </CampoFormulario>
            </div>
            <datalist id={`${identificador}-especialidades`}>
              {formulario.especialidades.map((especialidad) => <option key={especialidad.id} value={especialidad.nombre} />)}
            </datalist>
          </section>
          <AvisoCatalogo error={formulario.errorEspecialidades} reintentar={formulario.reintentarEspecialidades} />
        </FormularioBase>
      )}
    </>
  );
}

export default FormularioProfesional;
