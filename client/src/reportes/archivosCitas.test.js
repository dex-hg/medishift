import test from 'node:test';
import assert from 'node:assert/strict';
import Excel from 'exceljs';
import { generarArchivoCitas } from './archivosCitas.js';
import { construirReporteCitas, nombreArchivoCitas, opcionesReporteCitas, prepararCitasDelMes } from '../utils/reportesCitas.js';

const cita = {
  inicio: '2032-11-02T13:00:00Z', fin: '2032-11-02T13:30:00Z',
  paciente: 'Ana Pérez', dni: '00345678', correo: 'paciente@medishift.test',
  idArea: 'a', area: 'Medicina', idConsultorio: 's', consultorio: 'R01 · Sala de ensayo',
  idProfesional: 'm', profesional: 'Doctora Núñez', estado: 'programada',
};
const calendario = {
  periodo: '2032-11', zonaHoraria: 'America/Lima',
  citas: [
    cita, { ...cita, idArea: 'b', area: 'Cirugía', idConsultorio: 'r', consultorio: 'Sala 2' },
    { ...cita, idProfesional: 't', profesional: 'Otro médico', estado: 'cancelada' },
    { ...cita, inicio: '2032-11-01T04:50:00Z', fin: '2032-11-01T05:15:00Z' },
  ],
};

function reporte(filtros = {}, datos = calendario) {
  const citas = prepararCitasDelMes(datos);
  return { ...construirReporteCitas(datos, citas, opcionesReporteCitas(citas), filtros, 'Hospital de ensayo'),
    emitido: new Date('2032-11-02T13:00:00Z') };
}

test('filtra el mes local por área, consultorio y médico juntos o por separado', () => {
  assert.equal(reporte().citas.length, 3);
  assert.equal(reporte({ area: 'a' }).citas.length, 2);
  assert.equal(reporte({ consultorio: 's' }).citas.length, 2);
  assert.equal(reporte({ profesional: 'm' }).citas.length, 2);
  const seleccion = reporte({ area: 'a', consultorio: 's', profesional: 'm' });
  assert.equal(seleccion.citas.length, 1);
  assert.match(seleccion.subtitulo, /Medicina.*Sala de ensayo.*Núñez/);
  assert.match(nombreArchivoCitas(seleccion, 'xlsx'), /^citas-2032-11-.*\.xlsx$/);
  assert.equal(reporte({ area: 'a', consultorio: 'r' }).citas.length, 0);
  const sinArea = { ...calendario, citas: [{ ...cita, idArea: null, area: null }] };
  assert.equal(reporte({ area: 'sin-area' }, sinArea).citas[0].area, 'Sin área');
});

test('Excel conserva fechas, horas y nombres como datos y excluye DNI y correo', async () => {
  const seleccion = reporte({}, { ...calendario, citas: [{ ...cita, paciente: '=Ensayo', estado: 'cancelada' }] });
  const archivo = await generarArchivoCitas(seleccion, 'xlsx');
  const libro = new Excel.Workbook();
  await libro.xlsx.load(await archivo.arrayBuffer());
  const hoja = libro.getWorksheet('Citas');
  assert.deepEqual(hoja.getRow(6).values.slice(1), ['Fecha', 'Inicio', 'Fin', 'Paciente', 'Área', 'Consultorio', 'Médico', 'Estado']);
  assert.equal(hoja.getCell('A7').value.toISOString(), '2032-11-02T00:00:00.000Z');
  assert.equal(hoja.getCell('B7').value.getUTCHours(), 8);
  assert.equal(hoja.getCell('C7').value.getUTCMinutes(), 30);
  assert.equal(hoja.getCell('D7').value, '=Ensayo');
  assert.equal(hoja.getCell('D7').type, Excel.ValueType.String);
  assert.equal(hoja.getCell('H7').value, 'Cancelada');
  assert.ok(hoja.autoFilter);
  assert.doesNotMatch(JSON.stringify(hoja.getRow(7).values), /00345678|@medishift/);
});

test('PDF genera páginas para un mes con muchas citas', async () => {
  const seleccion = reporte({}, { ...calendario, citas: Array.from({ length: 90 }, () => cita) });
  const archivo = await generarArchivoCitas(seleccion, 'pdf');
  assert.equal(archivo.type, 'application/pdf');
  const contenido = await archivo.text();
  assert.match(contenido, /^%PDF-/);
  assert.ok((contenido.match(/\/Type \/Page\b/g) || []).length >= 2);
});
