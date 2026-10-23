import { nombreArchivoReporte } from '../utils/reportesMensuales.js';

const TIPOS = Object.freeze({ pdf: 'application/pdf', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });

export function permiteElegirArchivo(entorno = globalThis) {
  return entorno.isSecureContext === true && typeof entorno.showSaveFilePicker === 'function';
}

export async function generarArchivoReporte(reporte, formato) {
  if (formato === 'pdf') return (await import('../reportes/pdfMensual.js')).crearPdfMensual(reporte);
  if (formato === 'xlsx') return (await import('../reportes/excelMensual.js')).crearExcelMensual(reporte);
  throw new Error('Selecciona PDF o Excel.');
}

/** Debe invocarse directamente en el clic, antes de cargar librerías o generar el contenido. */
export async function guardarArchivoReporte(reporte, formato, { entorno = globalThis, generar = generarArchivoReporte, descargar = false } = {}) {
  const nombre = nombreArchivoReporte(reporte, formato);
  let destino;
  let escritura;
  try {
    if (!descargar && permiteElegirArchivo(entorno)) {
      destino = await entorno.showSaveFilePicker({ id: 'reportes-medishift', suggestedName: nombre,
        excludeAcceptAllOption: true, types: [{ description: formato === 'pdf' ? 'Documento PDF' : 'Libro de Excel',
          accept: { [TIPOS[formato]]: [`.${formato}`] } }] });
    }
    const archivo = await generar(reporte, formato);
    if (!(archivo instanceof Blob) || !archivo.size || archivo.type !== TIPOS[formato]) {
      throw new Error('No se pudo generar un archivo válido. Vuelve a intentarlo.');
    }
    if (destino) {
      escritura = await destino.createWritable();
      await escritura.write(archivo);
      await escritura.close();
      escritura = null;
      return { estado: 'guardado', nombre: destino.name || nombre };
    }
    const enlace = entorno.document.createElement('a');
    const direccion = entorno.URL.createObjectURL(archivo);
    try {
      enlace.href = direccion;
      enlace.download = nombre;
      enlace.hidden = true;
      entorno.document.body.append(enlace);
      enlace.click();
    } finally {
      enlace.remove();
      // El navegador necesita tiempo para comenzar a leer el blob descargado.
      entorno.setTimeout(() => entorno.URL.revokeObjectURL(direccion), 60000);
    }
    return { estado: 'descargado', nombre };
  } catch (fallo) {
    if (escritura) { try { await escritura.abort(); } catch { /* Conserva el error original. */ } }
    if (fallo?.name === 'AbortError' && !destino) return { estado: 'cancelado', nombre };
    if (['NotAllowedError', 'SecurityError'].includes(fallo?.name)) {
      throw new Error('No se permitió guardar el archivo. Pulsa de nuevo el botón y elige una carpeta con permiso de escritura.');
    }
    throw new Error('No se pudo guardar el reporte. Comprueba el espacio y los permisos de la carpeta e inténtalo nuevamente.', { cause: fallo });
  }
}
