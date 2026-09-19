import { Navigate, Route, Routes } from 'react-router-dom';
import EstructuraAplicacion from './components/EstructuraAplicacion';
import Portada from './pages/Portada';
import Inicio from './pages/Inicio';
import Horarios from './pages/schedules/Horarios';
import FormularioTurno from './pages/schedules/FormularioTurno';
import Profesionales from './pages/professionals/Profesionales';
import FormularioProfesional from './pages/professionals/FormularioProfesional';
import Consultorios from './pages/consulting-rooms/Consultorios';
import FormularioConsultorio from './pages/consulting-rooms/FormularioConsultorio';
import Disponibilidades from './pages/availabilities/Disponibilidades';
import FormularioDisponibilidad from './pages/availabilities/FormularioDisponibilidad';
import NoEncontrado from './pages/NoEncontrado';

function Aplicacion() {
  return (
    <Routes>
      <Route path="/" element={<Portada />} />
      <Route element={<EstructuraAplicacion />}>
        <Route path="panel" element={<Inicio />} />
        <Route path="horarios" element={<Horarios />} />
        <Route path="horarios/nuevo" element={<FormularioTurno modo="crear" />} />
        <Route path="horarios/:id/editar" element={<FormularioTurno modo="editar" />} />
        <Route path="profesionales" element={<Profesionales />} />
        <Route path="profesionales/nuevo" element={<FormularioProfesional modo="crear" />} />
        <Route path="profesionales/:id/editar" element={<FormularioProfesional modo="editar" />} />
        <Route path="consultorios" element={<Consultorios />} />
        <Route path="consultorios/nuevo" element={<FormularioConsultorio modo="crear" />} />
        <Route path="consultorios/:id/editar" element={<FormularioConsultorio modo="editar" />} />
        <Route path="disponibilidades" element={<Disponibilidades />} />
        <Route
          path="disponibilidades/nueva"
          element={<FormularioDisponibilidad modo="crear" />}
        />
        <Route
          path="disponibilidades/:id/editar"
          element={<FormularioDisponibilidad modo="editar" />}
        />
      </Route>
      <Route path="404" element={<NoEncontrado />} />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}

export default Aplicacion;
