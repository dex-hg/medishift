import Excel from 'exceljs';
import { nombreDia, resumenReporte } from '../utils/reportesMensuales.js';

const BORDE = { style: 'thin', color: { argb: 'FFD0D7DE' } };
const FONDO = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFEDF1F3' } };

function combinar(hoja, fila, texto, ultimaColumna, negrita = false) {
  hoja.mergeCells(fila, 1, fila, ultimaColumna);
  const celda = hoja.getCell(fila, 1);
  celda.value = texto;
  celda.font = { name: 'Calibri', size: fila === 1 ? 15 : 11, bold: negrita, color: { argb: 'FF000000' } };
  celda.alignment = { vertical: 'middle', wrapText: true };
  hoja.getRow(fila).height = fila === 1 ? 30 : Math.max(23, Math.ceil(String(texto).length / 110) * 16);
}

function prepararHoja(libro, nombre, reporte, columnas) {
  const hoja = libro.addWorksheet(nombre, { properties: { defaultRowHeight: 22 },
    pageSetup: { paperSize: 9, orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0,
      margins: { left: 0.3, right: 0.3, top: 0.4, bottom: 0.5, header: 0.2, footer: 0.2 } } });
  hoja.columns = columnas.map((ancho) => ({ width: ancho }));
  hoja.views = [{ state: 'frozen', ySplit: 8, xSplit: nombre === 'Horario individual' ? 1 : 0 }];
  hoja.headerFooter.oddFooter = '&LMediShift&C Página &P de &N';
  combinar(hoja, 1, `${reporte.titulo}: ${reporte.mes}`, columnas.length, true);
  combinar(hoja, 2, reporte.institucion, columnas.length, true);
  combinar(hoja, 3, `${reporte.profesional ? 'Profesional: ' : ''}${reporte.subtitulo}`, columnas.length, true);
  combinar(hoja, 4, reporte.profesional ? `Colegiatura: ${reporte.profesional.colegiatura || 'Sin registrar'}`
    : 'Alcance: turnos operativos del mes seleccionado.', columnas.length);
  combinar(hoja, 5, resumenReporte(reporte), columnas.length);
  combinar(hoja, 6, `Estados: ${reporte.estados.join(', ') || 'Sin turnos'}. Zona horaria: ${reporte.zonaHoraria}.`, columnas.length);
  const emision = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: reporte.zonaHoraria }).format(reporte.emitido);
  combinar(hoja, 7, `Fuente: calendario mensual consultado. Exportado: ${emision}. Los turnos en borrador no están aprobados.`, columnas.length);
  hoja.pageSetup.printTitlesRow = '1:8';
  return hoja;
}

function darFormatoTabla(hoja, primeraFila, ultimaFila, columnas) {
  for (let numero = primeraFila; numero <= ultimaFila; numero += 1) {
    const fila = hoja.getRow(numero);
    for (let columna = 1; columna <= columnas; columna += 1) {
      const celda = fila.getCell(columna);
      celda.font = { name: 'Calibri', size: 10, bold: numero === primeraFila, color: { argb: 'FF000000' } };
      celda.alignment = { vertical: 'middle', wrapText: true };
      celda.border = { top: BORDE, bottom: BORDE, left: BORDE, right: BORDE };
      if (numero === primeraFila) celda.fill = FONDO;
    }
  }
}

