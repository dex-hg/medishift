import { useCallback, useState } from 'react';
import { Copy, KeyRound } from 'lucide-react';
import VentanaPlanificador from '../schedules/VentanaPlanificador';
import { useConsultaAtencion, useOperacionAtencion } from '../../hooks/useAtencion';
import { emitirCodigoPaciente, obtenerAccesoPaciente } from '../../services/portalPaciente';

export default function PanelAccesoPaciente({ paciente, zonaHoraria }) {
  const { id, revision } = paciente;
  const consulta = useConsultaAtencion(useCallback((opciones) => obtenerAccesoPaciente(id, opciones), [id, revision]));
  const operacion = useOperacionAtencion();
  const [abierto, cambiarAbierto] = useState(false);
  const [codigo, cambiarCodigo] = useState(null);
  const [aviso, cambiarAviso] = useState('');
  const fecha = (instante) => new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short',
    timeZone: zonaHoraria }).format(new Date(instante));
  const cerrar = () => {
    cambiarAbierto(false);
    cambiarCodigo(null);
    cambiarAviso('');
    operacion.limpiar();
    consulta.recargar();
  };
  const generar = async () => {
    const respuesta = await operacion.ejecutar(() => emitirCodigoPaciente(id, { revision }));
    if (respuesta) cambiarCodigo(respuesta);
  };
  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(codigo.codigo);
      cambiarAviso('Código copiado.');
    } catch {
      cambiarAviso('Selecciona el código para copiarlo.');
    }
  };

  return <section className="panel atencion-historial" aria-busy={consulta.cargando}>
    <h2>Acceso al portal del paciente</h2>
    {consulta.cargando && <p role="status">Comprobando acceso…</p>}
    {consulta.error && <div role="alert"><p>{consulta.error}</p>
      <button className="boton boton--secundario" type="button" onClick={consulta.recargar}>Reintentar</button>
    </div>}
    {consulta.datos?.activado && <p>El paciente ya configuró su contraseña e ingresa con su DNI.</p>}
    {consulta.datos && !consulta.datos.activado && <>
      <p>{consulta.datos.venceEn ? 'Código vigente hasta el ' + fecha(consulta.datos.venceEn) + '.'
        : 'El paciente aún no ha activado su acceso.'}</p>
      <button className="boton boton--secundario" type="button" onClick={() => cambiarAbierto(true)}>
        <KeyRound size={16} aria-hidden="true" /> {consulta.datos.venceEn ? 'Generar nuevo código' : 'Generar código de activación'}
      </button>
    </>}
    {abierto && <VentanaPlanificador titulo={codigo ? 'Código de activación' : 'Verificar DNI del paciente'}
      descripcion={paciente.nombres + ' ' + paciente.apellidos} ocupado={operacion.guardando} cerrar={cerrar}>
      {codigo ? <>
        <p>Entrega este código al paciente. Vence el {fecha(codigo.venceEn)} y se muestra una sola vez.</p>
        <label className="campo-planificador"><span>Código de un solo uso</span>
          <input value={codigo.codigo} readOnly onFocus={(evento) => evento.currentTarget.select()} />
        </label>
        <p role="status">{aviso}</p>
        <div className="ventana-planificador__acciones">
          <button className="boton boton--fantasma" type="button" onClick={cerrar}>Cerrar</button>
          {navigator.clipboard && <button className="boton boton--primario" type="button" onClick={copiar}>
            <Copy size={16} aria-hidden="true" /> Copiar código
          </button>}
        </div>
      </> : <>
        <p>Compara el documento presentado con el DNI <strong>{paciente.dni}</strong> de esta ficha antes de generar el código.</p>
        {consulta.datos?.venceEn && <p>El código nuevo reemplaza al que ya se emitió.</p>}
        {operacion.error && <p className="planificador-error" role="alert">{operacion.error}</p>}
        <div className="ventana-planificador__acciones">
          <button className="boton boton--fantasma" type="button" disabled={operacion.guardando} onClick={cerrar}>Volver</button>
          <button className="boton boton--primario" type="button" disabled={operacion.guardando} onClick={generar}>
            {operacion.guardando ? 'Generando…' : 'DNI verificado, generar código'}
          </button>
        </div>
      </>}
    </VentanaPlanificador>}
  </section>;
}
