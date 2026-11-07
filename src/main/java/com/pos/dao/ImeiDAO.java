package com.pos.dao;

import com.pos.config.Conexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * IMEI / serial de cada unidad de los productos "por IMEI".
 * Regla: el stock de esos productos = cantidad de IMEI DISPONIBLES (se mantiene al agregar/quitar).
 */
public class ImeiDAO {

    // Los IMEI que se pueden vender de un producto.
    public List<String> disponibles(int idProducto) throws SQLException {
        return listar("SELECT imei FROM producto_imeis WHERE id_producto = ? AND estado = 'DISPONIBLE' ORDER BY id", idProducto);
    }

    // Todos los IMEI de un producto (disponibles y vendidos).
    public List<String> todos(int idProducto) throws SQLException {
        return listar("SELECT CONCAT(imei, IF(estado = 'VENDIDO', '  (vendido)', '')) FROM producto_imeis "
                + "WHERE id_producto = ? ORDER BY id", idProducto);
    }

    private List<String> listar(String sql, int idProducto) throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(rs.getString(1));
                }
            }
        }
        return lista;
    }

    // ¿Ya existe este IMEI en cualquier producto? Devuelve el nombre del producto o null.
    public String productoDe(String imei) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT p.nombre FROM producto_imeis i "
                     + "JOIN productos p ON p.id = i.id_producto WHERE i.imei = ?")) {
            ps.setString(1, imei);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // Agrega IMEI nuevos como disponibles y sube el stock en la misma transacción.
    public void agregar(int idProducto, int idUsuario, List<String> imeis, String motivo) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);
            for (String imei : imeis) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO producto_imeis (id_producto, imei) VALUES (?, ?)")) {
                    ps.setInt(1, idProducto);
                    ps.setString(2, imei);
                    ps.executeUpdate();
                }
            }
            movimiento(con, idProducto, idUsuario, "ENTRADA", imeis.size(), motivo);
            sincronizarStock(con, idProducto);
            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // Quita un IMEI que está disponible (se cargó por error, se dañó...) y baja el stock.
    public void quitar(int idProducto, int idUsuario, String imei, String motivo) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);
            int filas;
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM producto_imeis WHERE id_producto = ? AND imei = ? AND estado = 'DISPONIBLE'")) {
                ps.setInt(1, idProducto);
                ps.setString(2, imei);
                filas = ps.executeUpdate();
            }
            if (filas == 0) {
                throw new SQLException("Ese IMEI no está disponible (ya se vendió o no existe).");
            }
            movimiento(con, idProducto, idUsuario, "SALIDA", 1, motivo);
            sincronizarStock(con, idProducto);
            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // Marca IMEI como vendidos (dentro de la transacción de la venta). Falla si alguno ya no está disponible.
    public void marcarVendidos(Connection con, int idProducto, int idVenta, List<String> imeis) throws SQLException {
        for (String imei : imeis) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE producto_imeis SET estado = 'VENDIDO', id_venta = ? "
                    + "WHERE id_producto = ? AND imei = ? AND estado = 'DISPONIBLE'")) {
                ps.setInt(1, idVenta);
                ps.setInt(2, idProducto);
                ps.setString(3, imei);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("El IMEI " + imei + " ya no está disponible.");
                }
            }
        }
    }

    // Al anular una venta, sus IMEI vuelven a estar disponibles.
    public void liberarDeVenta(Connection con, int idVenta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE producto_imeis SET estado = 'DISPONIBLE', id_venta = NULL WHERE id_venta = ?")) {
            ps.setInt(1, idVenta);
            ps.executeUpdate();
        }
    }

    // El stock de un producto "por IMEI" siempre es igual a sus IMEI disponibles.
    public void sincronizarStock(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE productos SET stock = "
                + "(SELECT COUNT(*) FROM producto_imeis WHERE id_producto = ? AND estado = 'DISPONIBLE') WHERE id = ?")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idProducto);
            ps.executeUpdate();
        }
    }

    private void movimiento(Connection con, int idProducto, int idUsuario, String tipo, int cantidad, String motivo) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO movimientos_inventario "
                + "(id_producto, id_usuario, tipo, cantidad, motivo) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idUsuario);
            ps.setString(3, tipo);
            ps.setInt(4, cantidad);
            ps.setString(5, motivo);
            ps.executeUpdate();
        }
    }
}
