import { useId } from 'react';
import { ArrowLeft, CalendarClock } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioBase from '../../components/FormularioBase';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useFormularioProgramacion } from '../../hooks/useProgramacion';
import { obtenerProfesionales } from '../../services/catalogos';
import { actualizarDisponibilidad, crearDisponibilidad, listarRecursosHorario, obtenerDisponibilidad } from '../../services/programacion';
import { DIAS_SEMANA, fechaActual, sumarDias, validarDisponibilidad } from '../../utils/validacionesProgramacion';

const INICIALES = Object.freeze({ idProfesional: '', diaSemana: '1', horaInicio: '', horaFin: '',
  fechaInicio: '', fechaFin: '', estado: 'Activo', observacion: '' });
const ajustarIniciales = (datos, recursos) => {
  const inicio = fechaActual(recursos.zonaHoraria);
  return { ...datos, fechaInicio: datos.fechaInicio || inicio, fechaFin: datos.fechaFin || sumarDias(inicio, 6) };
};
const cargarRecursos = async (opciones) => {
  const [profesionales, recursos] = await Promise.all([obtenerProfesionales(opciones), listarRecursosHorario(opciones)]);
  return { profesionales, zonaHoraria: recursos.zonaHoraria };
};
const validarAntes = (datos, { recursos }) => {
  const errores = validarDisponibilidad(datos);
  if (!recursos.profesionales.some((item) => item.id === datos.idProfesional && item.estado === 'Activo')) {
    errores.idProfesional = 'Selecciona un profesional activo.';
  }
  return errores;
};

function FormularioDisponibilidad({ modo }) {
  const { id } = useParams();
  const identificador = useId();
  const formulario = useFormularioProgramacion({ modo, id, iniciales: INICIALES, obtener: obtenerDisponibilidad,
    recursos: cargarRecursos, crear: crearDisponibilidad, actualizar: actualizarDisponibilidad, validarAntes, ajustarIniciales,
    destino: '/disponibilidades', etiqueta: 'Disponibilidad' });
  const campo = (nombre) => ({ id: `${identificador}-${nombre}`, name: nombre, value: formulario.datos[nombre],
    onChange: formulario.actualizarCampo, 'aria-invalid': Boolean(formulario.errores[nombre]) });
  const volver = <Link className="boton boton--fantasma" to="/disponibilidades" onClick={formulario.evitarSalida}><ArrowLeft size={17} /> Cancelar</Link>;

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Disponibilidades', destino: '/disponibilidades' }, { etiqueta: modo === 'editar' ? 'Editar' : 'Nueva' }]}
      titulo={modo === 'editar' ? 'Editar disponibilidad' : 'Nueva disponibilidad'}
      descripcion="Define una franja recurrente en las fechas seleccionadas. Las horas se interpretan en la zona de tu institución." />
    {formulario.cargando || formulario.errorCarga ? <section className="panel">
      <AvisoCatalogo cargando={formulario.cargando} error={formulario.errorCarga} reintentar={formulario.recargar} />
      <div className="formulario__acciones">{volver}</div>
    </section> : <FormularioBase onGuardar={formulario.guardar} bloquearGuardado={formulario.conflicto}
      guardando={formulario.guardando} error={formulario.error} errores={formulario.errores} accionesSecundarias={volver}>
      <section className="seccion-formulario">
        <div className="seccion-formulario__titulo"><CalendarClock size={19} /><div><h2>Franja de atención</h2>
          <p>Zona horaria: {formulario.recursos.zonaHoraria}. Los turnos podrán ocupar esta ventana, respetando 6 horas diarias y 36 semanales.</p></div></div>
        <div className="rejilla-formulario">
          <CampoFormulario etiqueta="Profesional" requerido error={formulario.errores.idProfesional}>
            <select {...campo('idProfesional')} required><option value="">Seleccionar profesional</option>
              {formulario.recursos.profesionales.filter((item) => item.estado === 'Activo' || item.id === formulario.datos.idProfesional)
                .map((item) => <option key={item.id} value={item.id} disabled={item.estado !== 'Activo'}>
                  {item.nombres} {item.apellidos}{item.estado !== 'Activo' ? ' (Inactivo)' : ''}</option>)}
            </select>
          </CampoFormulario>
          <CampoFormulario etiqueta="Día de la semana" requerido error={formulario.errores.diaSemana}>
            <select {...campo('diaSemana')} required>{DIAS_SEMANA.map((dia, indice) => <option key={dia} value={indice + 1}>{dia}</option>)}</select>
          </CampoFormulario>
          <CampoFormulario etiqueta="Hora de inicio" requerido error={formulario.errores.horaInicio}><input {...campo('horaInicio')} type="time" step="60" required /></CampoFormulario>
          <CampoFormulario etiqueta="Hora de fin" requerido error={formulario.errores.horaFin}><input {...campo('horaFin')} type="time" step="60" required /></CampoFormulario>
          <CampoFormulario etiqueta="Vigente desde" requerido error={formulario.errores.fechaInicio}><input {...campo('fechaInicio')} type="date" required /></CampoFormulario>
          <CampoFormulario etiqueta="Vigente hasta" requerido error={formulario.errores.fechaFin}><input {...campo('fechaFin')} type="date" min={formulario.datos.fechaInicio} required /></CampoFormulario>
          <CampoFormulario etiqueta="Estado" requerido error={formulario.errores.estado}><select {...campo('estado')} required><option>Activo</option><option>Inactivo</option></select></CampoFormulario>
          <CampoFormulario etiqueta="Observación" ayuda="Opcional. Máximo 240 caracteres." error={formulario.errores.observacion}>
            <textarea {...campo('observacion')} maxLength={240} rows={3} />
          </CampoFormulario>
        </div>
      </section>
      <AvisoCatalogo mensaje={formulario.avisoRecarga} />
      {formulario.puedeRecargar && <div className="barra-recarga"><p>{formulario.conflicto
        ? 'Recarga la revisión vigente para continuar. Tus entradas se conservarán.'
        : 'Puedes corregir la restricción o actualizar los recursos conservando tus entradas.'}</p>
        <button className="boton boton--secundario" type="button" onClick={formulario.recargar}>Recargar conservando entradas</button></div>}
      {!formulario.recursos.profesionales.some((item) => item.estado === 'Activo') && <p className="nota-programacion">
        Registra o activa un <Link to="/profesionales">profesional</Link> antes de declarar disponibilidades.</p>}
    </FormularioBase>}
  </>;
}

export default FormularioDisponibilidad;
