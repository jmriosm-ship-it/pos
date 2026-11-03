package com.pos.modelo;

/** Los roles de usuario (coinciden con las filas de la tabla "roles"). Un enum es una lista fija de valores. */
public enum Rol {
    ADMIN(1, "Administrador"),
    CAJERO(2, "Cajero"),
    BODEGUERO(3, "Bodeguero");

    private final int id;
    private final String etiqueta;

    Rol(int id, String etiqueta) {
        this.id = id;
        this.etiqueta = etiqueta;
    }

    public int getId() {
        return id;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    // Busca el rol por su nombre en la base de datos ("CAJERO"). Devuelve null si no existe.
    public static Rol porNombre(String nombre) {
        for (Rol r : values()) {
            if (r.name().equals(nombre)) {
                return r;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return etiqueta;   // los ComboBox muestran esto
    }
}
