import { GripVertical, Search, UserRound } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { especialidadesCompatibles, TIPO_ARRASTRE } from '../../utils/calendarioMensual';
import { horasProfesional } from '../../utils/feriados';

export default function BancoProfesionales({ profesionales, turnos, feriados, consultorio, seleccionado, seleccionar, ocupado }) {
  const [busqueda, setBusqueda] = useState('');
  const activos = profesionales.filter((profesional) => profesional.estado === 'Activo');
  const visibles = activos.filter((profesional) => `${profesional.nombre} ${profesional.especialidades.map((item) => item.nombre).join(' ')}`
    .toLocaleLowerCase('es').includes(busqueda.trim().toLocaleLowerCase('es')));
  const arrastrar = (evento, profesional) => {
    const contenido = JSON.stringify({ tipo: 'profesional', id: profesional.id });
    evento.dataTransfer.setData(TIPO_ARRASTRE, contenido);
    evento.dataTransfer.setData('text/plain', contenido);
    evento.dataTransfer.effectAllowed = 'copyMove';
  };
  return <section className="panel banco-profesionales" aria-label="Profesionales para asignar">
    <div className="planificador-titulo"><div><span className="planificador-paso">2</span><h2>Elige un profesional</h2></div></div>
    <p className="planificador-ayuda">Arrástralo al día o selecciónalo y pulsa «Asignar».</p>
    <label className="planificador-busqueda"><Search size={16} aria-hidden="true" />
      <span className="solo-lectores">Buscar profesional o especialidad</span><input type="search" placeholder="Nombre o especialidad"
        value={busqueda} onChange={(evento) => setBusqueda(evento.target.value)} /></label>
    <div className="banco-profesionales__lista">{visibles.map((profesional) => {
      const compatible = especialidadesCompatibles(profesional, consultorio).length > 0;
      const habilitado = compatible && !ocupado;
      const resumen = horasProfesional(profesional.id, turnos, feriados);
      const horas = resumen.minutos / 60;
      return <button type="button" key={profesional.id} draggable={habilitado} onDragStart={(evento) => arrastrar(evento, profesional)}
        disabled={!habilitado} aria-pressed={seleccionado === profesional.id} onClick={() => seleccionar(seleccionado === profesional.id ? '' : profesional.id)}
        className={`profesional-arrastrable ${seleccionado === profesional.id ? 'seleccionado' : ''}`}>
        <span className="profesional-arrastrable__avatar"><UserRound size={18} /></span><span><strong>{profesional.nombre}</strong>
          <small>{profesional.especialidades.map((especialidad) => especialidad.nombre).join(', ') || 'Sin especialidad'}</small>
          <small>{consultorio && !compatible ? 'No compatible con este consultorio' : resumen.completo
            ? `${horas.toLocaleString('es-PE')} h en este mes · ${resumen.minutosFeriados / 60} h por feriados` : 'Total pendiente: verifica los feriados'}</small></span>
        <GripVertical size={17} aria-hidden="true" /></button>;
    })}</div>
    {!visibles.length && <p className="planificador-ayuda">{activos.length ? 'No hay coincidencias.' : 'Aún no tienes profesionales activos.'}</p>}
    <Link className="planificador-enlace" to="/profesionales/nuevo">+ Registrar profesional</Link>
  </section>;
}
