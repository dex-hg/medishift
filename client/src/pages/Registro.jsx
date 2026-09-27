import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeft, ArrowRight, Building2, CalendarDays, Check, CheckCircle2,
  ShieldCheck, Sparkles, UserRound,
} from 'lucide-react';
import CampoRegistro from '../components/CampoRegistro';
import { registrarInstitucion } from '../services/registro';
import {
  LIMITES_REGISTRO, ZONAS_HORARIAS, normalizarDatosRegistro,
  validarCuenta, validarInstitucion,
} from '../utils/validacionesRegistro';

const datosIniciales = {
  codigoInstitucion: '', nombreInstitucion: '', zonaHoraria: 'America/Lima',
  correo: '', contrasena: '', confirmacionContrasena: '',
};

function Registro() {
  const identificador = useId();
  const tituloFormulario = useRef(null);
  const avisoResultado = useRef(null);
  const formulario = useRef(null);
  const campoConError = useRef(null);
  const solicitudEnCurso = useRef(false);
  const [paso, setPaso] = useState(1);
  const [datos, setDatos] = useState(datosIniciales);
  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState(null);

  useEffect(() => {
    const tituloAnterior = document.title;
    document.title = 'Registro de institución | MediShift';
    return () => { document.title = tituloAnterior; };
  }, []);

  useEffect(() => { tituloFormulario.current?.focus(); }, [paso]);
  useEffect(() => {
    if (enviando) return;
    if (campoConError.current) {
      Array.from(formulario.current?.elements || [])
        .find((campo) => campo.name === campoConError.current)?.focus();
      campoConError.current = null;
    } else if (mensaje || resultado) avisoResultado.current?.focus();
  }, [errores, mensaje, paso, enviando, resultado]);

  const actualizarCampo = (evento) => {
    if (solicitudEnCurso.current) return;
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

  const mostrarErrores = (erroresNuevos) => {
    const camposInstitucion = ['nombreInstitucion', 'codigoInstitucion', 'zonaHoraria'];
    const ordenCampos = [...camposInstitucion, 'correo', 'contrasena', 'confirmacionContrasena'];
    campoConError.current = ordenCampos.find((campo) => erroresNuevos[campo]) || null;
    if (camposInstitucion.some((campo) => erroresNuevos[campo])) setPaso(1);
    setErrores(erroresNuevos);
  };

  const revisarPaso = async (evento) => {
    evento.preventDefault();
    if (solicitudEnCurso.current || resultado) return;
    const datosNormalizados = normalizarDatosRegistro(datos);
    const erroresInstitucion = validarInstitucion(datosNormalizados);
    const erroresPaso = paso === 1 ? erroresInstitucion : {
      ...erroresInstitucion, ...validarCuenta(datosNormalizados),
    };
    setDatos(datosNormalizados);
    mostrarErrores(erroresPaso);
    setMensaje('');

    if (Object.keys(erroresPaso).length) {
      return;
    }

    if (paso === 1) {
      setPaso(2);
      return;
    }
    solicitudEnCurso.current = true;
    setEnviando(true);
    try {
      const cuentaCreada = await registrarInstitucion(datosNormalizados);
      setDatos((anteriores) => ({ ...anteriores, contrasena: '', confirmacionContrasena: '' }));
      setResultado(cuentaCreada);
    } catch (error) {
      const erroresRespuesta = { ...(error.errores || {}) };
      if (error.estado === 409 && !erroresRespuesta.codigoInstitucion) {
        erroresRespuesta.codigoInstitucion = 'El código de institución ya está registrado.';
      }
      mostrarErrores(erroresRespuesta);
      setMensaje(error.message || 'No se pudo completar el registro. Inténtalo nuevamente.');
    } finally {
      solicitudEnCurso.current = false;
      setEnviando(false);
    }
  };

  const volverInstitucion = () => {
    if (solicitudEnCurso.current) return;
    setErrores({});
    setMensaje('');
    setPaso(1);
  };

  const registrarOtraInstitucion = () => {
    setResultado(null);
    setDatos(datosIniciales);
    volverInstitucion();
  };

  const evitarSalidaEnCurso = (evento) => {
    if (solicitudEnCurso.current) evento.preventDefault();
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
      ayuda: 'Elige una contraseña personal. Hasta 1024 caracteres.', autoComplete: 'new-password' },
    { nombre: 'confirmacionContrasena', etiqueta: 'Confirmar contraseña', tipo: 'password',
      autoComplete: 'new-password' },
  ];

  return (
    <div className="registro">
      <header className="registro__encabezado">
        <Link className="portada-marca" to="/" aria-label="MediShift, página principal"
          onClick={evitarSalidaEnCurso} aria-disabled={enviando || undefined} tabIndex={enviando ? -1 : undefined}>
          <span className="portada-marca__simbolo" aria-hidden="true"><Sparkles size={20} /></span>
          <span><strong>MediShift</strong><small>Gestión operativa</small></span>
        </Link>
        <Link className="registro__volver" to="/" onClick={evitarSalidaEnCurso}
          aria-disabled={enviando || undefined} tabIndex={enviando ? -1 : undefined}>
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
          {resultado ? (
            <div className="registro-confirmacion" ref={avisoResultado} tabIndex={-1} role="status">
              <CheckCircle2 size={38} aria-hidden="true" />
              <h2 id={`${identificador}-formulario`}>Registro completado</h2>
              <p>La institución y su primera cuenta se han creado correctamente.</p>
              <dl>
                <dt>Institución</dt><dd>{resultado.nombreInstitucion}</dd>
                <dt>Código</dt><dd>{resultado.codigoInstitucion}</dd>
                <dt>Correo de la cuenta</dt><dd>{resultado.correo}</dd>
              </dl>
              <p>El inicio de sesión estará disponible en la siguiente etapa.</p>
              <div className="registro-formulario__acciones">
                <Link className="boton boton--primario" to="/">Volver al inicio</Link>
                <button className="boton boton--fantasma" type="button" onClick={registrarOtraInstitucion}>
                  Registrar otra institución
                </button>
              </div>
            </div>
          ) : <>
          <ol className="registro-pasos" aria-label="Pasos del registro">
            {[{ numero: 1, nombre: 'Institución' }, { numero: 2, nombre: 'Primera cuenta' }].map((etapa) => (
              <li key={etapa.numero} className={paso >= etapa.numero ? 'registro-pasos__activo' : ''}
                aria-current={paso === etapa.numero ? 'step' : undefined}>
                <span aria-hidden="true">{paso > etapa.numero ? <Check size={16} /> : etapa.numero}</span>
                {etapa.nombre}
              </li>
            ))}
          </ol>

          <form ref={formulario} className="registro-formulario" onSubmit={revisarPaso} noValidate aria-busy={enviando}>
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
                <button type="button" onClick={volverInstitucion} disabled={enviando}>Editar</button>
              </div>
            )}

            <p className="registro-formulario__obligatorios">Todos los campos son obligatorios.</p>
            <div className="registro-formulario__campos">
              {(paso === 1 ? camposInstitucion : camposCuenta).map((campo) => (
                <CampoRegistro key={campo.nombre} {...campo}
                  identificador={`${identificador}-${campo.nombre}`}
                  value={datos[campo.nombre]} error={errores[campo.nombre]} onChange={actualizarCampo}
                  maxLength={campo.maxLength} disabled={enviando} />
              ))}
              {paso === 1 && (
                <div className={`registro-campo${errores.zonaHoraria ? ' registro-campo--error' : ''}`}>
                  <label htmlFor={`${identificador}-zonaHoraria`}>Zona horaria <span aria-hidden="true">*</span></label>
                  <select id={`${identificador}-zonaHoraria`} name="zonaHoraria" value={datos.zonaHoraria}
                    onChange={actualizarCampo} required disabled={enviando} aria-invalid={Boolean(errores.zonaHoraria)}
                    aria-describedby={`${identificador}-zona-ayuda${errores.zonaHoraria ? ` ${identificador}-zona-error` : ''}`}>
                    {ZONAS_HORARIAS.map((zona) => <option key={zona.valor} value={zona.valor}>{zona.etiqueta}</option>)}
                  </select>
                  <small id={`${identificador}-zona-ayuda`}>Se usará para mostrar los horarios de atención.</small>
                  {errores.zonaHoraria && <p className="registro-campo__error" id={`${identificador}-zona-error`}>{errores.zonaHoraria}</p>}
                </div>
              )}
            </div>

            {mensaje && (
              <div className="registro-resultado registro-resultado--error" ref={avisoResultado} tabIndex={-1} role="alert">
                <p>{mensaje}</p>
              </div>
            )}
            <div className="registro-formulario__acciones">
              {paso === 2 && <button className="boton boton--fantasma" type="button" onClick={volverInstitucion} disabled={enviando}>
                <ArrowLeft size={17} aria-hidden="true" /> Atrás
              </button>}
              <button className="boton boton--primario" type="submit" disabled={enviando}>
                {enviando ? 'Registrando…' : paso === 1 ? 'Continuar' : 'Crear institución y cuenta'}
                <ArrowRight size={18} aria-hidden="true" />
              </button>
            </div>
            <p className="registro-formulario__alcance" role={enviando ? 'status' : undefined}>
              {enviando ? 'Estamos creando la institución y su primera cuenta.' : 'La cuenta quedará vinculada a esta institución.'}
            </p>
          </form>
          </>}
        </section>
      </main>
      <footer className="registro__pie">MediShift · Gestión de profesionales, consultorios y horarios.</footer>
    </div>
  );
}

export default Registro;
