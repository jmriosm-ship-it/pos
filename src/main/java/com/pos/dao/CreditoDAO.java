package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.PlanCredito;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Créditos en cuotas: el crédito, sus cuotas, los pagos recibidos y los avisos ya enviados. */
public class CreditoDAO {

    // Un crédito para listar (datos del crédito + del cliente).
    public record Fila(int id, int idVenta, String ventaNumero, int idCliente, String cliente, String documento,
                       String telefono, String descripcion, BigDecimal precio, BigDecimal financiado, BigDecimal cuotaInicial,
                       int plazo, BigDecimal tasa, String frecuencia, BigDecimal cuotaValor, BigDecimal totalCredito,
                       BigDecimal intereses, String estado, LocalDateTime creado) {
    }

    public record Pago(int id, String metodo, BigDecimal valor, LocalDateTime fecha, String usuario) {
    }

    // Se llama dentro de la transacción de la venta: crea el crédito y sus cuotas.
    public void guardar(Connection con, Venta v, PlanCredito plan, BigDecimal cuotaInicial, String descripcion) throws SQLException {
        int idCredito;
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO creditos (id_venta, id_cliente, descripcion, precio, "
                + "financiado, cuota_inicial, plazo, tasa, frecuencia, cuota_valor, total_credito, intereses) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, v.getId());
            ps.setInt(2, v.getIdCliente());
            ps.setString(3, descripcion);
            ps.setBigDecimal(4, v.getTotal());
            ps.setBigDecimal(5, plan.financiado());
            ps.setBigDecimal(6, cuotaInicial);
            ps.setInt(7, plan.plazo());
            ps.setBigDecimal(8, plan.tasa());
            ps.setString(9, plan.frecuencia());
            ps.setBigDecimal(10, plan.cuotaValor());
            ps.setBigDecimal(11, plan.totalCredito());
            ps.setBigDecimal(12, plan.intereses());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                idCredito = keys.getInt(1);
            }
        }
        for (PlanCredito.Cuota c : plan.cuotas()) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO credito_cuotas (id_credito, n, vence, valor) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, idCredito);
                ps.setInt(2, c.n());
                ps.setDate(3, Date.valueOf(c.vence()));
                ps.setBigDecimal(4, c.valor());
                ps.executeUpdate();
            }
        }
    }

    // Anular la venta cancela su crédito (lo ya pagado se devuelve aparte).
    public void cancelarDeVenta(Connection con, int idVenta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE creditos SET estado = 'CANCELADO' WHERE id_venta = ?")) {
            ps.setInt(1, idVenta);
            ps.executeUpdate();
        }
    }

    public void cancelar(int idCredito) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE creditos SET estado = 'CANCELADO' WHERE id = ?")) {
            ps.setInt(1, idCredito);
            ps.executeUpdate();
        }
    }

    public boolean tieneCredito(int idVenta) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM creditos WHERE id_venta = ? AND estado = 'ACTIVO'")) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public List<Fila> listar(boolean soloActivos) throws SQLException {
        String sql = "SELECT c.*, cl.nombre AS cliente, cl.documento, cl.telefono, v.etiqueta, v.id AS vid FROM creditos c "
                + "JOIN clientes cl ON cl.id = c.id_cliente JOIN ventas v ON v.id = c.id_venta "
                + (soloActivos ? "WHERE c.estado = 'ACTIVO' " : "") + "ORDER BY c.id DESC";
        List<Fila> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(fila(rs));
            }
        }
        return lista;
    }

    public Fila buscar(int idCredito) throws SQLException {
        String sql = "SELECT c.*, cl.nombre AS cliente, cl.documento, cl.telefono, v.etiqueta, v.id AS vid FROM creditos c "
                + "JOIN clientes cl ON cl.id = c.id_cliente JOIN ventas v ON v.id = c.id_venta WHERE c.id = ?";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCredito);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fila(rs) : null;
            }
        }
    }

    private Fila fila(ResultSet rs) throws SQLException {
        String etiqueta = rs.getString("etiqueta");
        return new Fila(rs.getInt("id"), rs.getInt("id_venta"), etiqueta != null ? etiqueta : "#" + rs.getInt("vid"),
                rs.getInt("id_cliente"), rs.getString("cliente"), rs.getString("documento"), rs.getString("telefono"),
                rs.getString("descripcion"), rs.getBigDecimal("precio"), rs.getBigDecimal("financiado"),
                rs.getBigDecimal("cuota_inicial"), rs.getInt("plazo"), rs.getBigDecimal("tasa"), rs.getString("frecuencia"),
                rs.getBigDecimal("cuota_valor"), rs.getBigDecimal("total_credito"), rs.getBigDecimal("intereses"),
                rs.getString("estado"), rs.getTimestamp("creado_en").toLocalDateTime());
    }

    public List<PlanCredito.Cuota> cuotas(int idCredito) throws SQLException {
        List<PlanCredito.Cuota> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT n, vence, valor FROM credito_cuotas WHERE id_credito = ? ORDER BY n")) {
            ps.setInt(1, idCredito);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new PlanCredito.Cuota(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getBigDecimal(3)));
                }
            }
        }
        return lista;
    }

    public List<Pago> pagos(int idCredito) throws SQLException {
        List<Pago> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT p.*, u.nombre FROM credito_pagos p "
                     + "JOIN usuarios u ON u.id = p.id_usuario WHERE p.id_credito = ? ORDER BY p.id")) {
            ps.setInt(1, idCredito);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Pago(rs.getInt("id"), rs.getString("metodo"), rs.getBigDecimal("valor"),
                            rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("nombre")));
                }
            }
        }
        return lista;
    }

    public BigDecimal totalPagado(int idCredito) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(valor), 0) FROM credito_pagos WHERE id_credito = ?")) {
            ps.setInt(1, idCredito);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    public void insertarPago(int idCredito, int idUsuario, Integer idCaja, String metodo, BigDecimal valor) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO credito_pagos (id_credito, id_usuario, id_caja, metodo, valor) "
                     + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idCredito);
            ps.setInt(2, idUsuario);
            ps.setObject(3, idCaja);
            ps.setString(4, metodo);
            ps.setBigDecimal(5, valor);
            ps.executeUpdate();
        }
    }

    // ---- avisos de WhatsApp ya enviados (clave = "aviso:3" o "mora:2026-11-04")

    public Set<String> avisos(int idCredito) throws SQLException {
        Set<String> claves = new HashSet<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT clave FROM credito_avisos WHERE id_credito = ?")) {
            ps.setInt(1, idCredito);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claves.add(rs.getString(1));
                }
            }
        }
        return claves;
    }

    // Fecha del último aviso de mora de un crédito (null si nunca se avisó).
    public LocalDate ultimoAvisoMora(int idCredito) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT MAX(fecha) FROM credito_avisos WHERE id_credito = ? AND clave LIKE 'mora:%'")) {
            ps.setInt(1, idCredito);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                java.sql.Timestamp t = rs.getTimestamp(1);
                return t == null ? null : t.toLocalDateTime().toLocalDate();
            }
        }
    }

    public void marcarAviso(int idCredito, String clave) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO credito_avisos (id_credito, clave) VALUES (?, ?) "
                     + "ON DUPLICATE KEY UPDATE fecha = CURRENT_TIMESTAMP")) {
            ps.setInt(1, idCredito);
            ps.setString(2, clave);
            ps.executeUpdate();
        }
    }
}
