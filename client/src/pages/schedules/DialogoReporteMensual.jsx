import { useMemo, useRef, useState } from 'react';
import { FileDown, FileSpreadsheet, FileText, FolderOpen } from 'lucide-react';
import { construirReporteMensual, duracionReporte, nombreArchivoReporte } from '../../utils/reportesMensuales';
import { guardarArchivoReporte, permiteElegirArchivo } from '../../services/archivoReporte';
import VentanaPlanificador from './VentanaPlanificador';

export default function DialogoReporteMensual({ datos, periodo, idConsultorio, idProfesional, institucion, cerrar }) {
  const [alcance, setAlcance] = useState(idProfesional ? 'profesional' : idConsultorio ? 'consultorio' : 'mes');
  const [profesional, setProfesional] = useState(idProfesional || '');
  const [formato, setFormato] = useState('pdf');
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [usarDescarga, setUsarDescarga] = useState(false);
  const bloqueo = useRef(false);
  const selectorDisponible = permiteElegirArchivo();
  const consulta = useMemo(() => {
    try { return { reporte: construirReporteMensual(datos, { periodo, alcance, idConsultorio,
      idProfesional: profesional, institucion }), error: '' }; }
    catch (fallo) { return { reporte: null, error: fallo.message }; }
  }, [datos, periodo, alcance, idConsultorio, profesional, institucion]);
  const reporte = consulta.reporte;
  const cambiar = (actualizar, valor) => { actualizar(valor); setError(''); setMensaje(''); };
  const exportar = async (evento) => {
    evento.preventDefault();
    if (!reporte || bloqueo.current) return;
    bloqueo.current = true;
    setGuardando(true);
    setError('');
    setMensaje('');
    try {
      // El selector se abre en este clic, antes de los imports y de generar el reporte.
      const resultado = await guardarArchivoReporte({ ...reporte, emitido: new Date() }, formato, { descargar: usarDescarga });
      setMensaje(resultado.estado === 'cancelado' ? 'Se canceló la exportación.' : resultado.estado === 'guardado'
        ? `Archivo guardado: ${resultado.nombre}` : `Descarga iniciada: ${resultado.nombre}. Revisa las descargas de tu navegador.`);
    } catch (fallo) { setError(fallo.message || 'No se pudo exportar el horario. Vuelve a intentarlo.'); }
    finally { bloqueo.current = false; setGuardando(false); }
  };

  return <VentanaPlanificador titulo="Exportar horario mensual"
    descripcion="Comparte el calendario o prepara la ficha individual de un profesional para imprimirla."
    ocupado={guardando} cerrar={cerrar}>
    <form className="formulario-planificador reporte-mensual" onSubmit={exportar}>
      <fieldset className="formulario-planificador__campos" disabled={guardando}>
        <label className="campo-planificador"><span>Qué deseas exportar</span>
          <select value={alcance} onChange={(evento) => cambiar(setAlcance, evento.target.value)}>
            <option value="consultorio" disabled={!idConsultorio}>Consultorio visible en el calendario</option>
            <option value="mes">Todo el mes, todas las áreas y consultorios</option>
            <option value="profesional">Horario de un solo profesional</option>
          </select>
        </label>
        {alcance === 'profesional' && <label className="campo-planificador"><span>Profesional</span>
          <select required value={profesional} onChange={(evento) => cambiar(setProfesional, evento.target.value)}>
            <option value="">Selecciona un profesional</option>
            {datos.profesionales.map((registro) => <option key={registro.id} value={registro.id}>
              {registro.nombre} · {registro.colegiatura || 'Sin colegiatura'}{registro.estado === 'Inactivo' ? ' (Inactivo)' : ''}
            </option>)}
          </select>
          <small className="reporte-mensual__ayuda">Incluye todos sus turnos del mes en todas las áreas y consultorios.</small>
        </label>}
        <fieldset className="reporte-mensual__formatos"><legend>Formato del archivo</legend>
          <label><input type="radio" name="formato-reporte" value="pdf" checked={formato === 'pdf'} onChange={() => cambiar(setFormato, 'pdf')} />
            <FileText size={21} aria-hidden="true" /><span><strong>PDF</strong><small>Para compartir e imprimir</small></span></label>
          <label><input type="radio" name="formato-reporte" value="xlsx" checked={formato === 'xlsx'} onChange={() => cambiar(setFormato, 'xlsx')} />
            <FileSpreadsheet size={21} aria-hidden="true" /><span><strong>Excel</strong><small>Datos y totales editables</small></span></label>
        </fieldset>
      </fieldset>
      {consulta.error && <p className="planificador-ayuda" role="status">{consulta.error}</p>}
      {reporte && <div className="reporte-mensual__resumen" aria-label="Resumen del reporte">
        <strong>{reporte.mes}</strong><p>{reporte.subtitulo}</p>
        {reporte.profesional && <p>Colegiatura: <strong>{reporte.profesional.colegiatura || 'Sin registrar'}</strong></p>}
        <dl><div><dt>Turnos</dt><dd>{reporte.turnos.length}</dd></div>
          <div><dt>Días con turnos</dt><dd>{reporte.diasTrabajados}</dd></div>
          <div><dt>Horas asignadas</dt><dd>{duracionReporte(reporte.minutos)}</dd></div></dl>
        <p>Estados: {reporte.estados.join(', ') || 'Sin turnos'}.</p>
        {!reporte.turnos.length && <p>El archivo indicará que no hay turnos en esta selección.</p>}
        <small>{reporte.profesional ? 'Matriz de días y franjas horarias, con detalle de turnos.'
          : formato === 'pdf' ? 'Calendario mensual y detalle completo de turnos.' : 'Detalle de turnos con fechas, horas y totales.'}</small>
        <p className="reporte-mensual__archivo">{nombreArchivoReporte(reporte, formato)}</p>
      </div>}
      {selectorDisponible && <label className="reporte-mensual__descarga"><input type="checkbox" checked={usarDescarga} disabled={guardando}
        onChange={(evento) => cambiar(setUsarDescarga, evento.target.checked)} />Usar la descarga del navegador</label>}
      <p className="reporte-mensual__destino"><FolderOpen size={17} aria-hidden="true" />
        <span>{selectorDisponible && !usarDescarga ? 'Al guardar podrás elegir la carpeta y el nombre del archivo.'
          : 'Este navegador usa su carpeta de descargas. Para elegir dónde guardar, activa «Preguntar dónde guardar cada archivo» en su configuración de descargas o abre MediShift en Chrome o Edge de escritorio.'}</span></p>
      <p className="planificador-ayuda">Se exportan los datos cargados en el calendario. Para incluir cambios recientes de otros usuarios, cierra esta ventana y pulsa Actualizar.</p>
      {error && <p className="planificador-error" role="alert">{error}</p>}
      {mensaje && <p className="planificador-exito" role="status">{mensaje}</p>}
      {guardando && <p className="planificador-ayuda" role="status">{selectorDisponible && !usarDescarga
        ? 'Elige dónde guardar y espera a que termine la exportación…' : 'Preparando el archivo para descargar…'}</p>}
      <div className="ventana-planificador__acciones"><button className="boton boton--fantasma" type="button" disabled={guardando} onClick={cerrar}>Cerrar</button>
        <button className="boton boton--primario" type="submit" disabled={guardando || !reporte}>
          <FileDown size={17} aria-hidden="true" />{guardando ? 'Exportando…' : selectorDisponible && !usarDescarga
            ? `Guardar ${formato === 'pdf' ? 'PDF' : 'Excel'} como…` : `Descargar ${formato === 'pdf' ? 'PDF' : 'Excel'}`}</button></div>
    </form>
  </VentanaPlanificador>;
}
