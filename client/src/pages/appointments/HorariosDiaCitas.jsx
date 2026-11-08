import { CalendarClock } from 'lucide-react';
import { partesLocales, etiquetasEstadoCita } from '../../utils/validacionesAtencion';
import { segmentosTurno } from '../../utils/agendaVisual';
import { mostrarFecha } from '../../utils/validacionesProgramacion';

export default function HorariosDiaCitas({ turnos, citas, fecha, zonaHoraria, elegir, ocupado, excluir = '', seleccionado, verCita }) {
  const hora = (instante) => partesLocales(instante, zonaHoraria).hora;
  return <section className="panel agenda-dia" aria-label="Horarios del día seleccionado">
    <header><div><small>2. Elige un espacio libre</small><h2>{mostrarFecha(fecha)}</h2></div><CalendarClock size={22} aria-hidden="true" /></header>
    {!turnos.length && <div className="agenda-dia__vacio"><p>No hay turnos aprobados para este día.</p>
      <span>Elige otro día o solicita al administrador que apruebe los turnos.</span></div>}
    {turnos.map((turno) => {
      const segmentos = segmentosTurno(turno, citas, { excluir });
      return <article className={`agenda-turno ${seleccionado === turno.id ? 'agenda-turno--elegido' : ''}`} key={turno.id}>
        <div className="agenda-turno__cabecera"><div><h3>{turno.profesional}</h3><p>{turno.consultorio} · {turno.especialidad}</p></div><strong>{hora(turno.inicio)} a {hora(turno.fin)}</strong></div>
        <div className="agenda-turno__franjas">{segmentos.map((segmento, indice) => {
          const intervalo = `${hora(segmento.inicio)} a ${hora(segmento.fin)}`;
          if (segmento.tipo === 'libre') return <button type="button" key={indice} disabled={ocupado} className="agenda-franja agenda-franja--libre"
            aria-label={`Agendar de ${intervalo} con ${turno.profesional}`} onClick={() => elegir(turno, segmento)}><strong>{intervalo}</strong><span>Disponible · Elegir</span></button>;
          if (segmento.tipo === 'ocupado') return <button type="button" key={indice} disabled={ocupado} className="agenda-franja agenda-franja--ocupada"
            onClick={() => verCita(segmento.cita)}><strong>{intervalo}</strong><span>{segmento.cita.paciente}</span><small>{etiquetasEstadoCita[segmento.cita.estado]}</small></button>;
          return <div key={indice} className="agenda-franja agenda-franja--pasada"><strong>{intervalo}</strong><span>Horario transcurrido</span></div>;
        })}</div>
      </article>;
    })}
  </section>;
}
