package com.pos.servicio;

import com.pos.dao.PermisoDAO;
import com.pos.modelo.Modulo;
import com.pos.modelo.Rol;
import com.pos.modelo.Usuario;

import java.sql.SQLException;

/** Cambia qué módulos ve cada rol. Solo el administrador puede hacerlo. */
public class PermisoService {

    private final PermisoDAO dao = new PermisoDAO();

    // Enciende o apaga el acceso de un rol a un módulo.
    public void cambiar(Usuario quien, Rol rol, Modulo modulo, boolean permitido) throws SQLException {
        if (!quien.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede cambiar los permisos.");
        }
        if (rol == Rol.ADMIN) {
            throw new IllegalArgumentException("El administrador siempre ve todo; así nadie se queda sin acceso.");
        }
        if (modulo == Modulo.DASHBOARD) {
            throw new IllegalArgumentException("El Inicio lo ven todos los roles.");
        }
        if (permitido) {
            dao.agregar(rol.name(), modulo);
        } else {
            dao.quitar(rol.name(), modulo);
        }
        Permisos.recargar();   // para que el cambio se vea la próxima vez que alguien entre
    }

    // Enciende o apaga una acción (crear, editar, borrar) de un rol en un módulo.
    public void cambiarAccion(Usuario quien, Rol rol, Modulo modulo, com.pos.modelo.Accion accion, boolean permitido) throws SQLException {
        if (!quien.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede cambiar los permisos.");
        }
        if (rol == Rol.ADMIN) {
            throw new IllegalArgumentException("El administrador siempre puede todo.");
        }
        if (permitido) {
            dao.agregarAccion(rol.name(), modulo, accion);
        } else {
            dao.quitarAccion(rol.name(), modulo, accion);
        }
        Permisos.recargar();
    }
}
