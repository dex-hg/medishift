import { useId, useState } from 'react';
import { ArrowLeft, BriefcaseMedical, ContactRound, KeyRound } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import SelectorEtiquetas from '../../components/SelectorEtiquetas';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioBase from '../../components/FormularioBase';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useFormularioCatalogo } from '../../hooks/useCatalogos';
import { actualizarProfesional, crearProfesional, obtenerProfesional } from '../../services/catalogos';
import { LIMITES_ACCESO, LIMITES_PROFESIONAL } from '../../utils/validacionesCatalogos';

const DATOS_INICIALES = Object.freeze({
  nombres: '', apellidos: '', colegiatura: '', correo: '', telefono: '',
  categoria: '', estado: 'Activo', idsEtiquetas: [],
});

function FormularioProfesional({ modo }) {
  const { id } = useParams();
  const identificador = useId();
  const [creandoEtiqueta, cambiarCreandoEtiqueta] = useState(false);
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
  const conAcceso = formulario.datos.correoAcceso !== undefined;
  const cambiarAcceso = (evento) => {
    const habilitar = evento.target.checked;
    formulario.actualizarCampo({ target: { name: 'correoAcceso', value: habilitar ? formulario.datos.correo : undefined } });
    formulario.actualizarCampo({ target: { name: 'contrasenaAcceso', value: habilitar ? '' : undefined } });
  };

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
        <FormularioBase onGuardar={formulario.guardar} guardando={formulario.guardando || creandoEtiqueta}
          bloquearGuardado={formulario.cargandoEtiquetas || Boolean(formulario.errorEtiquetas)}
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
              <h2>Datos laborales</h2><p>Identificación y etiquetas que habilitan al profesional para recibir turnos.</p>
            </div></div>
            <div className="rejilla-formulario">
              <CampoFormulario etiqueta="Colegiatura" requerido ayuda="Ingresa el código de su colegio profesional."
                error={formulario.errores.colegiatura}>
                <input {...campo('colegiatura')} type="text" placeholder="Código de colegiatura" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Categoría profesional" requerido error={formulario.errores.categoria}>
                <input {...campo('categoria')} type="text" placeholder="Categoría del profesional" required />
              </CampoFormulario>
              <CampoFormulario etiqueta="Estado" requerido error={formulario.errores.estado}>
                <select {...campo('estado')} required><option>Activo</option><option>Inactivo</option></select>
              </CampoFormulario>
              <SelectorEtiquetas opciones={formulario.etiquetas} valor={formulario.datos.idsEtiquetas}
                cambiar={formulario.cambiarEtiquetas} error={formulario.errores.idsEtiquetas}
                deshabilitado={formulario.cargandoEtiquetas || Boolean(formulario.errorEtiquetas)} permitirAlta ancho
                alOcupar={cambiarCreandoEtiqueta} ayuda="Elige todas las competencias del profesional. Puedes completarlas después." />
            </div>
          </section>
          {modo === 'crear' && <section className="seccion-formulario">
            <div className="seccion-formulario__titulo"><KeyRound size={19} aria-hidden="true" /><div>
              <h2>Acceso a MediShift</h2>
              <p>Desde Profesionales → Acceso del doctor, puedes crear la cuenta más adelante o cambiar sus credenciales.</p>
            </div></div>
            <div className="rejilla-formulario">
              <label className="opcion-acceso-profesional">
                <input type="checkbox" checked={conAcceso} onChange={cambiarAcceso} /> Crear cuenta de acceso ahora
              </label>
              {conAcceso && <>
                <CampoFormulario etiqueta="Correo de acceso" requerido error={formulario.errores.correoAcceso}>
                  <input {...campo('correoAcceso')} type="email" maxLength={LIMITES_ACCESO.correo} autoComplete="off" required />
                </CampoFormulario>
                <CampoFormulario etiqueta="Contraseña inicial" requerido error={formulario.errores.contrasenaAcceso}
                  ayuda="Entre 12 y 128 caracteres. El doctor puede cambiarla en Mi perfil.">
                  <input {...campo('contrasenaAcceso')} type="password" minLength={LIMITES_ACCESO.contrasenaMinima}
                    maxLength={LIMITES_ACCESO.contrasenaMaxima} autoComplete="new-password" required />
                </CampoFormulario>
              </>}
            </div>
          </section>}
          <AvisoCatalogo error={formulario.errorEtiquetas} reintentar={formulario.reintentarEtiquetas} />
        </FormularioBase>
      )}
    </>
  );
}

export default FormularioProfesional;
