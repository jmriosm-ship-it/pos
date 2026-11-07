package com.pos.modelo;

/**
 * Los módulos (pantallas) del sistema. Un enum es una lista fija de valores posibles:
 * no se puede escribir "VENTAS" mal, porque Java lo revisa al compilar.
 * El menú lateral usa esto para mostrar solo lo que cada rol puede ver.
 */
public enum Modulo {
    DASHBOARD("Inicio"),
    PRODUCTOS("Productos"),
    CATEGORIAS_PROVEEDORES("Categorías y proveedores"),
    VENTAS("Ventas"),
    HISTORIAL("Historial y facturas"),
    CAJA("Caja y arqueo"),
    INVENTARIO("Inventario y compras"),
    CLIENTES("Clientes"),
    USUARIOS("Usuarios y permisos"),
    REPORTES("Reportes"),
    COPIAS("Copias de seguridad"),
    CONFIGURACION("Configuración");

    private final String titulo;   // el texto que se ve en el menú

    Modulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}
