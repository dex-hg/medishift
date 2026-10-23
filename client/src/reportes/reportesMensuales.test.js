import test from 'node:test';
import assert from 'node:assert/strict';
import Excel from 'exceljs';
import { crearExcelMensual } from './excelMensual.js';
import { crearPdfMensual } from './pdfMensual.js';
import { construirReporteMensual } from '../utils/reportesMensuales.js';
import { calendarioPrueba, identificadorPrueba, opcionesPrueba } from './datosReportePrueba.js';

async function leerExcel(reporte) {
  const archivo = await crearExcelMensual(reporte);
  assert.equal(archivo.type, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  const libro = new Excel.Workbook();
  await libro.xlsx.load(await archivo.arrayBuffer());
  return libro;
}

test('Excel individual abre con matriz mensual, identidad, impresión, horas numéricas y fórmulas', async () => {
  const reporte = construirReporteMensual(calendarioPrueba(), opcionesPrueba());
  const libro = await leerExcel(reporte);
  assert.deepEqual(libro.worksheets.map((hoja) => hoja.name), ['Horario individual', 'Detalle de turnos']);
  const matriz = libro.getWorksheet('Horario individual');
  assert.equal(matriz.getCell('A3').value, 'Profesional: María Núñez de la Peña');
  assert.equal(matriz.getCell('A4').value, 'Colegiatura: 001234');
  assert.equal(matriz.columnCount, 33);
  assert.match(matriz.getCell('AF8').value, /31$/);
  assert.equal(matriz.pageSetup.orientation, 'landscape');
  assert.equal(matriz.pageSetup.fitToWidth, 1);
  assert.equal(matriz.headerFooter.oddFooter, '&LMediShift&C Página &P de &N');
  assert.equal(matriz.getCell('D9').value, 3.5);
  assert.equal(matriz.getCell('C9').value, null);
  assert.equal(matriz.getCell('AG12').result, 11.5);
  assert.equal(matriz.getCell('AG12').formula, 'SUM(AG9:AG11)');
  const detalle = libro.getWorksheet('Detalle de turnos');
  assert.equal(detalle.getCell('D9').value, '001234');
  assert.equal(detalle.getCell('D9').type, Excel.ValueType.String);
  assert.ok(detalle.getCell('A9').value instanceof Date);
  assert.equal(detalle.getCell('E9').value.getUTCHours(), 6);
  assert.equal(detalle.getCell('F9').value.getUTCHours(), 12);
  assert.equal(detalle.getCell('G9').formula, '(F9-E9)*24');
  assert.equal(detalle.getCell('G12').result, 11.5);
  assert.equal(detalle.getCell('G12').formula, 'SUM(G9:G11)');
  assert.ok(detalle.autoFilter);
  assert.match(detalle.getCell('A6').value, /Borrador, Pendiente/);
});

test('Excel general exporta únicamente los turnos operativos de todas las áreas', async () => {
  const reporte = construirReporteMensual(calendarioPrueba(), opcionesPrueba('mes'));
  const libro = await leerExcel(reporte);
  assert.equal(libro.worksheets.length, 1);
  const hoja = libro.worksheets[0];
  assert.equal(hoja.getCell('G13').result, 17.5);
  const estados = [9, 10, 11, 12].map((fila) => hoja.getCell(fila, 11).value);
  assert.equal(estados.includes('Cancelado'), false);
  assert.equal(estados.includes('Sustituido'), false);
  assert.deepEqual(new Set([9, 10, 11, 12].map((fila) => hoja.getCell(fila, 8).value)),
    new Set(['Medicina general', 'Atención externa']));
});

test('Excel conserva cadenas que empiezan con signos como texto y no ejecuta contenido como fórmula', async () => {
  const datos = calendarioPrueba();
  datos.profesionales[0].nombre = '=HYPERLINK("https://example.test")';
  datos.profesionales[0].colegiatura = '+001234';
  datos.turnos[0].observacion = '=1+1';
  const libro = await leerExcel(construirReporteMensual(datos, opcionesPrueba()));
  const detalle = libro.getWorksheet('Detalle de turnos');
  for (const direccion of ['C9', 'D9', 'L9']) assert.equal(detalle.getCell(direccion).type, Excel.ValueType.String);
  assert.equal(detalle.getCell('L9').value, '=1+1');
  assert.equal(detalle.getCell('D9').value, '+001234');
});

test('Excel de febrero sin turnos conserva 28 días y totales sin referencias inválidas', async () => {
  const reporte = construirReporteMensual(calendarioPrueba('2026-02'), {
    ...opcionesPrueba('profesional', '2026-02'), idProfesional: identificadorPrueba(3),
  });
  const libro = await leerExcel(reporte);
  const matriz = libro.getWorksheet('Horario individual');
  assert.equal(matriz.columnCount, 30);
  assert.equal(matriz.getCell('AD10').result, 0);
  assert.equal(matriz.getCell('AD10').formula, 'SUM(AD9:AD9)');
  assert.equal(libro.getWorksheet('Detalle de turnos').getCell('G9').value, 0);
});

test('PDF produce un documento A4 horizontal con las páginas necesarias para fichas y reportes vacíos', async () => {
  for (const alcance of ['profesional', 'consultorio', 'mes']) {
    const archivo = crearPdfMensual(construirReporteMensual(calendarioPrueba(), opcionesPrueba(alcance)));
    const contenido = Buffer.from(await archivo.arrayBuffer()).toString('latin1');
    assert.equal(archivo.type, 'application/pdf');
    assert.ok(contenido.startsWith('%PDF-'));
    assert.ok(contenido.includes('/Count 2'));
    const caja = contenido.match(/\/MediaBox \[0 0 ([\d.]+) ([\d.]+)\]/);
    assert.ok(caja, 'El PDF debe declarar el tamaño de la página.');
    assert.ok(Math.abs(Number(caja[1]) - 841.89) < 0.01);
    assert.ok(Math.abs(Number(caja[2]) - 595.28) < 0.01);
  }
  const vacio = construirReporteMensual(calendarioPrueba(), { ...opcionesPrueba(), idProfesional: identificadorPrueba(3) });
  assert.ok(Buffer.from(await crearPdfMensual(vacio).arrayBuffer()).toString('latin1').includes('/Count 1'));
});
