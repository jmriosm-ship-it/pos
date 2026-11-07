package com.pos.dao;

import com.pos.config.Conexion;
import com.pos.modelo.Promocion;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PromocionDAO {

    // Todas las promociones, con el nombre de la categoría o producto al que aplican.
    public List<Promocion> listar() throws SQLException {
        String sql = "SELECT pr.*, COALESCE(c.nombre, p.nombre) AS objetivo FROM promociones pr "
                + "LEFT JOIN categorias c ON pr.alcance = 'CATEGORIA' AND c.id = pr.id_objetivo "
                + "LEFT JOIN productos p ON pr.alcance = 'PRODUCTO' AND p.id = pr.id_objetivo "
                + "ORDER BY pr.activa DESC, pr.nombre";
        List<Promocion> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Promocion p = new Promocion();
                p.setId(rs.getInt("id"));
                p.setNombre(rs.getString("nombre"));
                p.setAlcance(rs.getString("alcance"));
                p.setIdObjetivo((Integer) rs.getObject("id_objetivo"));
                p.setTipo(rs.getString("tipo"));
                p.setValor(rs.getBigDecimal("valor"));
                p.setLleva(rs.getInt("lleva"));
                p.setPaga(rs.getInt("paga"));
                Date d = rs.getDate("desde");
                Date h = rs.getDate("hasta");
                p.setDesde(d == null ? null : d.toLocalDate());
                p.setHasta(h == null ? null : h.toLocalDate());
                p.setActiva(rs.getBoolean("activa"));
                p.setNombreObjetivo(rs.getString("objetivo"));
                lista.add(p);
            }
        }
        return lista;
    }

    public void guardar(Promocion p) throws SQLException {
        String sql = p.getId() == 0
                ? "INSERT INTO promociones (nombre, alcance, id_objetivo, tipo, valor, lleva, paga, desde, hasta, activa) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE promociones SET nombre = ?, alcance = ?, id_objetivo = ?, tipo = ?, valor = ?, lleva = ?, "
                  + "paga = ?, desde = ?, hasta = ?, activa = ? WHERE id = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getAlcance());
            ps.setObject(3, p.getIdObjetivo());
            ps.setString(4, p.getTipo());
            ps.setBigDecimal(5, p.getValor());
            ps.setInt(6, p.getLleva());
            ps.setInt(7, p.getPaga());
            ps.setDate(8, p.getDesde() == null ? null : Date.valueOf(p.getDesde()));
            ps.setDate(9, p.getHasta() == null ? null : Date.valueOf(p.getHasta()));
            ps.setBoolean(10, p.isActiva());
            if (p.getId() != 0) {
                ps.setInt(11, p.getId());
            }
            ps.executeUpdate();
            if (p.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        p.setId(keys.getInt(1));
                    }
                }
            }
        }
    }

    public void eliminar(int id) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("DELETE FROM promociones WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
