import { ChevronLeft, ChevronRight } from 'lucide-react';
import { celdasDelMes, desplazarMes, nombreMes } from '../../utils/calendarioMensual';
import { DIAS_SEMANA, mostrarFecha } from '../../utils/validacionesProgramacion';
import { datosDelDia, espaciosLibres } from '../../utils/agendaVisual';

export default function CalendarioCitas({ datos, profesional, excluir = '', ...propiedades }) {
  return <CalendarioMensualCitas {...propiedades} mostrarTurnos resumirDia={(dia) => {
    const registros = datosDelDia(datos, dia, profesional);
    return { citas: registros.citas.length, turnos: registros.turnos.length,
      disponible: registros.turnos.some((turno) => espaciosLibres(turno, datos.citas, { excluir }).length > 0) };
  }} />;
}
export function CalendarioMensualCitas({ periodo, fecha, hoy, seleccionar, cambiarMes, ocupado, cargando, resumirDia, mostrarTurnos = false }) {
  return <section className="panel agenda-calendario" aria-label="Calendario mensual de citas" aria-busy={cargando}>
    <div className="agenda-calendario__cabecera"><div><small>{mostrarTurnos ? '1. Elige un día' : 'Elige un día'}</small><h2>{nombreMes(periodo)}</h2></div>
      <div className="agenda-meses"><button className="boton-icono" type="button" disabled={ocupado || desplazarMes(periodo, -1) === periodo}
        aria-label="Mes anterior" onClick={() => cambiarMes(desplazarMes(periodo, -1))}><ChevronLeft size={19} /></button>
        <label><span className="solo-lectores">Mes de la agenda</span><input type="month" min="0001-01" max="9999-12" value={periodo} disabled={ocupado}
          onChange={(evento) => { if (evento.target.value) cambiarMes(evento.target.value); }} /></label>
        <button className="boton-icono" type="button" disabled={ocupado || desplazarMes(periodo, 1) === periodo}
          aria-label="Mes siguiente" onClick={() => cambiarMes(desplazarMes(periodo, 1))}><ChevronRight size={19} /></button>
      </div>
    </div>
    <div className="agenda-calendario__rejilla">
      {DIAS_SEMANA.map((dia) => <abbr key={dia} title={dia} className="agenda-calendario__semana">{dia.slice(0, 3)}</abbr>)}
      {celdasDelMes(periodo).map((dia, indice) => {
        if (!dia) return <span className="agenda-calendario__fuera" key={`fuera-${indice}`} />;
        const { citas, turnos = 0, disponible = false } = resumirDia(dia);
        const cantidadCitas = citas + (citas === 1 ? ' cita' : ' citas');
        const horarios = mostrarTurnos ? turnos + ' turnos aprobados, ' : '';
        return <button type="button" key={dia} disabled={ocupado} aria-pressed={fecha === dia}
          aria-label={mostrarFecha(dia) + ', ' + horarios + cantidadCitas + (disponible ? ', hay espacios libres' : '')}
          className={['agenda-calendario__dia', dia === hoy && 'agenda-calendario__dia--hoy',
            dia === fecha && 'agenda-calendario__dia--elegido', citas > 0 && 'agenda-calendario__dia--con-citas'].filter(Boolean).join(' ')}
          onClick={() => seleccionar(dia)}>
          <time dateTime={dia}>{Number(dia.slice(-2))}</time>
          {!cargando && mostrarTurnos && turnos > 0 && <span className={disponible ? 'agenda-calendario__libre' : ''}>
            {turnos} {turnos === 1 ? 'turno' : 'turnos'}
          </span>}
          {!cargando && citas > 0 && <small>{cantidadCitas}</small>}
        </button>;
      })}
    </div>
    <p className="agenda-calendario__leyenda">{mostrarTurnos && <span>Turnos aprobados con espacios libres</span>}
      {mostrarTurnos ? 'Selecciona un día para ver sus horas y citas.' : 'Selecciona un día para ver tus citas.'}</p>
  </section>;
}
