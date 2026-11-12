import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2, FileDown, Info, LogOut, Sparkles } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import Estado from '../../components/Estado';
import { CalendarioMensualCitas } from '../appointments/CalendarioCitas';
import { useConsultaAtencion, useOperacionAtencion } from '../../hooks/useAtencion';
import { guardarArchivoReporte } from '../../services/archivoReporte';
import { cerrarSesionPaciente, obtenerCalendarioPaciente } from '../../services/portalPaciente';
import { etiquetasEstadoCita, partesLocales } from '../../utils/validacionesAtencion';
import { fechaActual, mostrarFecha } from '../../utils/validacionesProgramacion';

const ZONA_LOCAL = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Lima';

function ordenarCitas(datos) {
  if (!datos) return [];
  const hospitales = new Map(datos.hospitales.map((hospital) => [hospital.codigo, hospital]));
  return datos.citas.map((cita) => {
    const hospital = hospitales.get(cita.codigoHospital);
    const inicio = partesLocales(cita.inicio, hospital.zonaHoraria);
    const fin = partesLocales(cita.fin, hospital.zonaHoraria);
    return { ...cita, hospital, fecha: inicio.fecha, horaInicio: inicio.hora, horaFin: fin.hora, fechaFin: fin.fecha };
  }).sort((primera, segunda) => primera.fecha.localeCompare(segunda.fecha) || primera.horaInicio.localeCompare(segunda.horaInicio));
}

function DetalleCita({ cita }) {
  return <li className="portal-cita">
    <div className="portal-cita__cabecera">
      <div>
        <h3><time dateTime={cita.fecha}>{mostrarFecha(cita.fecha)}</time></h3>
        <p><time dateTime={cita.inicio}>{cita.horaInicio}</time> a <time dateTime={cita.fin}>
          {cita.fechaFin !== cita.fecha && mostrarFecha(cita.fechaFin) + ' '}{cita.horaFin}</time></p>
      </div>
      <Estado valor={etiquetasEstadoCita[cita.estado]} />
    </div>
    <dl>
      <div><dt>Hospital</dt><dd>{cita.hospital.nombre}</dd></div>
      <div><dt>Consultorio</dt><dd>{cita.consultorio}<small>{cita.ubicacion}</small></dd></div>
      <div><dt>Médico</dt><dd>{cita.profesional}</dd></div>
    </dl>
  </li>;
}

