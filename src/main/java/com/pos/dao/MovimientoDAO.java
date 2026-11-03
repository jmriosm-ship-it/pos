package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Movimiento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MovimientoDAO {

    // Los movimientos más recientes de todo el inventario (para la pantalla de historial).
    public List<Movimiento> listarRecientes(int limite) throws SQLException {
        String sql = baseConsulta() + " ORDER BY m.fecha DESC, m.id DESC LIMIT ?";
        return consultar(sql, limite);
    }

    // El historial de UN producto: de dónde vino y a dónde se fue cada unidad.
    public List<Movimiento> listarPorProducto(int idProducto) throws SQLException {
        String sql = baseConsulta() + " WHERE m.id_producto = ? ORDER BY m.fecha DESC, m.id DESC";
        return consultar(sql, idProducto);
    }

    // Ajuste manual de stock (conteo físico, merma, corrección).
    // Se hace en UNA transacción: lee el stock actual, lo cambia y anota la diferencia.
    // "FOR UPDATE" bloquea la fila para que nadie la cambie mientras ajustamos.
    public int ajustar(int idProducto, int idUsuario, int nuevoStock, String motivo) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);

            int actual;
            try (PreparedStatement ps = con.prepareStatement("SELECT stock FROM productos WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, idProducto);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("El producto no existe.");
                    }
                    actual = rs.getInt(1);
                }
            }

            int diferencia = nuevoStock - actual;   // negativo = había menos de lo que decía el sistema

            try (PreparedStatement ps = con.prepareStatement("UPDATE productos SET stock = ? WHERE id = ?")) {
                ps.setInt(1, nuevoStock);
                ps.setInt(2, idProducto);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO movimientos_inventario (id_producto, id_usuario, tipo, cantidad, motivo) "
                            + "VALUES (?, ?, 'AJUSTE', ?, ?)")) {
                ps.setInt(1, idProducto);
                ps.setInt(2, idUsuario);
                ps.setInt(3, diferencia);
                ps.setString(4, motivo);
                ps.executeUpdate();
            }

            con.commit();
            return diferencia;
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // SELECT común: une con productos y usuarios para traer los nombres.
    private String baseConsulta() {
        return "SELECT m.*, p.nombre AS producto, u.nombre AS usuario FROM movimientos_inventario m "
                + "JOIN productos p ON m.id_producto = p.id "
                + "JOIN usuarios u ON m.id_usuario = u.id";
    }

    private List<Movimiento> consultar(String sql, int parametro) throws SQLException {
        List<Movimiento> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Movimiento m = new Movimiento();
                    m.setId(rs.getInt("id"));
                    m.setIdProducto(rs.getInt("id_producto"));
                    m.setNombreProducto(rs.getString("producto"));
                    m.setIdUsuario(rs.getInt("id_usuario"));
                    m.setNombreUsuario(rs.getString("usuario"));
                    m.setTipo(rs.getString("tipo"));
                    m.setCantidad(rs.getInt("cantidad"));
                    m.setMotivo(rs.getString("motivo"));
                    m.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    lista.add(m);
                }
            }
        }
        return lista;
    }
}
