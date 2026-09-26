import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeft, ArrowRight, Building2, CalendarDays, Check, CheckCircle2,
  Eye, EyeOff, ShieldCheck, Sparkles, UserRound,
} from 'lucide-react';
import {
  LIMITES_REGISTRO, ZONAS_HORARIAS, normalizarDatosRegistro,
  validarCuenta, validarInstitucion,
} from '../utils/validacionesRegistro';

const datosIniciales = {
  codigoInstitucion: '', nombreInstitucion: '', zonaHoraria: 'America/Lima',
  correo: '', contrasena: '', confirmacionContrasena: '',
};

function CampoRegistro({ identificador, nombre, etiqueta, ayuda, error, tipo = 'text', ...propiedades }) {
  const [visible, setVisible] = useState(false);
  const esContrasena = tipo === 'password';
  const descripcion = [ayuda && `${identificador}-ayuda`, error && `${identificador}-error`]
    .filter(Boolean).join(' ') || undefined;

  return (
    <div className={`registro-campo${error ? ' registro-campo--error' : ''}`}>
      <label htmlFor={identificador}>{etiqueta} <span aria-hidden="true">*</span></label>
      <div className="registro-campo__control">
        <input
          {...propiedades}
          id={identificador}
          name={nombre}
          type={esContrasena && visible ? 'text' : tipo}
          className={esContrasena ? 'registro-campo__contrasena' : undefined}
          aria-invalid={Boolean(error)}
          aria-describedby={descripcion}
          required
        />
        {esContrasena && (
          <button
            className="registro-campo__visibilidad"
            type="button"
            aria-label={`${visible ? 'Ocultar' : 'Mostrar'} ${etiqueta.toLowerCase()}`}
            aria-pressed={visible}
            aria-controls={identificador}
            onClick={() => setVisible(!visible)}
          >
            {visible ? <EyeOff size={19} aria-hidden="true" /> : <Eye size={19} aria-hidden="true" />}
          </button>
        )}
      </div>
      {ayuda && <small id={`${identificador}-ayuda`}>{ayuda}</small>}
      {error && <p className="registro-campo__error" id={`${identificador}-error`}>{error}</p>}
    </div>
  );
}

