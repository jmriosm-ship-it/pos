package com.pos.servicio;

import com.pos.dao.CompraDAO;
import com.pos.dao.ProductoDAO;
import com.pos.modelo.Compra;
import com.pos.modelo.DetalleCompra;
import com.pos.modelo.Producto;
import com.pos.modelo.Proveedor;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class CompraService {

    private final CompraDAO compraDAO = new CompraDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    // Empieza una compra nueva (lista vacía) a un proveedor.
    public Compra nuevaCompra(Usuario usuario, Proveedor proveedor) {
        if (proveedor == null) {
            throw new IllegalArgumentException("Debes elegir un proveedor.");
        }
        Compra c = new Compra();
        c.setIdUsuario(usuario.getId());
        c.setIdProveedor(proveedor.getId());
        c.setNombreProveedor(proveedor.getNombre());
        return c;
    }

    // Agrega un producto a la compra, con la cantidad y el costo que pagamos por unidad.
    // Si el producto ya estaba en la lista, suma la cantidad y deja el costo nuevo.
    public void agregarProducto(Compra c, String codigo, int cantidad, BigDecimal costoUnit) throws SQLException {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        if (costoUnit == null || costoUnit.signum() <= 0) {
            throw new IllegalArgumentException("El costo debe ser mayor a cero.");
        }
        Producto p = productoDAO.buscarPorCodigo(codigo);
        if (p == null) {
            throw new IllegalArgumentException("No existe un producto con el código " + codigo);
        }

        for (DetalleCompra d : c.getDetalles()) {
            if (d.getIdProducto() == p.getId()) {
                d.setCantidad(d.getCantidad() + cantidad);
                d.setCostoUnit(costoUnit);
                c.calcularTotal();
                return;
            }
        }

        DetalleCompra d = new DetalleCompra();
        d.setIdProducto(p.getId());
        d.setNombreProducto(p.getNombre());
        d.setCantidad(cantidad);
        d.setCostoUnit(costoUnit);
        c.getDetalles().add(d);
        c.calcularTotal();
    }

    public void quitarProducto(Compra c, int idProducto) {
        c.getDetalles().removeIf(d -> d.getIdProducto() == idProducto);
        c.calcularTotal();
    }

    // Guarda la compra: sube el stock, actualiza costos y deja el historial.
    public void finalizar(Compra c) throws SQLException {
        if (c.getDetalles().isEmpty()) {
            throw new IllegalStateException("La compra no tiene productos.");
        }
        c.calcularTotal();
        compraDAO.guardar(c);
    }

    public List<Compra> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        return compraDAO.listar(desde, hasta);
    }
}
