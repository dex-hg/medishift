import { useId, useMemo } from 'react';
import { ArrowLeft, CalendarClock } from 'lucide-react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import CampoFormulario from '../../components/CampoFormulario';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioBase from '../../components/FormularioBase';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import { useFormularioProgramacion } from '../../hooks/useProgramacion';
import { actualizarTurno, crearTurno, ErrorProgramacion, listarRecursosHorario, listarSemana, obtenerTurno } from '../../services/programacion';
import { fechaActual, fechaValida, lunesDeSemana, validarTurno } from '../../utils/validacionesProgramacion';

const destinoTurno = (turno) => `/horarios?fechaInicio=${lunesDeSemana(turno.fecha)}`;
const ajustarIniciales = (datos, recursos) => ({ ...datos, fecha: datos.fecha || fechaActual(recursos.zonaHoraria) });
const validarAntes = async (datos, { recursos, registro, senal }) => {
  const errores = validarTurno(datos, { recursos });
  if (Object.keys(errores).length) return errores;
  const semana = await listarSemana(lunesDeSemana(datos.fecha), { senal });
  if (['Aprobado', 'Pendiente'].includes(semana.estado)) {
    throw new ErrorProgramacion('Esta semana está aprobada o pendiente. Reabre el horario antes de modificar sus turnos.', { estado: 409 });
  }
  return validarTurno(datos, { recursos, turnos: semana.turnos, idActual: registro?.id });
};

