package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.MovimientoCaja;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MovimientoCajaDAO {

    public void insertar(int idCaja, int idUsuario, String tipo, BigDecimal monto, String motivo) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO movimientos_caja (id_caja, id_usuario, tipo, monto, motivo) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idCaja);
            ps.setInt(2, idUsuario);
            ps.setString(3, tipo);
            ps.setBigDecimal(4, monto);
            ps.setString(5, motivo);
            ps.executeUpdate();
        }
    }

    // Los movimientos de una caja, el más reciente primero.
    public List<MovimientoCaja> listar(int idCaja) throws SQLException {
        List<MovimientoCaja> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT m.*, u.nombre FROM movimientos_caja m "
                     + "JOIN usuarios u ON u.id = m.id_usuario WHERE m.id_caja = ? ORDER BY m.id DESC")) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new MovimientoCaja(rs.getInt("id"), rs.getString("tipo"), rs.getBigDecimal("monto"),
                            rs.getString("motivo"), rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("nombre")));
                }
            }
        }
        return lista;
    }

    // Total de ingresos o egresos manuales de una caja (para el comprobante).
    public BigDecimal total(int idCaja, String tipo) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(monto), 0) FROM movimientos_caja WHERE id_caja = ? AND tipo = ?")) {
            ps.setInt(1, idCaja);
            ps.setString(2, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    // Lo devuelto en efectivo a clientes desde esta caja (para el comprobante).
    public BigDecimal devolucionesEfectivo(int idCaja) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(monto), 0) FROM devoluciones WHERE id_caja = ? AND metodo = 'EFECTIVO'")) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}