function crearMatriz(libro, reporte) {
  const cantidadColumnas = reporte.dias.length + 2;
  const hoja = prepararHoja(libro, 'Horario individual', reporte, [20, ...reporte.dias.map(() => 5), 11]);
  const ultimaLetraDia = hoja.getColumn(cantidadColumnas - 1).letter;
  const ultimaLetra = hoja.getColumn(cantidadColumnas).letter;
  hoja.getRow(8).values = ['Franja horaria', ...reporte.dias.map((dia) => `${nombreDia(dia, true).slice(0, 2)}\n${Number(dia.slice(-2))}`), 'Total (h)'];
  hoja.getRow(8).height = 33;
  const franjas = reporte.matriz.length ? reporte.matriz : [{ franja: 'Sin turnos', minutos: reporte.dias.map(() => 0), total: 0 }];
  for (let indice = 0; indice < franjas.length; indice += 1) {
    const franja = franjas[indice];
    const numero = indice + 9;
    const fila = hoja.getRow(numero);
    fila.values = [franja.franja, ...franja.minutos.map((minutos, dia) => minutos ? minutos / 60
      : franja.feriados?.[dia] ? 'H' : franja.faltas?.[dia] ? 'F' : null)];
    for (let dia = 0; dia < reporte.dias.length; dia += 1) {
      if (franja.faltas?.[dia]) fila.getCell(dia + 2).note = `${franja.faltas[dia]} falta(s). Sus horas no se suman. Consulta el detalle de turnos.`;
    }
    fila.getCell(cantidadColumnas).value = { formula: `SUM(B${numero}:${ultimaLetraDia}${numero})`, result: franja.total / 60 };
    fila.height = 25;
  }
  const filaTotal = 9 + franjas.length;
  hoja.getCell(filaTotal, 1).value = 'Total (h)';
  for (let columna = 2; columna <= cantidadColumnas; columna += 1) {
    const letra = hoja.getColumn(columna).letter;
    const minutos = columna === cantidadColumnas ? reporte.minutos : reporte.totalesDiarios[columna - 2];
    hoja.getCell(filaTotal, columna).value = { formula: `SUM(${letra}9:${letra}${filaTotal - 1})`, result: minutos / 60 };
  }
  darFormatoTabla(hoja, 8, filaTotal, cantidadColumnas);
  for (let fila = 8; fila <= filaTotal; fila += 1) {
    for (let columna = 2; columna <= cantidadColumnas; columna += 1) {
      hoja.getCell(fila, columna).alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      if (fila > 8) hoja.getCell(fila, columna).numFmt = '0.00';
    }
  }
  hoja.getRow(filaTotal).eachCell((celda) => { celda.font = { ...celda.font, bold: true }; celda.fill = FONDO; });
  combinar(hoja, filaTotal + 2, 'H: turno en feriado, sus horas se reemplazan por el abono institucional. F: falta. Blanco: sin turno. Horas decimales; 1.5 h = 1 h 30 min.', cantidadColumnas);
  hoja.pageSetup.printArea = `A1:${ultimaLetra}${filaTotal + 2}`;
}

function crearDetalle(libro, reporte) {
  const hoja = prepararHoja(libro, 'Detalle de turnos', reporte, [13, 13, 30, 17, 11, 11, 13, 23, 30, 25, 16, 38, 16, 32]);
  hoja.getRow(8).values = ['Fecha', 'Día', 'Profesional', 'Colegiatura', 'Inicio', 'Fin', 'Horas', 'Área', 'Consultorio', 'Especialidad', 'Estado', 'Observación', 'Asistencia', 'Feriado no laborable'];
  hoja.getRow(8).height = 30;
  for (let indice = 0; indice < reporte.turnos.length; indice += 1) {
    const turno = reporte.turnos[indice];
    const numero = indice + 9;
    const hora = (valor) => (Number(valor.slice(0, 2)) * 60 + Number(valor.slice(3))) / 1440;
    const fila = hoja.getRow(numero);
    // Los nombres, observaciones y colegiaturas se escriben como texto, nunca como fórmulas.
    fila.values = [new Date(`${turno.fecha}T00:00:00Z`), nombreDia(turno.fecha), turno.profesional,
      turno.colegiatura || 'Sin registrar', hora(turno.horaInicio), hora(turno.horaFin),
      { formula: `IF(N${numero}<>"",0,IF(M${numero}="Falta",0,(F${numero}-E${numero})*24))`, result: turno.minutos / 60 }, turno.area, turno.consultorio,
      turno.especialidad, turno.estado, turno.observacion || null, turno.asistencia, turno.nombreFeriado || null];
    fila.getCell(1).numFmt = 'dd/mm/yyyy';
    fila.getCell(4).numFmt = '@';
    fila.getCell(5).numFmt = 'hh:mm';
    fila.getCell(6).numFmt = 'hh:mm';
    fila.getCell(7).numFmt = '0.00';
    const lineas = Math.max(...fila.values.slice(1).map((valor, columna) => typeof valor === 'string'
      ? Math.ceil(valor.length / Math.max(8, hoja.getColumn(columna + 1).width - 2)) : 1));
    fila.height = Math.max(30, lineas * 15);
  }
  const filaTotal = 9 + reporte.turnos.length;
  if (!reporte.turnos.length) {
    hoja.getCell(9, 1).value = 'Sin turnos';
    hoja.getCell(9, 7).value = 0;
  } else {
    hoja.getCell(filaTotal, 1).value = 'Total (h)';
    hoja.getCell(filaTotal, 7).value = { formula: `SUM(G9:G${filaTotal - 1})`, result: reporte.minutosTurnos / 60 };
  }
  let ultimaFila = filaTotal;
  if (reporte.minutosFeriados > 0) {
    hoja.getCell(filaTotal, 1).value = 'Subtotal turnos (h)';
    hoja.getCell(filaTotal + 1, 1).value = 'Abono institucional (h)';
    hoja.getCell(filaTotal + 1, 7).value = { formula: `'Abonos por feriado'!D${9 + reporte.abonos.length}`, result: reporte.minutosFeriados / 60 };
    hoja.getCell(filaTotal + 2, 1).value = 'Total computable (h)';
    hoja.getCell(filaTotal + 2, 7).value = { formula: `G${filaTotal}+G${filaTotal + 1}`, result: reporte.minutos / 60 };
    ultimaFila += 2;
  }
  darFormatoTabla(hoja, 8, ultimaFila, 14);
  for (let numero = filaTotal; numero <= ultimaFila; numero += 1) {
    hoja.mergeCells(numero, 1, numero, 6);
    hoja.getCell(numero, 7).numFmt = '0.00';
    hoja.getRow(numero).height = 26;
    hoja.getRow(numero).eachCell((celda) => { celda.font = { ...celda.font, bold: true }; celda.fill = FONDO; });
  }
  if (reporte.turnos.length) hoja.autoFilter = `A8:N${filaTotal - 1}`;
  hoja.pageSetup.printArea = `A1:N${ultimaFila}`;
}

