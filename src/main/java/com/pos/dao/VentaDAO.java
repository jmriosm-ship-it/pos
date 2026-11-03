package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Pago;
import com.pos.modelo.Venta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class VentaDAO {

    // Guarda la venta completa dentro de UNA transacción.
    // Transacción = grupo de operaciones que se aplican todas juntas o ninguna.
    public void guardar(Venta v) throws SQLException {
        String sqlVenta = "INSERT INTO ventas (id_caja, id_usuario, id_cliente, subtotal, descuento, total, metodo_pago) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String sqlDetalle = "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unit, costo_unit, subtotal) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        // "stock >= ?" evita que el stock quede negativo aunque dos cajeros vendan a la vez.
        String sqlStock = "UPDATE productos SET stock = stock - ? WHERE id = ? AND stock >= ?";
        String sqlMovimiento = "INSERT INTO movimientos_inventario (id_producto, id_usuario, tipo, cantidad, motivo) "
                + "VALUES (?, ?, 'SALIDA', ?, ?)";

        Connection con = Conexion.obtener();
        try {
            // Apaga el guardado automático: nada se graba hasta que hagamos commit().
            con.setAutoCommit(false);

            // 1) La venta (encabezado)
            try (PreparedStatement ps = con.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, v.getIdCaja());
                ps.setInt(2, v.getIdUsuario());
                ps.setObject(3, v.getIdCliente());   // setObject permite enviar null
                ps.setBigDecimal(4, v.getSubtotal());
                ps.setBigDecimal(5, v.getDescuento());
                ps.setBigDecimal(6, v.getTotal());
                ps.setString(7, v.getMetodoPago());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    v.setId(keys.getInt(1));
                }
            }

            // 2) Cada línea del carrito
            for (DetalleVenta d : v.getDetalles()) {

                try (PreparedStatement ps = con.prepareStatement(sqlDetalle)) {
                    ps.setInt(1, v.getId());
                    ps.setInt(2, d.getIdProducto());
                    ps.setInt(3, d.getCantidad());
                    ps.setBigDecimal(4, d.getPrecioUnit());
                    ps.setBigDecimal(5, d.getCostoUnit());
                    ps.setBigDecimal(6, d.getSubtotal());
                    ps.executeUpdate();
                }

                // 3) Bajar el stock
                try (PreparedStatement ps = con.prepareStatement(sqlStock)) {
                    ps.setInt(1, d.getCantidad());
                    ps.setInt(2, d.getIdProducto());
                    ps.setInt(3, d.getCantidad());
                    int filas = ps.executeUpdate();   // cuántas filas se modificaron
                    if (filas == 0) {
                        // Nadie cumplió "stock >= cantidad": no alcanzó el stock.
                        throw new SQLException("Stock insuficiente para: " + d.getNombreProducto());
                    }
                }

                // 4) Anotar el movimiento de inventario
                try (PreparedStatement ps = con.prepareStatement(sqlMovimiento)) {
                    ps.setInt(1, d.getIdProducto());
                    ps.setInt(2, v.getIdUsuario());
                    ps.setInt(3, d.getCantidad());
                    ps.setString(4, "Venta #" + v.getId());
                    ps.executeUpdate();
                }
            }

            // 5) Los pagos de la venta (puede haber varios: efectivo + tarjeta, etc.)
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pagos_venta (id_venta, metodo, monto) VALUES (?, ?, ?)")) {
                for (Pago pago : v.getPagos()) {
                    ps.setInt(1, v.getId());
                    ps.setString(2, pago.metodo());
                    ps.setBigDecimal(3, pago.monto());
                    ps.executeUpdate();
                }
            }

            // 6) Si una parte de la venta quedó FIADA, se anota la deuda del cliente.
            for (Pago pago : v.getPagos()) {
                if ("FIADO".equals(pago.metodo())) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO fiados (id_venta, id_cliente, monto, saldo) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, v.getId());
                        ps.setInt(2, v.getIdCliente());
                        ps.setBigDecimal(3, pago.monto());
                        ps.setBigDecimal(4, pago.monto());
                        ps.executeUpdate();
                    }
                }
            }

            // Todo salió bien: ahora sí se guarda todo de golpe.
            con.commit();

        } catch (SQLException e) {
            // Algo falló: se deshace TODO lo que se alcanzó a hacer.
            con.rollback();
            throw e;   // se vuelve a lanzar para que el servicio se entere
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // Lista las ventas de un rango de fechas (sin sus líneas). Incluye las anuladas;
    // quien las muestre decide si las filtra.
    public List<Venta> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        // Se compara contra el día siguiente a "hasta" para incluir TODO el último día.
        String sql = consultaBase() + " WHERE v.fecha >= ? AND v.fecha < ? ORDER BY v.fecha DESC";
        List<Venta> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    // Historial de compras de un cliente.
    public List<Venta> listarPorCliente(int idCliente) throws SQLException {
        String sql = consultaBase() + " WHERE v.id_cliente = ? ORDER BY v.fecha DESC";
        List<Venta> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    // Trae UNA venta completa, con sus líneas (sirve para ver el detalle o reimprimir el recibo).
    public Venta buscarPorId(int id) throws SQLException {
        String sql = consultaBase() + " WHERE v.id = ?";
        String sqlDetalle = "SELECT d.*, p.nombre AS producto FROM detalle_venta d "
                + "JOIN productos p ON d.id_producto = p.id WHERE d.id_venta = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);

            Venta v;
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                v = mapear(rs);
            }

            try (PreparedStatement psd = con.prepareStatement(sqlDetalle)) {
                psd.setInt(1, id);
                try (ResultSet rs = psd.executeQuery()) {
                    while (rs.next()) {
                        DetalleVenta d = new DetalleVenta();
                        d.setId(rs.getInt("id"));
                        d.setIdVenta(id);
                        d.setIdProducto(rs.getInt("id_producto"));
                        d.setNombreProducto(rs.getString("producto"));
                        d.setCantidad(rs.getInt("cantidad"));
                        d.setPrecioUnit(rs.getBigDecimal("precio_unit"));
                        d.setCostoUnit(rs.getBigDecimal("costo_unit"));
                        v.getDetalles().add(d);
                    }
                }
            }

            // Los pagos de la venta
            try (PreparedStatement psp = con.prepareStatement(
                    "SELECT metodo, monto FROM pagos_venta WHERE id_venta = ? ORDER BY id")) {
                psp.setInt(1, id);
                try (ResultSet rs = psp.executeQuery()) {
                    while (rs.next()) {
                        v.getPagos().add(new Pago(rs.getString("metodo"), rs.getBigDecimal("monto")));
                    }
                }
            }
            return v;
        }
    }

    // Anula una venta: la marca ANULADA y DEVUELVE al inventario lo que se había vendido.
    // También en una transacción: o se hace todo o no se hace nada.
    public void anular(int idVenta, int idUsuario) throws SQLException {
        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);

            // Se bloquea la fila para que nadie más la anule al mismo tiempo.
            try (PreparedStatement ps = con.prepareStatement("SELECT estado FROM ventas WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("La venta no existe.");
                    }
                    if ("ANULADA".equals(rs.getString(1))) {
                        throw new SQLException("La venta ya está anulada.");
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement("UPDATE ventas SET estado = 'ANULADA' WHERE id = ?")) {
                ps.setInt(1, idVenta);
                ps.executeUpdate();
            }

            // Si una parte de la venta fue fiada: sin abonos se cancela la deuda; con abonos no se puede
            // anular (ya hay dinero recibido que habría que devolver, y eso se maneja a mano).
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM abonos_fiado a JOIN fiados f ON a.id_fiado = f.id WHERE f.id_venta = ?")) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new SQLException("Esta venta tiene parte fiada y el cliente ya hizo abonos; no se puede anular.");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE fiados SET estado = 'ANULADO', saldo = 0 WHERE id_venta = ?")) {
                ps.setInt(1, idVenta);
                ps.executeUpdate();
            }

            // Primero se leen las líneas completas, y luego se devuelve el stock de cada una.
            List<int[]> lineas = new ArrayList<>();   // cada int[] = {id_producto, cantidad}
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT id_producto, cantidad FROM detalle_venta WHERE id_venta = ?")) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lineas.add(new int[]{rs.getInt(1), rs.getInt(2)});
                    }
                }
            }

            for (int[] linea : lineas) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE productos SET stock = stock + ? WHERE id = ?")) {
                    ps.setInt(1, linea[1]);
                    ps.setInt(2, linea[0]);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO movimientos_inventario (id_producto, id_usuario, tipo, cantidad, motivo) "
                                + "VALUES (?, ?, 'ENTRADA', ?, ?)")) {
                    ps.setInt(1, linea[0]);
                    ps.setInt(2, idUsuario);
                    ps.setInt(3, linea[1]);
                    ps.setString(4, "Anulación venta #" + idVenta);
                    ps.executeUpdate();
                }
            }

            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // SELECT común: une con clientes (LEFT JOIN: la venta puede no tener cliente) y con usuarios.
    private String consultaBase() {
        return "SELECT v.*, c.nombre AS cliente, u.nombre AS vendedor FROM ventas v "
                + "LEFT JOIN clientes c ON v.id_cliente = c.id "
                + "JOIN usuarios u ON v.id_usuario = u.id";
    }

    // Convierte UNA fila del resultado en un objeto Venta (sin líneas).
    private Venta mapear(ResultSet rs) throws SQLException {
        Venta v = new Venta();
        v.setId(rs.getInt("id"));
        v.setIdCaja(rs.getInt("id_caja"));
        v.setIdUsuario(rs.getInt("id_usuario"));
        v.setIdCliente((Integer) rs.getObject("id_cliente"));
        v.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
        v.setSubtotal(rs.getBigDecimal("subtotal"));
        v.setDescuento(rs.getBigDecimal("descuento"));
        v.setTotal(rs.getBigDecimal("total"));
        v.setMetodoPago(rs.getString("metodo_pago"));
        v.setEstado(rs.getString("estado"));
        String cliente = rs.getString("cliente");
        v.setNombreCliente(cliente != null ? cliente : "Cliente general");
        v.setNombreUsuario(rs.getString("vendedor"));
        return v;
    }
}