export default function MisCitas() {
  const navegar = useNavigate();
  const hoy = fechaActual(ZONA_LOCAL);
  const [periodo, cambiarPeriodo] = useState(() => hoy.slice(0, 7));
  const [fecha, cambiarFecha] = useState('');
  const [cerrando, cambiarCerrando] = useState(false);
  const [errorSesion, cambiarErrorSesion] = useState('');
  const [mensajePdf, cambiarMensajePdf] = useState('');
  const descarga = useOperacionAtencion({ rutaSesion: '/acceso-paciente' });
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerCalendarioPaciente(periodo, opciones), [periodo]), {
    rutaSesion: '/acceso-paciente', mantenerDatos: true,
  });
  const datos = consulta.datos?.periodo === periodo && !consulta.cargando ? consulta.datos : null;
  const citas = useMemo(() => ordenarCitas(datos), [datos]);
  const citasPorDia = useMemo(() => {
    const dias = new Map();
    for (const cita of citas) dias.set(cita.fecha, (dias.get(cita.fecha) || 0) + 1);
    return dias;
  }, [citas]);
  const visibles = fecha ? citas.filter((cita) => cita.fecha === fecha) : citas;

  const descargarPdf = async () => {
    cambiarMensajePdf('');
    const resultado = await descarga.ejecutar(async () => {
      const mesVigente = await obtenerCalendarioPaciente(periodo);
      if (!mesVigente.citas.length) {
        consulta.recargar();
        throw new Error('No hay citas para exportar en este mes.');
      }
      return guardarArchivoReporte({ periodo, citas: ordenarCitas(mesVigente) }, 'pdf', {
        descargar: true,
        nombrar: (reporte) => 'mis-citas-' + reporte.periodo + '.pdf',
        generar: async (reporte) => (await import('../../reportes/archivosCitas.js')).crearPdfCitasPaciente(reporte),
      });
    });
    if (resultado) cambiarMensajePdf('La descarga del PDF comenzó.');
  };

  const cambiarMes = (mes) => {
    cambiarFecha('');
    cambiarPeriodo(mes);
    cambiarMensajePdf('');
    descarga.limpiar();
  };

  const cerrar = async () => {
    cambiarCerrando(true);
    cambiarErrorSesion('');
    try {
      await cerrarSesionPaciente();
      navegar('/acceso-paciente', { replace: true });
    } catch (fallo) {
      cambiarErrorSesion(fallo.message);
      cambiarCerrando(false);
    }
  };

  return <div className="portal-paciente">
    <header className="registro__encabezado">
      <Link className="portada-marca" to="/mis-citas" aria-label="MediShift, mis citas">
        <span className="portada-marca__simbolo"><Sparkles size={20} aria-hidden="true" /></span>
        <strong>MediShift</strong>
      </Link>
      {consulta.datos && <button className="boton boton--fantasma" type="button" disabled={cerrando || descarga.guardando} onClick={cerrar}>
        <LogOut size={17} aria-hidden="true" /> {cerrando ? 'Cerrando…' : 'Cerrar sesión'}
      </button>}
    </header>
    <main>
      <EncabezadoPagina titulo="Mis citas" descripcion="Revisa las fechas y el lugar de atención de tus citas."
        acciones={consulta.datos && <button className="boton boton--secundario" type="button"
          disabled={!datos?.citas.length || Boolean(consulta.error) || cerrando || descarga.guardando} onClick={descargarPdf}>
          <FileDown size={17} aria-hidden="true" /> {descarga.guardando ? 'Preparando PDF…' : 'Descargar PDF del mes'}
        </button>} />
      {errorSesion && <p className="atencion-error" role="alert">{errorSesion}</p>}
      {descarga.error && <p className="atencion-error" role="alert">{descarga.error}</p>}
      {mensajePdf && <p className="atencion-ayuda" role="status">{mensajePdf}</p>}
      {consulta.error && <div className="atencion-aviso" role="alert"><p>{consulta.error}</p>
        <button className="boton boton--secundario" type="button" onClick={consulta.recargar}>Reintentar</button>
      </div>}
      {consulta.cargando && <p className="atencion-ayuda" role="status">Cargando tus citas…</p>}
      {consulta.datos && <>
        <section className="panel portal-hospitales">
          <h2><Building2 size={18} aria-hidden="true" /> Hospitales donde estás registrado</h2>
          <ul>{consulta.datos.hospitales.map((hospital) => <li key={hospital.codigo}>{hospital.nombre}</li>)}</ul>
          <p className="portal-aviso"><Info size={16} aria-hidden="true" />
            <span>Si una cita no aparece, puede que el DNI o la fecha de nacimiento de tu ficha no coincidan.
              Pide al hospital que revise esos datos.</span>
          </p>
        </section>
        <div className="agenda-distribucion portal-calendario">
          <CalendarioMensualCitas periodo={periodo} fecha={fecha} hoy={hoy} cargando={!datos && !consulta.error}
            ocupado={descarga.guardando}
            resumirDia={(dia) => ({ citas: citasPorDia.get(dia) || 0 })}
            seleccionar={(dia) => cambiarFecha(dia === fecha ? '' : dia)}
            cambiarMes={cambiarMes} />
          <section className="panel portal-citas" aria-labelledby="titulo-citas-portal">
            <div className="portal-citas__cabecera">
              <h2 id="titulo-citas-portal" aria-live="polite">{fecha ? mostrarFecha(fecha) : 'Citas del mes'}</h2>
              {fecha && <button className="boton boton--fantasma" type="button" onClick={() => cambiarFecha('')}>Ver todo el mes</button>}
            </div>
            {datos && (visibles.length ? <><p className="portal-citas__zona">Horarios en la hora local de cada hospital.</p>
              <ul>{visibles.map((cita) => <DetalleCita key={cita.id} cita={cita} />)}</ul></>
              : <p className="portal-citas__zona" role="status">{fecha ? 'No tienes citas ese día.' : 'No tienes citas en este mes.'}</p>)}
          </section>
        </div>
      </>}
    </main>
  </div>;
}
