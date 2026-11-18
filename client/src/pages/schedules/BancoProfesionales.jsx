import { GripVertical, Search, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { TIPO_ARRASTRE } from '../../utils/calendarioMensual';
import { horasProfesional } from '../../utils/feriados';
import { etiquetasDelRecurso } from '../../utils/seleccionRecursosTurno';

function normalizarBusqueda(texto) {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').trim().toLocaleLowerCase('es');
}

export default function BancoProfesionales({ profesionales, hayActivos, turnos, feriados, idConsultorio, idArea,
  seleccionado, seleccionar, ocupado, editable }) {
  const [busqueda, cambiarBusqueda] = useState('');
  const lista = useRef(null);
  const palabras = normalizarBusqueda(busqueda).split(/\s+/).filter(Boolean);
  const visibles = profesionales.map((profesional) => ({ profesional,
    etiquetas: etiquetasDelRecurso(profesional).map((etiqueta) => etiqueta.nombre).join(', '),
  })).filter(({ profesional, etiquetas }) => {
    const texto = normalizarBusqueda(`${profesional.nombre} ${etiquetas}`);
    return palabras.every((palabra) => texto.includes(palabra));
  });

  useEffect(() => {
    if (lista.current) lista.current.scrollTop = 0;
  }, [busqueda, idArea, idConsultorio]);

  let aviso = '';
  if (!idArea) aviso = 'Selecciona un área, subárea o consultorio.';
  else if (!editable) aviso = 'Este calendario está en modo de consulta.';
  else if (!hayActivos) aviso = 'Aún no tienes profesionales activos.';
  else if (!profesionales.length) aviso = 'No hay profesionales con etiquetas compatibles para esta selección.';

  const arrastrar = (evento, profesional) => {
    const contenido = JSON.stringify({ tipo: 'profesional', id: profesional.id });
    evento.dataTransfer.setData(TIPO_ARRASTRE, contenido);
    evento.dataTransfer.setData('text/plain', contenido);
    evento.dataTransfer.effectAllowed = 'copyMove';
  };
  return <section className="panel banco-profesionales" aria-label="Profesionales para asignar">
    <div className="planificador-titulo"><div><span className="planificador-paso">2</span><h2>Profesionales compatibles</h2></div></div>
    {aviso ? <p className="planificador-ayuda" role="status">{aviso}</p> : <>
      <p className="banco-profesionales__ayuda planificador-ayuda">Arrastra al día o usa «Asignar».</p>
      <label className="planificador-busqueda"><Search size={16} aria-hidden="true" />
        <span className="solo-lectores">Buscar profesional o etiqueta</span><input type="search" placeholder="Nombre o etiqueta"
          value={busqueda} onChange={(evento) => cambiarBusqueda(evento.target.value)} /></label>
      <p className="banco-profesionales__resultado planificador-ayuda" role="status">{visibles.length
        ? `${visibles.length} ${visibles.length === 1 ? 'profesional' : 'profesionales'}`
        : 'Ningún profesional compatible coincide con la búsqueda.'}</p>
      {visibles.length > 0 && <div className="banco-profesionales__lista" ref={lista}>{visibles.map(({ profesional, etiquetas }) => {
        const resumen = horasProfesional(profesional.id, turnos, feriados);
        const horas = resumen.minutos / 60;
        const elegido = seleccionado === profesional.id;
        return <button type="button" key={profesional.id} draggable={!ocupado} onDragStart={(evento) => arrastrar(evento, profesional)}
          disabled={ocupado} aria-pressed={elegido} onClick={() => seleccionar(elegido ? '' : profesional.id)}
          className="profesional-arrastrable">
          <span className="profesional-arrastrable__avatar"><UserRound size={18} aria-hidden="true" /></span><span><strong>{profesional.nombre}</strong>
            <small>{etiquetas}</small><small>{resumen.completo
              ? `${horas.toLocaleString('es-PE')} h en este mes · ${resumen.minutosFeriados / 60} h por feriados` : 'Total pendiente: verifica los feriados'}</small></span>
          <GripVertical size={17} aria-hidden="true" /></button>;
      })}</div>}
    </>}
    <Link className="planificador-enlace" to="/profesionales/nuevo">+ Registrar profesional</Link>
  </section>;
}
