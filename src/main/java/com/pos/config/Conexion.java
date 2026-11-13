package com.pos.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Conexion: único lugar del programa donde se abre la conexión a la base de datos (PostgreSQL / Supabase).
 * Todos los DAO (clases que leen/escriben datos) piden la conexión aquí.
 *
 * Las conexiones vienen de un "pool" (un grupo de conexiones ya abiertas que se reutilizan): abrir una conexión
 * a un servidor en internet tarda, y sin pool cada consulta la pagaría. Al cerrar una conexión (try-with-resources)
 * no se cierra de verdad: vuelve al pool.
 *
 * Los datos de la conexión no están en el código: se leen del archivo
 *     <carpeta del usuario>/.sistema-sas/conexion.properties
 * (o de un conexion.properties junto al programa). Ejemplo para Supabase (pestaña "Connect" del proyecto,
 * "Session pooler"; el usuario lleva el id del proyecto):
 *     host=aws-0-us-east-1.pooler.supabase.com
 *     puerto=5432
 *     base=postgres
 *     usuario=postgres.abcdefghijklmnop
 *     clave=LA_CLAVE_DE_TU_BASE
 *     ssl=true
 */
public class Conexion {

    public static final Path ARCHIVO = Path.of(System.getProperty("user.home"), ".sistema-sas", "conexion.properties");

    private static final Properties CONFIG = cargar();
    private static HikariDataSource pool;   // se crea la primera vez que se pide una conexión

    // Constructor privado: esta clase solo tiene métodos estáticos, no se instancia.
    private Conexion() {
    }

    private static Properties cargar() {
        Properties p = new Properties();
        // Valores de una instalación local, por si no hay archivo.
        p.setProperty("host", "localhost");
        p.setProperty("puerto", "5432");
        p.setProperty("base", "postgres");
        p.setProperty("usuario", "postgres");
        p.setProperty("clave", "postgres");
        p.setProperty("ssl", "false");
        p.setProperty("pool", "8");
        // -Dsas.conexion=ruta permite probar con otra base sin tocar el archivo normal.
        String otra = System.getProperty("sas.conexion");
        Path[] candidatos = otra != null
                ? new Path[]{Path.of(otra)}
                : new Path[]{ARCHIVO, Path.of("conexion.properties")};
        for (Path candidato : candidatos) {
            if (Files.isRegularFile(candidato)) {
                try (InputStream in = Files.newInputStream(candidato)) {
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // si no se puede leer, quedan los valores locales
                }
                break;
            }
        }
        return p;
    }

    private static String url() {
        boolean ssl = Boolean.parseBoolean(CONFIG.getProperty("ssl", "false").trim());
        StringBuilder u = new StringBuilder("jdbc:postgresql://").append(host()).append(":").append(puerto())
                .append("/").append(baseDatos()).append("?ApplicationName=SistemaSAS");
        if (ssl) {
            u.append("&sslmode=require");
        }
        if (System.getProperty("sas.esquema") != null) {   // solo para pruebas: trabajar en un esquema aparte
            u.append("&currentSchema=").append(System.getProperty("sas.esquema"));
        }
        // El "transaction pooler" de Supabase (puerto 6543) no admite sentencias preparadas en el servidor.
        if (puerto().equals("6543")) {
            u.append("&prepareThreshold=0");
        }
        return u.toString();
    }

    private static synchronized HikariDataSource pool() {
        if (pool == null) {
            HikariConfig c = new HikariConfig();
            c.setJdbcUrl(url());
            c.setUsername(usuario());
            c.setPassword(clave());
            c.setPoolName("SistemaSAS");
            // Hikari revisa con un viaje a la base que una conexión quieta sigue viva; con la base en internet eso duplica
            // cada consulta. El vigilante (keepalive) ya las mantiene vivas, así que solo se revisa si pasaron 30 segundos.
            System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "30000");
            c.setMaximumPoolSize(Integer.parseInt(CONFIG.getProperty("pool", "8").trim()));
            c.setMinimumIdle(4);   // varias conexiones ya abiertas: abrir una nueva (TLS + clave) tarda 1 a 2 segundos
            c.setConnectionTimeout(6_000);
            c.setKeepaliveTime(60_000);            // evita que el servidor cierre conexiones quietas
            c.setMaxLifetime(25 * 60_000L);
            c.setInitializationFailTimeout(-1);     // no fallar al crear el pool; el error sale al pedir la conexión
            c.setConnectionInitSql("SET TIME ZONE 'America/Bogota'");   // las horas guardadas son las de Colombia
            pool = new HikariDataSource(c);
        }
        return pool;
    }

    /** Devuelve una conexión del pool. Quien la pida debe cerrarla (try-with-resources): así vuelve al pool.
     *  Si no hay conexión con el servidor falla de inmediato con SinConexionException (no se queda esperando). */
    // Cuántas veces se pidió una conexión (sirve para medir cuántas consultas hace cada pantalla).
    private static final boolean TRAZA = Boolean.getBoolean("sas.traza");   // para investigar: imprime quién pide cada conexión
    public static final java.util.concurrent.atomic.AtomicInteger PRESTAMOS = new java.util.concurrent.atomic.AtomicInteger();

    public static Connection obtener() throws SQLException {
        PRESTAMOS.incrementAndGet();
        if (TRAZA) {
            StackWalker.getInstance().walk(s -> s.skip(1).filter(f -> f.getClassName().startsWith("com.pos.")).limit(3)
                    .map(f -> f.getClassName().replace("com.pos.", "") + "." + f.getMethodName()).reduce((x, y) -> x + " < " + y))
                    .ifPresent(System.out::println);
        }
        if (!Conectividad.enLinea()) {
            throw new SinConexionException("Sin conexión con el servidor.");
        }
        try {
            return pool().getConnection();
        } catch (SQLException e) {
            if (Conectividad.esCaida(e)) {
                Conectividad.marcarCaida();
                throw new SinConexionException("Sin conexión con el servidor.", e);
            }
            throw e;
        }
    }

    // Una conexión de prueba para el vigilante (no mira si ya se marcó como caída).
    static Connection probar() throws SQLException {
        return pool().getConnection();
    }

    // Datos de la conexión (para las copias de seguridad, que usan los programas de PostgreSQL aparte).
    public static String host() {
        return CONFIG.getProperty("host").trim();
    }

    public static String puerto() {
        return CONFIG.getProperty("puerto").trim();
    }

    public static String usuario() {
        return CONFIG.getProperty("usuario").trim();
    }

    public static String clave() {
        return CONFIG.getProperty("clave").trim();
    }

    public static String baseDatos() {
        return CONFIG.getProperty("base").trim();
    }
}
