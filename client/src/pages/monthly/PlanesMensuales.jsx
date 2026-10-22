import { useState } from 'react';
import { CalendarDays, FolderPlus, Plus, ShieldAlert } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import CampoFormulario from '../../components/CampoFormulario';
import FormularioBase from '../../components/FormularioBase';
import Estado from '../../components/Estado';
import { AvisoCatalogo } from '../../components/AvisoCatalogo';
import usePlanesMensuales from '../../hooks/usePlanesMensuales';
import { periodoValido } from '../../services/planesMensuales';
import { mostrarFecha } from '../../utils/validacionesProgramacion';

function PlanesMensuales() {
  const consulta = usePlanesMensuales();
  const [mostrarUnidad, setMostrarUnidad] = useState(false);
  const [claveFormulario, setClaveFormulario] = useState(0);
  const guardarUnidad = async (datos) => {
    if (await consulta.guardarUnidad(datos)) {
      setMostrarUnidad(false);
      setClaveFormulario((valor) => valor + 1);
    }
  };
  const acceso = !consulta.cargando && !consulta.errorCarga && !consulta.restringido;
  const error = consulta.errorOperacion || consulta.errorPlanes;

  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Horarios' }]} titulo="Horarios mensuales"
      descripcion="Crea y consulta los meses de cada área o servicio."
      acciones={acceso && <button className="boton boton--secundario" type="button"
        disabled={consulta.guardando} onClick={() => setMostrarUnidad((valor) => !valor)}>
        <Plus size={18} aria-hidden="true" /> {mostrarUnidad ? 'Cerrar formulario' : 'Nueva área o servicio'}
      </button>} />
    {consulta.restringido ? <section className="panel" aria-label="Acceso restringido">
      <div className="sin-resultados"><ShieldAlert size={32} aria-hidden="true" />
        <strong>Acceso restringido</strong><p>Solo un administrador puede acceder a la planificación mensual.</p>
        <button className="boton boton--fantasma" type="button" onClick={consulta.recargar}>Comprobar acceso</button>
      </div>
    </section> : <>
      <AvisoCatalogo cargando={consulta.cargando} error={consulta.errorCarga} reintentar={consulta.recargar} />
      {acceso && <>
        {mostrarUnidad && <section className="panel" aria-label="Registrar unidad">
          <FormularioBase key={claveFormulario} onGuardar={guardarUnidad} guardando={consulta.guardando}
            error={consulta.errorOperacion} errores={consulta.errores}>
            <div className="seccion-formulario">
              <div className="seccion-formulario__titulo"><FolderPlus size={20} aria-hidden="true" /><div>
                <h2>Nueva área o servicio</h2><p>Registra el departamento o servicio cuyo horario vas a organizar.</p>
              </div></div>
              <div className="rejilla-formulario">
                <CampoFormulario etiqueta="Código" requerido error={consulta.errores.codigo}>
                  <input name="codigo" maxLength={32} required autoComplete="off" />
                </CampoFormulario>
                <CampoFormulario etiqueta="Tipo" requerido error={consulta.errores.tipo}>
                  <select name="tipo" defaultValue="Servicio" required>
                    <option value="Servicio">Servicio</option><option value="Departamento">Departamento</option>
                  </select>
                </CampoFormulario>
                <CampoFormulario etiqueta="Nombre" requerido ancho="doble" error={consulta.errores.nombre}>
                  <input name="nombre" maxLength={140} required autoComplete="off" />
                </CampoFormulario>
              </div>
            </div>
          </FormularioBase>
        </section>}
        <section className="panel panel--tabla" aria-busy={consulta.consultando || consulta.guardando}>
          <div className="seccion-formulario">
            <p className="nota-programacion">Zona horaria institucional: {consulta.zonaHoraria}</p>
            <div className="rejilla-formulario">
              <CampoFormulario etiqueta="Área o servicio" error={consulta.errores.idUnidad || consulta.errores.unidad}>
                <select value={consulta.idUnidad} disabled={consulta.guardando || !consulta.unidades.length}
                  onChange={(evento) => consulta.cambiarUnidad(evento.target.value)}>
                  {!consulta.unidades.length && <option value="">Sin áreas registradas</option>}
                  {consulta.unidades.map((unidad) => <option key={unidad.id} value={unidad.id}>
                    {unidad.codigo} · {unidad.nombre}{unidad.estado === 'Inactivo' ? ' (Inactivo)' : ''}
                  </option>)}
                </select>
              </CampoFormulario>
              <CampoFormulario etiqueta="Mes" error={consulta.errores.periodo
                || (!periodoValido(consulta.periodo) ? 'Selecciona un mes válido.' : '')}>
                <input type="month" min="0001-01" max="9999-12" value={consulta.periodo}
                  disabled={consulta.guardando} onChange={(evento) => consulta.cambiarPeriodo(evento.target.value)} />
              </CampoFormulario>
            </div>
          </div>
          <div className="formulario__acciones">
            <button className="boton boton--fantasma" type="button" onClick={consulta.recargar}
              disabled={consulta.guardando || consulta.consultando}>Recargar</button>
            <button className="boton boton--primario" type="button" onClick={consulta.guardarPlan}
              disabled={!consulta.puedeCrear}><Plus size={17} aria-hidden="true" />
              {consulta.guardando ? 'Guardando…' : 'Crear borrador mensual'}
            </button>
          </div>
          <AvisoCatalogo cargando={consulta.consultando} error={error} mensaje={consulta.mensaje}
            reintentar={consulta.recargar} />
          {consulta.unidad?.estado === 'Inactivo' && <p className="nota-programacion">
            Esta área está inactiva. Puedes consultar su historial.</p>}
          {consulta.hayBorrador && <p className="nota-programacion">
            Este mes ya tiene una versión operativa. Consulta sus datos en la tabla.</p>}
          {!consulta.consultando && !consulta.errorPlanes && periodoValido(consulta.periodo) && (consulta.planes.length ?
            <div className="tabla-responsive"><table className="tabla">
              <thead><tr><th>Versión</th><th>Periodo completo</th><th>Estado</th><th>Zona horaria</th></tr></thead>
              <tbody>{consulta.planes.map((plan) => <tr key={plan.id}>
                <td><strong>Versión {plan.version}</strong></td>
                <td>{mostrarFecha(plan.fechaInicio)} al {mostrarFecha(plan.fechaFin)}</td>
                <td><Estado valor={plan.estado} /></td><td>{plan.zonaHoraria}</td>
              </tr>)}</tbody>
            </table></div> : <div className="sin-resultados"><CalendarDays size={32} aria-hidden="true" />
              <strong>{consulta.unidades.length ? 'No hay versiones para este mes' : 'Aún no hay áreas registradas'}</strong>
              <p>{consulta.unidades.length ? 'Selecciona un área activa y crea el borrador mensual.'
                : 'Registra un departamento o servicio para comenzar.'}</p>
            </div>)}
        </section>
      </>}
    </>}
  </>;
}

export default PlanesMensuales;
