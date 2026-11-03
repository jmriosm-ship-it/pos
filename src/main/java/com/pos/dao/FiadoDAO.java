package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Abono;
import com.pos.modelo.Fiado;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiados (deudas de clientes) y sus abonos.
 * Los fiados se CREAN dentro de VentaDAO.guardar (en la misma transacción de la venta);
 * aquí se consultan y se registran los abonos.
 */
public class FiadoDAO {

    // Los fiados de un cliente (menos los anulados), el más reciente primero.
    public List<Fiado> listarPorCliente(int idCliente) throws SQLException {
        String sql = "SELECT * FROM fiados WHERE id_cliente = ? AND estado <> 'ANULADO' ORDER BY creado_en DESC, id DESC";
        List<Fiado> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Fiado f = new Fiado();
                    f.setId(rs.getInt("id"));
                    f.setIdVenta(rs.getInt("id_venta"));
                    f.setIdCliente(rs.getInt("id_cliente"));
                    f.setMonto(rs.getBigDecimal("monto"));
                    f.setSaldo(rs.getBigDecimal("saldo"));
                    f.setEstado(rs.getString("estado"));
                    f.setCreadoEn(rs.getTimestamp("creado_en").toLocalDateTime());
                    lista.add(f);
                }
            }
        }
        return lista;
    }

    // Cuánto debe en total un cliente (suma de los saldos de sus fiados pendientes).
    public BigDecimal saldoDeCliente(int idCliente) throws SQLException {
        String sql = "SELECT COALESCE(SUM(saldo), 0) FROM fiados WHERE id_cliente = ? AND estado = 'PENDIENTE'";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    // Los abonos que ha hecho un cliente, el más reciente primero.
    public List<Abono> listarAbonosPorCliente(int idCliente) throws SQLException {
        String sql = "SELECT a.*, f.id_venta, u.nombre AS usuario FROM abonos_fiado a "
                + "JOIN fiados f ON a.id_fiado = f.id JOIN usuarios u ON a.id_usuario = u.id "
                + "WHERE f.id_cliente = ? ORDER BY a.fecha DESC, a.id DESC";
        List<Abono> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Abono a = new Abono();
                    a.setId(rs.getInt("id"));
                    a.setIdFiado(rs.getInt("id_fiado"));
                    a.setIdVenta(rs.getInt("id_venta"));
                    a.setMetodo(rs.getString("metodo"));
                    a.setMonto(rs.getBigDecimal("monto"));
                    a.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    a.setNombreUsuario(rs.getString("usuario"));
                    lista.add(a);
                }
            }
        }
        return lista;
    }

    // Registra un abono del cliente. Se aplica primero a la deuda MÁS VIEJA, y si sobra, a la siguiente.
    // Todo en una transacción: o se aplica completo o no se toca nada.
    public void registrarAbono(int idCliente, int idUsuario, int idCaja, String metodo, BigDecimal monto) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);

            // Se leen los fiados pendientes (FOR UPDATE: nadie más los cambia mientras abonamos).
            List<int[]> ids = new ArrayList<>();         // {id del fiado}
            List<BigDecimal> saldos = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT id, saldo FROM fiados WHERE id_cliente = ? AND estado = 'PENDIENTE' "
                            + "ORDER BY creado_en, id FOR UPDATE")) {
                ps.setInt(1, idCliente);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(new int[]{rs.getInt(1)});
                        saldos.add(rs.getBigDecimal(2));
                    }
                }
            }

            BigDecimal deudaTotal = saldos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (monto.compareTo(deudaTotal) > 0) {
                throw new SQLException("El abono es mayor que lo que debe el cliente.");
            }

            BigDecimal restante = monto;
            for (int i = 0; i < ids.size() && restante.signum() > 0; i++) {
                BigDecimal aplicar = restante.min(saldos.get(i));

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO abonos_fiado (id_fiado, id_usuario, id_caja, metodo, monto) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, ids.get(i)[0]);
                    ps.setInt(2, idUsuario);
                    ps.setInt(3, idCaja);
                    ps.setString(4, metodo);
                    ps.setBigDecimal(5, aplicar);
                    ps.executeUpdate();
                }
                // Baja el saldo; si queda en cero el fiado pasa a PAGADO.
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE fiados SET saldo = saldo - ?, estado = IF(saldo - ? <= 0, 'PAGADO', 'PENDIENTE') WHERE id = ?")) {
                    ps.setBigDecimal(1, aplicar);
                    ps.setBigDecimal(2, aplicar);
                    ps.setInt(3, ids.get(i)[0]);
                    ps.executeUpdate();
                }
                restante = restante.subtract(aplicar);
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
