package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Proveedor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProveedorDAO {

    // Devuelve los proveedores activos, ordenados por nombre.
    public List<Proveedor> listar() throws SQLException {
        String sql = "SELECT * FROM proveedores WHERE activo = TRUE ORDER BY nombre";
        List<Proveedor> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    // Los proveedores activos con lo que se les ha comprado en total y la fecha de la última compra.
    // LEFT JOIN: un proveedor sin compras también sale (total 0 y sin fecha).
    public List<Proveedor> listarConResumen() throws SQLException {
        String sql = "SELECT p.*, COALESCE(SUM(c.total), 0) AS comprado, MAX(c.fecha) AS ultima "
                + "FROM proveedores p LEFT JOIN compras c ON c.id_proveedor = p.id "
                + "WHERE p.activo = TRUE GROUP BY p.id ORDER BY p.nombre";
        List<Proveedor> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Proveedor p = mapear(rs);
                p.setTotalComprado(rs.getBigDecimal("comprado"));
                java.sql.Timestamp ultima = rs.getTimestamp("ultima");
                p.setUltimaCompra(ultima == null ? null : ultima.toLocalDateTime());
                lista.add(p);
            }
        }
        return lista;
    }

    // Inserta un proveedor nuevo y le asigna el id generado por MySQL.
    public void insertar(Proveedor p) throws SQLException {
        String sql = "INSERT INTO proveedores (nombre, telefono, correo, direccion) VALUES (?, ?, ?, ?)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, p.getNombre());
            ps.setString(2, p.getTelefono());
            ps.setString(3, p.getCorreo());
            ps.setString(4, p.getDireccion());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setId(keys.getInt(1));
                }
            }
        }
    }

    // Actualiza los datos de un proveedor existente.
    public void actualizar(Proveedor p) throws SQLException {
        String sql = "UPDATE proveedores SET nombre = ?, telefono = ?, correo = ?, direccion = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, p.getNombre());
            ps.setString(2, p.getTelefono());
            ps.setString(3, p.getCorreo());
            ps.setString(4, p.getDireccion());
            ps.setInt(5, p.getId());
            ps.executeUpdate();
        }
    }

    // Lo desactivamos en vez de borrarlo, para no perder el historial de compras.
    public void desactivar(int id) throws SQLException {
        String sql = "UPDATE proveedores SET activo = FALSE WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Convierte UNA fila del resultado en un objeto Proveedor.
    private Proveedor mapear(ResultSet rs) throws SQLException {
        Proveedor p = new Proveedor();
        p.setId(rs.getInt("id"));
        p.setNombre(rs.getString("nombre"));
        p.setTelefono(rs.getString("telefono"));
        p.setCorreo(rs.getString("correo"));
        p.setDireccion(rs.getString("direccion"));
        p.setActivo(rs.getBoolean("activo"));
        return p;
    }
}