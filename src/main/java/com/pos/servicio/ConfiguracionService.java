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
    public static final String PUNTOS_POR_MIL = "puntos_por_mil";   // puntos que gana el cliente por cada $1.000 (0 = sin puntos)
    public static final String LECTOR_SONIDO = "lector_sonido";      // "true" = pitar al escanear con la pistola
    public static final String CAJA_NOMBRE = "caja_nombre";          // ej: "Caja 1"; sus iniciales forman el número de recibo (C1-00005)
    // Crédito en cuotas (se cambian en Configuración > Crédito)
    public static final String CREDITO_TASAS = "credito_tasas";                       // tasas mensuales ofrecidas, ej: 2.3, 2.5
    public static final String CREDITO_PLAZOS = "credito_plazos";                     // cuotas mensuales, ej: 6,12,18,24,36
    public static final String CREDITO_PLAZOS_QUINCENAL = "credito_plazos_quincenal"; // cuotas quincenales, ej: 24,36,48
    public static final String CREDITO_INICIAL_MIN = "credito_inicial_min";           // % mínimo de cuota inicial
    public static final String CREDITO_TASA_MAXIMA = "credito_tasa_maxima";           // tope por usura (vacío = sin tope)
    public static final String WA_DIAS_ANTES = "wa_dias_antes";                       // cuántos días antes se avisa la cuota
    public static final String WA_PLANTILLA = "wa_plantilla";
    public static final String WA_PLANTILLA_MORA = "wa_plantilla_mora";
    public static final String VALOR_PUNTO = "valor_punto";          // cuántos pesos vale cada punto al usarlo

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
