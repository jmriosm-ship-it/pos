package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UsuarioDAO {
    // Busca un usuario por su nombre de usuario (el que escribe para entrar).
    // JOIN con roles: así traemos también el texto del rol (ADMIN, CAJERO...).
    public Usuario buscarPorUsuario(String usuario) throws SQLException {
        String sql = "SELECT u.*, r.nombre AS rol "
                + "FROM usuarios u JOIN roles r ON u.id_rol = r.id "
                + "WHERE u.usuario = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, usuario);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = new Usuario();
                    u.setId(rs.getInt("id"));
                    u.setNombre(rs.getString("nombre"));
                    u.setUsuario(rs.getString("usuario"));
                    u.setClaveHash(rs.getString("clave_hash"));
                    u.setIdRol(rs.getInt("id_rol"));
                    u.setRol(rs.getString("rol"));
                    u.setActivo(rs.getBoolean("activo"));
                    return u;
                }
            }
        }
        return null;   // no existe ese usuario
    }

    // Lista todos los usuarios (con el texto de su rol), ordenados por nombre.
    public java.util.List<Usuario> listar() throws SQLException {
        String sql = "SELECT u.*, r.nombre AS rol FROM usuarios u JOIN roles r ON u.id_rol = r.id ORDER BY u.nombre";
        java.util.List<Usuario> lista = new java.util.ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Usuario u = new Usuario();
                u.setId(rs.getInt("id"));
                u.setNombre(rs.getString("nombre"));
                u.setUsuario(rs.getString("usuario"));
                u.setClaveHash(rs.getString("clave_hash"));
                u.setIdRol(rs.getInt("id_rol"));
                u.setRol(rs.getString("rol"));
                u.setActivo(rs.getBoolean("activo"));
                lista.add(u);
            }
        }
        return lista;
    }

    // Cambia el nombre y el rol de un usuario.
    public void actualizar(Usuario u) throws SQLException {
        String sql = "UPDATE usuarios SET nombre = ?, id_rol = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, u.getNombre());
            ps.setInt(2, u.getIdRol());
            ps.setInt(3, u.getId());
            ps.executeUpdate();
        }
    }

    // Guarda una clave nueva (ya cifrada).
    public void cambiarClave(int idUsuario, String claveHash) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE usuarios SET clave_hash = ? WHERE id = ?")) {
            ps.setString(1, claveHash);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    // Activa o desactiva un usuario. Nunca se borra: sus ventas y cajas dependen de él.
    public void cambiarActivo(int idUsuario, boolean activo) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE usuarios SET activo = ? WHERE id = ?")) {
            ps.setBoolean(1, activo);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    // Cuenta cuántos usuarios hay. Sirve para saber si es la primera vez que arranca el programa.
    public int contar() throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuarios";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);   // la primera (y única) columna
        }
    }

    // Inserta un usuario nuevo. La clave ya debe venir cifrada en u.getClaveHash().
    public void insertar(Usuario u) throws SQLException {
        String sql = "INSERT INTO usuarios (nombre, usuario, clave_hash, id_rol) VALUES (?, ?, ?, ?)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, u.getNombre());
            ps.setString(2, u.getUsuario());
            ps.setString(3, u.getClaveHash());
            ps.setInt(4, u.getIdRol());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    u.setId(keys.getInt(1));
                }
            }
        }
    }
}
