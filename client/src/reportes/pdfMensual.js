import { jsPDF as DocumentoPdf } from 'jspdf';
import { autoTable as dibujarTabla } from 'jspdf-autotable';
import { celdasDelMes } from '../utils/calendarioMensual.js';
import { DIAS_SEMANA } from '../utils/validacionesProgramacion.js';
import { duracionReporte, horasReporte, nombreDia, resumenReporte } from '../utils/reportesMensuales.js';

const MARGEN = 12;
const ANCHO = 273;
const GRIS = [237, 241, 243];

/** PDF vectorial A4 horizontal, con cabeceras repetidas y detalle sin recortes. */
export function crearPdfMensual(reporte) {
  const documento = new DocumentoPdf({ orientation: 'landscape', unit: 'mm', format: 'a4', compress: true });
  documento.setProperties({ title: `${reporte.titulo} - ${reporte.mes}`, author: reporte.institucion,
    subject: reporte.subtitulo, creator: 'MediShift' });
  const cabeceras = new Set();
  const subtitulo = `${reporte.profesional ? 'Profesional: ' : ''}${reporte.subtitulo}`;
  documento.setFontSize(10);
  const lineasSubtitulo = documento.splitTextToSize(subtitulo, ANCHO);
  documento.setFontSize(8);
  const lineasResumen = documento.splitTextToSize(resumenReporte(reporte), ANCHO);
  const inicio = 43 + lineasSubtitulo.length * 4 + (reporte.profesional ? 5 : 0) + (lineasResumen.length - 1) * 4;

  const encabezado = () => {
    const pagina = documento.getCurrentPageInfo().pageNumber;
    if (cabeceras.has(pagina)) return;
    cabeceras.add(pagina);
    documento.setTextColor(25);
    documento.setFont('helvetica', 'bold');
    documento.setFontSize(10);
    documento.text(documento.splitTextToSize(reporte.institucion, 224), MARGEN, 12);
    documento.text('MediShift', 285, 12, { align: 'right' });
    documento.setFontSize(16);
    documento.text(reporte.titulo, MARGEN, 25);
    documento.setFontSize(11);
    documento.text(reporte.mes, 285, 25, { align: 'right' });
    documento.setFont('helvetica', 'normal');
    documento.setFontSize(10);
    documento.text(lineasSubtitulo, MARGEN, 33);
    let posicion = 33 + lineasSubtitulo.length * 4;
    if (reporte.profesional) {
      documento.text(`Colegiatura: ${reporte.profesional.colegiatura || 'Sin registrar'}`, MARGEN, posicion);
      posicion += 5;
    }
    documento.setFontSize(8);
    documento.text(lineasResumen, MARGEN, posicion);
    documento.text(`Estados: ${reporte.estados.join(', ') || 'Sin turnos'}. Zona horaria: ${reporte.zonaHoraria}.`, MARGEN, posicion + lineasResumen.length * 4);
  };
  const nuevaPagina = () => { documento.addPage(); encabezado(); };
  const tabla = (opciones) => {
    dibujarTabla(documento, { theme: 'grid', margin: { top: inicio, left: MARGEN, right: MARGEN, bottom: 22 },
      styles: { font: 'helvetica', fontSize: 8, textColor: 25, lineColor: 195, lineWidth: 0.15,
        cellPadding: 2, overflow: 'linebreak', valign: 'middle' },
      headStyles: { fillColor: GRIS, textColor: 25, fontStyle: 'bold' },
      rowPageBreak: 'avoid', willDrawPage: encabezado, ...opciones });
    return documento.lastAutoTable.finalY;
  };
  encabezado();

  if (reporte.profesional) {
    const anchoDia = (ANCHO - 29 - 15) / reporte.dias.length;
    const cuerpo = reporte.matriz.map((fila) => [fila.franja,
      ...fila.minutos.map((minutos, dia) => minutos ? horasReporte(minutos) : fila.feriados?.[dia] ? 'H' : fila.faltas[dia] ? 'F' : ''), horasReporte(fila.total)]);
    if (!cuerpo.length) cuerpo.push(['Sin turnos', ...reporte.dias.map(() => ''), '0']);
    cuerpo.push(['Total (h)', ...reporte.totalesDiarios.map((minutos) => minutos ? horasReporte(minutos) : ''), horasReporte(reporte.minutos)]);
    const fin = tabla({ startY: inicio, head: [['Franja horaria', ...reporte.dias.map((dia) => `${nombreDia(dia, true).slice(0, 2)}\n${Number(dia.slice(-2))}`), 'Total\n(h)']],
      body: cuerpo, styles: { fontSize: 7.2, cellPadding: 1.2, halign: 'center', textColor: 25, lineWidth: 0.15, lineColor: 195 },
      columnStyles: { 0: { cellWidth: 29, halign: 'left' },
        ...Object.fromEntries(reporte.dias.map((dia, indice) => [indice + 1, { cellWidth: anchoDia }])),
        [reporte.dias.length + 1]: { cellWidth: 15, fontStyle: 'bold' } },
      didParseCell: (celda) => {
        if (celda.section === 'body' && celda.row.index === cuerpo.length - 1) {
          celda.cell.styles.fillColor = GRIS;
          celda.cell.styles.fontStyle = 'bold';
        }
      } });
    if (fin > 171) nuevaPagina();
    documento.setFontSize(8);
    documento.text('H: turno en feriado, reemplazado por abono institucional. F: falta. Blanco: sin turno. Horas decimales; 1.5 h = 1 h 30 min.', MARGEN, fin > 171 ? inicio : fin + 6);
  } else {
    const celdas = celdasDelMes(reporte.periodo);
    const semanas = Array.from({ length: celdas.length / 7 }, (_, indice) => celdas.slice(indice * 7, indice * 7 + 7));
    const cuerpo = semanas.map((semana) => semana.map((dia) => {
      if (!dia) return '';
      const turnos = reporte.turnos.filter((turno) => turno.fecha === dia);
      // El calendario orienta; el detalle posterior siempre incluye todos los turnos.
      const visibles = turnos.slice(0, 2).map((turno) => `${turno.horaInicio}-${turno.horaFin}\n${turno.profesional}${turno.asistencia === 'Falta' ? '\nFALTA (0 h)' : ''}`);
      const feriado = reporte.feriados.dias.find((registro) => registro.fecha === dia);
      return `${Number(dia.slice(-2))}${feriado ? '\nFERIADO NO LABORABLE' : ''}\n${visibles.join('\n')}${turnos.length > 2 ? `\n+${turnos.length - 2} turnos (ver detalle)` : ''}`;
    }));
    tabla({ startY: inicio, head: [DIAS_SEMANA], body: cuerpo,
      columnStyles: Object.fromEntries(DIAS_SEMANA.map((dia, indice) => [indice, { cellWidth: ANCHO / 7 }])),
      styles: { fontSize: 8, minCellHeight: 18, cellPadding: 2, textColor: 25, lineColor: 195, lineWidth: 0.15 },
      headStyles: { fillColor: GRIS, textColor: 25, fontStyle: 'bold', minCellHeight: 8 } });
  }

  if (reporte.turnos.length) {
    nuevaPagina();
    documento.setFont('helvetica', 'bold');
    documento.setFontSize(11);
    documento.text('Detalle de turnos', MARGEN, inicio);
    const columnas = reporte.profesional
      ? ['Fecha y día', 'Inicio', 'Fin', 'Duración', 'Área', 'Consultorio', 'Especialidad', 'Estado', 'Observación']
      : ['Fecha y día', 'Profesional / colegiatura', 'Inicio', 'Fin', 'Duración', 'Área / consultorio', 'Especialidad', 'Estado', 'Observación'];
    const cuerpo = reporte.turnos.map((turno) => {
      const fecha = `${turno.fecha}\n${nombreDia(turno.fecha)}`;
      const estado = `${turno.estado}\n${turno.asistencia}${turno.esFeriado ? '\nFeriado' : ''}`;
      return reporte.profesional
        ? [fecha, turno.horaInicio, turno.horaFin, duracionReporte(turno.minutos), turno.area, turno.consultorio, turno.especialidad, estado, turno.observacion]
        : [fecha, `${turno.profesional}\n${turno.colegiatura || 'Sin registrar'}`, turno.horaInicio, turno.horaFin,
          duracionReporte(turno.minutos), `${turno.area}\n${turno.consultorio}`, turno.especialidad, estado, turno.observacion];
    });
    tabla({ startY: inicio + 4, head: [columnas], body: cuerpo, columnStyles: reporte.profesional
      ? { 0: { cellWidth: 30 }, 1: { cellWidth: 14 }, 2: { cellWidth: 14 }, 3: { cellWidth: 24 },
        4: { cellWidth: 35 }, 5: { cellWidth: 39 }, 6: { cellWidth: 34 }, 7: { cellWidth: 23 }, 8: { cellWidth: 60 } }
      : { 0: { cellWidth: 29 }, 1: { cellWidth: 48 }, 2: { cellWidth: 13 }, 3: { cellWidth: 13 }, 4: { cellWidth: 22 },
        5: { cellWidth: 48 }, 6: { cellWidth: 32 }, 7: { cellWidth: 23 }, 8: { cellWidth: 45 } } });
  } else {
    documento.setFontSize(10);
    documento.text('No hay turnos para la selección en este mes.', MARGEN, documento.lastAutoTable.finalY + 12);
  }
  if (reporte.abonos.length) {
    let comienzo = (documento.lastAutoTable?.finalY || inicio) + 12;
    if (comienzo > 155) { nuevaPagina(); comienzo = inicio; }
    documento.setFont('helvetica', 'bold');
    documento.setFontSize(10);
    documento.text(reporte.abonosInformativos ? 'Abonos institucionales por feriado (informativos, fuera del total del consultorio)'
      : 'Abonos institucionales por feriado (incluidos una sola vez en el total)', MARGEN, comienzo);
    tabla({ startY: comienzo + 4, head: [['Fecha', 'Profesional', 'Feriado nacional', 'Horas']],
      body: reporte.abonos.map((abono) => [abono.fecha, abono.profesional, abono.nombre, duracionReporte(abono.minutos)]),
      columnStyles: { 0: { cellWidth: 29 }, 1: { cellWidth: 65 }, 2: { cellWidth: 150 }, 3: { cellWidth: 29 } } });
  }
  const paginas = documento.getNumberOfPages();
  const fechaEmision = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: reporte.zonaHoraria }).format(reporte.emitido);
  for (let pagina = 1; pagina <= paginas; pagina += 1) {
    documento.setPage(pagina);
    documento.setTextColor(75);
    documento.setFont('helvetica', 'normal');
    documento.setFontSize(7.5);
    documento.text(`Feriados: ${reporte.feriados.pais}. Fuente: ${reporte.feriados.fuente}.`, MARGEN, 193);
    documento.text('Fuente: calendario mensual consultado. Los turnos en borrador no están aprobados.', MARGEN, 197);
    documento.text(`Exportado: ${fechaEmision}`, MARGEN, 202);
    documento.text(`Página ${pagina} de ${paginas}`, 285, 202, { align: 'right' });
  }
  return new Blob([documento.output('arraybuffer')], { type: 'application/pdf' });
}
