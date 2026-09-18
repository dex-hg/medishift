function Estado({ valor }) {
  const clave = valor.toLocaleLowerCase('es').replaceAll(' ', '-');
  return <span className={`estado estado--${clave}`}>{valor}</span>;
}

export default Estado;