function FormularioTurno({ modo }) {
  const { id } = useParams();
  const [parametros] = useSearchParams();
  const identificador = useId();
  const fechaInicial = fechaValida(parametros.get('fecha')) ? parametros.get('fecha') : '';
  const iniciales = useMemo(() => ({ idProfesional: '', idConsultorio: '', idEspecialidad: '', fecha: fechaInicial,
    horaInicio: '', horaFin: '', observacion: '' }), [fechaInicial]);
  const formulario = useFormularioProgramacion({ modo, id, iniciales, obtener: obtenerTurno, recursos: listarRecursosHorario,
    crear: crearTurno, actualizar: actualizarTurno, validarAntes, ajustarIniciales, destino: destinoTurno, etiqueta: 'Turno' });
  const lunesSeleccionado = lunesDeSemana(formulario.datos.fecha);
  const semanaDestino = lunesSeleccionado ? `/horarios?fechaInicio=${lunesSeleccionado}` : '/horarios';
  const inmutable = formulario.registro && formulario.registro.estado !== 'Borrador';
  const campo = (nombre) => ({ id: `${identificador}-${nombre}`, name: nombre, value: formulario.datos[nombre],
    onChange: formulario.actualizarCampo, 'aria-invalid': Boolean(formulario.errores[nombre]) });
  const profesional = formulario.recursos?.profesionales.find((item) => item.id === formulario.datos.idProfesional);
  const especialidades = profesional?.especialidades || [];
  const consultorios = (formulario.recursos?.consultorios || []).filter((item) => item.estado === 'Activo'
    && (item.usoGeneral || item.especialidades.some((especialidad) => especialidad.id === formulario.datos.idEspecialidad)));
  const cambiarProfesional = (evento) => {
    formulario.actualizarCampo(evento);
    formulario.setDatos((datos) => ({ ...datos, idEspecialidad: '', idConsultorio: '' }));
  };
  const cambiarEspecialidad = (evento) => {
    formulario.actualizarCampo(evento);
    formulario.setDatos((datos) => ({ ...datos, idConsultorio: '' }));
  };
  const volver = <Link className="boton boton--fantasma" to={semanaDestino} onClick={formulario.evitarSalida}><ArrowLeft size={17} /> Volver al horario</Link>;

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Horarios', destino: semanaDestino }, { etiqueta: modo === 'editar' ? 'Editar' : 'Nuevo' }]}
      titulo={modo === 'editar' ? 'Editar turno' : 'Nuevo turno'}
      descripcion="Asigna un turno dentro de la disponibilidad del profesional. Máximo 6 horas diarias y 36 semanales; no se admiten turnos que crucen medianoche." />
    {formulario.cargando || formulario.errorCarga ? <section className="panel"><AvisoCatalogo cargando={formulario.cargando}
      error={formulario.errorCarga} reintentar={formulario.recargar} /><div className="formulario__acciones">{volver}</div></section>
      : inmutable ? <section className="panel"><div className="seccion-formulario"><h2>Turno {formulario.registro.estado.toLocaleLowerCase('es')}</h2>
        <p>Este registro conserva su historial. Para modificar una semana aprobada, vuelve al horario y crea una nueva versión con «Reabrir para editar».</p></div>
      <div className="formulario__acciones">{volver}</div></section>
        : <FormularioBase onGuardar={formulario.guardar} guardando={formulario.guardando} bloquearGuardado={formulario.conflicto}
          error={formulario.error} errores={formulario.errores} accionesSecundarias={volver}>
          <section className="seccion-formulario"><div className="seccion-formulario__titulo"><CalendarClock size={19} /><div><h2>Asignación de atención</h2>
            <p>Zona horaria: {formulario.recursos.zonaHoraria}. Los consultorios generales admiten cualquier especialidad del profesional.</p></div></div>
          <div className="rejilla-formulario">
            <CampoFormulario etiqueta="Profesional" requerido error={formulario.errores.idProfesional}>
              <select {...campo('idProfesional')} onChange={cambiarProfesional} required><option value="">Seleccionar profesional</option>
                {formulario.recursos.profesionales.filter((item) => item.estado === 'Activo' || item.id === formulario.datos.idProfesional)
                  .map((item) => <option key={item.id} value={item.id} disabled={item.estado !== 'Activo'}>{item.nombre}{item.estado !== 'Activo' ? ' (Inactivo)' : ''}</option>)}
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Especialidad de atención" requerido ayuda="Incluye las especialidades principales y secundarias del profesional." error={formulario.errores.idEspecialidad}>
              <select {...campo('idEspecialidad')} onChange={cambiarEspecialidad} required><option value="">Seleccionar especialidad</option>
                {especialidades.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Consultorio compatible" requerido error={formulario.errores.idConsultorio}>
              <select {...campo('idConsultorio')} required><option value="">Seleccionar consultorio</option>
                {consultorios.map((item) => <option key={item.id} value={item.id}>{item.codigo} · {item.nombre}{item.usoGeneral ? ' (General)' : ''}</option>)}
              </select>
            </CampoFormulario>
            <CampoFormulario etiqueta="Fecha de atención" requerido error={formulario.errores.fecha}><input {...campo('fecha')} type="date" min={fechaActual(formulario.recursos.zonaHoraria)} required /></CampoFormulario>
            <CampoFormulario etiqueta="Hora de inicio" requerido error={formulario.errores.horaInicio}><input {...campo('horaInicio')} type="time" step="60" required /></CampoFormulario>
            <CampoFormulario etiqueta="Hora de fin" requerido error={formulario.errores.horaFin}><input {...campo('horaFin')} type="time" step="60" required /></CampoFormulario>
            <CampoFormulario etiqueta="Observación" ancho="doble" ayuda="Opcional. Máximo 180 caracteres." error={formulario.errores.observacion}>
              <textarea {...campo('observacion')} rows={3} maxLength={180} />
            </CampoFormulario>
          </div></section>
          <p className="nota-programacion">Programa franjas futuras. Los turnos pasados o con asistencia conservan su historial. El servidor comprueba la disponibilidad vigente, los cruces y las horas acumuladas antes de guardar.
            {' '}<Link to="/disponibilidades">Revisar disponibilidades</Link>.</p>
          <AvisoCatalogo mensaje={formulario.avisoRecarga} />
          {formulario.puedeRecargar && <div className="barra-recarga"><p>{formulario.conflicto
            ? 'Recarga la revisión vigente sin perder tus entradas, o revisa el estado de la semana.'
            : 'Puedes corregir la restricción o actualizar los recursos conservando tus entradas.'}</p>
            <button className="boton boton--secundario" type="button" onClick={formulario.recargar}>Recargar conservando entradas</button>
            <Link className="boton boton--fantasma" to={semanaDestino}>Revisar semana</Link></div>}
          {formulario.datos.idEspecialidad && !consultorios.length && <p className="nota-programacion">No hay consultorios activos compatibles con la especialidad seleccionada.</p>}
        </FormularioBase>}
  </>;
}

export default FormularioTurno;
