package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Compra;
import com.pos.modelo.DetalleCompra;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CompraDAO {

    // Guarda la compra completa en UNA transacción (igual que VentaDAO, pero el stock SUBE):
    //   1) encabezado de la compra  2) cada línea  3) sube stock y actualiza costo
    //   4) anota el movimiento ENTRADA en el historial de inventario.
    public void guardar(Compra c) throws SQLException {
        String sqlCompra = "INSERT INTO compras (id_proveedor, id_usuario, total) VALUES (?, ?, ?)";
        String sqlDetalle = "INSERT INTO detalle_compra (id_compra, id_producto, cantidad, costo_unit) VALUES (?, ?, ?, ?)";
        // Sube el stock y deja como precio_compra el costo de ESTA compra (el más reciente).
        String sqlStock = "UPDATE productos SET stock = stock + ?, precio_compra = ? WHERE id = ?";
        String sqlMov = "INSERT INTO movimientos_inventario (id_producto, id_usuario, tipo, cantidad, motivo) "
                + "VALUES (?, ?, 'ENTRADA', ?, ?)";

        Connection con = Conexion.obtener();
        try {
            con.setAutoCommit(false);   // nada se graba hasta el commit()

            try (PreparedStatement ps = con.prepareStatement(sqlCompra, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, c.getIdProveedor());
                ps.setInt(2, c.getIdUsuario());
                ps.setBigDecimal(3, c.getTotal());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    c.setId(keys.getInt(1));
                }
            }

            for (DetalleCompra d : c.getDetalles()) {
                try (PreparedStatement ps = con.prepareStatement(sqlDetalle)) {
                    ps.setInt(1, c.getId());
                    ps.setInt(2, d.getIdProducto());
                    ps.setInt(3, d.getCantidad());
                    ps.setBigDecimal(4, d.getCostoUnit());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(sqlStock)) {
                    ps.setInt(1, d.getCantidad());
                    ps.setBigDecimal(2, d.getCostoUnit());
                    ps.setInt(3, d.getIdProducto());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(sqlMov)) {
                    ps.setInt(1, d.getIdProducto());
                    ps.setInt(2, c.getIdUsuario());
                    ps.setInt(3, d.getCantidad());
                    ps.setString(4, "Compra #" + c.getId());
                    ps.executeUpdate();
                }
            }

            con.commit();
        } catch (SQLException e) {
            con.rollback();   // si algo falló, se deshace todo
            throw e;
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    // Historial de compras a un proveedor, con cuántas unidades trajo cada una (la más reciente primero).
    public List<Compra> listarPorProveedor(int idProveedor) throws SQLException {
        String sql = "SELECT c.*, COALESCE(SUM(d.cantidad), 0) AS unidades FROM compras c "
                + "LEFT JOIN detalle_compra d ON d.id_compra = c.id "
                + "WHERE c.id_proveedor = ? GROUP BY c.id ORDER BY c.fecha DESC, c.id DESC";
        List<Compra> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idProveedor);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Compra c = new Compra();
                    c.setId(rs.getInt("id"));
                    c.setIdProveedor(rs.getInt("id_proveedor"));
                    c.setIdUsuario(rs.getInt("id_usuario"));
                    c.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    c.setTotal(rs.getBigDecimal("total"));
                    c.setTotalUnidades(rs.getInt("unidades"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    // Lista las compras de un rango de fechas (sin sus líneas), con el nombre del proveedor.
    public List<Compra> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        // Se compara contra el día siguiente a "hasta" para incluir TODO el último día.
        String sql = "SELECT c.*, p.nombre AS proveedor FROM compras c "
                + "JOIN proveedores p ON c.id_proveedor = p.id "
                + "WHERE c.fecha >= ? AND c.fecha < ? ORDER BY c.fecha DESC";
        List<Compra> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Compra c = new Compra();
                    c.setId(rs.getInt("id"));
                    c.setIdProveedor(rs.getInt("id_proveedor"));
                    c.setNombreProveedor(rs.getString("proveedor"));
                    c.setIdUsuario(rs.getInt("id_usuario"));
                    c.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    c.setTotal(rs.getBigDecimal("total"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }
}
