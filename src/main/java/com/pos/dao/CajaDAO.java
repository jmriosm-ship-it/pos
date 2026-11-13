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
    // La fecha de apertura y el estado ABIERTA los pone la base de datos solo (DEFAULT del esquema).
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
        try {
            return buscarAbiertaDeUsuarioDb(idUsuario);
        } catch (com.pos.config.SinConexionException e) {
            return com.pos.offline.CacheLocal.cajaAbierta(idUsuario);
        }
    }

    private Caja buscarAbiertaDeUsuarioDb(int idUsuario) throws SQLException {
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

    // Un cierre ya hecho, con el nombre de quien tenía la caja.
    public record CierreFila(Caja caja, String cajero) {
    }

    // Los últimos cierres (los más recientes primero). idUsuario = null trae los de todos.
    public java.util.List<CierreFila> listarCerradas(Integer idUsuario, int limite) throws SQLException {
        String sql = "SELECT c.*, u.nombre AS cajero FROM cajas c JOIN usuarios u ON u.id = c.id_usuario "
                + "WHERE c.estado = 'CERRADA'" + (idUsuario == null ? " " : " AND c.id_usuario = ? ")
                + "ORDER BY c.cerrada_en DESC LIMIT ?";
        java.util.List<CierreFila> lista = new java.util.ArrayList<>();
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (idUsuario != null) {
                ps.setInt(i++, idUsuario);
            }
            ps.setInt(i, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new CierreFila(mapear(rs), rs.getString("cajero")));
                }
            }
        }
        return lista;
    }

    // Cambia el monto con el que se abrió una caja que sigue abierta (por ejemplo, si se olvidó ponerlo bien).
    public void corregirBase(int idCaja, java.math.BigDecimal nuevoMonto) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE cajas SET monto_inicial = ? WHERE id = ? AND estado = 'ABIERTA'")) {
            ps.setBigDecimal(1, nuevoMonto);
            ps.setInt(2, idCaja);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("Esa caja ya no está abierta.");
            }
        }
    }

    // Cierra la caja: guarda lo esperado, lo contado y la diferencia.
    // NOW() es la fecha y hora actual de la base de datos.
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
                + " UNION ALL"
                // Ingresos y egresos manuales, y devoluciones de ventas pagadas en efectivo (salen de la caja).
                + " SELECT CASE m.tipo WHEN 'INGRESO' THEN m.monto ELSE -m.monto END FROM movimientos_caja m WHERE m.id_caja = ?"
                + " UNION ALL"
                + " SELECT -d.monto FROM devoluciones d WHERE d.id_caja = ? AND d.metodo = 'EFECTIVO'"
                + " UNION ALL"
                + " SELECT cp.valor FROM credito_pagos cp WHERE cp.id_caja = ? AND cp.metodo = 'EFECTIVO'"
                // El efectivo que salió de la caja para pagarle a los proveedores.
                + " UNION ALL"
                + " SELECT -ac.monto FROM abonos_compra ac WHERE ac.id_caja = ? AND ac.metodo = 'EFECTIVO'"
                + ") t";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, idCaja);
            ps.setInt(2, idCaja);
            ps.setInt(3, idCaja);
            ps.setInt(4, idCaja);
            ps.setInt(5, idCaja);
            ps.setInt(6, idCaja);
            ps.setInt(7, idCaja);

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

    // El efectivo que salió de esta caja para pagarle a los proveedores.
    public BigDecimal pagosProveedoresEfectivo(int idCaja) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(monto), 0) FROM abonos_compra WHERE id_caja = ? AND metodo = 'EFECTIVO'")) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
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