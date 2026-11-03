package com.pos.servicio;

import com.pos.dao.CategoriaDAO;
import com.pos.dao.ProductoDAO;
import com.pos.modelo.Categoria;
import com.pos.modelo.Producto;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoriaService {

    private final CategoriaDAO dao = new CategoriaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    // "Sin categoría" no existe en la base de datos: es una categoría de mentira (id 0)
    // para poder mostrar en la pantalla los productos que todavía no tienen ninguna.
    public static Categoria sinCategoria() {
        return new Categoria("Sin categoría");   // id 0
    }

    // Las categorías reales con su número de productos.
    public List<Categoria> listar() throws SQLException {
        return dao.listarConConteo();
    }

    // Igual que listar(), pero si hay productos sin categoría agrega al final "Sin categoría".
    public List<Categoria> listarConSinCategoria() throws SQLException {
        List<Categoria> lista = new ArrayList<>(dao.listarConConteo());
        List<Producto> sueltos = productoDAO.listarSinCategoria();
        if (!sueltos.isEmpty()) {
            Categoria sin = sinCategoria();
            sin.setTotalProductos(sueltos.size());
            lista.add(sin);
        }
        return lista;
    }

    // Los productos de una categoría (o los sueltos si es "Sin categoría").
    public List<Producto> productosDe(Categoria c) throws SQLException {
        return c.getId() == 0 ? productoDAO.listarSinCategoria() : productoDAO.listarPorCategoria(c.getId());
    }

    // Pasa un producto a otra categoría. Si el destino es "Sin categoría", se la quita.
    public void moverProducto(Producto p, Categoria destino) throws SQLException {
        productoDAO.cambiarCategoria(p.getId(), destino.getId() == 0 ? null : destino.getId());
    }

    // Crea o renombra una categoría, validando el nombre.
    public void guardar(Categoria c) throws SQLException {
        if (c.getNombre() == null || c.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio.");
        }
        c.setNombre(c.getNombre().trim());
        if (c.getNombre().equalsIgnoreCase("Sin categoría")) {
            throw new IllegalArgumentException("Ese nombre está reservado; elige otro.");
        }

        try {
            // id == 0 significa que es nueva (todavía no tiene id de la base de datos).
            if (c.getId() == 0) {
                dao.insertar(c);
            } else {
                dao.actualizar(c);
            }
        } catch (SQLException e) {
            // El nombre es UNIQUE en la base: si ya existe, MySQL avisa con "Duplicate entry".
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw new IllegalArgumentException("Ya existe una categoría llamada \"" + c.getNombre() + "\".");
            }
            throw e;
        }
    }

    // Elimina una categoría solo si ningún producto la usa.
    public void eliminar(Categoria c) throws SQLException {
        int usados = dao.contarProductos(c.getId());
        if (usados > 0) {
            throw new IllegalStateException("\"" + c.getNombre() + "\" tiene " + usados
                    + (usados == 1 ? " producto" : " productos")
                    + ". Muévelos a otra categoría antes de eliminarla.");
        }
        dao.eliminar(c.getId());
    }
}
