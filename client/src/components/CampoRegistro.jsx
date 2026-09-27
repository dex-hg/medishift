import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

function CampoRegistro({ identificador, nombre, etiqueta, ayuda, error, tipo = 'text', disabled, ...propiedades }) {
  const [visible, setVisible] = useState(false);
  const esContrasena = tipo === 'password';
  const descripcion = [ayuda && `${identificador}-ayuda`, error && `${identificador}-error`]
    .filter(Boolean).join(' ') || undefined;

  return (
    <div className={`registro-campo${error ? ' registro-campo--error' : ''}`}>
      <label htmlFor={identificador}>{etiqueta} <span aria-hidden="true">*</span></label>
      <div className="registro-campo__control">
        <input {...propiedades} id={identificador} name={nombre}
          type={esContrasena && visible ? 'text' : tipo}
          className={esContrasena ? 'registro-campo__contrasena' : undefined}
          aria-invalid={Boolean(error)} aria-describedby={descripcion} required disabled={disabled} />
        {esContrasena && (
          <button className="registro-campo__visibilidad" type="button" disabled={disabled}
            aria-label={`${visible ? 'Ocultar' : 'Mostrar'} ${etiqueta.toLowerCase()}`}
            aria-pressed={visible} aria-controls={identificador} onClick={() => setVisible(!visible)}>
            {visible ? <EyeOff size={19} aria-hidden="true" /> : <Eye size={19} aria-hidden="true" />}
          </button>
        )}
      </div>
      {ayuda && <small id={`${identificador}-ayuda`}>{ayuda}</small>}
      {error && <p className="registro-campo__error" id={`${identificador}-error`}>{error}</p>}
    </div>
  );
}

export default CampoRegistro;
