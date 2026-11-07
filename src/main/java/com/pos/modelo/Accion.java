package com.pos.modelo;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lo que se puede hacer dentro de un módulo, además de verlo (Ver = tener el módulo en el menú).
 * No todos los módulos usan las tres: Reportes, por ejemplo, solo se ve.
 */
public enum Accion {
    CREAR("Crear"),
    EDITAR("Editar"),
    BORRAR("Borrar");

    private final String titulo;

    Accion(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }

    // Las acciones que tienen sentido en cada módulo.
    public static Set<Accion> deModulo(Modulo m) {
        return switch (m) {
            case PRODUCTOS, CATEGORIAS_PROVEEDORES, CLIENTES, USUARIOS -> EnumSet.allOf(Accion.class);
            case VENTAS, INVENTARIO -> EnumSet.of(CREAR, EDITAR);   // ventas: cobrar y cambiar precio · inventario: compras y ajustes
            case HISTORIAL -> EnumSet.of(BORRAR);                    // anular y devolver
            case CAJA -> EnumSet.of(CREAR, BORRAR);                  // abrir/cerrar y movimientos · devolver dinero
            default -> EnumSet.noneOf(Accion.class);
        };
    }

    // Qué significa cada acción en cada módulo (se muestra en la pantalla de permisos).
    public static String explicacion(Modulo m, Accion a) {
        return switch (m) {
            case VENTAS -> a == CREAR ? "Cobrar ventas" : "Cambiar precios al cobrar";
            case HISTORIAL -> "Anular y devolver";
            case CAJA -> a == CREAR ? "Abrir, cerrar y movimientos" : "Devolver dinero";
            case INVENTARIO -> a == CREAR ? "Registrar compras" : "Ajustar existencias";
            default -> a.titulo;
        };
    }
}
