package com.dextre.medishift.disponibilidad;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.dextre.medishift.catalogos.ManejadorErroresCatalogos;

/** Conserva el mismo contrato de errores y la protección de detalles internos que los catálogos. */
@RestControllerAdvice(assignableTypes = ControladorDisponibilidades.class)
public class ManejadorErroresDisponibilidades extends ManejadorErroresCatalogos {
}
