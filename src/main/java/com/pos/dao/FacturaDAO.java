package com.pos.dao;

import com.pos.config.Conexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Fotos de facturas (de compras o ventas) guardadas en el computador; la papelera las oculta sin borrarlas. */
public class FacturaDAO {

    public record Fila(int id, String cliente, String nota, String archivo, String subidaPor, LocalDateTime fecha) {
    }

    public void insertar(String cliente, String nota, String archivo, int idUsuario) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO facturas (cliente, nota, archivo, id_usuario) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, cliente);
            ps.setString(2, nota);
            ps.setString(3, archivo);
            ps.setInt(4, idUsuario);
            ps.executeUpdate();
        }
    }

    public List<Fila> listar(boolean papelera, String texto) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        List<Fila> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT f.*, u.nombre FROM facturas f JOIN usuarios u ON u.id = f.id_usuario "
                     + "WHERE f.eliminada = ? AND (f.cliente LIKE ? OR f.nota LIKE ? OR u.nombre LIKE ?) ORDER BY f.id DESC")) {
            ps.setBoolean(1, papelera);
            ps.setString(2, patron);
            ps.setString(3, patron);
            ps.setString(4, patron);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Fila(rs.getInt("id"), rs.getString("cliente"), rs.getString("nota"), rs.getString("archivo"),
                            rs.getString("nombre"), rs.getTimestamp("fecha").toLocalDateTime()));
                }
            }
        }
        return lista;
    }

    public void marcarEliminada(int id, boolean eliminada) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE facturas SET eliminada = ?, fecha_eliminada = "
                     + "IF(?, NOW(), NULL) WHERE id = ?")) {
            ps.setBoolean(1, eliminada);
            ps.setBoolean(2, eliminada);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }
}
