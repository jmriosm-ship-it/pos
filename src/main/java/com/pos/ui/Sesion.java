package com.pos.ui;

import com.pos.modelo.Usuario;

/** Quién está usando el sistema ahora mismo. Las pantallas lo consultan en vez de pasarlo de mano en mano. */
public class Sesion {

    private static Usuario usuario;

    private Sesion() {
    }

    public static Usuario usuario() {
        return usuario;
    }

    public static void iniciar(Usuario u) {
        usuario = u;
    }

    public static void cerrar() {
        usuario = null;
    }
}
