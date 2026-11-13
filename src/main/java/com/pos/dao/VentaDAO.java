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
        guardar(v, false);
    }

    // Sube una venta que se hizo sin conexión: conserva su fecha y hora originales, queda marcada con su identificador
    // único (si ya se había subido, la base la rechaza y no se duplica) y, aunque el stock de hoy no alcance, la
    // venta ya ocurrió: el stock puede quedar negativo para que se vea que hay que revisarlo.
    public void guardarSincronizada(Venta v) throws SQLException {
        guardar(v, true);
    }

    private void guardar(Venta v, boolean sincronizada) throws SQLException {
        // La venta y su número de recibo van en UNA sola instrucción (el id sale de la secuencia y se usa ya para armar
        // el número). La base está en internet y cada ida y vuelta cuesta ~140 ms: por eso se piden pocas.
        String sqlVenta = "WITH n AS (SELECT nextval(pg_get_serial_sequence('ventas', 'id')) AS id) "
                + "INSERT INTO ventas (id, id_caja, id_usuario, id_cliente, subtotal, descuento, total, metodo_pago, "
                + "descuento_promo, descuento_puntos, puntos_usados, puntos_ganados, financiera_nombre, etiqueta"
                + (sincronizada ? ", fecha, origen_offline" : "") + ") "
                + "SELECT n.id, ?, ?, ?::integer, ?, ?, ?, ?, ?, ?, ?, ?, ?::varchar, ? || '-' || lpad(n.id::text, 5, '0')"
                + (sincronizada ? ", ?, ?" : "") + " FROM n RETURNING id";

        Connection con = Conexion.obtener();
        try {
            // Apaga el guardado automático: nada se graba hasta que hagamos commit().
            con.setAutoCommit(false);

            // 1) La venta (encabezado), ya con su número de recibo por caja: prefijo + id con ceros (C1-00005).
            try (PreparedStatement ps = con.prepareStatement(sqlVenta)) {
                ps.setInt(1, v.getIdCaja());
                ps.setInt(2, v.getIdUsuario());
                ps.setObject(3, v.getIdCliente());   // setObject permite enviar null
                ps.setBigDecimal(4, v.getSubtotal());
                ps.setBigDecimal(5, v.getDescuento());
                ps.setBigDecimal(6, v.getTotal());
                ps.setString(7, v.getMetodoPago());
                ps.setBigDecimal(8, v.getDescuentoPromo());
                ps.setBigDecimal(9, v.getDescuentoPuntos());
                ps.setInt(10, v.getPuntosUsados());
                ps.setInt(11, v.getPuntosGanados());
                ps.setString(12, v.getFinancieraNombre());
                ps.setString(13, v.getPrefijoCaja());
                if (sincronizada) {
                    ps.setTimestamp(14, Timestamp.valueOf(v.getFecha()));
                    ps.setString(15, v.getOrigenOffline());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    v.setId(rs.getInt(1));
                }
            }
            v.setEtiqueta(v.getPrefijoCaja() + "-" + String.format("%05d", v.getId()));

            // 2) Todo lo demás (líneas, stock, movimientos, pagos, deuda) se manda junto, en un solo envío.
            //    Son números y textos que arma el propio programa; los textos se escapan.
            List<String> sentencias = new ArrayList<>();
            List<Integer> posicionStock = new ArrayList<>();   // dónde quedó cada "bajar stock", para revisar que alcanzó
            for (DetalleVenta d : v.getDetalles()) {
                sentencias.add("INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unit, costo_unit, subtotal, precio_original) VALUES ("
                        + v.getId() + ", " + d.getIdProducto() + ", " + d.getCantidad() + ", " + d.getPrecioUnit().toPlainString() + ", "
                        + d.getCostoUnit().toPlainString() + ", " + d.getSubtotal().toPlainString() + ", " + d.getPrecioOriginal().toPlainString() + ")");
                // "stock >= cantidad" evita que el stock quede negativo aunque dos cajeros vendan a la vez.
                posicionStock.add(sentencias.size());
                sentencias.add("UPDATE productos SET stock = stock - " + d.getCantidad() + " WHERE id = " + d.getIdProducto()
                        + (sincronizada ? "" : " AND stock >= " + d.getCantidad()));
                sentencias.add("INSERT INTO movimientos_inventario (id_producto, id_usuario, tipo, cantidad, motivo) VALUES ("
                        + d.getIdProducto() + ", " + v.getIdUsuario() + ", 'SALIDA', " + d.getCantidad() + ", " + texto("Venta #" + v.getId()) + ")");
            }
            // Los pagos (puede haber varios: efectivo + tarjeta, etc.)
            for (Pago pago : v.getPagos()) {
                sentencias.add("INSERT INTO pagos_venta (id_venta, metodo, monto) VALUES (" + v.getId() + ", " + texto(pago.metodo())
                        + ", " + pago.monto().toPlainString() + ")");
            }
            // Si una parte de la venta quedó FIADA, se anota la deuda del cliente.
            for (Pago pago : v.getPagos()) {
                if ("FIADO".equals(pago.metodo())) {
                    sentencias.add("INSERT INTO fiados (id_venta, id_cliente, monto, saldo) VALUES (" + v.getId() + ", "
                            + v.getIdCliente() + ", " + pago.monto().toPlainString() + ", " + pago.monto().toPlainString() + ")");
                }
            }
            try (Statement st = con.createStatement()) {
                for (String s : sentencias) {
                    st.addBatch(s);
                }
                int[] filas = st.executeBatch();
                for (int i = 0; i < posicionStock.size(); i++) {
                    if (filas[posicionStock.get(i)] == 0) {   // nadie cumplió "stock >= cantidad": no alcanzó el stock
                        throw new SQLException("Stock insuficiente para: " + v.getDetalles().get(i).getNombreProducto());
                    }
                }
            }

            // 3) Si parte de la venta se financia en cuotas, se crea el crédito con su plan de pagos.
            if (v.getPlanCredito() != null) {
                String descripcion = v.getDetalles().stream().map(d -> d.getCantidad() + " x " + d.getNombreProducto())
                        .collect(java.util.stream.Collectors.joining(", "));
                if (descripcion.length() > 190) {
                    descripcion = descripcion.substring(0, 187) + "...";
                }
                new CreditoDAO().guardar(con, v, v.getPlanCredito(), v.getTotal().subtract(v.getPlanCredito().financiado()), descripcion);
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

    // Un texto como literal de SQL (entre comillas simples, con las comillas internas duplicadas).
    private static String texto(String s) {
        return "'" + s.replace("'", "''") + "'";
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

    // Los recibos más recientes que coinciden con el texto: número, cliente, vendedor o producto.
    public List<Venta> buscarRecibos(String texto, int limite) throws SQLException {
        String sql = consultaBase() + " WHERE (v.etiqueta ILIKE ? OR CAST(v.id AS TEXT) ILIKE ? OR c.nombre ILIKE ? OR u.nombre ILIKE ? "
                + "OR EXISTS (SELECT 1 FROM detalle_venta d JOIN productos p ON p.id = d.id_producto "
                + "WHERE d.id_venta = v.id AND p.nombre ILIKE ?)) ORDER BY v.id DESC LIMIT ?";
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        List<Venta> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, patron);
            }
            ps.setInt(6, limite);
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
            new CreditoDAO().cancelarDeVenta(con, idVenta);   // si la venta era a crédito, el crédito se cancela

            // Primero se leen las líneas completas, y luego se devuelve el stock de cada una.
            List<int[]> lineas = new ArrayList<>();   // cada int[] = {id_producto, cantidad}
            try (PreparedStatement ps = con.prepareStatement(
                    // Solo vuelve al inventario lo que no se había devuelto antes en devoluciones parciales.
                    "SELECT dv.id_producto, dv.cantidad - COALESCE((SELECT SUM(di.cantidad) FROM devolucion_items di "
                            + "JOIN devoluciones d ON d.id = di.id_devolucion WHERE d.id_venta = dv.id_venta "
                            + "AND di.id_producto = dv.id_producto), 0) FROM detalle_venta dv WHERE dv.id_venta = ?")) {
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

    // Puntos de un cliente = los que ganó menos los que usó, solo en ventas no anuladas.
    public int puntosDeCliente(int idCliente) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(puntos_ganados - puntos_usados), 0) "
                     + "FROM ventas WHERE id_cliente = ? AND estado = 'COMPLETADA'")) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // SELECT común: une con clientes (LEFT JOIN: la venta puede no tener cliente) y con usuarios.
    private String consultaBase() {
        return "SELECT v.*, c.nombre AS cliente, u.nombre AS vendedor, "
                + "(SELECT COALESCE(SUM(dv.cantidad), 0) FROM detalle_venta dv WHERE dv.id_venta = v.id) AS unidades FROM ventas v "
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
        v.setDescuentoPromo(rs.getBigDecimal("descuento_promo"));
        v.setDescuentoPuntos(rs.getBigDecimal("descuento_puntos"));
        v.setPuntosUsados(rs.getInt("puntos_usados"));
        v.setPuntosGanados(rs.getInt("puntos_ganados"));
        v.setEtiqueta(rs.getString("etiqueta"));
        v.setFinancieraNombre(rs.getString("financiera_nombre"));
        v.setEstado(rs.getString("estado"));
        String cliente = rs.getString("cliente");
        v.setNombreCliente(cliente != null ? cliente : "Cliente general");
        v.setNombreUsuario(rs.getString("vendedor"));
        v.setUnidades(rs.getInt("unidades"));
        return v;
    }
}