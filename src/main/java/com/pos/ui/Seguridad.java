package com.pos.ui;

import com.pos.modelo.Accion;
import com.pos.modelo.Modulo;
import com.pos.servicio.Permisos;

/** Revisa si la persona con sesión puede hacer algo y, si no, se lo explica. */
public final class Seguridad {

    private Seguridad() {
    }

    // true si puede; si no, muestra un aviso y devuelve false.
    public static boolean permitido(Modulo modulo, Accion accion, String queQuiere) {
        if (Permisos.puede(Sesion.usuario(), modulo, accion)) {
            return true;
        }
        Mensajes.info("Sin permiso", "No tienes permiso para " + queQuiere + ".\nPídele al administrador que te lo active en Usuarios y permisos.");
        return false;
    }

    // Igual, pero sin avisar (para ocultar o bloquear botones).
    public static boolean puede(Modulo modulo, Accion accion) {
        return Permisos.puede(Sesion.usuario(), modulo, accion);
    }
}
