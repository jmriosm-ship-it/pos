package com.pos.servicio;

import com.pos.dao.UsuarioDAO;
import com.pos.modelo.Usuario;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;

public class AutenticacionService {

    // El servicio usa el DAO para hablar con la base de datos.
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    // Si no hay ningún usuario (primera vez), crea el administrador inicial.
    // Usuario: admin / Clave: admin123  -> hay que cambiarla cuando se entregue el POS.
    public void crearAdminSiNoExiste() throws SQLException {
        if (usuarioDAO.contar() == 0) {
            Usuario admin = new Usuario();
            admin.setNombre("Administrador");
            admin.setUsuario("admin");
            // hashpw cifra la clave. gensalt() agrega un "ingrediente al azar"
            // para que dos claves iguales no den el mismo cifrado.
            admin.setClaveHash(BCrypt.hashpw("admin123", BCrypt.gensalt()));
            admin.setIdRol(1);   // 1 = ADMIN (lo insertamos en schema.sql)
            usuarioDAO.insertar(admin);
        }
    }

    // Devuelve el Usuario si el login es correcto, o null si falla.
    public Usuario login(String usuario, String clave) throws SQLException {
        Usuario u = usuarioDAO.buscarPorUsuario(usuario);

        // Falla si no existe, está desactivado o la clave no coincide.
        // Por seguridad no se dice cuál de las tres: "usuario o clave incorrectos".
        if (u == null || !u.isActivo()) {
            return null;
        }
        // checkpw compara la clave escrita contra el cifrado guardado.
        if (!BCrypt.checkpw(clave, u.getClaveHash())) {
            return null;
        }
        return u;
    }
}