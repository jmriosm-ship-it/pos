package com.pos.servicio;

import com.pos.dao.ConfiguracionDAO;
import com.pos.modelo.Usuario;

import java.sql.SQLException;
import java.util.Map;

/**
 * Los ajustes del negocio. Se leen una vez y se guardan en memoria (caché), porque el recibo
 * y las pantallas los piden muchas veces y no vale la pena ir a MySQL cada vez.
 */
public class ConfiguracionService {

    // Claves usadas en el sistema (las mismas que insertó schema.sql).
    public static final String NEGOCIO_NOMBRE = "negocio_nombre";
    public static final String NEGOCIO_NIT = "negocio_nit";
    public static final String NEGOCIO_TELEFONO = "negocio_telefono";
    public static final String IMPRESORA_NOMBRE = "impresora_nombre";
    public static final String IMPRESORA_ANCHO_MM = "impresora_ancho_mm";

    private final ConfiguracionDAO dao = new ConfiguracionDAO();
    private Map<String, String> cache;   // null hasta la primera lectura

    // Devuelve un ajuste, o el valor por defecto si no existe o está vacío.
    public String obtener(String clave, String porDefecto) throws SQLException {
        if (cache == null) {
            cache = dao.todos();
        }
        String valor = cache.get(clave);
        return (valor == null || valor.isBlank()) ? porDefecto : valor;
    }

    // Guarda un ajuste. Solo un ADMIN puede cambiar la configuración.
    public void guardar(Usuario usuario, String clave, String valor) throws SQLException {
        if (!usuario.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede cambiar la configuración.");
        }
        dao.guardar(clave, valor == null ? "" : valor.trim());
        cache = null;   // se vuelve a leer la próxima vez, para ver el cambio
    }

    public String nombreNegocio() throws SQLException {
        return obtener(NEGOCIO_NOMBRE, "Mi Negocio");
    }
}
