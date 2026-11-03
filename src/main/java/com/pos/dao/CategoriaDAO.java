package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Categoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CategoriaDAO {

    // Devuelve todas las categorías ordenadas por nombre.
    public List<Categoria> listar() throws SQLException {
        String sql = "SELECT * FROM categorias ORDER BY nombre";
        List<Categoria> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Categoria c = new Categoria();
                c.setId(rs.getInt("id"));
                c.setNombre(rs.getString("nombre"));
                lista.add(c);
            }
        }
        return lista;
    }

    // Las categorías con cuántos productos ACTIVOS tiene cada una (para la pantalla).
    // LEFT JOIN: las categorías sin productos también salen, con 0.
    public List<Categoria> listarConConteo() throws SQLException {
        String sql = "SELECT c.id, c.nombre, COUNT(p.id) AS total FROM categorias c "
                + "LEFT JOIN productos p ON p.id_categoria = c.id AND p.activo = TRUE "
                + "GROUP BY c.id, c.nombre ORDER BY c.nombre";
        List<Categoria> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Categoria c = new Categoria();
                c.setId(rs.getInt("id"));
                c.setNombre(rs.getString("nombre"));
                c.setTotalProductos(rs.getInt("total"));
                lista.add(c);
            }
        }
        return lista;
    }

    // Cuántos productos (activos o no) usan esta categoría. Si es más de 0 no se puede eliminar.
    public int contarProductos(int idCategoria) throws SQLException {
        String sql = "SELECT COUNT(*) FROM productos WHERE id_categoria = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCategoria);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Cambia el nombre de una categoría existente.
    public void actualizar(Categoria c) throws SQLException {
        String sql = "UPDATE categorias SET nombre = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.getNombre());
            ps.setInt(2, c.getId());
            ps.executeUpdate();
        }
    }

    // Inserta una categoría nueva y le asigna el id que generó MySQL.
    public void insertar(Categoria c) throws SQLException {
        String sql = "INSERT INTO categorias (nombre) VALUES (?)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, c.getNombre());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getInt(1));
                }
            }
        }
    }

    // Elimina una categoría. Si algún producto la usa, MySQL lo rechaza
    // (por la llave foránea) y lanza SQLException.
    public void eliminar(int id) throws SQLException {
        String sql = "DELETE FROM categorias WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}