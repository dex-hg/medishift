import { useCallback, useState } from 'react';
import { Link, useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import FormularioPaciente from './FormularioPaciente';
import PanelAccesoPaciente from './PanelAccesoPaciente';
import TablaCitas from '../appointments/TablaCitas';
import { useConsultaAtencion } from '../../hooks/useAtencion';
import { obtenerPaciente, obtenerHistorialCitas } from '../../services/atencion';

function HistorialPaciente({ id }) {
  const [pagina, cambiarPagina] = useState(0);
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerHistorialCitas(id, pagina, opciones), [id, pagina]));
  return <section className="panel atencion-historial" aria-busy={consulta.cargando}>
    <h2>Historial de citas</h2>
    {consulta.cargando && <p role="status">Cargando citas…</p>}
    {consulta.error && <div role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" onClick={consulta.recargar}>Reintentar</button></div>}
    {consulta.datos && <><TablaCitas citas={consulta.datos.citas} zonaHoraria={consulta.datos.zonaHoraria} />
      <div className="atencion-paginacion"><button className="boton boton--fantasma" disabled={!pagina} onClick={() => cambiarPagina(pagina - 1)}>Anterior</button>
        <span>Página {pagina + 1} · {consulta.datos.total} citas</span>
        <button className="boton boton--fantasma" disabled={(pagina + 1) * 30 >= consulta.datos.total} onClick={() => cambiarPagina(pagina + 1)}>Siguiente</button></div></>}
  </section>;
}

function FichaExistente({ id }) {
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerPaciente(id, opciones), [id]));
  const { configuracion, sesion } = useOutletContext();
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Pacientes', destino: '/pacientes' }, { etiqueta: 'Ficha' }]} titulo={consulta.datos ? `${consulta.datos.nombres} ${consulta.datos.apellidos}` : 'Ficha del paciente'}
      descripcion={consulta.datos?.codigo || 'Datos administrativos del paciente.'}
      acciones={consulta.datos?.activo && <Link className="boton boton--primario" to="/citas" state={{ paciente: consulta.datos }}>Agendar cita</Link>} />
    {consulta.cargando && <p role="status">Cargando ficha…</p>}
    {consulta.error && <div className="panel atencion-aviso" role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" onClick={consulta.recargar}>Reintentar</button></div>}
    {consulta.datos && <><FormularioPaciente key={consulta.datos.revision} paciente={consulta.datos} zonaHoraria={configuracion.zonaHoraria} guardado={consulta.recargar} recargar={consulta.recargar} />
      {sesion.esAdministrador && consulta.datos.activo && <PanelAccesoPaciente key={consulta.datos.revision}
        paciente={consulta.datos} zonaHoraria={configuracion.zonaHoraria} />}
      <HistorialPaciente id={id} /></>}
  </>;
}

export default function FichaPaciente() {
  const { id } = useParams();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const { configuracion } = useOutletContext();
  if (id) return <FichaExistente key={id} id={id} />;
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Pacientes', destino: '/pacientes' }, { etiqueta: 'Registro' }]} titulo="Registrar paciente" descripcion="Crea una ficha administrativa institucional." />
    <FormularioPaciente dni={ubicacion.state?.dni} zonaHoraria={configuracion.zonaHoraria} guardado={(paciente) => navegar(`/pacientes/${paciente.id}`, { replace: true })} />
  </>;
}
