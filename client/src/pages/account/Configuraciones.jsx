import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { Building2, SlidersHorizontal } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import { guardarConfiguracion, guardarPreferencias } from '../../services/configuracion';
import { ZONAS_HORARIAS } from '../../utils/validacionesRegistro';
import useGuardarCuenta from '../../hooks/useGuardarCuenta';
import { CampoCuenta, EstadoGuardado } from './ElementosCuenta';

const mostrarHoras = (minutos) => `${Math.floor(minutos / 60)}:${String(minutos % 60).padStart(2, '0')}`;
const minutosDeTexto = (valor) => /^\d{1,3}:[0-5]\d$/.test(valor) ? Number(valor.split(':')[0]) * 60 + Number(valor.split(':')[1]) : NaN;

function PreferenciasPersonales({ cuenta, actualizar, recargar }) {
  const [datos, cambiarDatos] = useState(cuenta.preferencias);
  const estado = useGuardarCuenta(actualizar);
  useEffect(() => cambiarDatos((anteriores) => ({ ...anteriores, barraCompacta: cuenta.preferencias.barraCompacta })), [cuenta.preferencias.barraCompacta]);
  const campo = (nombre) => ({ value: datos[nombre], onChange: (evento) => {
    const valor = evento.target.value;
    cambiarDatos((anteriores) => ({ ...anteriores, [nombre]: valor }));
  } });
  return <form className="panel cuenta-tarjeta" onSubmit={(evento) => {
    evento.preventDefault(); estado.guardar((senal) => guardarPreferencias(datos, { revision: cuenta.revision, senal }));
  }}>
    <div className="cuenta-tarjeta__titulo"><SlidersHorizontal size={21} /><div><h2>Preferencias personales</h2>
      <p>Se guardan en tu cuenta y se aplican al volver a ingresar.</p></div></div>
    <fieldset className="cuenta-campos" disabled={estado.guardando || estado.desactualizado}>
      <label className="cuenta-opcion"><input type="checkbox" checked={datos.barraCompacta}
        onChange={(evento) => { const marcada = evento.target.checked; cambiarDatos((anteriores) => ({ ...anteriores, barraCompacta: marcada })); }} />
        <span><strong>Barra lateral compacta</strong><small>Deja más espacio para el calendario.</small></span></label>
      <div><h3>Horas predeterminadas para nuevos turnos</h3><p className="cuenta-ayuda">Puedes cambiarlas al asignar cada turno. Se usa la zona horaria institucional.</p></div>
      <div className="cuenta-fila"><CampoCuenta etiqueta="Desde" nombre="horaInicio" type="time" step={60} required {...campo('horaInicio')} error={estado.errores.horaInicio} />
        <CampoCuenta etiqueta="Hasta" nombre="horaFin" type="time" step={60} required {...campo('horaFin')} error={estado.errores.horaFin} /></div>
      <button className="boton boton--primario" type="submit">{estado.guardando ? 'Guardando…' : 'Guardar preferencias'}</button>
    </fieldset><EstadoGuardado estado={estado} recargar={recargar} />
  </form>;
}

