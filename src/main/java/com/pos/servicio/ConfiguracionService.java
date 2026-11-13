package com.pos.servicio;

import com.pos.dao.ConfiguracionDAO;
import com.pos.modelo.Usuario;

import java.sql.SQLException;
import java.util.Map;

/**
 * Los ajustes del negocio. Se leen una vez y se guardan en memoria (caché), porque el recibo
 * y las pantallas los piden muchas veces y no vale la pena ir a la base de datos cada vez.
 */
public class ConfiguracionService {

    // Claves usadas en el sistema (las mismas que insertó schema.sql).
    public static final String NEGOCIO_NOMBRE = "negocio_nombre";
    public static final String NEGOCIO_NIT = "negocio_nit";
    public static final String NEGOCIO_TELEFONO = "negocio_telefono";
    public static final String NEGOCIO_TITULAR = "negocio_titular";      // dueño o razón social que sale en el recibo
    public static final String NEGOCIO_ACTIVIDAD = "negocio_actividad";  // ej: PERFUMERÍA (bajo el nombre en el recibo)
    public static final String NEGOCIO_CIUDAD = "negocio_ciudad";
    public static final String RECIBO_PIE = "recibo_pie";                 // mensaje al final del recibo
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
    // Una sola copia para todo el programa (antes cada pantalla pedía la suya a la base): se renueva cada 5 minutos o al guardar.
    private static Map<String, String> cache;
    private static long cargadoEn;

    // Ajustes que son de CADA computador y no de todo el negocio: su impresora, el ancho del papel, el nombre de su caja
    // (de ahí sale el prefijo del recibo, C1 o C2) y el sonido de la pistola. Cada local tiene su impresora y su caja;
    // por eso se guardan en este PC (~/.sistema-sas/equipo.properties) y no en la base de datos compartida.
    private static final java.util.Set<String> POR_EQUIPO = java.util.Set.of(IMPRESORA_NOMBRE, IMPRESORA_ANCHO_MM, CAJA_NOMBRE, LECTOR_SONIDO, "copias_carpeta", "copia_auto_ultima");
    private static final java.nio.file.Path ARCHIVO_EQUIPO =
            java.nio.file.Path.of(System.getProperty("user.home"), ".sistema-sas", "equipo.properties");
    private static final java.util.Properties EQUIPO = leerEquipo();

    private static java.util.Properties leerEquipo() {
        java.util.Properties p = new java.util.Properties();
        if (java.nio.file.Files.isRegularFile(ARCHIVO_EQUIPO)) {
            try (java.io.Reader in = java.nio.file.Files.newBufferedReader(ARCHIVO_EQUIPO, java.nio.charset.StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (java.io.IOException e) {
                // si no se puede leer, se usan los valores de la base de datos
            }
        }
        return p;
    }

    static synchronized void guardarEquipo(String clave, String valor) {
        EQUIPO.setProperty(clave, valor);
        try {
            java.nio.file.Files.createDirectories(ARCHIVO_EQUIPO.getParent());
            try (java.io.Writer out = java.nio.file.Files.newBufferedWriter(ARCHIVO_EQUIPO, java.nio.charset.StandardCharsets.UTF_8)) {
                EQUIPO.store(out, "Ajustes de este computador");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo guardar el ajuste de este computador: " + e.getMessage());
        }
    }

    // Devuelve un ajuste, o el valor por defecto si no existe o está vacío.
    public String obtener(String clave, String porDefecto) throws SQLException {
        if (POR_EQUIPO.contains(clave)) {
            String local = EQUIPO.getProperty(clave);
            if (local != null) {
                return local.isBlank() ? porDefecto : local;
            }
            // Si este PC aún no lo ha elegido, se parte de lo que había en la base de datos (si lo hay).
        }
        Map<String, String> actual;
        synchronized (ConfiguracionService.class) {
            if (cache == null || System.currentTimeMillis() - cargadoEn > 300_000) {
                cache = dao.todos();
                cargadoEn = System.currentTimeMillis();
            }
            actual = cache;
        }
        String valor = actual.get(clave);
        return (valor == null || valor.isBlank()) ? porDefecto : valor;
    }

    // Guarda un ajuste. Solo un ADMIN puede cambiar la configuración.
    public void guardar(Usuario usuario, String clave, String valor) throws SQLException {
        if (!usuario.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede cambiar la configuración.");
        }
        if (POR_EQUIPO.contains(clave)) {
            guardarEquipo(clave, valor == null ? "" : valor.trim());
            return;
        }
        dao.guardar(clave, valor == null ? "" : valor.trim());
        synchronized (ConfiguracionService.class) {
            cache = null;   // se vuelve a leer la próxima vez, para ver el cambio
        }
    }

    public String nombreNegocio() throws SQLException {
        return obtener(NEGOCIO_NOMBRE, "Mi Negocio");
    }
}
