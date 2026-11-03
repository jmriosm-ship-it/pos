package com.pos.dao;

import com.pos.config.Conexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tabla "configuracion": ajustes tipo clave/valor (nombre del negocio, impresora, etc.). */
public class ConfiguracionDAO {

    // Todos los ajustes de una vez.
    public Map<String, String> todos() throws SQLException {
        Map<String, String> mapa = new LinkedHashMap<>();

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT clave, valor FROM configuracion ORDER BY clave");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getString(1), rs.getString(2));
            }
        }
        return mapa;
    }

    // Guarda un ajuste: si la clave ya existe lo actualiza, y si no, lo crea.
    // "ON DUPLICATE KEY UPDATE" hace ese "crear o actualizar" en una sola instrucción.
    public void guardar(String clave, String valor) throws SQLException {
        String sql = "INSERT INTO configuracion (clave, valor) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE valor = VALUES(valor)";

        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            ps.executeUpdate();
        }
    }
}
