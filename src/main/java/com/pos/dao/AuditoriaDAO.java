package com.pos.dao;

import com.pos.config.Conexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** El registro de lo que se hace en el sistema: quién, cuándo y qué. */
public class AuditoriaDAO {

    public record Fila(LocalDateTime fecha, String usuario, String accion, String detalle) {
    }

    public void insertar(Integer idUsuario, String accion, String detalle) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO auditoria (id_usuario, accion, detalle) VALUES (?, ?, ?)")) {
            ps.setObject(1, idUsuario);
            ps.setString(2, accion);
            ps.setString(3, detalle.length() > 300 ? detalle.substring(0, 300) : detalle);
            ps.executeUpdate();
        }
    }

    // Lo más reciente primero; "texto" filtra por acción, detalle o usuario.
    public List<Fila> listar(String texto, int limite) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        List<Fila> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT a.fecha, COALESCE(u.nombre, 'Sistema') AS usuario, a.accion, a.detalle "
                     + "FROM auditoria a LEFT JOIN usuarios u ON u.id = a.id_usuario "
                     + "WHERE a.accion LIKE ? OR a.detalle LIKE ? OR u.nombre LIKE ? ORDER BY a.id DESC LIMIT ?")) {
            ps.setString(1, patron);
            ps.setString(2, patron);
            ps.setString(3, patron);
            ps.setInt(4, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Fila(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        }
        return lista;
    }
}
