export function CampoCuenta({ etiqueta, nombre, error, ayuda, children, ...atributos }) {
  return <label className="cuenta-campo" htmlFor={`cuenta-${nombre}`}>
    <span>{etiqueta}</span>{children || <input id={`cuenta-${nombre}`} name={nombre} aria-invalid={Boolean(error)}
      aria-describedby={error || ayuda ? `ayuda-${nombre}` : undefined} {...atributos} />}
    {(error || ayuda) && <small id={`ayuda-${nombre}`} className={error ? 'cuenta-error-campo' : ''}>{error || ayuda}</small>}
  </label>;
}

export function EstadoGuardado({ estado, recargar }) {
  return <>
    {estado.error && <div className="cuenta-aviso cuenta-aviso--error" role="alert"><p>{estado.error}</p>
      {estado.desactualizado && <button className="boton boton--secundario" type="button" onClick={recargar}>Recargar datos guardados</button>}</div>}
    {estado.mensaje && <p className="cuenta-aviso cuenta-aviso--exito" role="status">{estado.mensaje}</p>}
  </>;
}
