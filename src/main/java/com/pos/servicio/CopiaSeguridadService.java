package com.pos.servicio;

import com.pos.config.Conexion;
import com.pos.modelo.Usuario;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copias de seguridad de la base de datos.
 * Usa los programas que trae MySQL: "mysqldump" (exporta todo a un archivo .sql)
 * y "mysql" (lo vuelve a cargar). Se ejecutan como procesos externos con ProcessBuilder.
 *
 * Truco para la nube sin pagar servidor: la carpeta de destino puede ser una carpeta
 * de OneDrive o Google Drive, y la copia se sincroniza sola.
 */
public class CopiaSeguridadService {

    // Carpeta donde MySQL instaló mysqldump.exe y mysql.exe. Cámbiala si instalaste en otro lugar.
    private static final String CARPETA_MYSQL = "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin";

    // Crea una copia en la carpeta indicada y devuelve el archivo creado.
    // El nombre lleva la fecha y hora: pos2_20261031_235900.sql
    public Path crearCopia(Usuario usuario, Path carpetaDestino) throws IOException, InterruptedException {
        exigirAdmin(usuario);
        Files.createDirectories(carpetaDestino);

        String nombre = "pos2_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".sql";
        Path archivo = carpetaDestino.resolve(nombre);

        ProcessBuilder pb = new ProcessBuilder(
                CARPETA_MYSQL + File.separator + "mysqldump.exe",
                "--user=" + Conexion.usuario(),
                "--single-transaction",       // copia consistente sin bloquear el sistema
                "--routines",
                "--result-file=" + archivo,
                Conexion.baseDatos());
        // La clave va por variable de entorno y no como argumento, para que no se vea en la lista de procesos.
        pb.environment().put("MYSQL_PWD", Conexion.clave());
        ejecutar(pb, "No se pudo crear la copia de seguridad");
        return archivo;
    }

    // Restaura una copia: BORRA los datos actuales y deja los del archivo.
    // Es peligroso, por eso solo ADMIN y la pantalla debe pedir confirmación.
    public void restaurar(Usuario usuario, Path archivoSql) throws IOException, InterruptedException {
        exigirAdmin(usuario);
        if (!Files.isRegularFile(archivoSql)) {
            throw new IllegalArgumentException("El archivo de copia no existe.");
        }

        ProcessBuilder pb = new ProcessBuilder(
                CARPETA_MYSQL + File.separator + "mysql.exe",
                "--user=" + Conexion.usuario(),
                Conexion.baseDatos());
        pb.environment().put("MYSQL_PWD", Conexion.clave());
        pb.redirectInput(archivoSql.toFile());   // equivale a:  mysql pos2 < archivo.sql
        ejecutar(pb, "No se pudo restaurar la copia de seguridad");
    }

    // Lista las copias que hay en una carpeta, la más reciente primero.
    public List<Path> listarCopias(Path carpeta) throws IOException {
        if (!Files.isDirectory(carpeta)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos
                    .filter(p -> p.getFileName().toString().startsWith("pos2_")
                            && p.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .toList();
        }
    }

    // Ejecuta el proceso, espera a que termine y, si falla, lanza el mensaje de error de MySQL.
    private void ejecutar(ProcessBuilder pb, String mensajeError) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);   // junta los errores con la salida normal
        if (pb.redirectInput() == ProcessBuilder.Redirect.PIPE) {
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        Process proceso = pb.start();
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int codigo = proceso.waitFor();   // 0 = todo bien, cualquier otro número = falló

        if (codigo != 0) {
            throw new IOException(mensajeError + ": " + salida.trim());
        }
    }

    private void exigirAdmin(Usuario u) {
        if (!u.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede manejar copias de seguridad.");
        }
    }
}
