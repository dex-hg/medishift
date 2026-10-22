import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import PlanesMensuales from '../monthly/PlanesMensuales';
import HorariosSemanales from './HorariosSemanales';

export default function Horarios() {
  const { sesion } = useOutletContext();
  const [parametros] = useSearchParams();
  const semanal = parametros.has('fechaInicio') || parametros.get('vista') === 'semanal'
    || !sesion.esAdministrador;

  return <>
    <nav className="vistas-horario" aria-label="Vistas de horarios">
      {sesion.esAdministrador && <Link className={`boton ${!semanal ? 'boton--primario' : 'boton--fantasma'}`}
        to="/horarios" aria-current={!semanal ? 'page' : undefined}>Meses y áreas</Link>}
      <Link className={`boton ${semanal ? 'boton--primario' : 'boton--fantasma'}`}
        to="/horarios?vista=semanal" aria-current={semanal ? 'page' : undefined}>Turnos semanales</Link>
    </nav>
    {semanal ? <HorariosSemanales /> : <PlanesMensuales />}
  </>;
}
