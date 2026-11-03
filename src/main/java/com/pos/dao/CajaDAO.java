package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Caja;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class CajaDAO {

    // Abre una caja nueva: guarda quién la abre y con cuánto efectivo empieza.
    // La fecha de apertura y el estado ABIERTA los pone MySQL solo (DEFAULT del esquema).
    public void abrir(Caja c) throws SQLException {
        String sql = "INSERT INTO cajas (id_usuario, monto_inicial) VALUES (?, ?)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, c.getIdUsuario());
            ps.setBigDecimal(2, c.getMontoInicial());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getInt(1));
                }
            }
        }
    }

    // Busca la caja ABIERTA de un usuario. Devuelve null si no tiene ninguna.
    public Caja buscarAbiertaDeUsuario(int idUsuario) throws SQLException {
        String sql = "SELECT * FROM cajas WHERE id_usuario = ? AND estado = 'ABIERTA'";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, idUsuario);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
        }
        return null;
    }

    // Cierra la caja: guarda lo esperado, lo contado y la diferencia.
    // NOW() es la fecha y hora actual de MySQL.
    public void cerrar(Caja c) throws SQLException {
        String sql = "UPDATE cajas SET cerrada_en = NOW(), monto_esperado = ?, monto_contado = ?, "
                + "diferencia = ?, estado = 'CERRADA' WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setBigDecimal(1, c.getMontoEsperado());
            ps.setBigDecimal(2, c.getMontoContado());
            ps.setBigDecimal(3, c.getDiferencia());
            ps.setInt(4, c.getId());
            ps.executeUpdate();
        }
    }

    // Suma lo vendido en EFECTIVO en esta caja (sin contar ventas anuladas).
    // COALESCE: si no hay ventas, SUM devuelve NULL; así lo cambiamos por 0.
    public BigDecimal totalEfectivoVentas(int idCaja) throws SQLException {
        // Se suman los pagos en EFECTIVO de las ventas de esta caja. Como una venta puede pagarse con
        // varios medios, ya no basta con mirar el método de la venta. La segunda parte cubre ventas
        // viejas que todavía no tengan sus pagos registrados.
        String sql = "SELECT COALESCE(SUM(monto), 0) FROM ("
                + " SELECT pv.monto AS monto FROM pagos_venta pv JOIN ventas v ON v.id = pv.id_venta"
                + "  WHERE v.id_caja = ? AND v.estado = 'COMPLETADA' AND pv.metodo = 'EFECTIVO'"
                + " UNION ALL"
                + " SELECT v.total FROM ventas v"
                + "  WHERE v.id_caja = ? AND v.estado = 'COMPLETADA' AND v.metodo_pago = 'EFECTIVO'"
                + "  AND NOT EXISTS (SELECT 1 FROM pagos_venta p WHERE p.id_venta = v.id)"
                + " UNION ALL"
                // Los abonos de fiados que se recibieron en efectivo en esta caja también cuentan.
                + " SELECT a.monto FROM abonos_fiado a WHERE a.id_caja = ? AND a.metodo = 'EFECTIVO'"
                + ") t";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, idCaja);
            ps.setInt(2, idCaja);
            ps.setInt(3, idCaja);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    // Lo vendido en esta caja por cada método de pago (EFECTIVO, TARJETA, TRANSFERENCIA, FIADO),
    // sin contar ventas anuladas. Sirve para el comprobante del cierre.
    public java.util.Map<String, BigDecimal> ventasPorMetodo(int idCaja) throws SQLException {
        return sumarPorMetodo("SELECT pv.metodo, SUM(pv.monto) FROM pagos_venta pv JOIN ventas v ON v.id = pv.id_venta "
                + "WHERE v.id_caja = ? AND v.estado = 'COMPLETADA' GROUP BY pv.metodo", idCaja);
    }

    // Lo que se recibió en esta caja como abonos de fiados, por método de pago.
    public java.util.Map<String, BigDecimal> abonosPorMetodo(int idCaja) throws SQLException {
        return sumarPorMetodo("SELECT metodo, SUM(monto) FROM abonos_fiado WHERE id_caja = ? GROUP BY metodo", idCaja);
    }

    // Cuántas ventas (no anuladas) se hicieron en esta caja.
    public int contarVentas(int idCaja) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM ventas WHERE id_caja = ? AND estado = 'COMPLETADA'")) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Ejecuta un SELECT "método, suma" con el id de la caja como único parámetro.
    private java.util.Map<String, BigDecimal> sumarPorMetodo(String sql, int idCaja) throws SQLException {
        java.util.Map<String, BigDecimal> mapa = new java.util.LinkedHashMap<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString(1), rs.getBigDecimal(2));
                }
            }
        }
        return mapa;
    }

    // Convierte UNA fila del resultado en un objeto Caja.
    private Caja mapear(ResultSet rs) throws SQLException {
        Caja c = new Caja();
        c.setId(rs.getInt("id"));
        c.setIdUsuario(rs.getInt("id_usuario"));
        c.setAbiertoEn(rs.getTimestamp("abierta_en").toLocalDateTime());

        // cerrada_en es null mientras la caja esté abierta: hay que revisarlo
        // antes de convertir, si no daría error.
        Timestamp cierre = rs.getTimestamp("cerrada_en");
        if (cierre != null) {
            c.setCerradaEn(cierre.toLocalDateTime());
        }

        c.setMontoInicial(rs.getBigDecimal("monto_inicial"));
        c.setMontoEsperado(rs.getBigDecimal("monto_esperado"));   // null si sigue abierta
        c.setMontoContado(rs.getBigDecimal("monto_contado"));
        c.setDiferencia(rs.getBigDecimal("diferencia"));
        c.setEstado(rs.getString("estado"));
        return c;
    }
}