import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';

function EncabezadoPagina({ ruta = [], titulo, descripcion, acciones }) {
  return (
    <header className="encabezado-pagina">
      <div className="encabezado-pagina__texto">
        {ruta.length > 0 && (
          <nav className="migas" aria-label="Ruta de navegación">
            <Link to="/">Inicio</Link>
            {ruta.map((elemento) => (
              <span className="migas__elemento" key={elemento.etiqueta}>
                <ChevronRight size={13} aria-hidden="true" />
                {elemento.destino ? (
                  <Link to={elemento.destino}>{elemento.etiqueta}</Link>
                ) : (
                  <span aria-current="page">{elemento.etiqueta}</span>
                )}
              </span>
            ))}
          </nav>
        )}
        <h1>{titulo}</h1>
        {descripcion && <p>{descripcion}</p>}
      </div>
      {acciones && <div className="encabezado-pagina__acciones">{acciones}</div>}
    </header>
  );
}

export default EncabezadoPagina;

