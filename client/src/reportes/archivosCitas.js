import { columnasReporteCitas, datosReporteCita } from '../utils/reportesCitas.js';

const ETIQUETAS = columnasReporteCitas.map(({ etiqueta }) => etiqueta);
const MARGEN = 12;

function metadatos(reporte) {
  const emision = new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'short', timeStyle: 'short', timeZone: reporte.zonaHoraria,
  }).format(reporte.emitido);
  return [
    reporte.institucion,
    'Citas del mes · ' + reporte.mes,
    reporte.subtitulo,
    reporte.citas.length + (reporte.citas.length === 1 ? ' cita' : ' citas')
      + ' · ' + reporte.zonaHoraria + ' · Exportado el ' + emision,
  ];
}

export async function generarArchivoCitas(reporte, formato) {
  if (formato === 'pdf') return crearPdfCitas(reporte);
  if (formato === 'xlsx') return crearExcelCitas(reporte);
  throw new Error('Selecciona PDF o Excel.');
}

async function crearPdfCitas(reporte) {
  const [{ jsPDF: DocumentoPdf }, { autoTable: dibujarTabla }] = await Promise.all([
    import('jspdf'), import('jspdf-autotable'),
  ]);
  const documento = new DocumentoPdf({ orientation: 'landscape', unit: 'mm', format: 'a4', compress: true });
  documento.setProperties({ title: 'Citas del mes · ' + reporte.mes, author: reporte.institucion, creator: 'MediShift' });
  const cabecera = metadatos(reporte).map((texto, indice) => {
    const tamano = indice === 1 ? 16 : 9;
    const fuente = indice === 1 ? 'bold' : 'normal';
    documento.setFont('helvetica', fuente);
    documento.setFontSize(tamano);
    const interlineado = [7, 8, 4.5, 4.5][indice];
    return { lineas: documento.splitTextToSize(texto, 273), tamano, fuente, interlineado };
  });
  const limite = MARGEN + cabecera.reduce((suma, texto) => suma + texto.lineas.length * texto.interlineado, 0) + 4;
  dibujarTabla(documento, {
    startY: limite,
    margin: { top: limite, bottom: 17, left: MARGEN, right: MARGEN },
    theme: 'grid',
    head: [ETIQUETAS],
    body: reporte.citas.map((cita) => [mostrarFecha(cita.fecha), cita.horaInicio,
      cita.fecha === cita.fechaFin ? cita.horaFin : mostrarFecha(cita.fechaFin) + ' ' + cita.horaFin, ...datosReporteCita(cita)]),
    styles: { fontSize: 9, cellPadding: 2, overflow: 'linebreak', valign: 'middle' },
    headStyles: { fillColor: [232, 240, 239], textColor: [24, 42, 40] },
    rowPageBreak: 'avoid',
    willDrawPage: () => {
      let posicion = MARGEN;
      documento.setTextColor(24, 42, 40);
      for (const { lineas, tamano, fuente, interlineado } of cabecera) {
        documento.setFont('helvetica', fuente);
        documento.setFontSize(tamano);
        for (const linea of lineas) {
          documento.text(linea, MARGEN, posicion);
          posicion += interlineado;
        }
      }
    },
    didDrawPage: ({ pageNumber: pagina }) => {
      documento.setFontSize(8);
      documento.text('MediShift  ·  Página ' + pagina, 297 - MARGEN, 200, { align: 'right' });
    },
  });
  return documento.output('blob');
}

const mostrarFecha = (fecha) => fecha.split('-').reverse().join('/');
const horaExcel = (hora) => (Number(hora.slice(0, 2)) * 60 + Number(hora.slice(3))) / 1440;

async function crearExcelCitas(reporte) {
  const { default: Excel } = await import('exceljs');
  const libro = new Excel.Workbook();
  libro.creator = 'MediShift';
  libro.created = reporte.emitido;
  libro.modified = reporte.emitido;
  const hoja = libro.addWorksheet('Citas', {
    views: [{ state: 'frozen', ySplit: 6 }],
    pageSetup: { orientation: 'landscape', paperSize: 9, fitToPage: true, fitToWidth: 1, fitToHeight: 0, printTitlesRow: '1:6' },
  });
  hoja.columns = columnasReporteCitas.map(({ ancho }) => ({ width: ancho }));
  hoja.headerFooter.oddFooter = '&LMediShift&C Página &P de &N';
  metadatos(reporte).forEach((texto, indice) => {
    const numero = indice + 1;
    hoja.mergeCells(numero, 1, numero, ETIQUETAS.length);
    const celda = hoja.getCell(numero, 1);
    celda.value = texto;
    celda.font = { bold: numero <= 2, size: numero === 2 ? 16 : 11 };
    celda.alignment = { vertical: 'middle', wrapText: true };
    hoja.getRow(numero).height = Math.max(numero === 2 ? 27 : 20, Math.ceil(texto.length / 135) * 16);
  });
  const encabezado = hoja.getRow(6);
  encabezado.values = ETIQUETAS;
  encabezado.height = 23;
  encabezado.eachCell((celda) => {
    celda.font = { bold: true, color: { argb: 'FF182A28' } };
    celda.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFE8F0EF' } };
    celda.alignment = { vertical: 'middle' };
  });
  for (const cita of reporte.citas) {
    const mismoDia = cita.fecha === cita.fechaFin;
    const fin = mismoDia ? horaExcel(cita.horaFin) : new Date(cita.fechaFin + 'T' + cita.horaFin + ':00Z');
    const detalle = datosReporteCita(cita);
    const fila = hoja.addRow([new Date(cita.fecha + 'T00:00:00Z'), horaExcel(cita.horaInicio), fin, ...detalle]);
    fila.getCell(1).numFmt = 'dd/mm/yyyy';
    fila.getCell(2).numFmt = 'hh:mm';
    fila.getCell(3).numFmt = mismoDia ? 'hh:mm' : 'dd/mm/yyyy hh:mm';
    fila.alignment = { vertical: 'middle', wrapText: true };
    fila.height = Math.max(20, ...detalle.map((texto, indice) =>
      Math.ceil(texto.length / (columnasReporteCitas[indice + 3].ancho - 4)) * 14 + 4));
  }
  hoja.autoFilter = { from: 'A6', to: 'H' + hoja.rowCount };
  return new Blob([await libro.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
}
