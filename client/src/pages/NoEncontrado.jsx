import { ArrowLeft, SearchX } from 'lucide-react';
import { Link } from 'react-router-dom';

function NoEncontrado() {
  return (
    <section className="pagina-vacia">
      <SearchX size={42} aria-hidden="true" />
      <p className="sobrelinea">Error 404</p>
      <h1>La vista solicitada no existe</h1>
      <p>Comprueba la dirección o vuelve al panel operativo.</p>
      <Link className="boton boton--primario" to="/">
        <ArrowLeft size={17} />
        Volver al inicio
      </Link>
    </section>
  );
}

export default NoEncontrado;

