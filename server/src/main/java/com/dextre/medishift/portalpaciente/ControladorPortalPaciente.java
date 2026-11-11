package com.dextre.medishift.portalpaciente;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.mensual.ValidadorPlanesMensuales;
import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.SesionesHttp;

@RestController
@RequestMapping("/api/portal-paciente")
public class ControladorPortalPaciente {
    private final ServicioPortalPaciente servicio;
    private final AccesoCatalogos acceso;

    public ControladorPortalPaciente(ServicioPortalPaciente servicio, AccesoCatalogos acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @PostMapping(value = "/sesion", consumes = "application/json")
    public ResponseEntity<Void> iniciar(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
        acceso.validarOrigen(solicitud);
        Credenciales credenciales = validar(datos, false);
        return abrirSesion(solicitud, servicio.autenticar(credenciales.dni(), credenciales.contrasena()));
    }

    @PostMapping(value = "/activacion", consumes = "application/json")
    public ResponseEntity<Void> activar(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
        acceso.validarOrigen(solicitud);
        Credenciales credenciales = validar(datos, true);
        return abrirSesion(solicitud, servicio.activar(credenciales.dni(), credenciales.codigo(), credenciales.contrasena()));
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrar(HttpServletRequest solicitud) {
        acceso.validarOrigen(solicitud);
        HttpSession sesion = solicitud.getSession(false);
        if (SesionesHttp.identificarPortal(sesion) != null) SesionesHttp.invalidar(sesion);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendario")
    public ServicioPortalPaciente.Calendario calendario(@RequestParam String periodo, HttpServletRequest solicitud) {
        UUID cuenta = SesionesHttp.identificarPortal(solicitud.getSession(false));
        if (cuenta == null) throw new AccesoNoAutorizadoException();
        return servicio.calendario(cuenta, periodo, ValidadorPlanesMensuales.periodo(periodo));
    }

    private static ResponseEntity<Void> abrirSesion(HttpServletRequest solicitud, UUID cuenta) {
        SesionesHttp.abrirPortal(solicitud, cuenta);
        return ResponseEntity.noContent().build();
    }

    private static Credenciales validar(Map<String, Object> datos, boolean activacion) {
        Map<String, String> errores = new LinkedHashMap<>();
        String dni = texto(datos, "dni").strip();
        String codigo = texto(datos, "codigo").strip();
        String contrasena = texto(datos, "contrasena");
        if (!dni.matches("[0-9]{8}")) errores.put("dni", "Ingresa un DNI de 8 dígitos.");
        if (activacion && !codigo.matches("[A-Za-z0-9_-]{22}")) {
            errores.put("codigo", "Ingresa el código de 22 caracteres que te entregó el hospital.");
        }
        boolean invalida = contrasena.isBlank() || contrasena.length() > 128
                || contrasena.codePoints().anyMatch(valor -> Character.isISOControl(valor) || (valor >= 0xd800 && valor <= 0xdfff));
        if (invalida || (activacion && contrasena.codePointCount(0, contrasena.length()) < 12)) {
            errores.put("contrasena", activacion ? "Usa una contraseña de 12 a 128 caracteres."
                    : "Ingresa tu contraseña.");
        }
        if (!errores.isEmpty()) throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los datos de acceso.", errores);
        return new Credenciales(dni, codigo, contrasena);
    }

    private static String texto(Map<String, Object> datos, String campo) {
        return datos != null && datos.get(campo) instanceof String valor ? valor : "";
    }

    private record Credenciales(String dni, String codigo, String contrasena) {
        @Override public String toString() { return "Credenciales[datos omitidos]"; }
    }
}
