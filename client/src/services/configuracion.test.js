import test from 'node:test';
import assert from 'node:assert/strict';
import { cambiarContrasena, esPolitica, extraerConfiguracion, extraerCuenta, guardarConfiguracion,
  guardarPerfil, guardarPreferencias, obtenerCuenta } from './configuracion.js';
import { validarTurno } from '../utils/validacionesProgramacion.js';
import { turnoInicial } from '../utils/calendarioMensual.js';
import { crearAbonosFeriados, validarFeriados } from '../utils/feriados.js';

const revision = 'a'.repeat(64);
const cuenta = () => ({ nombre: 'Usuario de ensayo', correo: 'ensayo@example.test', creada: '2026-01-01T12:00:00Z',
  esAdministrador: true, revision, preferencias: { barraCompacta: false, horaInicio: '08:00', horaFin: '12:00' } });
const configuracion = () => ({ nombreInstitucion: 'Institución ficticia', zonaHoraria: 'America/Lima',
  minutosDiarios: 480, minutosSemanales: 2400, minutosPorFeriado: 450, revision });
const responder = (status, contenido) => ({ status, json: async () => contenido });

test('consulta la cuenta con cookie sin caché y conserva preferencias verificadas', async () => {
  const resultado = await obtenerCuenta({ ejecutarSolicitud: async (ruta, opciones) => {
    assert.equal(ruta, '/api/cuenta'); assert.equal(opciones.method, 'GET');
    assert.equal(opciones.credentials, 'same-origin'); assert.equal(opciones.cache, 'no-store');
    return responder(200, { ...cuenta(), hash: 'dato inesperado' });
  } });
  assert.deepEqual(resultado, cuenta());
});

test('guarda cada formulario con If-Match sin identificadores ni permisos agregados', async () => {
  const casos = [[guardarPerfil, '/api/cuenta/perfil', { nombre: 'Ensayo' }],
    [guardarPreferencias, '/api/cuenta/preferencias', cuenta().preferencias],
    [cambiarContrasena, '/api/cuenta/contrasena', { contrasenaActual: 'Clave ficticia anterior', contrasenaNueva: 'Clave ficticia nueva' }],
    [guardarConfiguracion, '/api/configuracion', configuracion()]];
  for (const [guardar, rutaEsperada, datos] of casos) {
    await guardar(datos, { revision, ejecutarSolicitud: async (ruta, opciones) => {
      assert.equal(ruta, rutaEsperada); assert.equal(opciones.method, 'PUT');
      assert.equal(opciones.headers['If-Match'], `"${revision}"`);
      assert.deepEqual(JSON.parse(opciones.body), datos);
      return responder(200, ruta === '/api/configuracion' ? configuracion() : cuenta());
    } });
  }
});

test('rechaza guardados sin revisión antes de contactar el servidor', async () => {
  let llamadas = 0;
  await assert.rejects(guardarPerfil({ nombre: 'Ensayo' }, { ejecutarSolicitud: async () => { llamadas += 1; } }),
    (error) => error.estado === 428);
  assert.equal(llamadas, 0);
});

test('conserva errores de campos, sesión, permisos y conflictos de revisión', async () => {
  for (const estado of [400, 401, 403, 409, 428, 503]) {
    await assert.rejects(obtenerCuenta({ ejecutarSolicitud: async () => responder(estado,
      { mensaje: 'No se pudo guardar', errores: { revision: 'Recarga', invalido: 123 } }) }),
    (error) => error.estado === estado && error.errores.revision === 'Recarga' && !('invalido' in error.errores));
  }
});

test('maneja respuesta inválida, caída de red y solicitudes que no terminan', async () => {
  await assert.rejects(obtenerCuenta({ ejecutarSolicitud: async () => responder(200, {}) }), /inválida/);
  await assert.rejects(obtenerCuenta({ ejecutarSolicitud: async () => { throw new TypeError('Red'); } }), /conexión/);
  await assert.rejects(obtenerCuenta({ tiempoEspera: 5, ejecutarSolicitud: async () => new Promise(() => {}) }), /tardó demasiado/);
});

