package com.pos.servicio;

import com.pos.config.Conexion;
import com.pos.modelo.Usuario;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Copias de seguridad de la base de datos.
 * Se hacen desde el propio programa (sin instalar nada más): cada tabla se guarda en un archivo .sql como una
 * lista de INSERT, una línea por registro, y restaurar vuelve a cargar ese archivo.
 *
 * Truco para la nube sin pagar servidor: la carpeta de destino puede ser una carpeta de OneDrive o Google Drive,
 * y la copia se sincroniza sola. (Supabase además guarda sus propias copias automáticas.)
 */
public class CopiaSeguridadService {

    private static final String PREFIJO = "sas_";

    // Las tablas en orden: primero las que no dependen de otras (así los INSERT cumplen las llaves foráneas).
    private static final List<String> TABLAS = List.of("roles", "usuarios", "configuracion", "categorias", "proveedores",
            "clientes", "cajas", "productos", "ventas", "detalle_venta", "pagos_venta", "producto_imeis", "compras",
            "detalle_compra", "abonos_compra", "movimientos_inventario", "movimientos_caja", "fiados", "abonos_fiado", "gastos",
            "devoluciones", "devolucion_items", "creditos", "credito_cuotas", "credito_pagos", "credito_avisos",
            "cobros_financiera", "nomina", "facturas", "promociones", "auditoria", "permisos_rol", "permisos_accion",
            "usuario_modulos");

    // Las que no tienen columna "id" numérica automática (no hay contador que reajustar).
    private static final Set<String> SIN_ID = Set.of("configuracion", "credito_avisos", "permisos_rol", "permisos_accion",
            "usuario_modulos");

    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FECHA_SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Crea una copia en la carpeta indicada y devuelve el archivo creado.
    // El nombre lleva la fecha y hora: sas_20261031_235900.sql
    public Path crearCopia(Usuario usuario, Path carpetaDestino) throws IOException, InterruptedException {
        exigirAdmin(usuario);
        return crearCopiaInterna(carpetaDestino);
    }

    private Path crearCopiaInterna(Path carpetaDestino) throws IOException {
        Files.createDirectories(carpetaDestino);
        Path archivo = carpetaDestino.resolve(PREFIJO + LocalDateTime.now().format(FECHA_ARCHIVO) + ".sql");

        try (Connection con = Conexion.obtener();
             Statement st = con.createStatement();
             BufferedWriter out = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8)) {
            con.setAutoCommit(false);   // una sola "foto" consistente de todas las tablas
            st.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");
            out.write("-- Sistema SAS: copia de seguridad " + LocalDateTime.now() + "\n");
            for (String tabla : TABLAS) {
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + tabla + " ORDER BY 1")) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int n = meta.getColumnCount();
                    StringBuilder columnas = new StringBuilder();
                    for (int i = 1; i <= n; i++) {
                        columnas.append(i > 1 ? ", " : "").append(meta.getColumnName(i));
                    }
                    while (rs.next()) {
                        StringBuilder fila = new StringBuilder("INSERT INTO " + tabla + " (" + columnas + ") VALUES (");
                        for (int i = 1; i <= n; i++) {
                            fila.append(i > 1 ? ", " : "").append(literal(rs.getObject(i)));
                        }
                        out.write(fila.append(");\n").toString());
                    }
                }
            }
            con.commit();
        } catch (SQLException e) {
            try {
                Files.deleteIfExists(archivo);   // no dejar una copia a medias
            } catch (IOException ignorar) {
                // da igual
            }
            throw new IOException("No se pudo crear la copia de seguridad: " + e.getMessage(), e);
        }
        return archivo;
    }

    private static final int COPIAS_A_GUARDAR = 30;

    /**
     * La copia diaria automática: si hoy todavía no se ha hecho una en este computador, la hace (y borra las más viejas,
     * dejando las últimas 30). La carpeta es la misma de la pantalla Copias de seguridad. Devuelve el archivo, o null si no hacía falta.
     */
    public Path copiaAutomaticaSiHaceFalta() throws IOException, SQLException {
        ConfiguracionService cfg = new ConfiguracionService();
        String hoy = java.time.LocalDate.now().toString();
        if (hoy.equals(cfg.obtener("copia_auto_ultima", ""))) {
            return null;
        }
        Path carpeta = Path.of(cfg.obtener("copias_carpeta", Path.of(System.getProperty("user.home"), "Documents", "POS-copias").toString()));
        Path archivo = crearCopiaInterna(carpeta);
        ConfiguracionService.guardarEquipo("copia_auto_ultima", hoy);
        List<Path> copias = listarCopias(carpeta);
        for (int i = COPIAS_A_GUARDAR; i < copias.size(); i++) {
            Files.deleteIfExists(copias.get(i));   // las más viejas (la lista viene de la más reciente a la más antigua)
        }
        return archivo;
    }

    // Restaura una copia: BORRA los datos actuales y deja los del archivo.
    // Es peligroso, por eso solo ADMIN y la pantalla debe pedir confirmación.
    public void restaurar(Usuario usuario, Path archivoSql) throws IOException, InterruptedException {
        exigirAdmin(usuario);
        if (!Files.isRegularFile(archivoSql)) {
            throw new IllegalArgumentException("El archivo de copia no existe.");
        }
        List<String> lineas = Files.readAllLines(archivoSql, StandardCharsets.UTF_8);

        try (Connection con = Conexion.obtener(); Statement st = con.createStatement()) {
            con.setAutoCommit(false);   // si algo falla, no se pierde nada: se deshace todo
            try {
                st.execute("TRUNCATE TABLE " + String.join(", ", TABLAS) + " RESTART IDENTITY CASCADE");
                int pendientes = 0;
                for (String linea : lineas) {
                    if (linea.startsWith("INSERT INTO ")) {
                        st.addBatch(linea);
                        if (++pendientes % 500 == 0) {
                            st.executeBatch();
                        }
                    }
                }
                st.executeBatch();
                // Los contadores de id siguen donde quedó la copia.
                for (String tabla : TABLAS) {
                    if (!SIN_ID.contains(tabla)) {
                        st.execute("SELECT setval(pg_get_serial_sequence('" + tabla + "', 'id'), "
                                + "GREATEST((SELECT COALESCE(MAX(id), 0) FROM " + tabla + "), 1), "
                                + "(SELECT COUNT(*) > 0 FROM " + tabla + "))");
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("No se pudo restaurar la copia de seguridad: " + e.getMessage(), e);
        }
    }

    // Lista las copias que hay en una carpeta, la más reciente primero.
    public List<Path> listarCopias(Path carpeta) throws IOException {
        if (!Files.isDirectory(carpeta)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos
                    .filter(p -> p.getFileName().toString().startsWith(PREFIJO)
                            && p.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .toList();
        }
    }

    // Un valor de la base escrito como lo entiende PostgreSQL. Los textos van en una sola línea (\n escapado).
    private static String literal(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof Boolean || v instanceof Number || v instanceof BigDecimal) {
            return v.toString();
        }
        if (v instanceof Timestamp t) {
            return "'" + t.toLocalDateTime().format(FECHA_SQL) + "'";
        }
        if (v instanceof java.sql.Date || v instanceof java.time.temporal.Temporal) {
            return "'" + v + "'";
        }
        String s = v.toString().replace("\\", "\\\\").replace("'", "''").replace("\r", "\\r").replace("\n", "\\n");
        return "E'" + s + "'";
    }

    private void exigirAdmin(Usuario u) {
        if (!u.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede manejar copias de seguridad.");
        }
    }
}
