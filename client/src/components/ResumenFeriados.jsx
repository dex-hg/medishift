import { CalendarCheck2, RefreshCw } from 'lucide-react';
import { mostrarFecha } from '../utils/validacionesProgramacion';

export default function ResumenFeriados({ feriados, error, ocupado, actualizar }) {
  if (!feriados) return null;
  const disponible = feriados.estado === 'Disponible';
  return <section className={`panel resumen-feriados ${disponible ? '' : 'resumen-feriados--pendiente'}`} aria-label="Feriados nacionales del mes">
    <div className="resumen-feriados__titulo"><CalendarCheck2 size={21} aria-hidden="true" /><div>
      <strong>Feriados nacionales{feriados.pais ? ` de ${feriados.pais}` : ''}</strong>
      <p>{disponible ? `${feriados.dias.length} ${feriados.dias.length === 1 ? 'feriado' : 'feriados'} este mes. ${feriados.minutosPorFeriado / 60} h por fecha y especialista con turnos en el mes.`
        : 'Horas por feriados pendientes de verificar.'}</p></div>
      {feriados.codigoPais && <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={actualizar}>
        <RefreshCw size={15} /> Actualizar feriados</button>}</div>
    {disponible && feriados.dias.length > 0 && <ul className="resumen-feriados__dias">{feriados.dias.map((dia) =>
      <li key={dia.fecha}><time dateTime={dia.fecha}>{mostrarFecha(dia.fecha)}</time> · {dia.nombre}</li>)}</ul>}
    <p className="resumen-feriados__nota">El abono es institucional, incluye fines de semana y reemplaza las horas de turnos de ese día. Una falta no anula el abono.</p>
    {(error || feriados.mensaje) && <p className="resumen-feriados__aviso" role="status">{error || feriados.mensaje}</p>}
    <small>Fuente: {feriados.fuente}{feriados.actualizado ? ` · Consulta: ${new Date(feriados.actualizado).toLocaleString('es-PE')}` : ''}.</small>
  </section>;
}
