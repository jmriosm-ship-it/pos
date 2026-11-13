package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Compra;
import com.pos.modelo.DetalleCompra;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lo que se puede hacer con una compra ya registrada: ver sus líneas, anularla o editarla. */
public class CompraExtra {

    // Las líneas de una compra, con el nombre de cada producto.
    public List<DetalleCompra> detalles(int idCompra) throws SQLException {
        String sql = "SELECT d.*, p.nombre FROM detalle_compra d JOIN productos p ON p.id = d.id_producto "
                + "WHERE d.id_compra = ? ORDER BY d.id";
        List<DetalleCompra> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCompra);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(leer(rs, idCompra));
                }
            }
        }
        return lista;
    }

    private DetalleCompra leer(ResultSet rs, int idCompra) throws SQLException {
        DetalleCompra d = new DetalleCompra();
        d.setId(rs.getInt("id"));
        d.setIdCompra(idCompra);
        d.setIdProducto(rs.getInt("id_producto"));
        d.setNombreProducto(rs.getString("nombre"));
        d.setCantidad(rs.getInt("cantidad"));
        d.setCostoUnit(rs.getBigDecimal("costo_unit"));
        return d;
    }

    // Anula la compra: devuelve el stock que había subido (si alguna unidad ya se vendió, no se puede) y la marca anulada.
    public void anular(int idCompra, int idUsuario, String motivo) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);
            comprobarActiva(con, idCompra, "Esa compra ya estaba anulada.");
            for (DetalleCompra d : detallesEn(con, idCompra)) {
                restarStock(con, d.getIdProducto(), d.getCantidad(), d.getNombreProducto());
                anotar(con, d.getIdProducto(), idUsuario, "SALIDA", d.getCantidad(), "Anulación de la compra #" + idCompra);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE compras SET anulada = TRUE, fecha_anulacion = NOW(), motivo_anulacion = ? WHERE id = ?")) {
                ps.setString(1, motivo);
                ps.setInt(2, idCompra);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // Cambia las cantidades y costos de una compra (y su proveedor): el stock se corrige por la diferencia.
    public void editar(Compra nueva, int idUsuario) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);
            comprobarActiva(con, nueva.getId(), "No se puede editar una compra anulada.");

            Map<Integer, Integer> antes = new HashMap<>();
            Map<Integer, String> nombres = new HashMap<>();
            for (DetalleCompra d : detallesEn(con, nueva.getId())) {
                antes.merge(d.getIdProducto(), d.getCantidad(), Integer::sum);
                nombres.put(d.getIdProducto(), d.getNombreProducto());
            }
            Map<Integer, Integer> despues = new HashMap<>();
            for (DetalleCompra d : nueva.getDetalles()) {
                despues.merge(d.getIdProducto(), d.getCantidad(), Integer::sum);
                nombres.put(d.getIdProducto(), d.getNombreProducto());
            }
            Set<Integer> todos = new HashSet<>(antes.keySet());
            todos.addAll(despues.keySet());
            for (int idProducto : todos) {
                int diferencia = despues.getOrDefault(idProducto, 0) - antes.getOrDefault(idProducto, 0);
                if (diferencia < 0) {
                    restarStock(con, idProducto, -diferencia, nombres.get(idProducto));
                } else if (diferencia > 0) {
                    try (PreparedStatement ps = con.prepareStatement("UPDATE productos SET stock = stock + ? WHERE id = ?")) {
                        ps.setInt(1, diferencia);
                        ps.setInt(2, idProducto);
                        ps.executeUpdate();
                    }
                }
                if (diferencia != 0) {
                    anotar(con, idProducto, idUsuario, "AJUSTE", diferencia, "Edición de la compra #" + nueva.getId());
                }
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM detalle_compra WHERE id_compra = ?")) {
                ps.setInt(1, nueva.getId());
                ps.executeUpdate();
            }
            for (DetalleCompra d : nueva.getDetalles()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO detalle_compra (id_compra, id_producto, cantidad, costo_unit) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, nueva.getId());
                    ps.setInt(2, d.getIdProducto());
                    ps.setInt(3, d.getCantidad());
                    ps.setBigDecimal(4, d.getCostoUnit());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE productos SET precio_compra = ? WHERE id = ?")) {
                    ps.setBigDecimal(1, d.getCostoUnit());
                    ps.setInt(2, d.getIdProducto());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE compras SET id_proveedor = ?, total = ? WHERE id = ?")) {
                ps.setInt(1, nueva.getIdProveedor());
                ps.setBigDecimal(2, nueva.getTotal());
                ps.setInt(3, nueva.getId());
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    private void comprobarActiva(Connection con, int idCompra, String siAnulada) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT anulada FROM compras WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, idCompra);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Esa compra no existe.");
                }
                if (rs.getBoolean(1)) {
                    throw new IllegalStateException(siAnulada);
                }
            }
        }
    }

    private List<DetalleCompra> detallesEn(Connection con, int idCompra) throws SQLException {
        String sql = "SELECT d.*, p.nombre FROM detalle_compra d JOIN productos p ON p.id = d.id_producto "
                + "WHERE d.id_compra = ? ORDER BY d.id";
        List<DetalleCompra> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCompra);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(leer(rs, idCompra));
                }
            }
        }
        return lista;
    }

    // Resta stock solo si alcanza; si ya se vendió parte de lo que entró, avisa en vez de dejar el stock negativo.
    private void restarStock(Connection con, int idProducto, int cantidad, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE productos SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, idProducto);
            ps.setInt(3, cantidad);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("No se puede: de \"" + nombre + "\" ya se vendieron unidades y el stock actual "
                        + "no alcanza para devolver " + cantidad + ".");
            }
        }
    }

    private void anotar(Connection con, int idProducto, int idUsuario, String tipo, int cantidad, String motivo) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO movimientos_inventario "
                + "(id_producto, id_usuario, tipo, cantidad, motivo) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idUsuario);
            ps.setString(3, tipo);
            ps.setInt(4, cantidad);
            ps.setString(5, motivo);
            ps.executeUpdate();
        }
    }

    /** Un pago hecho a un proveedor por una compra. */
    public record Abono(int id, java.time.LocalDateTime fecha, String metodo, java.math.BigDecimal monto, String usuario) {
    }

    // Los pagos que se han hecho por una compra, del más antiguo al más reciente.
    public List<Abono> abonos(int idCompra) throws SQLException {
        String sql = "SELECT a.id, a.fecha, a.metodo, a.monto, u.nombre FROM abonos_compra a JOIN usuarios u ON u.id = a.id_usuario "
                + "WHERE a.id_compra = ? ORDER BY a.id";
        List<Abono> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCompra);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Abono(rs.getInt(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3), rs.getBigDecimal(4), rs.getString(5)));
                }
            }
        }
        return lista;
    }

    // Registra un pago al proveedor (no puede pasar de lo que se debe). El efectivo sale de la caja indicada.
    public void abonar(int idCompra, int idUsuario, Integer idCaja, String metodo, java.math.BigDecimal monto) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);
            java.math.BigDecimal total;
            java.math.BigDecimal pagado;
            try (PreparedStatement ps = con.prepareStatement("SELECT total, pagado, anulada FROM compras WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, idCompra);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Esa compra no existe.");
                    }
                    if (rs.getBoolean(3)) {
                        throw new IllegalStateException("Esa compra está anulada.");
                    }
                    total = rs.getBigDecimal(1);
                    pagado = rs.getBigDecimal(2);
                }
            }
            java.math.BigDecimal saldo = pagado == null ? java.math.BigDecimal.ZERO : total.subtract(pagado);
            if (saldo.signum() <= 0) {
                throw new IllegalStateException("Esa compra ya está pagada.");
            }
            if (monto.compareTo(saldo) > 0) {
                throw new IllegalArgumentException("El pago no puede pasar de lo que se debe (" + saldo.toPlainString() + ").");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO abonos_compra (id_compra, id_usuario, id_caja, metodo, monto) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, idCompra);
                ps.setInt(2, idUsuario);
                ps.setObject(3, "EFECTIVO".equals(metodo) ? idCaja : null);
                ps.setString(4, metodo);
                ps.setBigDecimal(5, monto);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE compras SET pagado = pagado + ? WHERE id = ?")) {
                ps.setBigDecimal(1, monto);
                ps.setInt(2, idCompra);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // Todo lo que se le debe a los proveedores (compras vigentes con saldo).
    public java.math.BigDecimal totalPorPagar() throws SQLException {
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(total - pagado), 0) FROM compras WHERE anulada = FALSE AND pagado IS NOT NULL AND total > pagado");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }
}
