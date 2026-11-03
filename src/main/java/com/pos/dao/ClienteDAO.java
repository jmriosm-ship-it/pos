package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Cliente;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAO {

    // Todos los clientes, ordenados por nombre.
    public List<Cliente> listar() throws SQLException {
        return consultar("SELECT * FROM clientes ORDER BY nombre", null);
    }

    // Busca por parte del nombre, del documento o del teléfono (para el buscador de la caja).
    public List<Cliente> buscar(String texto) throws SQLException {
        String sql = "SELECT * FROM clientes WHERE nombre LIKE ? OR documento LIKE ? OR telefono LIKE ? "
                + "ORDER BY nombre LIMIT 50";
        return consultar(sql, "%" + texto + "%");
    }

    // Los clientes con lo que han comprado en total y lo que deben por fiados (para la lista y la ficha).
    public List<Cliente> listarConResumen() throws SQLException {
        String sql = "SELECT c.*, "
                + "COALESCE((SELECT SUM(v.total) FROM ventas v WHERE v.id_cliente = c.id AND v.estado = 'COMPLETADA'), 0) AS comprado, "
                + "COALESCE((SELECT SUM(f.saldo) FROM fiados f WHERE f.id_cliente = c.id AND f.estado = 'PENDIENTE'), 0) AS debe "
                + "FROM clientes c ORDER BY c.nombre";
        List<Cliente> lista = new ArrayList<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Cliente c = new Cliente();
                c.setId(rs.getInt("id"));
                c.setNombre(rs.getString("nombre"));
                c.setDocumento(rs.getString("documento"));
                c.setTelefono(rs.getString("telefono"));
                c.setCorreo(rs.getString("correo"));
                c.setDireccion(rs.getString("direccion"));
                c.setCreadoEn(rs.getTimestamp("creado_en").toLocalDateTime());
                c.setTotalComprado(rs.getBigDecimal("comprado"));
                c.setSaldoPendiente(rs.getBigDecimal("debe"));
                lista.add(c);
            }
        }
        return lista;
    }

    // Inserta un cliente nuevo y le asigna el id generado.
    public void insertar(Cliente c) throws SQLException {
        String sql = "INSERT INTO clientes (nombre, documento, telefono, correo, direccion) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, vacioANull(c.getDocumento()));   // el documento es UNIQUE: "" repetido chocaría
            ps.setString(3, c.getTelefono());
            ps.setString(4, c.getCorreo());
            ps.setString(5, c.getDireccion());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getInt(1));
                }
            }
        }
    }

    public void actualizar(Cliente c) throws SQLException {
        String sql = "UPDATE clientes SET nombre = ?, documento = ?, telefono = ?, correo = ?, direccion = ? WHERE id = ?";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, vacioANull(c.getDocumento()));
            ps.setString(3, c.getTelefono());
            ps.setString(4, c.getCorreo());
            ps.setString(5, c.getDireccion());
            ps.setInt(6, c.getId());
            ps.executeUpdate();
        }
    }

    // Suma lo que el cliente ha comprado (sin contar ventas anuladas).
    public BigDecimal totalComprado(int idCliente) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM ventas WHERE id_cliente = ? AND estado = 'COMPLETADA'";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    // Un cliente con ventas NO se puede borrar (la llave foránea lo impide y el historial se perdería).
    public void eliminar(int id) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clientes WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Texto vacío -> null, para que MySQL guarde "sin documento" en vez de un "" repetido.
    private String vacioANull(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }

    // Si patron != null, se usa 3 veces (nombre, documento, teléfono).
    private List<Cliente> consultar(String sql, String patron) throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (patron != null) {
                ps.setString(1, patron);
                ps.setString(2, patron);
                ps.setString(3, patron);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cliente c = new Cliente();
                    c.setId(rs.getInt("id"));
                    c.setNombre(rs.getString("nombre"));
                    c.setDocumento(rs.getString("documento"));
                    c.setTelefono(rs.getString("telefono"));
                    c.setCorreo(rs.getString("correo"));
                    c.setDireccion(rs.getString("direccion"));
                    c.setCreadoEn(rs.getTimestamp("creado_en").toLocalDateTime());
                    lista.add(c);
                }
            }
        }
        return lista;
    }
}
