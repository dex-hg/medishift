import { useId, useMemo, useRef, useState } from 'react';
import { FileDown, FileSpreadsheet, FileText } from 'lucide-react';
import { guardarArchivoReporte, permiteElegirArchivo } from '../../services/archivoReporte';
import { construirReporteCitas, filtrosReporteCitas, nombreArchivoCitas, opcionesReporteCitas,
  prepararCitasDelMes } from '../../utils/reportesCitas';
import VentanaPlanificador from '../schedules/VentanaPlanificador';

export default function DialogoReporteCitas({ datos, institucion, idProfesional, cerrar }) {
  const identificador = useId();
  const citas = useMemo(() => prepararCitasDelMes(datos), [datos]);
  const opciones = useMemo(() => opcionesReporteCitas(citas), [citas]);
  const [filtros, cambiarFiltros] = useState(() => ({
    profesional: opciones.profesional.some(([id]) => id === idProfesional) ? idProfesional : '',
  }));
  const [formato, cambiarFormato] = useState('pdf');
  const [descargar, cambiarDescarga] = useState(false);
  const [guardando, cambiarGuardando] = useState(false);
  const [error, cambiarError] = useState('');
  const [mensaje, cambiarMensaje] = useState('');
  const bloqueo = useRef(false);
  const selectorDisponible = permiteElegirArchivo();
  const guardarComo = selectorDisponible && !descargar;
  const reporte = construirReporteCitas(datos, citas, opciones, filtros, institucion);
  const etiquetaFormato = formato === 'pdf' ? 'PDF' : 'Excel';

  const limpiarResultado = () => { cambiarError(''); cambiarMensaje(''); };
  const exportar = async (evento) => {
    evento.preventDefault();
    if (!reporte.citas.length || bloqueo.current) return;
    bloqueo.current = true;
    cambiarGuardando(true);
    limpiarResultado();
    try {
      const resultado = await guardarArchivoReporte({ ...reporte, emitido: new Date() }, formato, {
        descargar, nombrar: nombreArchivoCitas,
        generar: async (datosReporte, extension) =>
          (await import('../../reportes/archivosCitas.js')).generarArchivoCitas(datosReporte, extension),
      });
      if (resultado.estado === 'cancelado') cambiarMensaje('Se canceló la exportación.');
      if (resultado.estado === 'guardado') cambiarMensaje('Archivo guardado · ' + resultado.nombre);
      if (resultado.estado === 'descargado') cambiarMensaje('Descarga iniciada · ' + resultado.nombre);
    } catch (fallo) {
      cambiarError(fallo.message || 'No se pudo guardar el reporte de citas.');
    } finally {
      bloqueo.current = false;
      cambiarGuardando(false);
    }
  };

  return <VentanaPlanificador titulo="Exportar citas" descripcion={reporte.mes} ocupado={guardando} cerrar={cerrar}>
    <form className="formulario-planificador reporte-mensual" onSubmit={exportar}>
      <fieldset className="formulario-planificador__campos" disabled={guardando}>
        {filtrosReporteCitas.map(({ clave, etiqueta, general }) => <label className="campo-planificador" key={clave}>
          <span id={identificador + '-' + clave}>{etiqueta}</span>
          <select aria-labelledby={identificador + '-' + clave} value={filtros[clave] || ''} onChange={(evento) => {
            cambiarFiltros({ ...filtros, [clave]: evento.target.value });
            limpiarResultado();
          }}>
            <option value="">{general}</option>
            {opciones[clave].map(([id, nombre]) => <option key={id} value={id}>{nombre}</option>)}
          </select>
        </label>)}
        <fieldset className="reporte-mensual__formatos"><legend>Formato del archivo</legend>
          {[['pdf', 'PDF', FileText], ['xlsx', 'Excel', FileSpreadsheet]].map(([extension, etiqueta, Icono]) =>
            <label key={extension}><input type="radio" name="formato-citas" value={extension}
              checked={formato === extension} onChange={() => { cambiarFormato(extension); limpiarResultado(); }} />
              <Icono size={21} aria-hidden="true" /><span><strong>{etiqueta}</strong></span></label>)}
        </fieldset>
      </fieldset>
      <div className="reporte-mensual__resumen" aria-live="polite">
        <strong>{reporte.citas.length} {reporte.citas.length === 1 ? 'cita' : 'citas'}</strong>
        <p>{reporte.citas.length ? reporte.subtitulo : 'No hay citas que coincidan con estos filtros.'}</p>
      </div>
      {selectorDisponible && <label className="reporte-mensual__descarga">
        <input type="checkbox" checked={descargar} disabled={guardando} onChange={(evento) => {
          cambiarDescarga(evento.target.checked); limpiarResultado();
        }} />Usar la descarga del navegador
      </label>}
      {error && <p className="planificador-error" role="alert">{error}</p>}
      {mensaje && <p className="planificador-exito" role="status">{mensaje}</p>}
      <div className="ventana-planificador__acciones">
        <button className="boton boton--fantasma" type="button" disabled={guardando} onClick={cerrar}>Cerrar</button>
        <button className="boton boton--primario" type="submit" disabled={guardando || !reporte.citas.length}>
          <FileDown size={17} aria-hidden="true" />
          {guardando ? 'Exportando…' : guardarComo ? 'Guardar ' + etiquetaFormato + ' como…' : 'Descargar ' + etiquetaFormato}
        </button>
      </div>
    </form>
  </VentanaPlanificador>;
}
