import { useEffect, useRef, useState } from 'react';
import { AlertCircle } from 'lucide-react';

function FormularioBase({ children, accionesSecundarias, onGuardar, guardando = false, bloquearGuardado = false, error = '', errores = {} }) {
  const formulario = useRef(null);
  const aviso = useRef(null);
  const solicitudEnCurso = useRef(false);
  const [errorLocal, setErrorLocal] = useState('');
  const [enviando, setEnviando] = useState(false);
  const ocupado = guardando || enviando;
  const mensaje = error || errorLocal;

  useEffect(() => {
    if (ocupado) return;
    const primerCampo = Array.from(formulario.current?.elements || [])
      .find((campo) => errores[campo.name]);
    if (primerCampo) primerCampo.focus();
    else if (mensaje) aviso.current?.focus();
  }, [errores, mensaje, ocupado]);

  const guardarFormulario = async (evento) => {
    evento.preventDefault();
    if (solicitudEnCurso.current || guardando || bloquearGuardado || !onGuardar) return;
    const elemento = evento.currentTarget;
    setErrorLocal('');
    if (!elemento.checkValidity()) {
      setErrorLocal('Revisa los campos obligatorios antes de continuar.');
      elemento.reportValidity();
      return;
    }
    solicitudEnCurso.current = true;
    setEnviando(true);
    try {
      await onGuardar(Object.fromEntries(new FormData(elemento)));
    } catch (fallo) {
      setErrorLocal(fallo.message || 'No se pudo guardar el registro. Inténtalo nuevamente.');
    } finally {
      solicitudEnCurso.current = false;
      setEnviando(false);
    }
  };

  return (
    <form ref={formulario} className="formulario" onSubmit={guardarFormulario} noValidate aria-busy={ocupado}>
      <fieldset disabled={ocupado} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        {children}
      </fieldset>
      {mensaje && (
        <div ref={aviso} className="aviso aviso--restriccion" role="alert" tabIndex={-1}>
          <AlertCircle size={18} aria-hidden="true" /><p>{mensaje}</p>
        </div>
      )}
      {!onGuardar && <p className="aviso">Este formulario está pendiente de conexión con el servidor.</p>}
      <div className="formulario__acciones">
        {accionesSecundarias}
        <button className="boton boton--primario" type="submit" disabled={ocupado || bloquearGuardado || !onGuardar}>
          {ocupado ? 'Guardando…' : 'Guardar'}
        </button>
      </div>
    </form>
  );
}

export default FormularioBase;
