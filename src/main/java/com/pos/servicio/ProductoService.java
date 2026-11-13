package com.pos.servicio;

import com.pos.dao.ProductoDAO;
import com.pos.modelo.Producto;

import java.sql.SQLException;
import java.util.List;

public class ProductoService {

    private final ProductoDAO dao = new ProductoDAO();

    // Texto vacío = todos los productos; si no, filtra por nombre o código.
    public List<Producto> buscar(String texto) throws SQLException {
        if (texto == null || texto.isBlank()) {
            return dao.listar();
        }
        return dao.buscar(texto.trim());
    }

    // Para el buscador de la pantalla de ventas (suena en cada letra que se escribe): si la copia local es reciente se busca
    // en memoria, al instante, sin viajar a internet. La venta en sí siempre confirma con la base de datos.
    public List<Producto> buscarRapido(String texto) throws SQLException {
        if (texto != null && !texto.isBlank() && com.pos.config.Conectividad.enLinea() && com.pos.offline.CacheLocal.fresco(45_000)) {
            return com.pos.offline.CacheLocal.buscarProductos(texto.trim(), 50);
        }
        return buscar(texto);
    }

    // Crea o actualiza un producto, validando antes los datos.
    public void guardar(Producto p) throws SQLException {
        if (p.getCodigo() == null || p.getCodigo().isBlank()) {
            throw new IllegalArgumentException("El código es obligatorio.");
        }
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (p.getPrecioVenta() == null || p.getPrecioVenta().signum() <= 0) {
            throw new IllegalArgumentException("El precio de venta debe ser mayor a cero.");
        }
        if (p.getPrecioCompra() == null || p.getPrecioCompra().signum() < 0) {
            throw new IllegalArgumentException("El precio de compra no puede ser negativo.");
        }
        if (p.getStock() < 0 || p.getStockMinimo() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        p.setCodigo(p.getCodigo().trim());
        p.setNombre(p.getNombre().trim());

        // El código no se puede repetir. Se permite si es el mismo producto que estamos editando.
        Producto existente = dao.buscarPorCodigo(p.getCodigo());
        if (existente != null && existente.getId() != p.getId()) {
            throw new IllegalArgumentException("Ya existe un producto con el código " + p.getCodigo() + ".");
        }

        try {
            if (p.getId() == 0) {
                dao.insertar(p);
            } else {
                dao.actualizar(p);
            }
            com.pos.offline.Sincronizador.pedirRefresco();
        } catch (SQLException e) {
            // Un producto desactivado sigue ocupando su código en la base de datos.
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Ese código ya está en uso (puede ser de un producto desactivado).");
            }
            throw e;
        }
    }

    public void desactivar(Producto p) throws SQLException {
        dao.desactivar(p.getId());
    }
}
