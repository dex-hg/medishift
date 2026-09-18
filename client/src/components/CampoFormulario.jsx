function CampoFormulario({ etiqueta, ayuda, error, requerido = false, ancho = 'normal', children }) {
  return (
    <div className={`campo campo--${ancho} ${error ? 'campo--error' : ''}`}>
      <label>
        <span>
          {etiqueta}
          {requerido && <abbr title="Obligatorio"> *</abbr>}
        </span>
        {children}
      </label>
      {error ? <small className="campo__error">{error}</small> : ayuda && <small>{ayuda}</small>}
    </div>
  );
}

export default CampoFormulario;