function crearAbonos(libro, reporte) {
  const hoja = prepararHoja(libro, 'Abonos por feriado', reporte, [16, 36, 65, 24]);
  hoja.getRow(8).values = ['Fecha', 'Profesional', 'Feriado nacional', 'Horas institucionales'];
  reporte.abonos.forEach((abono, indice) => {
    const fila = hoja.getRow(indice + 9);
    fila.values = [new Date(`${abono.fecha}T00:00:00Z`), abono.profesional, abono.nombre, abono.minutos / 60];
    fila.getCell(1).numFmt = 'dd/mm/yyyy';
    fila.getCell(4).numFmt = '0.00';
    fila.height = Math.max(28, Math.ceil(abono.nombre.length / 60) * 15);
  });
  const filaTotal = 9 + reporte.abonos.length;
  hoja.getCell(filaTotal, 1).value = 'Total abonos (h)';
  hoja.getCell(filaTotal, 4).value = { formula: `SUM(D9:D${filaTotal - 1})`,
    result: reporte.abonos.reduce((total, abono) => total + abono.minutos, 0) / 60 };
  darFormatoTabla(hoja, 8, filaTotal, 4);
  hoja.mergeCells(filaTotal, 1, filaTotal, 3);
  hoja.autoFilter = `A8:D${filaTotal - 1}`;
  combinar(hoja, filaTotal + 2, reporte.abonosInformativos
    ? 'Abonos informativos: pertenecen al profesional, no se suman al consultorio.'
    : 'Se acredita un único abono por profesional y fecha, aunque tenga turnos en varios consultorios.', 4);
  hoja.pageSetup.printArea = `A1:D${filaTotal + 2}`;
}

export async function crearExcelMensual(reporte) {
  const libro = new Excel.Workbook();
  libro.creator = 'MediShift';
  libro.created = reporte.emitido;
  libro.modified = reporte.emitido;
  libro.calcProperties.fullCalcOnLoad = true;
  if (reporte.profesional) crearMatriz(libro, reporte);
  crearDetalle(libro, reporte);
  if (reporte.abonos.length) crearAbonos(libro, reporte);
  const contenido = await libro.xlsx.writeBuffer();
  return new Blob([contenido], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
}
