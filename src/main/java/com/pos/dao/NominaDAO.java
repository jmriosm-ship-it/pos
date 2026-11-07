package com.pos.dao;

import com.pos.config.Conexion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** La nómina: lo que cada trabajador saca del local a cuenta del sueldo (consumos) y los pagos de sueldo. */
public class NominaDAO {

    // Un renglón de la nómina (consumo o pago). Los anulados quedan en el historial pero no cuentan.
    public record Mov(int id, String tipo, String concepto, BigDecimal valor, BigDecimal sueldo, BigDecimal descuento,
                      boolean anulado, LocalDateTime fecha, String registro) {
    }

    // Una caja que abrió o cerró el trabajador: con cuánto empezó y con cuánto terminó.
    public record CajaTurno(int id, LocalDateTime abierta, LocalDateTime cerrada, BigDecimal inicial, BigDecimal contado) {
    }

    public void insertarConsumo(int idUsuario, String concepto, BigDecimal valor, int idRegistro) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO nomina (id_usuario, tipo, concepto, valor, id_registro) "
                     + "VALUES (?, 'CONSUMO', ?, ?, ?)")) {
            ps.setInt(1, idUsuario);
            ps.setString(2, concepto);
            ps.setBigDecimal(3, valor);
            ps.setInt(4, idRegistro);
            ps.executeUpdate();
        }
    }

    public void insertarPago(int idUsuario, String concepto, BigDecimal sueldo, BigDecimal descuento, BigDecimal neto,
                             int dias, BigDecimal valorDia, String periodo, int idRegistro) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO nomina (id_usuario, tipo, concepto, valor, sueldo, "
                     + "descuento, dias, valor_dia, periodo, id_registro) VALUES (?, 'PAGO', ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, idUsuario);
            ps.setString(2, concepto);
            ps.setBigDecimal(3, neto);
            ps.setBigDecimal(4, sueldo);
            ps.setBigDecimal(5, descuento);
            ps.setInt(6, dias);
            ps.setBigDecimal(7, valorDia);
            ps.setString(8, periodo);
            ps.setInt(9, idRegistro);
            ps.executeUpdate();
        }
    }

    public void anular(int id) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE nomina SET anulado = TRUE WHERE id = ? AND tipo = 'CONSUMO'")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // pendiente = consumos - lo que ya se descontó en sueldos pagados (nunca menos de cero).
    public BigDecimal pendiente(int idUsuario) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(CASE tipo WHEN 'CONSUMO' THEN valor "
                     + "ELSE -COALESCE(descuento, 0) END), 0) FROM nomina WHERE id_usuario = ? AND anulado = FALSE")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).max(BigDecimal.ZERO);
            }
        }
    }

    public List<Mov> listar(int idUsuario) throws SQLException {
        List<Mov> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT n.*, r.nombre AS registro FROM nomina n "
                     + "JOIN usuarios r ON r.id = n.id_registro WHERE n.id_usuario = ? ORDER BY n.id DESC")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Mov(rs.getInt("id"), rs.getString("tipo"), rs.getString("concepto"), rs.getBigDecimal("valor"),
                            rs.getBigDecimal("sueldo"), rs.getBigDecimal("descuento"), rs.getBoolean("anulado"),
                            rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("registro")));
                }
            }
        }
        return lista;
    }

    // Días distintos en que el trabajador hizo ventas (ayuda para calcular el sueldo).
    public int diasVendidos(int idUsuario, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(DISTINCT DATE(fecha)) FROM ventas "
                     + "WHERE id_usuario = ? AND estado = 'COMPLETADA' AND fecha >= ? AND fecha < ?")) {
            ps.setInt(1, idUsuario);
            ps.setTimestamp(2, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Ventas netas de un trabajador en un rango: lo vendido menos lo devuelto. {cantidad, valor}
    public BigDecimal[] ventasNetas(int idUsuario, LocalDate desde, LocalDate hasta) throws SQLException {
        Timestamp d = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp h = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
        BigDecimal cantidad;
        BigDecimal vendido;
        BigDecimal devuelto;
        try (Connection con = Conexion.obtener()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*), COALESCE(SUM(total), 0) FROM ventas "
                    + "WHERE id_usuario = ? AND estado = 'COMPLETADA' AND fecha >= ? AND fecha < ?")) {
                ps.setInt(1, idUsuario);
                ps.setTimestamp(2, d);
                ps.setTimestamp(3, h);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cantidad = BigDecimal.valueOf(rs.getInt(1));
                    vendido = rs.getBigDecimal(2);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(dv.monto), 0) FROM devoluciones dv "
                    + "JOIN ventas v ON v.id = dv.id_venta WHERE v.id_usuario = ? AND v.estado = 'COMPLETADA' "
                    + "AND v.fecha >= ? AND v.fecha < ?")) {
                ps.setInt(1, idUsuario);
                ps.setTimestamp(2, d);
                ps.setTimestamp(3, h);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    devuelto = rs.getBigDecimal(1);
                }
            }
        }
        return new BigDecimal[]{cantidad, vendido.subtract(devuelto)};
    }

    // Las cajas que abrió el trabajador en un rango, con cuánto empezó y cuánto contó al cerrar.
    public List<CajaTurno> cajas(int idUsuario, LocalDate desde, LocalDate hasta) throws SQLException {
        List<CajaTurno> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM cajas WHERE id_usuario = ? AND abierta_en >= ? "
                     + "AND abierta_en < ? ORDER BY id DESC")) {
            ps.setInt(1, idUsuario);
            ps.setTimestamp(2, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp cerrada = rs.getTimestamp("cerrada_en");
                    lista.add(new CajaTurno(rs.getInt("id"), rs.getTimestamp("abierta_en").toLocalDateTime(),
                            cerrada == null ? null : cerrada.toLocalDateTime(), rs.getBigDecimal("monto_inicial"),
                            rs.getBigDecimal("monto_contado")));
                }
            }
        }
        return lista;
    }
}
