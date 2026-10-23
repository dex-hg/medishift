import test from 'node:test';
import assert from 'node:assert/strict';
import { guardarArchivoReporte, permiteElegirArchivo } from './archivoReporte.js';

const reporte = { periodo: '2026-10', alcance: 'mes' };
const archivo = () => new Blob(['%PDF-1.4 contenido de prueba'], { type: 'application/pdf' });
const falloConNombre = (nombre) => Object.assign(new Error('Fallo de prueba'), { name: nombre });

test('abre Guardar como durante el clic, antes de generar y escribir, y respeta el nombre elegido', async () => {
  const eventos = [];
  const entorno = { isSecureContext: true, showSaveFilePicker: (opciones) => {
    eventos.push('selector');
    assert.equal(opciones.suggestedName, 'horario-2026-10-general.pdf');
    assert.deepEqual(opciones.types[0].accept, { 'application/pdf': ['.pdf'] });
    return Promise.resolve({ name: 'Elegido por usuario.pdf', createWritable: async () => ({
      write: async (contenido) => { assert.equal(contenido.type, 'application/pdf'); eventos.push('escribir'); },
      close: async () => eventos.push('cerrar'),
    }) });
  } };
  const tarea = guardarArchivoReporte(reporte, 'pdf', { entorno, generar: async () => { eventos.push('generar'); return archivo(); } });
  assert.deepEqual(eventos, ['selector']);
  const resultado = await tarea;
  assert.deepEqual(eventos, ['selector', 'generar', 'escribir', 'cerrar']);
  assert.equal(resultado.nombre, 'Elegido por usuario.pdf');
  assert.equal(resultado.estado, 'guardado');
});

test('cancelar el selector no genera archivos ni inicia descargas', async () => {
  const resultado = await guardarArchivoReporte(reporte, 'pdf', { entorno: { isSecureContext: true,
    showSaveFilePicker: async () => { throw falloConNombre('AbortError'); } },
  generar: () => assert.fail('No debe generar al cancelar') });
  assert.equal(resultado.estado, 'cancelado');
});

test('denegar permiso no inicia una descarga alternativa silenciosa', async () => {
  await assert.rejects(guardarArchivoReporte(reporte, 'pdf', { entorno: { isSecureContext: true,
    showSaveFilePicker: async () => { throw falloConNombre('NotAllowedError'); } },
  generar: () => assert.fail('No debe generar sin permiso') }), /No se permitió/);
});

test('al fallar una escritura aborta el flujo y no confirma un guardado', async () => {
  const eventos = [];
  await assert.rejects(guardarArchivoReporte(reporte, 'pdf', { entorno: { isSecureContext: true,
    showSaveFilePicker: async () => ({ createWritable: async () => ({
      write: async () => { throw falloConNombre('AbortError'); }, close: async () => assert.fail('No debe cerrar'),
      abort: async () => eventos.push('abortado'),
    }) }) }, generar: archivo }), /No se pudo guardar/);
  assert.deepEqual(eventos, ['abortado']);
});

test('detecta ausencia del selector y descarga con MIME y nombre correctos, liberando la URL después', async () => {
  const eventos = [];
  let liberacion;
  const enlace = { click: () => eventos.push('descarga'), remove: () => eventos.push('retirar') };
  const entorno = { isSecureContext: false, showSaveFilePicker: () => assert.fail('Contexto inseguro'),
    document: { createElement: () => enlace, body: { append: () => eventos.push('insertar') } },
    URL: { createObjectURL: () => 'blob:prueba', revokeObjectURL: (direccion) => { assert.equal(direccion, 'blob:prueba'); eventos.push('liberar'); } },
    setTimeout: (funcion, tiempo) => { assert.equal(tiempo, 60000); liberacion = funcion; },
  };
  assert.equal(permiteElegirArchivo(entorno), false);
  const resultado = await guardarArchivoReporte(reporte, 'pdf', { entorno, generar: archivo });
  assert.equal(resultado.estado, 'descargado');
  assert.equal(enlace.download, 'horario-2026-10-general.pdf');
  assert.deepEqual(eventos, ['insertar', 'descarga', 'retirar']);
  liberacion();
  assert.deepEqual(eventos, ['insertar', 'descarga', 'retirar', 'liberar']);
});

test('rechaza archivos vacíos o de otro tipo antes de abrir el flujo de escritura', async () => {
  for (const contenido of [new Blob([], { type: 'application/pdf' }), new Blob(['x'], { type: 'text/plain' })]) {
    await assert.rejects(guardarArchivoReporte(reporte, 'pdf', { entorno: { isSecureContext: true,
      showSaveFilePicker: async () => ({ createWritable: () => assert.fail('Archivo inválido') }) },
    generar: () => contenido }), /No se pudo guardar/);
  }
});

test('la descarga alternativa omite el selector solo cuando se solicita expresamente', async () => {
  const enlace = { click() {}, remove() {} };
  const entorno = { isSecureContext: true, showSaveFilePicker: () => assert.fail('Se eligió descarga explícita'),
    document: { createElement: () => enlace, body: { append() {} } }, URL: { createObjectURL: () => 'blob:prueba', revokeObjectURL() {} },
    setTimeout() {},
  };
  const resultado = await guardarArchivoReporte(reporte, 'pdf', { entorno, generar: archivo, descargar: true });
  assert.equal(resultado.estado, 'descargado');
});
