import { Link } from 'react-router-dom';
import Estado from '../../components/Estado';
import { accionesCita, etiquetasEstadoCita } from '../../utils/validacionesAtencion';

export default function TablaCitas({ citas, zonaHoraria, actuar, ocupado = false }) {
  const mostrar = (instante) => new Intl.DateTimeFormat('es-PE', { timeZone: zonaHoraria, dateStyle: 'short', timeStyle: 'short' }).format(new Date(instante));
  if (!citas.length) return <p className="atencion-aviso">No hay citas registradas en esta consulta.</p>;
  return <div className="tabla-responsive"><table className="tabla-citas">
    <thead><tr><th>Paciente</th><th>Horario</th><th>Médico y consultorio</th><th>Estado</th>{actuar && <th>Acciones</th>}</tr></thead>
    <tbody>{citas.map((cita) => <tr key={cita.id}>
      <td><span className="dato-doble"><Link to={`/pacientes/${cita.idPaciente}`}><strong>{cita.paciente}</strong></Link><small>{cita.codigoPaciente}</small></span></td>
      <td><span className="dato-doble"><strong>{mostrar(cita.inicio)}</strong><small>Hasta {mostrar(cita.fin)}</small></span></td>
      <td><span className="dato-doble"><strong>{cita.profesional}</strong><small>{cita.consultorio}</small></span></td>
      <td><Estado valor={etiquetasEstadoCita[cita.estado]} /></td>
      {actuar && <td><div className="atencion-acciones">{accionesCita(cita).map((accion) => <button className="boton boton--fantasma" type="button" key={accion} disabled={ocupado}
        onClick={() => actuar(cita, accion)}>{accion === 'reprogramar' ? 'Reprogramar' : accion === 'cancelada' ? 'Cancelar' : accion === 'atendida' ? 'Marcar atendida' : 'No asistió'}</button>)}</div></td>}
    </tr>)}</tbody>
  </table></div>;
}
