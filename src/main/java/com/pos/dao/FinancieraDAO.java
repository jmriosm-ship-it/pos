package com.pos.dao;

import com.pos.config.Conexion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ventas cuyo pago (todo o parte) lo hace una financiera después, y lo que la financiera ha ido pagando. */
public class FinancieraDAO {

    // Una venta con parte financiada: lo esperado, lo recibido y lo que ya se cerró con ajustes.
    public record Fila(int idVenta, String ventaNumero, LocalDateTime fecha, String cliente, String financiera, BigDecimal esperado,
                       BigDecimal recibido, BigDecimal ajustes, boolean anulada) {
        // Lo que falta por cobrar (0 si la venta se anuló).
        public BigDecimal falta() {
            return anulada ? BigDecimal.ZERO : esperado.subtract(recibido).subtract(ajustes).max(BigDecimal.ZERO);
        }
    }

    public List<Fila> listar() throws SQLException {
        String sql = "SELECT v.id, v.etiqueta, v.fecha, COALESCE(c.nombre, 'Cliente general') AS cliente, v.financiera_nombre, "
                + "pv.monto, v.estado, "
                + "COALESCE((SELECT SUM(cf.valor) FROM cobros_financiera cf WHERE cf.id_venta = v.id AND cf.tipo = 'PAGO'), 0) AS recibido, "
                + "COALESCE((SELECT SUM(cf.valor) FROM cobros_financiera cf WHERE cf.id_venta = v.id AND cf.tipo = 'AJUSTE'), 0) AS ajustes "
                + "FROM ventas v JOIN pagos_venta pv ON pv.id_venta = v.id AND pv.metodo = 'FINANCIERA' "
                + "LEFT JOIN clientes c ON c.id = v.id_cliente ORDER BY v.id DESC";
        List<Fila> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String et = rs.getString("etiqueta");
                lista.add(new Fila(rs.getInt("id"), et != null ? et : "#" + rs.getInt("id"), rs.getTimestamp("fecha").toLocalDateTime(),
                        rs.getString("cliente"), rs.getString("financiera_nombre"), rs.getBigDecimal("monto"),
                        rs.getBigDecimal("recibido"), rs.getBigDecimal("ajustes"), "ANULADA".equals(rs.getString("estado"))));
            }
        }
        return lista;
    }

    public void registrar(int idVenta, String tipo, BigDecimal valor, String nota, int idUsuario) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO cobros_financiera (id_venta, tipo, valor, nota, id_usuario) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idVenta);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, valor);
            ps.setString(4, nota);
            ps.setInt(5, idUsuario);
            ps.executeUpdate();
        }
    }

    // Los nombres de financiera ya usados (para sugerirlos al cobrar).
    public List<String> nombres() throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT DISTINCT financiera_nombre FROM ventas WHERE financiera_nombre IS NOT NULL ORDER BY 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        }
        return lista;
    }

    // Lo que falta por cobrar de todas las financieras (para el Inicio).
    public BigDecimal totalPorCobrar() throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        for (Fila f : listar()) {
            total = total.add(f.falta());
        }
        return total;
    }
}
