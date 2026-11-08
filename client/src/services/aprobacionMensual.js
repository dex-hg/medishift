import { ErrorAtencion, solicitarAtencion } from './atencion.js';
import { REVISION, UUID } from '../utils/validacionesProgramacion.js';

export function cambiarAprobacionMensual(plan, reabrir, opciones) {
  if (!UUID.test(plan?.id) || !REVISION.test(plan?.revision)) throw new ErrorAtencion('Recarga el mes antes de continuar.');
  return solicitarAtencion(`/api/planes-mensuales/${plan.id}/${reabrir ? 'reabrir' : 'aprobar'}`, 'POST', (valor) => {
    if (valor?.id !== plan.id || !REVISION.test(valor.revision) || valor.estado !== (reabrir ? 'Borrador' : 'Aprobado')) throw new ErrorAtencion('No se pudo confirmar el estado del mes. Recarga los datos.');
    return valor;
  }, { ...opciones, revision: plan.revision });
}

export function obtenerHistorialMensual(id, opciones) {
  if (!UUID.test(id)) throw new ErrorAtencion('El identificador del mes no es válido.');
  return solicitarAtencion(`/api/planes-mensuales/${id}/historial`, 'GET', (valor) => {
    if (!Array.isArray(valor)) throw new ErrorAtencion('El historial del mes no es válido.');
    return valor.map((registro) => {
      let contenido;
      try { contenido = JSON.parse(registro.contenido); } catch { throw new ErrorAtencion('La versión archivada no es válida.'); }
      if (!Number.isInteger(registro.version) || registro.version < 1 || !Number.isFinite(Date.parse(registro.archivadoEn))
        || !Array.isArray(contenido.turnos) || !contenido.plan || contenido.plan.id_schedule !== id) throw new ErrorAtencion('La versión archivada no es válida.');
      return { ...registro, contenido };
    });
  }, opciones);
}
