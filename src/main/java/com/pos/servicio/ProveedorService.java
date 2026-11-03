package com.pos.servicio;

import com.pos.dao.CompraDAO;
import com.pos.dao.ProductoDAO;
import com.pos.dao.ProveedorDAO;
import com.pos.modelo.Compra;
import com.pos.modelo.Producto;
import com.pos.modelo.Proveedor;

import java.sql.SQLException;
import java.util.List;

public class ProveedorService {

    private final ProveedorDAO dao = new ProveedorDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CompraDAO compraDAO = new CompraDAO();

    // Los proveedores activos, con total comprado y última compra.
    public List<Proveedor> listar() throws SQLException {
        return dao.listarConResumen();
    }

    // Crea o actualiza un proveedor, validando los datos.
    public void guardar(Proveedor p) throws SQLException {
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio.");
        }
        p.setNombre(p.getNombre().trim());
        p.setTelefono(limpiar(p.getTelefono()));
        p.setCorreo(limpiar(p.getCorreo()));
        p.setDireccion(limpiar(p.getDireccion()));

        // Revisión sencilla del correo: debe tener @ y un punto después.
        String correo = p.getCorreo();
        if (correo != null && (!correo.contains("@") || correo.indexOf('.', correo.indexOf('@')) < 0)) {
            throw new IllegalArgumentException("El correo no parece válido (ej: ventas@empresa.com).");
        }

        // id == 0 significa que es nuevo (todavía no tiene id de la base de datos).
        if (p.getId() == 0) {
            dao.insertar(p);
        } else {
            dao.actualizar(p);
        }
    }

    // Lo desactiva: ya no aparece al comprar, pero su historial se conserva.
    public void desactivar(Proveedor p) throws SQLException {
        dao.desactivar(p.getId());
    }

    // Los productos que este proveedor nos surte.
    public List<Producto> productosDe(Proveedor p) throws SQLException {
        return productoDAO.listarPorProveedor(p.getId());
    }

    // Su historial de compras (la más reciente primero).
    public List<Compra> comprasDe(Proveedor p) throws SQLException {
        return compraDAO.listarPorProveedor(p.getId());
    }

    // Texto vacío -> null, para no guardar cadenas en blanco en la base de datos.
    private String limpiar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
