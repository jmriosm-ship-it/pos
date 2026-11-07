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

/**
 * El historial unificado: ventas, compras, cobros, gastos, movimientos de caja, devoluciones y sueldos en una sola lista.
 * valor: positivo = entra plata o se vende; negativo = sale plata.
 */
public class MovimientosDAO {

    public record Fila(LocalDateTime fecha, String grupo, String tipo, String detalle, BigDecimal valor, String usuario) {
    }

    public static final List<String> GRUPOS = List.of("Ventas", "Compras", "Cobros", "Gastos", "Caja", "Devoluciones", "Nómina");

    // Cada SELECT entrega las mismas columnas: fecha, grupo, tipo, detalle, valor, usuario.
    private static final String UNION =
            "SELECT v.fecha AS fecha, 'Ventas' AS grupo, IF(v.estado = 'ANULADA', 'Venta anulada', 'Venta') AS tipo, "
            + " CONCAT(COALESCE(v.etiqueta, CONCAT('#', v.id)), ' · ', COALESCE(c.nombre, 'Cliente general')) AS detalle, "
            + " v.total AS valor, u.nombre AS usuario FROM ventas v LEFT JOIN clientes c ON c.id = v.id_cliente "
            + " JOIN usuarios u ON u.id = v.id_usuario"
            + " UNION ALL SELECT co.fecha, 'Compras', 'Compra', CONCAT('Compra #', co.id, ' · ', p.nombre), -co.total, u.nombre "
            + " FROM compras co JOIN proveedores p ON p.id = co.id_proveedor JOIN usuarios u ON u.id = co.id_usuario"
            + " UNION ALL SELECT a.fecha, 'Cobros', 'Abono de fiado', CONCAT(c.nombre, ' · ', a.metodo), a.monto, u.nombre "
            + " FROM abonos_fiado a JOIN fiados f ON f.id = a.id_fiado JOIN clientes c ON c.id = f.id_cliente JOIN usuarios u ON u.id = a.id_usuario"
            + " UNION ALL SELECT p.fecha, 'Cobros', 'Pago de crédito', CONCAT(c.nombre, ' · ', COALESCE(v.etiqueta, CONCAT('#', v.id)), ' · ', p.metodo), p.valor, u.nombre "
            + " FROM credito_pagos p JOIN creditos cr ON cr.id = p.id_credito JOIN clientes c ON c.id = cr.id_cliente "
            + " JOIN ventas v ON v.id = cr.id_venta JOIN usuarios u ON u.id = p.id_usuario"
            + " UNION ALL SELECT g.fecha, 'Gastos', g.categoria, g.concepto, -g.monto, u.nombre FROM gastos g JOIN usuarios u ON u.id = g.id_usuario"
            + " UNION ALL SELECT m.fecha, 'Caja', IF(m.tipo = 'INGRESO', 'Ingreso de caja', 'Egreso de caja'), m.motivo, "
            + " IF(m.tipo = 'INGRESO', m.monto, -m.monto), u.nombre FROM movimientos_caja m JOIN usuarios u ON u.id = m.id_usuario"
            + " UNION ALL SELECT ca.abierta_en, 'Caja', 'Apertura de caja', CONCAT('Caja #', ca.id), ca.monto_inicial, u.nombre "
            + " FROM cajas ca JOIN usuarios u ON u.id = ca.id_usuario"
            + " UNION ALL SELECT ca.cerrada_en, 'Caja', 'Cierre de caja', CONCAT('Caja #', ca.id, ' · diferencia ', ca.diferencia), ca.monto_contado, u.nombre "
            + " FROM cajas ca JOIN usuarios u ON u.id = ca.id_usuario WHERE ca.cerrada_en IS NOT NULL"
            + " UNION ALL SELECT d.fecha, 'Devoluciones', 'Devolución', CONCAT('Venta ', COALESCE(v.etiqueta, CONCAT('#', v.id)), ' · ', d.metodo), -d.monto, u.nombre "
            + " FROM devoluciones d JOIN ventas v ON v.id = d.id_venta JOIN usuarios u ON u.id = d.id_usuario"
            + " UNION ALL SELECT n.fecha, 'Nómina', IF(n.tipo = 'PAGO', 'Pago de sueldo', 'Consumo de cupo'), n.concepto, "
            + " IF(n.tipo = 'PAGO', -n.valor, n.valor), u.nombre FROM nomina n JOIN usuarios u ON u.id = n.id_registro WHERE n.anulado = FALSE";

    // grupo = null → todos. texto filtra por tipo, detalle o usuario. limite/salto sirven para paginar.
    public List<Fila> listar(LocalDate desde, LocalDate hasta, String grupo, String texto, int limite, int salto) throws SQLException {
        String sql = "SELECT * FROM (" + UNION + ") t WHERE t.fecha >= ? AND t.fecha < ? "
                + (grupo == null ? "" : "AND t.grupo = ? ")
                + "AND (t.tipo LIKE ? OR t.detalle LIKE ? OR t.usuario LIKE ?) ORDER BY t.fecha DESC LIMIT ? OFFSET ?";
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        List<Fila> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setTimestamp(i++, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(i++, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            if (grupo != null) {
                ps.setString(i++, grupo);
            }
            ps.setString(i++, patron);
            ps.setString(i++, patron);
            ps.setString(i++, patron);
            ps.setInt(i++, limite);
            ps.setInt(i, salto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Fila(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getBigDecimal(5), rs.getString(6)));
                }
            }
        }
        return lista;
    }
}