function ConfiguracionInstitucional({ configuracion, administrador, actualizar, recargar }) {
  const [datos, cambiarDatos] = useState(() => ({ nombreInstitucion: configuracion.nombreInstitucion, zonaHoraria: configuracion.zonaHoraria,
    minutosDiarios: mostrarHoras(configuracion.minutosDiarios), minutosSemanales: mostrarHoras(configuracion.minutosSemanales),
    minutosPorFeriado: mostrarHoras(configuracion.minutosPorFeriado), autoprogramacionMedica: configuracion.autoprogramacionMedica }));
  const [errorHoras, cambiarErrorHoras] = useState('');
  const estado = useGuardarCuenta(actualizar);
  const campo = (nombre) => ({ value: datos[nombre], onChange: (evento) => {
    const valor = evento.target.value;
    cambiarErrorHoras(''); cambiarDatos((anteriores) => ({ ...anteriores, [nombre]: valor }));
  } });
  const enviar = (evento) => {
    evento.preventDefault();
    const politicas = Object.fromEntries(['minutosDiarios', 'minutosSemanales', 'minutosPorFeriado'].map((nombre) => [nombre, minutosDeTexto(datos[nombre])]));
    if (Object.values(politicas).some((valor) => !Number.isFinite(valor))) { cambiarErrorHoras('Escribe las horas como H:MM; por ejemplo 7:30 o 40:00.'); return; }
    estado.guardar((senal) => guardarConfiguracion({ ...datos, ...politicas }, { revision: configuracion.revision, senal }));
  };
  return <form className="panel cuenta-tarjeta" onSubmit={enviar}>
    <div className="cuenta-tarjeta__titulo"><Building2 size={21} /><div><h2>Reglas de la institución</h2>
      <p>Se aplican a todos los profesionales y usuarios de esta institución.</p></div></div>
    {!administrador && <p className="cuenta-aviso">Solo un administrador puede modificar estas reglas.</p>}
    <fieldset className="cuenta-campos" disabled={!administrador || estado.guardando || estado.desactualizado}>
      <CampoCuenta etiqueta="Nombre de la institución" nombre="nombreInstitucion" maxLength={140} required {...campo('nombreInstitucion')} error={estado.errores.nombreInstitucion} />
      <CampoCuenta etiqueta="Zona horaria y país de feriados" nombre="zonaHoraria" error={estado.errores.zonaHoraria}>
        <select id="cuenta-zonaHoraria" name="zonaHoraria" {...campo('zonaHoraria')} aria-invalid={Boolean(estado.errores.zonaHoraria)}>
          {ZONAS_HORARIAS.map((zona) => <option key={zona.valor} value={zona.valor}>{zona.etiqueta}</option>)}</select>
      </CampoCuenta>
      <p className="cuenta-ayuda">La zona horaria solo puede cambiarse antes de registrar programación.</p>
      <div className="cuenta-fila"><CampoCuenta etiqueta="Máximo diario por profesional" nombre="minutosDiarios" required
        pattern="[0-9]{1,3}:[0-5][0-9]" {...campo('minutosDiarios')} error={estado.errores.minutosDiarios} ayuda="Horas y minutos, de 0:01 a 24:00." />
        <CampoCuenta etiqueta="Máximo semanal por profesional" nombre="minutosSemanales" required
          pattern="[0-9]{1,3}:[0-5][0-9]" {...campo('minutosSemanales')} error={estado.errores.minutosSemanales} ayuda="De lunes a domingo. Ejemplo: 36:00." /></div>
      <label className="cuenta-opcion"><input type="checkbox" checked={datos.autoprogramacionMedica}
        onChange={(evento) => { const habilitada = evento.target.checked; cambiarDatos((anteriores) => ({ ...anteriores, autoprogramacionMedica: habilitada })); }}
        aria-describedby="ayuda-autoprogramacion" />
        <span><strong>Habilitar autoprogramación médica</strong><small id="ayuda-autoprogramacion">Permite que cada doctor cree, cambie o elimine sus propios turnos, eligiendo fecha, horas y un consultorio compatible con su especialidad. Se mantienen los límites y se impiden cruces de médicos y consultorios. El administrador conserva acceso y puede hacer cambios. Al desactivarla, los doctores solo consultan sus turnos; la programación existente se conserva.</small></span></label>
      <CampoCuenta etiqueta="Abono por feriado" nombre="minutosPorFeriado" required pattern="[0-9]{1,3}:[0-5][0-9]"
        {...campo('minutosPorFeriado')} error={estado.errores.minutosPorFeriado} ayuda="De 0:00 a 24:00. Usa 0:00 para desactivar el abono." />
      <p className="cuenta-aviso">Al reducir los límites se revisan los turnos futuros. Cambiar el abono recalcula los totales y las próximas exportaciones de todos los meses; los archivos ya descargados se conservan.</p>
      {administrador && <button className="boton boton--primario" type="submit">{estado.guardando ? 'Guardando…' : 'Guardar reglas institucionales'}</button>}
    </fieldset>
    {errorHoras && <p className="cuenta-aviso cuenta-aviso--error" role="alert">{errorHoras}</p>}
    <EstadoGuardado estado={estado} recargar={recargar} />
  </form>;
}

export default function Configuraciones() {
  const { cuenta, configuracion, actualizarCuenta, actualizarConfiguracion, recargarConfiguracion } = useOutletContext();
  return <><EncabezadoPagina titulo="Configuraciones" descripcion="Personaliza tu espacio y administra las reglas de programación." />
    <div className="cuenta-rejilla"><PreferenciasPersonales cuenta={cuenta} actualizar={actualizarCuenta} recargar={recargarConfiguracion} />
      <ConfiguracionInstitucional configuracion={configuracion} administrador={cuenta.esAdministrador}
        actualizar={actualizarConfiguracion} recargar={recargarConfiguracion} /></div></>;
}
