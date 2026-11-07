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
                + "(codigo, nombre, id_categoria, id_proveedor, precio_compra, precio_venta, stock, stock_minimo, "
                + "marca, condicion, garantia_dias, por_imei, capacidad, color) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            extras(ps, p, 9);
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
                + "precio_compra = ?, precio_venta = ?, stock_minimo = ?, marca = ?, condicion = ?, garantia_dias = ?, "
                + "por_imei = ?, capacidad = ?, color = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            ps.setObject(3, p.getIdCategoria());
            ps.setObject(4, p.getIdProveedor());
            ps.setBigDecimal(5, p.getPrecioCompra());
            ps.setBigDecimal(6, p.getPrecioVenta());
            ps.setInt(7, p.getStockMinimo());
            extras(ps, p, 8);
            ps.setInt(14, p.getId());
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

    // El producto al que pertenece un IMEI/serial exacto (null si no existe).
    public Producto buscarPorImei(String imei) throws SQLException {
        return consultaUno("SELECT p.* FROM productos p JOIN producto_imeis i ON i.id_producto = p.id WHERE i.imei = ? AND p.activo = TRUE", imei);
    }

    private Producto consultaUno(String sql, String parametro) throws SQLException {
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
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
        String sql = "SELECT * FROM productos WHERE activo = TRUE AND (nombre LIKE ? OR codigo LIKE ? OR marca LIKE ? OR id IN (SELECT id_producto FROM producto_imeis WHERE imei LIKE ?)) "
                + "ORDER BY nombre LIMIT 50";
        List<Producto> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String patron = "%" + texto + "%";
            ps.setString(1, patron);
            ps.setString(2, patron);
            ps.setString(3, patron);
            ps.setString(4, patron);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    // Los 6 datos nuevos (marca, condición, garantía, por IMEI, talla/capacidad, color) desde el parámetro "desde".
    private void extras(PreparedStatement ps, Producto p, int desde) throws SQLException {
        ps.setString(desde, vacioANulo(p.getMarca()));
        ps.setString(desde + 1, p.getCondicion() == null ? "Nuevo" : p.getCondicion());
        ps.setInt(desde + 2, p.getGarantiaDias());
        ps.setBoolean(desde + 3, p.isPorImei());
        ps.setString(desde + 4, vacioANulo(p.getCapacidad()));
        ps.setString(desde + 5, vacioANulo(p.getColor()));
    }

    private String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
        p.setMarca(rs.getString("marca"));
        p.setCondicion(rs.getString("condicion"));
        p.setGarantiaDias(rs.getInt("garantia_dias"));
        p.setPorImei(rs.getBoolean("por_imei"));
        p.setCapacidad(rs.getString("capacidad"));
        p.setColor(rs.getString("color"));
        return p;
    }
}

