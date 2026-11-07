package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Modulo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Tabla "permisos_rol": qué módulos puede ver cada rol. */
public class PermisoDAO {

    // Todos los permisos de una vez: nombre del rol -> módulos que puede ver.
    public Map<String, Set<Modulo>> cargarTodos() throws SQLException {
        String sql = "SELECT r.nombre, p.modulo FROM permisos_rol p JOIN roles r ON p.id_rol = r.id";
        Map<String, Set<Modulo>> mapa = new HashMap<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    mapa.computeIfAbsent(rs.getString(1), k -> EnumSet.noneOf(Modulo.class))
                            .add(Modulo.valueOf(rs.getString(2)));
                } catch (IllegalArgumentException e) {
                    // un módulo que ya no existe en el programa: se ignora
                }
            }
        }
        return mapa;
    }

    // Las acciones de cada rol: nombre del rol -> claves "MODULO:ACCION".
    public Map<String, Set<String>> cargarAcciones() throws SQLException {
        String sql = "SELECT r.nombre, a.modulo, a.accion FROM permisos_accion a JOIN roles r ON a.id_rol = r.id";
        Map<String, Set<String>> mapa = new HashMap<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.computeIfAbsent(rs.getString(1), k -> new java.util.HashSet<>())
                        .add(rs.getString(2) + ":" + rs.getString(3));
            }
        }
        return mapa;
    }

    public void agregarAccion(String rol, Modulo modulo, com.pos.modelo.Accion accion) throws SQLException {
        String sql = "INSERT IGNORE INTO permisos_accion (id_rol, modulo, accion) SELECT id, ?, ? FROM roles WHERE nombre = ?";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, modulo.name());
            ps.setString(2, accion.name());
            ps.setString(3, rol);
            ps.executeUpdate();
        }
    }

    public void quitarAccion(String rol, Modulo modulo, com.pos.modelo.Accion accion) throws SQLException {
        String sql = "DELETE a FROM permisos_accion a JOIN roles r ON a.id_rol = r.id "
                + "WHERE r.nombre = ? AND a.modulo = ? AND a.accion = ?";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rol);
            ps.setString(2, modulo.name());
            ps.setString(3, accion.name());
            ps.executeUpdate();
        }
    }

    // Le da a un rol acceso a un módulo (si ya lo tenía, no pasa nada).
    public void agregar(String rol, Modulo modulo) throws SQLException {
        String sql = "INSERT IGNORE INTO permisos_rol (id_rol, modulo) SELECT id, ? FROM roles WHERE nombre = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, modulo.name());
            ps.setString(2, rol);
            ps.executeUpdate();
        }
    }

    // Le quita a un rol el acceso a un módulo.
    public void quitar(String rol, Modulo modulo) throws SQLException {
        String sql = "DELETE p FROM permisos_rol p JOIN roles r ON p.id_rol = r.id WHERE r.nombre = ? AND p.modulo = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rol);
            ps.setString(2, modulo.name());
            ps.executeUpdate();
        }
    }
}
