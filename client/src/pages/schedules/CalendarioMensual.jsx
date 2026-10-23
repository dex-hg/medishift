import { CalendarDays, GripVertical, Plus } from 'lucide-react';
import { useState } from 'react';
import { celdasDelMes, leerArrastre, TIPO_ARRASTRE } from '../../utils/calendarioMensual';
import { DIAS_SEMANA, mostrarFecha } from '../../utils/validacionesProgramacion';

export default function CalendarioMensual({ periodo, consultorio, area, turnos, hoy, seleccionado, asignar, editar, soltar, ocupado, editable }) {
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
      <h2>{consultorio ? consultorio.nombre : 'Tu horario mensual'}</h2><p>{area ? `${area.nombre} · ${consultorio.codigo}${area.especialidad ? ` · ${area.especialidad}` : ''}` : 'Selecciona un consultorio en las carpetas de la izquierda'}</p>
    </div></div><span className="calendario-mensual__estado">{editable ? 'Borrador mensual' : 'Solo lectura'}</span></div>
    {!consultorio ? <div className="planificador-vacio calendario-mensual__inicio"><CalendarDays size={40} /><h3>Un mes completo, un solo lugar</h3>
      <p>Selecciona un consultorio, elige un profesional y asígnalo al día que necesites.</p></div> : <>
      <p className="calendario-mensual__instruccion" role="status">{!area?.idEspecialidad ? 'Configura la especialidad de esta área para asignar nuevos turnos.'
        : !editable ? 'Este consultorio o su plan mensual no admite cambios.'
        : seleccionado ? 'Profesional seleccionado. Pulsa «Asignar» en un día o arrástralo hasta allí.'
          : 'Arrastra un profesional a un día. También puedes pulsar «Asignar» para elegirlo.'}</p>
      <div className="calendario-mensual__desplazamiento"><div className="calendario-mensual__rejilla">
        {DIAS_SEMANA.map((dia) => <div className="calendario-mensual__dia-semana" key={dia}><abbr title={dia}>{dia.slice(0, 3)}</abbr></div>)}
        {celdasDelMes(periodo).map((fecha, indice) => {
          if (!fecha) return <div key={`vacio-${indice}`} className="calendario-mensual__fuera" aria-hidden="true" />;
          const habilitado = editable && !ocupado && fecha >= hoy;
          const delDia = turnos.filter((turno) => turno.fecha === fecha).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
          return <div key={fecha} className={`calendario-mensual__dia ${fecha === hoy ? 'hoy' : ''} ${fecha < hoy ? 'pasado' : ''} ${destino === fecha ? 'destino' : ''}`}
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
            <div className="calendario-mensual__turnos">{delDia.map((turno) => <button key={turno.id} type="button" className="turno-mensual"
              disabled={ocupado} draggable={habilitado && turno.estado === 'Borrador'} onClick={() => editar(turno)}
              onDragStart={(evento) => {
                const contenido = JSON.stringify({ tipo: 'turno', id: turno.id });
                evento.dataTransfer.setData(TIPO_ARRASTRE, contenido);
                evento.dataTransfer.setData('text/plain', contenido);
                evento.dataTransfer.effectAllowed = 'move';
              }}
              aria-label={`Ver turno de ${turno.profesional}, ${mostrarFecha(fecha)}, ${turno.horaInicio} a ${turno.horaFin}`}>
              <span><strong>{turno.horaInicio}–{turno.horaFin}</strong>{habilitado && <GripVertical size={12} aria-hidden="true" />}</span>
              <span>{turno.profesional}</span><small>{turno.especialidad}</small>
            </button>)}</div>
            {habilitado && <button type="button" className="calendario-mensual__asignar" onClick={() => asignar(fecha)}
              aria-label={`Asignar profesional el ${mostrarFecha(fecha)}`}><Plus size={13} /> Asignar</button>}
          </div>;
        })}
      </div></div>
    </>}
  </section>;
}
