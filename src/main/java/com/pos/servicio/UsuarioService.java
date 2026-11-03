package com.pos.servicio;

import com.pos.dao.UsuarioDAO;
import com.pos.modelo.Usuario;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;
import java.util.List;

public class UsuarioService {

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    public List<Usuario> listar() throws SQLException {
        return usuarioDAO.listar();
    }

    // Crea un usuario nuevo. Solo un ADMIN puede hacerlo.
    // idRol: 1 = ADMIN, 2 = CAJERO, 3 = BODEGUERO.
    public Usuario crear(Usuario quienCrea, String nombre, String usuario, String clave, int idRol) throws SQLException {
        exigirAdmin(quienCrea);
        if (nombre == null || nombre.isBlank() || usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("El nombre y el usuario son obligatorios.");
        }
        validarClave(clave);
        if (idRol < 1 || idRol > 3) {
            throw new IllegalArgumentException("Rol no válido.");
        }
        if (usuarioDAO.buscarPorUsuario(usuario.trim()) != null) {
            throw new IllegalArgumentException("Ya existe un usuario llamado \"" + usuario.trim() + "\".");
        }

        Usuario u = new Usuario();
        u.setNombre(nombre.trim());
        u.setUsuario(usuario.trim());
        u.setClaveHash(BCrypt.hashpw(clave, BCrypt.gensalt()));
        u.setIdRol(idRol);
        usuarioDAO.insertar(u);
        return u;
    }

    // Cambia nombre y rol. Solo ADMIN. No puedes quitarte tu propio rol de administrador.
    public void actualizar(Usuario quienEdita, Usuario objetivo) throws SQLException {
        exigirAdmin(quienEdita);
        if (quienEdita.getId() == objetivo.getId() && objetivo.getIdRol() != 1) {
            throw new IllegalStateException("No puedes quitarte a ti mismo el rol de administrador.");
        }
        usuarioDAO.actualizar(objetivo);
    }

    // Un usuario cambia su PROPIA clave: debe saber la actual.
    public void cambiarMiClave(Usuario usuario, String claveActual, String claveNueva) throws SQLException {
        Usuario guardado = usuarioDAO.buscarPorUsuario(usuario.getUsuario());
        if (guardado == null || !BCrypt.checkpw(claveActual, guardado.getClaveHash())) {
            throw new IllegalArgumentException("La clave actual no es correcta.");
        }
        validarClave(claveNueva);
        usuarioDAO.cambiarClave(usuario.getId(), BCrypt.hashpw(claveNueva, BCrypt.gensalt()));
    }

    // Un ADMIN restablece la clave de otro (por si la olvidó).
    public void restablecerClave(Usuario quienRestablece, Usuario objetivo, String claveNueva) throws SQLException {
        exigirAdmin(quienRestablece);
        validarClave(claveNueva);
        usuarioDAO.cambiarClave(objetivo.getId(), BCrypt.hashpw(claveNueva, BCrypt.gensalt()));
    }

    // Activa o desactiva a un usuario. No puedes desactivarte a ti mismo.
    public void cambiarActivo(Usuario quienEdita, Usuario objetivo, boolean activo) throws SQLException {
        exigirAdmin(quienEdita);
        if (quienEdita.getId() == objetivo.getId() && !activo) {
            throw new IllegalStateException("No puedes desactivar tu propio usuario.");
        }
        usuarioDAO.cambiarActivo(objetivo.getId(), activo);
        objetivo.setActivo(activo);
    }

    private void exigirAdmin(Usuario u) {
        if (!u.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede hacer esto.");
        }
    }

    private void validarClave(String clave) {
        if (clave == null || clave.length() < 6) {
            throw new IllegalArgumentException("La clave debe tener al menos 6 caracteres.");
        }
    }
}
