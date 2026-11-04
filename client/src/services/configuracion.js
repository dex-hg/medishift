import { horaValida, REVISION } from '../utils/validacionesProgramacion.js';

const esObjeto = (valor) => valor !== null && typeof valor === 'object' && !Array.isArray(valor);
const rutas = new Set(['/api/cuenta', '/api/configuracion', '/api/cuenta/perfil',
  '/api/cuenta/preferencias', '/api/cuenta/contrasena']);

export class ErrorConfiguracion extends Error {
  constructor(mensaje, { estado = 0, errores = {} } = {}) {
    super(mensaje);
    this.name = 'ErrorConfiguracion';
    this.estado = estado;
    this.errores = errores;
  }
}

export function esPolitica(valor) {
  return esObjeto(valor) && Number.isInteger(valor.minutosDiarios) && valor.minutosDiarios >= 1 && valor.minutosDiarios <= 1440
    && Number.isInteger(valor.minutosSemanales) && valor.minutosSemanales >= valor.minutosDiarios
    && valor.minutosSemanales <= valor.minutosDiarios * 7 && Number.isInteger(valor.minutosPorFeriado)
    && valor.minutosPorFeriado >= 0 && valor.minutosPorFeriado <= 1440;
}

export function extraerConfiguracion(valor) {
  if (!esPolitica(valor) || typeof valor.nombreInstitucion !== 'string' || !valor.nombreInstitucion.trim()
    || typeof valor.zonaHoraria !== 'string' || typeof valor.autoprogramacionMedica !== 'boolean' || !REVISION.test(valor.revision)) respuestaInvalida();
  try { new Intl.DateTimeFormat('es-PE', { timeZone: valor.zonaHoraria }); } catch { respuestaInvalida(); }
  return { nombreInstitucion: valor.nombreInstitucion, zonaHoraria: valor.zonaHoraria,
    minutosDiarios: valor.minutosDiarios, minutosSemanales: valor.minutosSemanales,
    minutosPorFeriado: valor.minutosPorFeriado, autoprogramacionMedica: valor.autoprogramacionMedica, revision: valor.revision };
}

export function extraerCuenta(valor) {
  const preferencias = valor?.preferencias;
  if (!esObjeto(valor) || typeof valor.nombre !== 'string' || [...valor.nombre].length > 120
    || typeof valor.correo !== 'string' || !valor.correo.trim() || typeof valor.esAdministrador !== 'boolean'
    || typeof valor.creada !== 'string' || !Number.isFinite(Date.parse(valor.creada)) || !REVISION.test(valor.revision)
    || !esObjeto(preferencias) || typeof preferencias.barraCompacta !== 'boolean'
    || !horaValida(preferencias.horaInicio) || !horaValida(preferencias.horaFin)
    || preferencias.horaFin <= preferencias.horaInicio) respuestaInvalida();
  return { nombre: valor.nombre, correo: valor.correo, creada: valor.creada, esAdministrador: valor.esAdministrador,
    revision: valor.revision, preferencias: { barraCompacta: preferencias.barraCompacta,
      horaInicio: preferencias.horaInicio, horaFin: preferencias.horaFin } };
}

function respuestaInvalida() { throw new ErrorConfiguracion('El servidor devolvió una configuración inválida. Recarga los datos.'); }

async function solicitar(ruta, { datos, revision, senal, ejecutarSolicitud = globalThis.fetch, tiempoEspera = 15000 } = {}) {
  if (!rutas.has(ruta) || typeof ejecutarSolicitud !== 'function' || !Number.isFinite(tiempoEspera) || tiempoEspera <= 0
    || (senal !== undefined && !(senal instanceof AbortSignal))) throw new ErrorConfiguracion('No se pudo iniciar la solicitud.');
  if (datos && !REVISION.test(revision)) throw new ErrorConfiguracion('Recarga la configuración antes de guardar.', { estado: 428 });
  const controlador = new AbortController();
  const cancelar = () => controlador.abort();
  senal?.addEventListener('abort', cancelar, { once: true });
  if (senal?.aborted) controlador.abort();
  let temporizador;
  const vencimiento = new Promise((resolver, rechazar) => {
    temporizador = setTimeout(() => {
      rechazar(new ErrorConfiguracion('La solicitud tardó demasiado. Comprueba los datos antes de volver a guardar.'));
      controlador.abort();
    }, tiempoEspera);
  });
  try {
    const operacion = async () => {
      const respuesta = await ejecutarSolicitud(ruta, { method: datos ? 'PUT' : 'GET', credentials: 'same-origin',
        cache: 'no-store', signal: controlador.signal, headers: { Accept: 'application/json',
          ...(datos ? { 'Content-Type': 'application/json', 'If-Match': `"${revision}"` } : {}) },
        ...(datos ? { body: JSON.stringify(datos) } : {}) });
      let contenido;
      try { contenido = await respuesta.json(); } catch { contenido = null; }
      if (respuesta.status !== 200) {
        const errores = esObjeto(contenido?.errores) ? Object.fromEntries(Object.entries(contenido.errores)
          .filter(([, valor]) => typeof valor === 'string')) : {};
        throw new ErrorConfiguracion(typeof contenido?.mensaje === 'string' ? contenido.mensaje
          : 'No se pudo completar la solicitud. Inténtalo nuevamente.', { estado: respuesta.status, errores });
      }
      return ruta === '/api/configuracion' ? extraerConfiguracion(contenido) : extraerCuenta(contenido);
    };
    return await Promise.race([operacion(), vencimiento]);
  } catch (error) {
    if (error instanceof ErrorConfiguracion) throw error;
    throw new ErrorConfiguracion('No se pudo contactar al servidor. Comprueba tu conexión.');
  } finally {
    clearTimeout(temporizador);
    senal?.removeEventListener('abort', cancelar);
  }
}

export const obtenerCuenta = (opciones) => solicitar('/api/cuenta', opciones);
export const obtenerConfiguracion = (opciones) => solicitar('/api/configuracion', opciones);
export const guardarPerfil = (datos, opciones) => solicitar('/api/cuenta/perfil', { ...opciones, datos });
export const guardarPreferencias = (datos, opciones) => solicitar('/api/cuenta/preferencias', { ...opciones, datos });
export const cambiarContrasena = (datos, opciones) => solicitar('/api/cuenta/contrasena', { ...opciones, datos });
export const guardarConfiguracion = (datos, opciones) => solicitar('/api/configuracion', { ...opciones, datos });
