package com.pos.servicio;

import com.pos.dao.UsuarioDAO;
import com.pos.modelo.Usuario;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;

public class AutenticacionService {

    // El servicio usa el DAO para hablar con la base de datos.
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    public static final String CLAVE_INICIAL = "admin123";

    // ¿Esta persona todavía usa la clave inicial del sistema? Entonces debe cambiarla antes de seguir.
    public boolean usaClaveInicial(Usuario u) {
        return u != null && u.getClaveHash() != null && BCrypt.checkpw(CLAVE_INICIAL, u.getClaveHash());
    }

    // Si no hay ningún usuario (primera vez), crea el administrador inicial.
    // Usuario: admin / Clave: admin123  -> hay que cambiarla cuando se entregue el POS.
    public void crearAdminSiNoExiste() throws SQLException {
        if (usuarioDAO.contar() == 0) {
            Usuario admin = new Usuario();
            admin.setNombre("Administrador");
            admin.setUsuario("admin");
            // hashpw cifra la clave. gensalt() agrega un "ingrediente al azar"
            // para que dos claves iguales no den el mismo cifrado.
            admin.setClaveHash(BCrypt.hashpw(CLAVE_INICIAL, BCrypt.gensalt()));
            admin.setIdRol(1);   // 1 = ADMIN (lo insertamos en schema.sql)
            usuarioDAO.insertar(admin);
        }
    }

    /**
     * La primera vez que se usa el sistema (todavía no hay ningún usuario): el dueño crea el suyo, que queda como
     * administrador. No hay un usuario ni una clave de fábrica: cada negocio elige los suyos.
     */
    public Usuario crearPrimerAdmin(String nombre, String usuario, String clave) throws SQLException {
        if (usuarioDAO.contar() > 0) {
            throw new IllegalStateException("Ya hay usuarios creados; entra con el tuyo.");
        }
        String n = nombre == null ? "" : nombre.trim();
        String login = usuario == null ? "" : usuario.trim();
        if (n.length() < 3) {
            throw new IllegalArgumentException("Escribe tu nombre completo.");
        }
        if (!login.matches("[A-Za-z0-9._-]{3,30}")) {
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 30 letras o números (sin espacios ni tildes).");
        }
        if (clave == null || clave.length() < 6) {
            throw new IllegalArgumentException("La clave debe tener al menos 6 caracteres.");
        }
        if (CLAVE_INICIAL.equals(clave)) {
            throw new IllegalArgumentException("Elige otra clave.");
        }
        Usuario u = new Usuario();
        u.setNombre(n);
        u.setUsuario(login);
        u.setClaveHash(BCrypt.hashpw(clave, BCrypt.gensalt()));
        u.setIdRol(1);   // 1 = ADMIN
        usuarioDAO.insertar(u);
        u.setRol("ADMIN");
        u.setActivo(true);
        AuditoriaService.registrar(u, "Primer uso", "el dueño creó su usuario");
        return u;
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
        AuditoriaService.registrar(u, "Inicio de sesión", u.getNombre());
        com.pos.offline.Sincronizador.pedirRefresco();
        return u;
    }
}