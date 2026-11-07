package com.pos.dao;

import com.pos.config.Conexion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Devoluciones de productos de una venta ya hecha (parciales o completas). */
public class DevolucionDAO {

    // Unidades ya devueltas de cada producto de una venta: id_producto -> cantidad.
    public Map<Integer, Integer> yaDevueltas(int idVenta) throws SQLException {
        Map<Integer, Integer> mapa = new HashMap<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT di.id_producto, SUM(di.cantidad) FROM devolucion_items di "
                     + "JOIN devoluciones d ON d.id = di.id_devolucion WHERE d.id_venta = ? GROUP BY di.id_producto")) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getInt(1), rs.getInt(2));
                }
            }
        }
        return mapa;
    }

    // Todo en una transacción: la devolución, sus líneas, el stock que vuelve y, si el reembolso fue
    // "fiado", la deuda que baja.
    public void guardar(int idVenta, int idUsuario, Integer idCaja, String metodo, BigDecimal monto,
                        Map<Integer, Integer> cantidades) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);
            int idDev;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO devoluciones (id_venta, id_usuario, id_caja, metodo, monto) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idVenta);
                ps.setInt(2, idUsuario);
                ps.setObject(3, idCaja);
                ps.setString(4, metodo);
                ps.setBigDecimal(5, monto);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    idDev = keys.getInt(1);
                }
            }
            for (Map.Entry<Integer, Integer> e : cantidades.entrySet()) {
                if (e.getValue() <= 0) {
                    continue;
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO devolucion_items (id_devolucion, id_producto, cantidad) VALUES (?, ?, ?)")) {
                    ps.setInt(1, idDev);
                    ps.setInt(2, e.getKey());
                    ps.setInt(3, e.getValue());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE productos SET stock = stock + ? WHERE id = ?")) {
                    ps.setInt(1, e.getValue());
                    ps.setInt(2, e.getKey());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO movimientos_inventario "
                        + "(id_producto, id_usuario, tipo, cantidad, motivo) VALUES (?, ?, 'ENTRADA', ?, ?)")) {
                    ps.setInt(1, e.getKey());
                    ps.setInt(2, idUsuario);
                    ps.setInt(3, e.getValue());
                    ps.setString(4, "Devolución venta #" + idVenta);
                    ps.executeUpdate();
                }
            }
            if ("FIADO".equals(metodo)) {   // el reembolso se descuenta de lo que el cliente debe
                try (PreparedStatement ps = con.prepareStatement("UPDATE fiados SET "
                        + "estado = IF(saldo - ? <= 0, 'PAGADO', estado), saldo = GREATEST(0, saldo - ?) "
                        + "WHERE id_venta = ? AND estado = 'PENDIENTE'")) {
                    ps.setBigDecimal(1, monto);
                    ps.setBigDecimal(2, monto);
                    ps.setInt(3, idVenta);
                    ps.executeUpdate();
                }
            }
            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }
}
