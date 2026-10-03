import { useId } from 'react';
import { ArrowLeft, Building2, Settings2 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioBase from '../../components/FormularioBase';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useFormularioCatalogo } from '../../hooks/useCatalogos';
import { actualizarConsultorio, crearConsultorio, obtenerConsultorio } from '../../services/catalogos';
import { LIMITES_CONSULTORIO } from '../../utils/validacionesCatalogos';

const DATOS_INICIALES = Object.freeze({
  codigo: '', nombre: '', ubicacion: '', especialidad: '', estado: 'Activo',
});

function FormularioConsultorio({ modo }) {
  const { id } = useParams();
  const identificador = useId();
  const formulario = useFormularioCatalogo({
    modo, id, iniciales: DATOS_INICIALES, obtener: obtenerConsultorio,
    crear: crearConsultorio, actualizar: actualizarConsultorio,
    destino: '/consultorios', etiqueta: 'Consultorio',
  });
  const campo = (nombre) => ({
    id: `${identificador}-${nombre}`, name: nombre, value: formulario.datos[nombre],
    onChange: formulario.actualizarCampo, maxLength: LIMITES_CONSULTORIO[nombre],
    'aria-invalid': Boolean(formulario.errores[nombre]),
  });

  return (
    <>
      <EncabezadoPagina ruta={[{ etiqueta: 'Consultorios', destino: '/consultorios' },
        { etiqueta: modo === 'editar' ? 'Editar' : 'Nuevo' }]}
        titulo={modo === 'editar' ? 'Editar consultorio' : 'Nuevo consultorio'}
        descripcion="Registra un ambiente físico de tu institución para la atención." />
      {formulario.cargando || formulario.errorCarga ? (
        <section className="panel">
          <AvisoCatalogo cargando={formulario.cargando} error={formulario.errorCarga}
            reintentar={formulario.noEncontrado ? undefined : formulario.reintentar} />
          <div className="formulario__acciones"><Link className="boton boton--fantasma" to="/consultorios">
            <ArrowLeft size={17} aria-hidden="true" /> Volver a consultorios
          </Link></div>
        </section>
      ) : (
        <FormularioBase onGuardar={formulario.guardar} guardando={formulario.guardando}
          error={formulario.error} errores={formulario.errores}
          accionesSecundarias={<Link className="boton boton--fantasma" to="/consultorios"
            onClick={formulario.evitarSalida} aria-disabled={formulario.guardando || undefined}>
            <ArrowLeft size={17} aria-hidden="true" /> Cancelar
          </Link>}>
          <section className="seccion-formulario">
            <div className="seccion-formulario__titulo"><Building2 size={19} aria-hidden="true" /><div>
              <h2>Identificación del ambiente</h2><p>Datos que identifican al consultorio dentro de la institución.</p>
            </div></div>
            <div className="rejilla-formulario">
              <CampoFormulario etiqueta="Código" requerido ayuda="Usa un código único dentro de tu institución."
                error={formulario.errores.codigo}>
                <input {...campo('codigo')} type="text" placeholder="Código del consultorio" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Nombre descriptivo" requerido error={formulario.errores.nombre}>
                <input {...campo('nombre')} type="text" placeholder="Nombre del consultorio" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Piso o zona" requerido error={formulario.errores.ubicacion}>
                <input {...campo('ubicacion')} type="text" placeholder="Ubicación del consultorio" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Uso principal" ayuda="Deja el campo vacío para uso general, o escribe una especialidad."
                error={formulario.errores.especialidad}>
                <input {...campo('especialidad')} type="text" list={`${identificador}-especialidades`} placeholder="Uso general" />
              </CampoFormulario>
            </div>
            <datalist id={`${identificador}-especialidades`}>
              {formulario.especialidades.map((especialidad) => <option key={especialidad.id} value={especialidad.nombre} />)}
            </datalist>
          </section>
          <section className="seccion-formulario">
            <div className="seccion-formulario__titulo"><Settings2 size={19} aria-hidden="true" /><div>
              <h2>Estado operativo</h2><p>Inactiva el consultorio cuando no esté disponible para la atención.</p>
            </div></div>
            <div className="rejilla-formulario">
              <CampoFormulario etiqueta="Estado" requerido error={formulario.errores.estado}>
                <select {...campo('estado')} required><option>Activo</option><option>Inactivo</option></select>
              </CampoFormulario>
            </div>
          </section>
          <AvisoCatalogo error={formulario.errorEspecialidades} reintentar={formulario.reintentarEspecialidades} />
        </FormularioBase>
      )}
    </>
  );
}

export default FormularioConsultorio;