test('valida horarios, revisión y tipos recibidos antes de mostrar datos de cuenta', () => {
  for (const alteracion of [{ revision: 'incorrecta' }, { nombre: null }, { esAdministrador: 'true' },
    { preferencias: { ...cuenta().preferencias, horaInicio: '24:00' } }, { creada: 'sin fecha' }]) {
    assert.throws(() => extraerCuenta({ ...cuenta(), ...alteracion }), /inválida/);
  }
});

test('las reglas admiten minutos exactos y abonos cero y rechazan rangos inconsistentes', () => {
  assert.equal(esPolitica({ minutosDiarios: 450, minutosSemanales: 2250, minutosPorFeriado: 0 }), true);
  for (const cambios of [{ minutosDiarios: 0 }, { minutosDiarios: 1441 }, { minutosSemanales: 479 },
    { minutosSemanales: 3361 }, { minutosPorFeriado: -1 }, { minutosDiarios: 480.5 }]) {
    assert.equal(esPolitica({ ...configuracion(), ...cambios }), false);
  }
  assert.throws(() => extraerConfiguracion({ ...configuracion(), zonaHoraria: 'Zona/Inexistente' }), /inválida/);
});

const identificador = '10000000-0000-0000-0000-000000000001';
const turno = () => ({ idProfesional: identificador, idConsultorio: identificador, idEspecialidad: identificador,
  fecha: '2032-11-02', horaInicio: '08:00', horaFin: '16:00', observacion: '' });
const recursos = (politica) => ({ politica, profesionales: [{ id: identificador, estado: 'Activo', especialidades: [{ id: identificador }] }],
  consultorios: [{ id: identificador, estado: 'Activo', usoGeneral: true, especialidades: [] }] });

test('el cliente respeta el máximo diario configurado en vez del antiguo valor fijo', () => {
  assert.deepEqual(validarTurno(turno(), { recursos: recursos(configuracion()) }), {});
  assert.ok(validarTurno({ ...turno(), horaFin: '16:01' }, { recursos: recursos(configuracion()) }).horaFin);
  assert.ok(validarTurno(turno(), { recursos: recursos({ ...configuracion(), minutosDiarios: 420 }) }).horaFin);
});

test('la validación acumula el máximo semanal personalizado y omite turnos cancelados', () => {
  const anteriores = [{ ...turno(), id: 'otro', fecha: '2032-11-01', horaInicio: '08:00', horaFin: '10:01', estado: 'Aprobado' }];
  const politica = { ...configuracion(), minutosSemanales: 600 };
  assert.ok(validarTurno(turno(), { recursos: recursos(politica), turnos: anteriores }).fecha);
  assert.deepEqual(validarTurno(turno(), { recursos: recursos(politica), turnos: [{ ...anteriores[0], estado: 'Cancelado' }] }), {});
});

test('la validación estructural del servicio permite jornadas configuradas superiores a seis horas', () => {
  assert.deepEqual(validarTurno(turno(), { aplicarLimites: false }), {});
  assert.ok(validarTurno({ ...turno(), horaInicio: '17:00' }, { aplicarLimites: false }).horaFin);
});

test('las horas predeterminadas solo se aplican a nuevos turnos y preservan una edición', () => {
  const preferencias = { horaInicio: '09:00', horaFin: '13:30' };
  assert.equal(turnoInicial({}, preferencias).horaInicio, '09:00');
  assert.equal(turnoInicial({ turno: { horaInicio: '10:00', horaFin: '12:00' } }, preferencias).horaInicio, '10:00');
});

test('feriados admite abonos desactivados sin perder sus fechas verificadas', () => {
  const feriados = { codigoPais: 'PE', pais: 'Perú', estado: 'Disponible', fuente: 'Ensayo', actualizado: '2032-11-01T00:00:00Z',
    minutosPorFeriado: 0, requiereActualizacion: false, dias: [{ fecha: '2032-11-01', nombre: 'Feriado de ensayo' }], mensaje: '' };
  assert.equal(validarFeriados(feriados, '2032-11'), true);
  assert.equal(crearAbonosFeriados([{ ...turno(), estado: 'Borrador', profesional: 'Persona de ensayo' }], feriados)[0].minutos, 0);
});
