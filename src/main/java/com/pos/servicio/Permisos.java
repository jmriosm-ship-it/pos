package com.pos.servicio;

import com.pos.dao.PermisoDAO;
import com.pos.modelo.Modulo;
import com.pos.modelo.Usuario;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Qué módulos puede ver cada rol. Los permisos viven en la base de datos (tabla permisos_rol) y el
 * administrador los cambia desde "Usuarios y permisos". Se leen una vez y se guardan en memoria;
 * al cambiarlos se llama a recargar().
 *  - El administrador siempre ve todo (así nadie puede quedarse sin acceso).
 *  - El Inicio lo ven todos los roles.
 */
public class Permisos {

    // Permisos de siempre: se usan si la base de datos no responde.
    private static final Map<String, Set<Modulo>> PREDETERMINADOS = Map.of(
            "CAJERO", EnumSet.of(Modulo.DASHBOARD, Modulo.VENTAS, Modulo.CAJA, Modulo.CLIENTES),
            "BODEGUERO", EnumSet.of(Modulo.DASHBOARD, Modulo.PRODUCTOS, Modulo.CATEGORIAS_PROVEEDORES, Modulo.INVENTARIO)
    );

    private static Map<String, Set<Modulo>> cache;   // null hasta la primera lectura

    private Permisos() {
    }

    // ¿Este usuario puede entrar a este módulo?
    public static boolean puedeVer(Usuario usuario, Modulo modulo) {
        return modulosDe(usuario).contains(modulo);
    }

    // Todos los módulos que puede ver (para armar el menú lateral).
    public static Set<Modulo> modulosDe(Usuario usuario) {
        return modulosDeRol(usuario.getRol());
    }

    public static synchronized Set<Modulo> modulosDeRol(String rol) {
        if ("ADMIN".equals(rol)) {
            return EnumSet.allOf(Modulo.class);
        }
        Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);
        Set<Modulo> guardados = cargar().get(rol);
        if (guardados != null) {
            modulos.addAll(guardados);
        }
        modulos.add(Modulo.DASHBOARD);   // el Inicio siempre está disponible
        return modulos;
    }

    // Olvida lo leído para volver a consultar la base de datos (después de cambiar permisos).
    public static synchronized void recargar() {
        cache = null;
    }

    private static Map<String, Set<Modulo>> cargar() {
        if (cache == null) {
            try {
                cache = new PermisoDAO().cargarTodos();
            } catch (SQLException e) {
                cache = PREDETERMINADOS;
            }
        }
        return cache;
    }
}
