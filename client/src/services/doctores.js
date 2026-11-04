import { REVISION, UUID } from '../utils/validacionesProgramacion.js';

export async function gestionarAccesoDoctor(idProfesional, { datos, revision, senal,
  ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000 } = {}) {
  if (!UUID.test(idProfesional) || typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0
    || (senal !== undefined && !(senal instanceof AbortSignal)) || (datos && !REVISION.test(revision))) {
    throw new Error('Recarga el acceso antes de guardar.');
  }
  const controlador = new AbortController();
  const cancelar = () => controlador.abort();
  senal?.addEventListener('abort', cancelar, { once: true });
  if (senal?.aborted) cancelar();
  const temporizador = setTimeout(cancelar, tiempoEspera);
  try {
    const respuesta = await ejecutarSolicitud('/api/profesionales/' + idProfesional + '/acceso', {
      method: datos ? 'PUT' : 'GET', credentials: 'same-origin', cache: 'no-store', signal: controlador.signal,
      headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json', 'If-Match': '"' + revision + '"' } : {}) },
      ...(datos ? { body: JSON.stringify(datos) } : {}),
    });
    let contenido;
    try { contenido = await respuesta.json(); } catch { throw new Error('El servidor devolvió una respuesta inválida.'); }
    if (!respuesta.ok) {
      const fallo = new Error(contenido.mensaje || 'No se pudo gestionar el acceso del doctor.');
      fallo.estado = respuesta.status;
      fallo.errores = contenido.errores || {};
      throw fallo;
    }
    if (!contenido || (contenido.idCuenta !== null && !UUID.test(contenido.idCuenta))
      || typeof contenido.correo !== 'string' || typeof contenido.habilitado !== 'boolean' || !REVISION.test(contenido.revision)) {
      throw new Error('El servidor devolvió un acceso inválido.');
    }
    return { idCuenta: contenido.idCuenta, correo: contenido.correo, habilitado: contenido.habilitado, revision: contenido.revision };
  } catch (fallo) {
    if (controlador.signal.aborted) throw new Error('La solicitud se canceló o tardó demasiado. Actualiza para comprobar si se guardó.');
    if (fallo instanceof TypeError) throw new Error('No se pudo contactar al servidor. Comprueba la conexión.');
    throw fallo;
  } finally {
    clearTimeout(temporizador);
    senal?.removeEventListener('abort', cancelar);
  }
}
