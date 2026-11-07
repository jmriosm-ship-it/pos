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

    // ---- Financiera: lo que paga una financiera cuenta como ingreso el día que PAGA, no el día de la venta.

    // Parte financiada de las ventas hechas en el rango (se resta de los ingresos de esos días).
    public BigDecimal financieraVendida(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(pv.monto), 0) FROM pagos_venta pv JOIN ventas v ON v.id = pv.id_venta "
                + "WHERE pv.metodo = 'FINANCIERA' AND v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ?", desde, hasta);
    }

    // El costo de esa parte (proporcional al valor financiado).
    public BigDecimal financieraCostoVendido(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(pv.monto / v.total * (SELECT SUM(d.costo_unit * d.cantidad) FROM detalle_venta d "
                + "WHERE d.id_venta = v.id)), 0) FROM pagos_venta pv JOIN ventas v ON v.id = pv.id_venta "
                + "WHERE pv.metodo = 'FINANCIERA' AND v.estado = 'COMPLETADA' AND v.total > 0 AND v.fecha >= ? AND v.fecha < ?", desde, hasta);
    }

    // Lo que las financieras pagaron en el rango (ingreso real). Los ajustes no suman.
    public BigDecimal financieraCobrada(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(cf.valor), 0) FROM cobros_financiera cf JOIN ventas v ON v.id = cf.id_venta "
                + "WHERE cf.tipo = 'PAGO' AND v.estado = 'COMPLETADA' AND cf.fecha >= ? AND cf.fecha < ?", desde, hasta);
    }

    // El costo que corresponde a lo cobrado (proporcional: cobrado / total de la venta).
    public BigDecimal financieraCostoCobrado(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(cf.valor / v.total * (SELECT SUM(d.costo_unit * d.cantidad) FROM detalle_venta d "
                + "WHERE d.id_venta = v.id)), 0) FROM cobros_financiera cf JOIN ventas v ON v.id = cf.id_venta "
                + "WHERE cf.tipo = 'PAGO' AND v.estado = 'COMPLETADA' AND v.total > 0 AND cf.fecha >= ? AND cf.fecha < ?", desde, hasta);
    }

    // Unidades vendidas de cada producto en un rango (ventas completadas): id_producto -> unidades.
    public Map<Integer, Integer> unidadesVendidas(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT d.id_producto, SUM(d.cantidad) FROM detalle_venta d JOIN ventas v ON v.id = d.id_venta "
                + "WHERE v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ? GROUP BY d.id_producto";
        Map<Integer, Integer> mapa = new java.util.HashMap<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return mapa;
    }

    // Lo que se devolvió a clientes en el periodo (por fecha de la devolución).
    public BigDecimal devoluciones(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(d.monto), 0) FROM devoluciones d JOIN ventas v ON v.id = d.id_venta "
                + "WHERE v.estado = 'COMPLETADA' AND d.fecha >= ? AND d.fecha < ?", desde, hasta);
    }

    // Lo que costaba la mercancía que volvió por devoluciones (se resta del costo vendido).
    public BigDecimal costoDevuelto(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(di.cantidad * dv.costo_unit), 0) FROM devolucion_items di "
                + "JOIN devoluciones d ON d.id = di.id_devolucion JOIN ventas v ON v.id = d.id_venta "
                + "JOIN detalle_venta dv ON dv.id_venta = d.id_venta AND dv.id_producto = di.id_producto "
                + "WHERE v.estado = 'COMPLETADA' AND d.fecha >= ? AND d.fecha < ?", desde, hasta);
    }

    // Intereses ganados: de cada pago de un crédito activo, la parte que corresponde a intereses.
    public BigDecimal interesesCobrados(LocalDate desde, LocalDate hasta) throws SQLException {
        return suma("SELECT COALESCE(SUM(p.valor * c.intereses / c.total_credito), 0) FROM credito_pagos p "
                + "JOIN creditos c ON c.id = p.id_credito WHERE c.estado = 'ACTIVO' AND c.total_credito > 0 "
                + "AND p.fecha >= ? AND p.fecha < ?", desde, hasta);
    }

    private BigDecimal suma(String sql, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    // Una categoría con lo vendido y lo ganado (antes de descuentos de la venta).
    public record FilaCategoria(String nombre, BigDecimal ingresos, BigDecimal ganancia) {
    }

    public List<FilaCategoria> gananciaPorCategoria(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT COALESCE(c.nombre, 'Sin categoría'), SUM(d.subtotal), SUM(d.subtotal - d.costo_unit * d.cantidad) AS g "
                + "FROM detalle_venta d JOIN ventas v ON v.id = d.id_venta JOIN productos p ON p.id = d.id_producto "
                + "LEFT JOIN categorias c ON c.id = p.id_categoria WHERE v.estado = 'COMPLETADA' AND v.fecha >= ? AND v.fecha < ? "
                + "GROUP BY c.id, c.nombre ORDER BY g DESC";
        List<FilaCategoria> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new FilaCategoria(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)));
            }
        }
        return lista;
    }

    // Total vendido por día, opcionalmente solo de un vendedor (para el calendario).
    public List<VentaDia> ventasPorDiaDe(LocalDate desde, LocalDate hasta, Integer idUsuario) throws SQLException {
        String sql = "SELECT DATE(fecha), SUM(total) FROM ventas WHERE estado = 'COMPLETADA' AND fecha >= ? AND fecha < ? "
                + (idUsuario == null ? "" : "AND id_usuario = ? ") + "GROUP BY DATE(fecha) ORDER BY 1";
        List<VentaDia> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = preparar(con, sql, desde, hasta)) {
            if (idUsuario != null) {
                ps.setInt(3, idUsuario);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new VentaDia(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)));
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
