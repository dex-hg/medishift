package com.dextre.medishift.portalpaciente;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.atencion.RepositorioPacientes;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.portalpaciente.RepositorioPortalPaciente.CitaPortal;
import com.dextre.medishift.portalpaciente.RepositorioPortalPaciente.Hospital;
import com.dextre.medishift.programacion.mensual.PeriodoMensual;
import com.dextre.medishift.sesion.AccesoNoAutorizadoException;

@Service
public class ServicioPortalPaciente {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final long VENTANA_INTENTOS = Duration.ofMinutes(10).toNanos();
    private static final int MAXIMO_INTENTOS = 8;
    private static final int MAXIMO_DNI_CONTROLADOS = 10_000;

    private final RepositorioPortalPaciente portal;
    private final RepositorioPacientes pacientes;
    private final RepositorioEspecialidades exclusiones;
    private final ServicioAccesoMensual accesos;
    private final PasswordEncoder codificador;
    private final String hashFicticio;
    private final LinkedHashMap<String, Intentos> intentos = new LinkedHashMap<>(128, 0.75f, true);

    public record EstadoAcceso(boolean activado, Instant venceEn) { }
    public record CodigoEmitido(String codigo, Instant venceEn) {
        @Override public String toString() { return "CodigoEmitido[datos omitidos]"; }
    }
    public record Calendario(String periodo, List<Hospital> hospitales, List<CitaPortal> citas) { }
    private record Intentos(long inicio, int cantidad) { }

    public ServicioPortalPaciente(RepositorioPortalPaciente portal, RepositorioPacientes pacientes,
            RepositorioEspecialidades exclusiones, ServicioAccesoMensual accesos, PasswordEncoder codificador) {
        this.portal = portal;
        this.pacientes = pacientes;
        this.exclusiones = exclusiones;
        this.accesos = accesos;
        this.codificador = codificador;
        this.hashFicticio = codificador.encode("verificacion-interna-portal-paciente");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EstadoAcceso estado(UUID institucion, UUID actor, UUID idPaciente) {
        accesos.exigirAdministrador(institucion, actor);
        pacientes.verificarEsquema();
        var ficha = pacientes.buscar(institucion, idPaciente);
        boolean activado = portal.cuentaExiste(ficha.dni());
        return new EstadoAcceso(activado, ficha.activo() && !activado
                ? portal.vencimiento(institucion, ficha.id(), ficha.dni()) : null);
    }

    @Transactional
    public CodigoEmitido emitir(UUID institucion, UUID actor, UUID idPaciente, String revision) {
        exclusiones.bloquearInstitucion(institucion);
        accesos.exigirAdministrador(institucion, actor);
        pacientes.verificarEsquema();
        var ficha = pacientes.buscar(institucion, idPaciente);
        if (!ficha.revision().equals(revision)) {
            throw CatalogoException.conflicto("revision", "La ficha cambió. Recárgala y verifica el DNI nuevamente.");
        }
        if (!ficha.activo()) throw CatalogoException.conflicto("activo", "La ficha debe estar activa.");
        portal.bloquearDni(ficha.dni());
        if (portal.cuentaExiste(ficha.dni())) {
            throw CatalogoException.conflicto("accesoPortal", "El paciente ya configuró su acceso.");
        }
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant vence = portal.emitir(institucion, idPaciente, ficha.dni(), huellaCodigo(codigo));
        pacientes.auditar(institucion, actor, idPaciente, "emision_codigo_portal");
        return new CodigoEmitido(codigo, vence);
    }

    @Transactional
    public UUID activar(String dni, String codigo, String contrasena) {
        portal.bloquearDni(dni);
        if (portal.cuentaExiste(dni) || !portal.consumirCodigo(dni, huellaCodigo(codigo))) {
            throw CatalogoException.autenticacionFallida("El código no es válido o ya venció. Solicita otro en el hospital.");
        }
        return portal.crearCuenta(dni, codificador.encode(contrasena));
    }

    public UUID autenticar(String dni, String contrasena) {
        registrarIntento(dni);
        var credencial = portal.credencial(dni).orElse(null);
        try {
            boolean coincide = codificador.matches(contrasena, credencial == null ? hashFicticio : credencial.hash());
            if (coincide && credencial != null) {
                synchronized (this) { intentos.remove(dni); }
                return credencial.id();
            }
        } catch (IllegalArgumentException fallo) {
            codificador.matches(contrasena, hashFicticio);
        }
        throw CatalogoException.autenticacionFallida("El DNI o la contraseña no son correctos.");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calendario calendario(UUID cuenta, String textoPeriodo, PeriodoMensual periodo) {
        if (!portal.cuentaExiste(cuenta)) throw new AccesoNoAutorizadoException();
        return new Calendario(textoPeriodo, portal.hospitales(cuenta), portal.citas(cuenta, periodo));
    }

    private synchronized void registrarIntento(String dni) {
        long ahora = System.nanoTime();
        Intentos ventana = intentos.get(dni);
        if (ventana == null || ahora - ventana.inicio() >= VENTANA_INTENTOS) {
            intentos.put(dni, new Intentos(ahora, 1));
            if (intentos.size() > MAXIMO_DNI_CONTROLADOS) intentos.pollFirstEntry();
        } else if (ventana.cantidad() >= MAXIMO_INTENTOS) {
            throw CatalogoException.limiteIntentos("Se alcanzó el límite de intentos para este DNI. Inténtalo en unos minutos.");
        } else {
            intentos.put(dni, new Intentos(ventana.inicio(), ventana.cantidad() + 1));
        }
    }

    private static String huellaCodigo(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(codigo.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException fallo) {
            throw new IllegalStateException("SHA-256 no está disponible.", fallo);
        }
    }
}
