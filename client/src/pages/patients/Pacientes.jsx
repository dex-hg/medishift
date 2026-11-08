import { useCallback, useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import { Plus, Search } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { useConsultaAtencion, useOperacionAtencion } from '../../hooks/useAtencion';
import { buscarPacienteDni, listarPacientes } from '../../services/atencion';

export default function Pacientes() {
  const { sesion } = useOutletContext();
  const [busqueda, cambiarBusqueda] = useState('');
  const [termino, cambiarTermino] = useState('');
  const [pagina, cambiarPagina] = useState(0);
  const [dni, cambiarDni] = useState('');
  const [encontrados, cambiarEncontrados] = useState(null);
  const consulta = useConsultaAtencion(useCallback((opciones) => listarPacientes(termino, pagina, opciones), [termino, pagina]));
  const operacion = useOperacionAtencion();
  useEffect(() => {
    const temporizador = setTimeout(() => { cambiarTermino(busqueda.trim()); cambiarPagina(0); }, 300);
    return () => clearTimeout(temporizador);
  }, [busqueda]);
  const buscarDni = async (evento) => {
    evento.preventDefault(); cambiarEncontrados(null);
    const resultado = await operacion.ejecutar(() => buscarPacienteDni(dni));
    if (resultado) cambiarEncontrados(resultado);
  };
  const acciones = (paciente) => <div className="acciones-tabla">
    {paciente.puedeConsultar && <Link className="boton boton--fantasma" to={`/pacientes/${paciente.id}`}>Abrir ficha</Link>}
    {paciente.activo && <Link className="boton boton--fantasma" to="/citas" state={{ paciente }}>Agendar cita</Link>}
  </div>;
  return <>
    <EncabezadoPagina ruta={[{ etiqueta: 'Pacientes' }]} titulo={sesion.esAdministrador ? 'Pacientes' : 'Mis pacientes'}
      descripcion="Fichas administrativas y citas registradas en tu institución."
      acciones={<Link className="boton boton--primario" to="/pacientes/nuevo"><Plus size={18} /> Registrar paciente</Link>} />
    <section className="panel atencion-busqueda" aria-labelledby="buscar-dni">
      <h2 id="buscar-dni">Buscar por DNI</h2>
      <p>Comprueba si el paciente ya tiene una ficha antes de registrarlo.</p>
      <form className="atencion-filtros" onSubmit={buscarDni}>
        <label className="campo-planificador"><span>DNI del paciente</span><input inputMode="numeric" pattern="[0-9]{8}" maxLength={8}
          required value={dni} onChange={(evento) => { cambiarDni(evento.target.value); cambiarEncontrados(null); operacion.limpiar(); }} /></label>
        <button className="boton boton--secundario" type="submit" disabled={operacion.guardando || !/^[0-9]{8}$/.test(dni)}>
          <Search size={17} />{operacion.guardando ? 'Buscando…' : 'Buscar paciente'}</button>
      </form>
      {operacion.error && <p className="atencion-error" role="alert">{operacion.error}</p>}
      {encontrados && <div aria-live="polite">{encontrados.length ? encontrados.map((paciente) => <div className="atencion-resultado" key={paciente.id}>
        <div><strong>{paciente.nombres} {paciente.apellidos}</strong><p>{paciente.codigo} · {paciente.activo ? 'Activo' : 'Inactivo'}</p>
          {!paciente.puedeConsultar && <p>Podrás abrir la ficha cuando tenga una cita contigo.</p>}</div>{acciones(paciente)}</div>)
        : <p>No hay una ficha con ese DNI. <Link className="planificador-enlace" to="/pacientes/nuevo" state={{ dni }}>Registrar paciente</Link></p>}</div>}
    </section>
    <section className="panel panel--tabla" aria-busy={consulta.cargando} aria-label="Listado de pacientes">
      <div className="barra-tabla"><label className="buscador"><Search size={17} /><span className="solo-lectores">Buscar en el listado de pacientes</span>
        <input type="search" placeholder="Nombre, DNI o código" value={busqueda} maxLength={100} onChange={(evento) => cambiarBusqueda(evento.target.value)} /></label>
        {consulta.datos && <span className="contador-resultados">{consulta.datos.total} pacientes</span>}</div>
      {consulta.cargando && <p className="atencion-aviso" role="status">Cargando pacientes…</p>}
      {consulta.error && <div className="atencion-aviso" role="alert"><p>{consulta.error}</p><button className="boton boton--secundario" onClick={consulta.recargar}>Reintentar</button></div>}
      {consulta.datos && (consulta.datos.pacientes.length ? <div className="tabla-responsive"><table>
        <thead><tr><th>Paciente</th><th>DNI</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>{consulta.datos.pacientes.map((paciente) => <tr key={paciente.id}>
          <td><span className="dato-doble"><strong>{paciente.nombres} {paciente.apellidos}</strong><small>{paciente.codigo}</small></span></td>
          <td>{paciente.dni}</td><td><Estado valor={paciente.activo ? 'Activo' : 'Inactivo'} /></td><td>{acciones(paciente)}</td></tr>)}</tbody>
      </table></div> : <p className="atencion-aviso">{sesion.esAdministrador ? 'No hay pacientes que coincidan con la búsqueda.' : 'No tienes pacientes que coincidan. Puedes registrar uno o buscarlo por DNI para agendar su primera cita contigo.'}</p>)}
      {consulta.datos && <div className="atencion-paginacion"><button className="boton boton--fantasma" disabled={pagina === 0} onClick={() => cambiarPagina(pagina - 1)}>Anterior</button>
        <span>Página {pagina + 1}</span><button className="boton boton--fantasma" disabled={(pagina + 1) * 30 >= consulta.datos.total} onClick={() => cambiarPagina(pagina + 1)}>Siguiente</button></div>}
    </section>
  </>;
}
