package com.pos.servicio;

import com.pos.dao.MovimientoDAO;
import com.pos.dao.ProductoDAO;
import com.pos.modelo.Movimiento;
import com.pos.modelo.Producto;
import com.pos.modelo.Usuario;

import java.sql.SQLException;
import java.util.List;

public class InventarioService {

    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    // Corrige el stock de un producto después de contarlo físicamente.
    // Devuelve la diferencia (negativa = faltaban unidades, positiva = sobraban).
    public int ajustarStock(Usuario usuario, Producto producto, int stockContado, String motivo) throws SQLException {
        if (stockContado < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Debes escribir el motivo del ajuste (ej: conteo físico, merma).");
        }
        return movimientoDAO.ajustar(producto.getId(), usuario.getId(), stockContado, motivo.trim());
    }

    // Productos que ya llegaron al stock mínimo: la lista de "toca comprar".
    public List<Producto> productosConStockBajo() throws SQLException {
        return productoDAO.listarStockBajo();
    }

    public List<Movimiento> historialReciente(int limite) throws SQLException {
        return movimientoDAO.listarRecientes(limite);
    }

    public List<Movimiento> historialDeProducto(Producto p) throws SQLException {
        return movimientoDAO.listarPorProducto(p.getId());
    }
}
