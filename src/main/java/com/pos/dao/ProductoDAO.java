package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Producto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAO {
    //devuelve todos los productos activos, ordenados por nombre.

    public List<Producto> listar() throws SQLException{
        String sql= "SELECT * FROM productos WHERE activo = TRUE ORDER BY nombre";
        List<Producto> Lista = new ArrayList<>();

        //try- with :cierra conexion, consulta y resulta automatico

        try(Connection con= Conexion.obtener();
           PreparedStatement ps= con.prepareStatement(sql);
           ResultSet rs= ps.executeQuery()){

            //avanzara fila por fila; devuelve el false cuando ya no hay mas

            while (rs.next()){
                Lista.add(mapear(rs));
            }
        }

        return Lista;

    }

    // Busca un producto por su código de barras. Devuelve null si no existe.
    public Producto buscarPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT * FROM productos WHERE codigo = ? AND activo = TRUE";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            // El "?" se reemplaza por el valor. Así se evita la inyección SQL,
            // que es cuando alguien escribe SQL malicioso en un campo de texto.
            ps.setString(1, codigo);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
        }
        return null;
    }

    // Inserta un producto nuevo y le asigna al objeto el id que generó MySQL.
    public void insertar(Producto p) throws SQLException {
        String sql = "INSERT INTO productos "
                + "(codigo, nombre, id_categoria, id_proveedor, precio_compra, precio_venta, stock, stock_minimo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        // RETURN_GENERATED_KEYS: pide a MySQL que nos devuelva el id nuevo.
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            ps.setObject(3, p.getIdCategoria());   // setObject permite enviar null
            ps.setObject(4, p.getIdProveedor());
            ps.setBigDecimal(5, p.getPrecioCompra());
            ps.setBigDecimal(6, p.getPrecioVenta());
            ps.setInt(7, p.getStock());
            ps.setInt(8, p.getStockMinimo());
            ps.executeUpdate();   // executeUpdate = INSERT/UPDATE/DELETE (no devuelve filas)

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setId(keys.getInt(1));
                }
            }
        }
    }

    // Actualiza los datos de un producto existente (se identifica por su id).
    public void actualizar(Producto p) throws SQLException {
        String sql = "UPDATE productos SET codigo = ?, nombre = ?, id_categoria = ?, id_proveedor = ?, "
                + "precio_compra = ?, precio_venta = ?, stock_minimo = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            ps.setObject(3, p.getIdCategoria());
            ps.setObject(4, p.getIdProveedor());
            ps.setBigDecimal(5, p.getPrecioCompra());
            ps.setBigDecimal(6, p.getPrecioVenta());
            ps.setInt(7, p.getStockMinimo());
            ps.setInt(8, p.getId());
            ps.executeUpdate();
        }
    }

    // No borramos el producto: lo desactivamos. Así las ventas viejas que lo
    // usaron siguen teniendo sentido (no se rompe el historial).
    public void desactivar(int id) throws SQLException {
        String sql = "UPDATE productos SET activo = FALSE WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Productos activos de una categoría.
    public List<Producto> listarPorCategoria(int idCategoria) throws SQLException {
        return consultarLista("SELECT * FROM productos WHERE activo = TRUE AND id_categoria = ? ORDER BY nombre", idCategoria);
    }

    // Productos activos que no tienen categoría asignada.
    public List<Producto> listarSinCategoria() throws SQLException {
        return consultarLista("SELECT * FROM productos WHERE activo = TRUE AND id_categoria IS NULL ORDER BY nombre", null);
    }

    // Productos activos que nos surte un proveedor.
    public List<Producto> listarPorProveedor(int idProveedor) throws SQLException {
        return consultarLista("SELECT * FROM productos WHERE activo = TRUE AND id_proveedor = ? ORDER BY nombre", idProveedor);
    }

    // Cambia la categoría de un producto. idCategoria = null lo deja "sin categoría".
    public void cambiarCategoria(int idProducto, Integer idCategoria) throws SQLException {
        String sql = "UPDATE productos SET id_categoria = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, idCategoria);   // setObject permite enviar null
            ps.setInt(2, idProducto);
            ps.executeUpdate();
        }
    }

    // Ejecuta un SELECT de productos con, como máximo, un parámetro numérico.
    private List<Producto> consultarLista(String sql, Integer parametro) throws SQLException {
        List<Producto> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (parametro != null) {
                ps.setInt(1, parametro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    // Busca un producto por su id. Devuelve null si no existe.
    public Producto buscarPorId(int id) throws SQLException {
        String sql = "SELECT * FROM productos WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
        }
        return null;
    }

    // Productos activos cuyo stock ya llegó al mínimo (o está por debajo): alerta de reposición.
    public List<Producto> listarStockBajo() throws SQLException {
        String sql = "SELECT * FROM productos WHERE activo = TRUE AND stock <= stock_minimo ORDER BY stock, nombre";
        List<Producto> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    // Busca productos activos por parte del nombre o del código (para el buscador del POS).
    // LIKE '%texto%' = "que contenga el texto en cualquier parte".
    public List<Producto> buscar(String texto) throws SQLException {
        String sql = "SELECT * FROM productos WHERE activo = TRUE AND (nombre LIKE ? OR codigo LIKE ?) "
                + "ORDER BY nombre LIMIT 50";
        List<Producto> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String patron = "%" + texto + "%";
            ps.setString(1, patron);
            ps.setString(2, patron);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    // Convierte UNA fila del resultado en un objeto Producto.
    // Lo usan listar() y buscarPorCodigo() para no repetir código.
    private Producto mapear(ResultSet rs) throws SQLException {
        Producto p = new Producto();
        p.setId(rs.getInt("id"));
        p.setCodigo(rs.getString("codigo"));
        p.setNombre(rs.getString("nombre"));
        // getObject para respetar los null (getInt devolvería 0 si es null).
        p.setIdCategoria((Integer) rs.getObject("id_categoria"));
        p.setIdProveedor((Integer) rs.getObject("id_proveedor"));
        p.setPrecioCompra(rs.getBigDecimal("precio_compra"));
        p.setPrecioVenta(rs.getBigDecimal("precio_venta"));
        p.setStock(rs.getInt("stock"));
        p.setStockMinimo(rs.getInt("stock_minimo"));
        p.setActivo(rs.getBoolean("activo"));
        return p;
    }
}

