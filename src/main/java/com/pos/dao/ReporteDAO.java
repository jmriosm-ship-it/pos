package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.ProductoVendido;
import com.pos.modelo.VentaDia;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de solo lectura para los reportes y el dashboard.
 * Todas cuentan únicamente ventas COMPLETADAS (las anuladas no suman).
 * Los rangos son [desde, hasta] incluyendo todo el último día.
 */
public class ReporteDAO {

    // Cuántas ventas hubo y cuánto se vendió en total.
    // Devuelve {cantidadDeVentas, totalVendido}.
    public Object[] ventasTotales(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM ventas "
                + "WHERE estado = 'COMPLETADA' AND fecha >= ? AND fecha < ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new Object[]{rs.getInt(1), rs.getBigDecimal(2)};
        }
    }

    // Lo que nos costó la mercancía vendida: suma de (costo × cantidad) de cada línea.
    // Por eso guardamos costo_unit en cada venta: el costo de HOY no altera ventas viejas.
    public BigDecimal costoVendido(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT COALESCE(SUM(d.costo_unit * d.cantidad), 0) FROM detalle_venta d "
                + "JOIN ventas v ON d.id_venta = v.id "
                + "WHERE v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    // Cuánto se vendió con cada método de pago: {EFECTIVO=..., TARJETA=...}.
    public Map<String, BigDecimal> ventasPorMetodoPago(LocalDate desde, LocalDate hasta) throws SQLException {
        // Se suman los PAGOS (una venta puede pagarse con varios medios). La segunda parte cubre
        // ventas viejas que todavía no tengan pagos registrados.
        String sql = "SELECT metodo, SUM(monto) FROM ("
                + " SELECT pv.metodo AS metodo, pv.monto AS monto FROM pagos_venta pv JOIN ventas v ON v.id = pv.id_venta"
                + "  WHERE v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ?"
                + " UNION ALL"
                + " SELECT v.metodo_pago, v.total FROM ventas v"
                + "  WHERE v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ? AND v.metodo_pago <> 'MIXTO'"
                + "  AND NOT EXISTS (SELECT 1 FROM pagos_venta p WHERE p.id_venta = v.id)"
                + ") t GROUP BY metodo";
        // LinkedHashMap mantiene el orden en que se agregan los datos.
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            java.sql.Timestamp ini = Timestamp.valueOf(desde.atStartOfDay());
            java.sql.Timestamp fin = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
            ps.setTimestamp(1, ini);
            ps.setTimestamp(2, fin);
            ps.setTimestamp(3, ini);
            ps.setTimestamp(4, fin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString(1), rs.getBigDecimal(2));
                }
            }
        }
        return mapa;
    }

    // Total vendido por día (solo los días que tuvieron ventas), del más viejo al más nuevo.
    public List<VentaDia> ventasPorDia(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT DATE(fecha) AS dia, SUM(total) FROM ventas "
                + "WHERE estado = 'COMPLETADA' AND fecha >= ? AND fecha < ? GROUP BY DATE(fecha) ORDER BY dia";
        List<VentaDia> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new VentaDia(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)));
            }
        }
        return lista;
    }

    // Los productos que más se vendieron (por unidades) en el periodo.
    public List<ProductoVendido> masVendidos(LocalDate desde, LocalDate hasta, int limite) throws SQLException {
        String sql = "SELECT p.nombre, SUM(d.cantidad) AS unidades, SUM(d.subtotal) AS total "
                + "FROM detalle_venta d JOIN ventas v ON d.id_venta = v.id "
                + "JOIN productos p ON d.id_producto = p.id "
                + "WHERE v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ? "
                + "GROUP BY p.id, p.nombre ORDER BY unidades DESC LIMIT ?";
        List<ProductoVendido> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta)) {
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ProductoVendido(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)));
                }
            }
        }
        return lista;
    }

    // Cuánta plata hay "parada" en el inventario: stock × costo de cada producto activo.
    public BigDecimal valorInventarioAlCosto() throws SQLException {
        String sql = "SELECT COALESCE(SUM(stock * precio_compra), 0) FROM productos WHERE activo = TRUE";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    // Arma el PreparedStatement con las dos fechas del rango (parámetros 1 y 2).
    // El día siguiente a "hasta" a las 00:00 sirve de tope para incluir TODO el último día.
    private PreparedStatement preparar(Connection con, String sql, LocalDate desde, LocalDate hasta) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
        ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
        return ps;
    }
}
