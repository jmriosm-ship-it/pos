package com.pos.offline;

import com.pos.modelo.Venta;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Las ventas hechas sin conexión, guardadas en este PC (una carpeta con un archivo por venta) hasta que se puedan
 * subir. Cada venta se escribe completa en un archivo temporal y luego se renombra: si se va la luz justo al
 * guardar, nunca queda una venta a medias.
 */
public final class ColaVentas {

    private static final Path CARPETA = Path.of(System.getProperty("user.home"), ".sistema-sas", "pendientes");
    private static final Path ERRORES = CARPETA.resolve("con_error");
    private static final Path CONTADOR = CARPETA.resolve("contador.txt");

    private ColaVentas() {
    }

    /** Guarda la venta en la cola y le pone su número provisional (ej: C1-OFF003). */
    public static synchronized void guardar(Venta v) throws IOException {
        Files.createDirectories(CARPETA);
        int numero = siguienteNumero();
        v.setOrigenOffline(UUID.randomUUID().toString());
        v.setEtiqueta(v.getPrefijoCaja() + "-OFF" + String.format("%03d", numero));
        v.setPendienteDeSubir(true);
        String nombre = String.format("venta_%013d_%04d.ser", System.currentTimeMillis(), numero);
        Path temporal = CARPETA.resolve(nombre + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temporal))) {
            out.writeObject(v);
        }
        Files.move(temporal, CARPETA.resolve(nombre), StandardCopyOption.ATOMIC_MOVE);
    }

    private static int siguienteNumero() throws IOException {
        int n = 0;
        if (Files.isRegularFile(CONTADOR)) {
            try {
                n = Integer.parseInt(Files.readString(CONTADOR, StandardCharsets.UTF_8).trim());
            } catch (NumberFormatException e) {
                n = 0;
            }
        }
        n++;
        Files.writeString(CONTADOR, String.valueOf(n), StandardCharsets.UTF_8);
        return n;
    }

    /** Los archivos de las ventas pendientes, de la más antigua a la más nueva. */
    public static synchronized List<Path> archivos() {
        if (!Files.isDirectory(CARPETA)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(CARPETA)) {
            return s.filter(p -> p.getFileName().toString().startsWith("venta_") && p.getFileName().toString().endsWith(".ser"))
                    .sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static int cantidad() {
        return archivos().size();
    }

    public static synchronized int cantidadConError() {
        if (!Files.isDirectory(ERRORES)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(ERRORES)) {
            return (int) s.filter(p -> p.getFileName().toString().endsWith(".ser")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public static Venta leer(Path archivo) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(archivo))) {
            return (Venta) in.readObject();
        }
    }

    /** Todas las ventas pendientes ya leídas (las que no se puedan leer se saltan). */
    public static List<Venta> leerTodas() {
        List<Venta> lista = new ArrayList<>();
        for (Path p : archivos()) {
            try {
                lista.add(leer(p));
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                // un archivo dañado no impide ver los demás
            }
        }
        return lista;
    }

    public static synchronized void eliminar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            // si no se puede borrar, la próxima vez la base de datos dirá que ya estaba subida y se borrará
        }
    }

    /** Una venta que la base rechazó: se aparta en la carpeta con_error (junto a su motivo) para revisarla, sin perderla. */
    public static synchronized void apartarConError(Path archivo, String motivo) {
        try {
            Files.createDirectories(ERRORES);
            Files.writeString(ERRORES.resolve(archivo.getFileName() + ".txt"), motivo, StandardCharsets.UTF_8);
            Files.move(archivo, ERRORES.resolve(archivo.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // queda donde estaba
        }
    }
}
