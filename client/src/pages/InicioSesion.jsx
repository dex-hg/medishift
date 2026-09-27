import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Building2, Info, LogIn, ShieldCheck, Sparkles } from 'lucide-react';
import CampoRegistro from '../components/CampoRegistro';
import {
  LIMITES_INICIO_SESION, normalizarDatosInicioSesion, validarInicioSesion,
} from '../utils/validacionesInicioSesion';

function obtenerDatosIniciales(estado) {
  const datosPublicos = estado && typeof estado === 'object' && !Array.isArray(estado) ? estado : {};
  return normalizarDatosInicioSesion({
    codigoInstitucion: typeof datosPublicos.codigoInstitucion === 'string' ? datosPublicos.codigoInstitucion : '',
    correo: typeof datosPublicos.correo === 'string' ? datosPublicos.correo : '',
    contrasena: '',
  });
}

function InicioSesion() {
  const ubicacion = useLocation();
  const identificador = useId();
  const formulario = useRef(null);
  const tituloFormulario = useRef(null);
  const avisoAcceso = useRef(null);
  const campoConError = useRef(null);
  const [datos, setDatos] = useState(() => obtenerDatosIniciales(ubicacion.state));
  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');

  useEffect(() => {
    const tituloAnterior = document.title;
    document.title = 'Iniciar sesión | MediShift';
    tituloFormulario.current?.focus();
    return () => { document.title = tituloAnterior; };
  }, []);

  useEffect(() => {
    if (campoConError.current) {
      Array.from(formulario.current?.elements || [])
        .find((campo) => campo.name === campoConError.current)?.focus();
      campoConError.current = null;
    } else if (mensaje) avisoAcceso.current?.focus();
  }, [errores, mensaje]);

  const actualizarCampo = (evento) => {
    const { name: nombre, value: valor } = evento.target;
    setDatos((anteriores) => ({ ...anteriores, [nombre]: valor }));
    setMensaje('');
    setErrores((anteriores) => {
      const siguientes = { ...anteriores };
      delete siguientes[nombre];
      return siguientes;
    });
  };

  const revisarAcceso = (evento) => {
    evento.preventDefault();
    const datosNormalizados = normalizarDatosInicioSesion(datos);
    const erroresAcceso = validarInicioSesion(datosNormalizados);
    setDatos(datosNormalizados);
    setErrores(erroresAcceso);
    campoConError.current = Array.from(evento.currentTarget.elements)
      .find((campo) => erroresAcceso[campo.name])?.name || null;
    setMensaje(Object.keys(erroresAcceso).length ? '' : 'El inicio de sesión aún no está habilitado.');
  };

  const campos = [
    {
      nombre: 'codigoInstitucion', etiqueta: 'Código de institución', autoComplete: 'off',
      ayuda: 'El código con el que se registró tu institución.', placeholder: 'Por ejemplo: CLINICA-SUR',
    },
    {
      nombre: 'correo', etiqueta: 'Correo de tu cuenta', tipo: 'email', autoComplete: 'username',
      ayuda: 'El correo vinculado a esta institución.', placeholder: 'nombre@institucion.pe',
      maxLength: LIMITES_INICIO_SESION.correo,
    },
    {
      nombre: 'contrasena', etiqueta: 'Contraseña', tipo: 'password', autoComplete: 'current-password',
      ayuda: 'Usa la contraseña de tu cuenta.',
    },
  ];

  return (
    <div className="registro inicio-sesion">
      <header className="registro__encabezado">
        <Link className="portada-marca" to="/" aria-label="MediShift, página principal">
          <span className="portada-marca__simbolo" aria-hidden="true"><Sparkles size={20} /></span>
          <span><strong>MediShift</strong><small>Gestión operativa</small></span>
        </Link>
        <Link className="registro__volver" to="/">
          <ArrowLeft size={16} aria-hidden="true" /> Volver al inicio
        </Link>
      </header>

      <main className="registro__contenido inicio-sesion__contenido">
        <section className="registro-presentacion inicio-sesion__presentacion" aria-labelledby={`${identificador}-titulo`}>
          <span className="registro-presentacion__etiqueta">
            <LogIn size={16} aria-hidden="true" /> Acceso a MediShift
          </span>
          <h1 id={`${identificador}-titulo`}>Tu institución, sus recursos y cada turno.</h1>
          <p>Un espacio para organizar profesionales, consultorios y horarios de atención.</p>
          <ul className="registro-presentacion__beneficios">
            <li><Building2 size={20} aria-hidden="true" /><div>
              <strong>El código identifica tu institución</strong>
              <span>Cada cuenta pertenece a una clínica u hospital.</span>
            </div></li>
            <li><ShieldCheck size={20} aria-hidden="true" /><div>
              <strong>Una cuenta personal</strong>
              <span>Usa tu correo y conserva tu contraseña en privado.</span>
            </div></li>
          </ul>
        </section>

        <section className="registro-tarjeta inicio-sesion__tarjeta" aria-labelledby={`${identificador}-formulario`}>
          <form ref={formulario} className="registro-formulario" onSubmit={revisarAcceso} noValidate>
            <div className="registro-formulario__titulo">
              <span className="registro-formulario__icono" aria-hidden="true"><LogIn size={22} /></span>
              <div>
                <h2 ref={tituloFormulario} tabIndex={-1} id={`${identificador}-formulario`}>Iniciar sesión</h2>
                <p>Ingresa los datos de tu cuenta.</p>
              </div>
            </div>
            <p className="registro-formulario__obligatorios">Todos los campos son obligatorios.</p>
            <div className="registro-formulario__campos">
              {campos.map((campo) => (
                <CampoRegistro key={campo.nombre} {...campo}
                  identificador={`${identificador}-${campo.nombre}`} value={datos[campo.nombre]}
                  error={errores[campo.nombre]} onChange={actualizarCampo} />
              ))}
            </div>
            {mensaje && (
              <div className="inicio-sesion__aviso" ref={avisoAcceso} tabIndex={-1} role="status">
                <Info size={20} aria-hidden="true" /><p>{mensaje}</p>
              </div>
            )}
            <div className="registro-formulario__acciones">
              <button className="boton boton--primario" type="submit">
                Iniciar sesión <ArrowRight size={18} aria-hidden="true" />
              </button>
            </div>
            <p className="registro-formulario__alcance">El acceso estará disponible próximamente.</p>
            <p className="inicio-sesion__registro">¿Tu institución aún no está registrada?{' '}
              <Link className="inicio-sesion__enlace" to="/registro">Registrar institución</Link>
            </p>
          </form>
        </section>
      </main>
      <footer className="registro__pie">MediShift · Gestión de profesionales, consultorios y horarios.</footer>
    </div>
  );
}

export default InicioSesion;
