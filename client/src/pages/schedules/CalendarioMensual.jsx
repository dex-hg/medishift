import { CalendarDays, GripVertical, Plus } from 'lucide-react';
import { useState } from 'react';
import { celdasDelMes, etiquetasDelArea, leerArrastre, TIPO_ARRASTRE } from '../../utils/calendarioMensual';
import { DIAS_SEMANA, mostrarFecha } from '../../utils/validacionesProgramacion';
import { buscarFeriado } from '../../utils/feriados';
import { reservasDelDia } from '../../utils/autoprogramacion';

export default function CalendarioMensual({ periodo, consultorio, area, nombreArea, turnos, ocupaciones = [], zonaHoraria, feriados, hoy, seleccionado, asignar, editar, soltar, ocupado, editable }) {
  const [destino, setDestino] = useState('');
  const recibir = (evento, fecha) => {
    evento.preventDefault();
    setDestino('');
    if (ocupado || !editable || fecha < hoy) return;
    const contenido = leerArrastre(evento.dataTransfer.getData(TIPO_ARRASTRE) || evento.dataTransfer.getData('text/plain'));
    if (contenido) soltar(contenido, fecha);
  };
  return <section className="panel calendario-mensual" aria-label="Calendario mensual">
    <div className="calendario-mensual__titulo"><div><span className="planificador-paso">3</span><div>
      <h2>{consultorio?.nombre || nombreArea || 'Calendario mensual'}</h2>
      {area && <p>{consultorio && `${nombreArea} · ${consultorio.codigo} · `}{etiquetasDelArea(area).map((etiqueta) => etiqueta.nombre).join(', ')}</p>}
    </div></div><span className="calendario-mensual__estado">{editable ? 'Borrador mensual' : 'Solo lectura'}</span></div>
    {!area ? <div className="planificador-vacio calendario-mensual__inicio"><CalendarDays size={40} /><p>Selecciona un área, subárea o consultorio.</p></div> : <>
      <p className="calendario-mensual__instruccion" role="status">{!etiquetasDelArea(area).length ? 'Configura las etiquetas de esta unidad para programar turnos.'
        : !editable ? 'La unidad o su plan mensual no admite cambios en esta vista.'
        : seleccionado ? 'Profesional seleccionado. Pulsa «Asignar» en un día o arrástralo hasta allí.'
          : 'Arrastra un profesional a un día. También puedes pulsar «Asignar» para elegirlo.'}</p>
      <div className="calendario-mensual__desplazamiento"><div className="calendario-mensual__rejilla">
        {DIAS_SEMANA.map((dia) => <div className="calendario-mensual__dia-semana" key={dia}><abbr title={dia}>{dia.slice(0, 3)}</abbr></div>)}
        {celdasDelMes(periodo).map((fecha, indice) => {
          if (!fecha) return <div key={`vacio-${indice}`} className="calendario-mensual__fuera" aria-hidden="true" />;
          const habilitado = editable && !ocupado && fecha >= hoy;
          const feriado = buscarFeriado(feriados, fecha);
          const delDia = turnos.filter((turno) => turno.fecha === fecha).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
          return <div key={fecha} className={`calendario-mensual__dia ${feriado ? 'calendario-mensual__dia--feriado' : ''} ${fecha === hoy ? 'hoy' : ''} ${fecha < hoy ? 'pasado' : ''} ${destino === fecha ? 'destino' : ''}`}
            onDragOver={(evento) => {
              const tipos = Array.from(evento.dataTransfer.types);
              if (habilitado && (tipos.includes(TIPO_ARRASTRE) || tipos.includes('text/plain'))) {
                evento.preventDefault();
                evento.dataTransfer.dropEffect = ['move', 'copyMove'].includes(evento.dataTransfer.effectAllowed) ? 'move' : 'copy';
                setDestino(fecha);
              }
            }}
            onDragLeave={(evento) => { if (!evento.currentTarget.contains(evento.relatedTarget)) setDestino(''); }} onDrop={(evento) => recibir(evento, fecha)}>
            <div className="calendario-mensual__fecha"><time dateTime={fecha}>{Number(fecha.slice(-2))}</time>{fecha === hoy && <small>Hoy</small>}</div>
            {feriado && <div className="calendario-mensual__feriado" title={feriado.nombre}><strong>Feriado no laborable</strong>
              <span>{feriado.nombre}</span><small>{feriados.minutosPorFeriado / 60} h institucionales</small></div>}
            <div className="calendario-mensual__turnos">{delDia.map((turno) => <button key={turno.id} type="button" className={`turno-mensual ${turno.asistencia === 'Falta' ? 'turno-mensual--falta' : ''}`}
              disabled={ocupado} draggable={habilitado && turno.estado === 'Borrador' && turno.asistencia !== 'Falta'} onClick={() => editar(turno)}
              onDragStart={(evento) => {
                const contenido = JSON.stringify({ tipo: 'turno', id: turno.id });
                evento.dataTransfer.setData(TIPO_ARRASTRE, contenido);
                evento.dataTransfer.setData('text/plain', contenido);
                evento.dataTransfer.effectAllowed = 'move';
              }}
              aria-label={`Ver turno de ${turno.profesional}, ${mostrarFecha(fecha)}, ${turno.horaInicio} a ${turno.horaFin}`}>
              <span><strong>{turno.horaInicio}–{turno.horaFin}</strong>{habilitado && <GripVertical size={12} aria-hidden="true" />}</span>
              <span>{turno.profesional}</span><small>{turno.especialidad}</small>
              {!consultorio && <small>{turno.consultorio}</small>}
              {turno.asistencia === 'Falta' && <small className="turno-mensual__falta">Falta · 0 h</small>}
            </button>)}</div>
            {consultorio && reservasDelDia(ocupaciones, consultorio.id, fecha, zonaHoraria, delDia.map((turno) => turno.id)).map((reserva) => <p key={reserva.idTurno} className="planificador-ayuda" aria-label={'Consultorio ocupado de ' + reserva.horaInicio + ' a ' + reserva.horaFin}><strong>Ocupado</strong><br />{reserva.horaInicio} a {reserva.horaFin}</p>)}
            {habilitado && <button type="button" className="calendario-mensual__asignar" onClick={() => asignar(fecha)}
              aria-label={`Asignar profesional el ${mostrarFecha(fecha)}`}><Plus size={13} /> Asignar</button>}
          </div>;
        })}
      </div></div>
    </>}
  </section>;
}
