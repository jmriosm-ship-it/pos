package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Gasto;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GastoDAO {

    public void insertar(Gasto g) throws SQLException {
        String sql = "INSERT INTO gastos (id_usuario, concepto, monto) VALUES (?, ?, ?)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, g.getIdUsuario());
            ps.setString(2, g.getConcepto());
            ps.setBigDecimal(3, g.getMonto());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    g.setId(keys.getInt(1));
                }
            }
        }
    }

    // Gastos de un rango de fechas (el último día se incluye completo).
    public List<Gasto> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT g.*, u.nombre AS usuario FROM gastos g JOIN usuarios u ON g.id_usuario = u.id "
                + "WHERE g.fecha >= ? AND g.fecha < ? ORDER BY g.fecha DESC";
        List<Gasto> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Gasto g = new Gasto();
                    g.setId(rs.getInt("id"));
                    g.setIdUsuario(rs.getInt("id_usuario"));
                    g.setNombreUsuario(rs.getString("usuario"));
                    g.setConcepto(rs.getString("concepto"));
                    g.setMonto(rs.getBigDecimal("monto"));
                    g.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    lista.add(g);
                }
            }
        }
        return lista;
    }

    // Suma de gastos de un rango de fechas (para los reportes).
    public BigDecimal total(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT COALESCE(SUM(monto), 0) FROM gastos WHERE fecha >= ? AND fecha < ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    public void eliminar(int id) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("DELETE FROM gastos WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
