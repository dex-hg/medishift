import { CalendarClock } from 'lucide-react';
import { Link } from 'react-router-dom';
import EncabezadoPagina from './EncabezadoPagina';

function ModuloPendiente({ titulo, descripcion }) {
  return (
    <>
      <EncabezadoPagina titulo={titulo} descripcion={descripcion} />
      <section className="panel">
        <div className="sin-resultados">
          <CalendarClock size={32} aria-hidden="true" />
          <strong>Disponible en una próxima etapa</strong>
          <p>Primero registra los profesionales y consultorios de tu institución.</p>
          <Link className="boton boton--secundario" to="/panel">Volver al panel</Link>
        </div>
      </section>
    </>
  );
}

export default ModuloPendiente;
