package com.pos.util;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Hace varias consultas a la base de datos al mismo tiempo. La base está en internet y cada consulta tarda un rato
 * en ir y volver; si se piden de una en una se suman todas las esperas, y en paralelo tarda lo que la más lenta.
 */
public final class Paralelo {

    // Para tareas que a su vez lanzan consultas en paralelo: con otro grupo de hilos, así no se esperan entre sí.
    private static final ExecutorService GRUPOS = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "consulta-grupo");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService HILOS = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "consulta-paralela");
        t.setDaemon(true);
        return t;
    });

    private Paralelo() {
    }

    public interface Tarea<T> {
        T ejecutar() throws Exception;
    }

    /** Empieza la consulta ya y devuelve un "ticket" para recoger el resultado después con esperar(). */
    public static <T> Future<T> lanzar(Tarea<T> tarea) {
        return HILOS.submit(tarea::ejecutar);
    }

    /** Igual que lanzar, pero para una tarea que ya usa Paralelo por dentro (por ejemplo un resumen de reportes). */
    public static <T> Future<T> lanzarGrupo(Tarea<T> tarea) {
        return GRUPOS.submit(tarea::ejecutar);
    }

    /** Espera el resultado; los errores de la base de datos llegan como SQLException, como si se hubiera llamado directo. */
    public static <T> T esperar(Future<T> futuro) throws SQLException {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("La consulta se interrumpió.", e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof SQLException s) {
                throw s;
            }
            if (causa instanceof RuntimeException r) {
                throw r;
            }
            throw new SQLException(causa);
        }
    }
}