function Registro() {
  const identificador = useId();
  const tituloFormulario = useRef(null);
  const avisoResultado = useRef(null);
  const campoConError = useRef(null);
  const [paso, setPaso] = useState(1);
  const [datos, setDatos] = useState(datosIniciales);
  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');

  useEffect(() => {
    const tituloAnterior = document.title;
    document.title = 'Registro de institución | MediShift';
    return () => { document.title = tituloAnterior; };
  }, []);

  useEffect(() => { tituloFormulario.current?.focus(); }, [paso]);
  useEffect(() => { if (mensaje) avisoResultado.current?.focus(); }, [mensaje]);
  useEffect(() => {
    campoConError.current?.focus();
    campoConError.current = null;
  }, [errores]);

  const actualizarCampo = (evento) => {
    const { name: nombre, value: valor } = evento.target;
    setDatos((anteriores) => ({ ...anteriores, [nombre]: valor }));
    setMensaje('');
    setErrores((anteriores) => {
      const siguientes = { ...anteriores };
      delete siguientes[nombre];
      if (nombre === 'contrasena') delete siguientes.confirmacionContrasena;
      return siguientes;
    });
  };

  const revisarPaso = (evento) => {
    evento.preventDefault();
    const datosNormalizados = normalizarDatosRegistro(datos);
    const erroresInstitucion = validarInstitucion(datosNormalizados);
    const erroresPaso = paso === 1 ? erroresInstitucion : {
      ...erroresInstitucion, ...validarCuenta(datosNormalizados),
    };
    setDatos(datosNormalizados);
    setErrores(erroresPaso);
    setMensaje('');

    if (Object.keys(erroresPaso).length) {
      if (paso === 2 && Object.keys(erroresInstitucion).length) {
        setPaso(1);
      } else {
        campoConError.current = Array.from(evento.currentTarget.elements)
          .find((campo) => erroresPaso[campo.name]);
      }
      return;
    }

    if (paso === 1) {
      setPaso(2);
      return;
    }
    setMensaje('Los datos son válidos. El registro todavía no se ha enviado.');
  };

  const volverInstitucion = () => {
    setErrores({});
    setMensaje('');
    setPaso(1);
  };

  const camposInstitucion = [
    { nombre: 'nombreInstitucion', etiqueta: 'Nombre de la institución',
      ayuda: 'Nombre del hospital o clínica. Hasta 140 caracteres.', autoComplete: 'organization',
      placeholder: 'Nombre de tu institución' },
    { nombre: 'codigoInstitucion', etiqueta: 'Código de institución',
      ayuda: 'Identificador único en MediShift. Hasta 32 caracteres.', autoComplete: 'off',
      placeholder: 'Por ejemplo: CLINICA-SUR' },
  ];
  const camposCuenta = [
    { nombre: 'correo', etiqueta: 'Correo de la primera cuenta', tipo: 'email',
      ayuda: 'Se usará como identificador para acceder.', autoComplete: 'email',
      placeholder: 'nombre@institucion.pe', maxLength: LIMITES_REGISTRO.correo },
    { nombre: 'contrasena', etiqueta: 'Contraseña', tipo: 'password',
      ayuda: 'Elige una contraseña personal y no la compartas.', autoComplete: 'new-password' },
    { nombre: 'confirmacionContrasena', etiqueta: 'Confirmar contraseña', tipo: 'password',
      autoComplete: 'new-password' },
  ];

  return (
    <div className="registro">
      <header className="registro__encabezado">
        <Link className="portada-marca" to="/" aria-label="MediShift, página principal">
          <span className="portada-marca__simbolo" aria-hidden="true"><Sparkles size={20} /></span>
          <span><strong>MediShift</strong><small>Gestión operativa</small></span>
        </Link>
        <Link className="registro__volver" to="/">
          <ArrowLeft size={16} aria-hidden="true" /> Volver al inicio
        </Link>
      </header>

      <main className="registro__contenido">
        <section className="registro-presentacion" aria-labelledby={`${identificador}-titulo`}>
          <span className="registro-presentacion__etiqueta">
            <Building2 size={16} aria-hidden="true" /> Registro de institución
          </span>
          <h1 id={`${identificador}-titulo`}>El primer paso para organizar tu institución.</h1>
          <p>Reúne la información de tu hospital o clínica y prepara su primera cuenta en MediShift.</p>
          <ul className="registro-presentacion__beneficios">
            <li><Building2 size={20} aria-hidden="true" /><div><strong>Una institución identificada</strong>
              <span>Un nombre y un código propio para organizar sus recursos.</span></div></li>
            <li><CalendarDays size={20} aria-hidden="true" /><div><strong>Horarios en su zona local</strong>
              <span>La zona horaria de referencia para la programación.</span></div></li>
            <li><ShieldCheck size={20} aria-hidden="true" /><div><strong>Una primera cuenta</strong>
              <span>Un correo de acceso vinculado a la institución.</span></div></li>
          </ul>
          <p className="registro-presentacion__nota">¿Una clínica o un hospital? El registro empieza con los mismos datos.</p>
        </section>

        <section className="registro-tarjeta" aria-labelledby={`${identificador}-formulario`}>
          <ol className="registro-pasos" aria-label="Pasos del registro">
            {[{ numero: 1, nombre: 'Institución' }, { numero: 2, nombre: 'Primera cuenta' }].map((etapa) => (
              <li key={etapa.numero} className={paso >= etapa.numero ? 'registro-pasos__activo' : ''}
                aria-current={paso === etapa.numero ? 'step' : undefined}>
                <span aria-hidden="true">{paso > etapa.numero ? <Check size={16} /> : etapa.numero}</span>
                {etapa.nombre}
              </li>
            ))}
          </ol>

          <form className="registro-formulario" onSubmit={revisarPaso} noValidate>
            <div className="registro-formulario__titulo">
              <span className="registro-formulario__icono" aria-hidden="true">
                {paso === 1 ? <Building2 size={22} /> : <UserRound size={22} />}
              </span>
              <div>
                <h2 ref={tituloFormulario} tabIndex={-1} id={`${identificador}-formulario`}>
                  {paso === 1 ? 'Datos de la institución' : 'Datos de la primera cuenta'}
                </h2>
                <p>{paso === 1 ? 'Identifica el centro que usará MediShift.' : 'Esta cuenta pertenecerá a tu institución.'}</p>
              </div>
            </div>

            {paso === 2 && (
              <div className="registro-institucion">
                <Building2 size={18} aria-hidden="true" />
                <div><strong>{datos.nombreInstitucion}</strong><span>{datos.codigoInstitucion} · {datos.zonaHoraria}</span></div>
                <button type="button" onClick={volverInstitucion}>Editar</button>
              </div>
            )}

            <p className="registro-formulario__obligatorios">Todos los campos son obligatorios.</p>
            <div className="registro-formulario__campos">
              {(paso === 1 ? camposInstitucion : camposCuenta).map((campo) => (
                <CampoRegistro key={campo.nombre} {...campo}
                  identificador={`${identificador}-${campo.nombre}`}
                  value={datos[campo.nombre]} error={errores[campo.nombre]} onChange={actualizarCampo}
                  maxLength={campo.maxLength} />
              ))}
              {paso === 1 && (
                <div className={`registro-campo${errores.zonaHoraria ? ' registro-campo--error' : ''}`}>
                  <label htmlFor={`${identificador}-zonaHoraria`}>Zona horaria <span aria-hidden="true">*</span></label>
                  <select id={`${identificador}-zonaHoraria`} name="zonaHoraria" value={datos.zonaHoraria}
                    onChange={actualizarCampo} required aria-invalid={Boolean(errores.zonaHoraria)}
                    aria-describedby={`${identificador}-zona-ayuda${errores.zonaHoraria ? ` ${identificador}-zona-error` : ''}`}>
                    {ZONAS_HORARIAS.map((zona) => <option key={zona.valor} value={zona.valor}>{zona.etiqueta}</option>)}
                  </select>
                  <small id={`${identificador}-zona-ayuda`}>Se usará para mostrar los horarios de atención.</small>
                  {errores.zonaHoraria && <p className="registro-campo__error" id={`${identificador}-zona-error`}>{errores.zonaHoraria}</p>}
                </div>
              )}
            </div>

            {mensaje && (
              <div className="registro-resultado" ref={avisoResultado} tabIndex={-1} role="status">
                <CheckCircle2 size={20} aria-hidden="true" /><p>{mensaje}</p>
              </div>
            )}
            <div className="registro-formulario__acciones">
              {paso === 2 && <button className="boton boton--fantasma" type="button" onClick={volverInstitucion}>
                <ArrowLeft size={17} aria-hidden="true" /> Atrás
              </button>}
              <button className="boton boton--primario" type="submit">
                {paso === 1 ? 'Continuar' : 'Revisar registro'}<ArrowRight size={18} aria-hidden="true" />
              </button>
            </div>
            <p className="registro-formulario__alcance">Por ahora puedes revisar tus datos. La creación de cuentas se habilitará en el siguiente paso.</p>
          </form>
        </section>
      </main>
      <footer className="registro__pie">MediShift · Gestión de profesionales, consultorios y horarios.</footer>
    </div>
  );
}

export default Registro;
