package com.pos.offline;

import com.pos.config.Conectividad;
import com.pos.dao.VentaDAO;
import com.pos.modelo.Venta;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Vigila la conexión y, apenas vuelve, sube solas las ventas guardadas en este PC. También mantiene al día la copia
 * local (CacheLocal) mientras hay conexión. Corre en un hilo aparte: no frena ninguna pantalla.
 */
public final class Sincronizador {

    private static final List<Runnable> OYENTES = new CopyOnWriteArrayList<>();
    private static Thread hilo;
    private static long ultimoRefresco = 0;
    private static final long ARRANQUE = System.currentTimeMillis();
    private static long ultimoIntentoCopia = 0;
    private static volatile boolean subiendo = false;

    private Sincronizador() {
    }

    /** Se avisa (en otro hilo) cada vez que cambia algo: conexión, ventas pendientes o ventas subidas. */
    public static void alCambiar(Runnable oyente) {
        OYENTES.add(oyente);
    }

    public static boolean subiendo() {
        return subiendo;
    }

    public static synchronized void iniciar() {
        if (hilo != null) {
            return;
        }
        Conectividad.iniciar();
        Conectividad.alCambiar(en -> {
            avisar();
            if (en) {
                despertar();   // volvió la conexión: subir ya, sin esperar el siguiente ciclo
            }
        });
        hilo = new Thread(() -> {
            while (true) {
                try {
                    if (Conectividad.enLinea()) {
                        ciclo();
                    }
                    synchronized (Sincronizador.class) {
                        Sincronizador.class.wait(6000);
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (RuntimeException e) {
                    // un error inesperado no debe matar el vigilante
                }
            }
        }, "sincronizador");
        hilo.setDaemon(true);
        hilo.start();
    }

    // Algo importante cambió (se abrió o cerró una caja, alguien entró): se vuelve a copiar todo en cuanto se pueda.
    public static void pedirRefresco() {
        ultimoRefresco = 0;
        despertar();
    }

    private static void despertar() {
        synchronized (Sincronizador.class) {
            Sincronizador.class.notifyAll();
        }
    }

    private static void ciclo() {
        subirVentas();
        // La copia de seguridad diaria: una vez al día, ya con el programa abierto un rato (para no frenar el arranque).
        long ahora = System.currentTimeMillis();
        if (ahora - ARRANQUE > 90_000 && ahora - ultimoIntentoCopia > 30 * 60_000L) {
            ultimoIntentoCopia = ahora;
            try {
                new com.pos.servicio.CopiaSeguridadService().copiaAutomaticaSiHaceFalta();
            } catch (Exception e) {
                // si falla (carpeta sin permiso, internet), se reintenta en media hora
            }
        }
        if (System.currentTimeMillis() - ultimoRefresco > 20_000) {
            try {
                CacheLocal.refrescar();
                ultimoRefresco = System.currentTimeMillis();
            } catch (SQLException e) {
                if (Conectividad.esCaida(e)) {
                    Conectividad.marcarCaida();
                }
            }
        }
    }

    /** Sube las ventas pendientes, de la más antigua a la más nueva. Devuelve cuántas se subieron. */
    public static synchronized int subirVentas() {
        List<Path> pendientes = ColaVentas.archivos();
        if (pendientes.isEmpty()) {
            return 0;
        }
        subiendo = true;
        avisar();
        int subidas = 0;
        try {
            VentaDAO dao = new VentaDAO();
            for (Path archivo : pendientes) {
                Venta v;
                try {
                    v = ColaVentas.leer(archivo);
                } catch (Exception e) {
                    ColaVentas.apartarConError(archivo, "No se pudo leer el archivo: " + e);
                    continue;
                }
                try {
                    dao.guardarSincronizada(v);
                    ColaVentas.eliminar(archivo);
                    subidas++;
                } catch (SQLException e) {
                    if (Conectividad.esCaida(e)) {
                        Conectividad.marcarCaida();
                        break;   // se cayó otra vez: lo que falta se sube después
                    }
                    if ("23505".equals(e.getSQLState())) {
                        ColaVentas.eliminar(archivo);   // ya estaba subida (se cortó antes de borrar el archivo)
                    } else {
                        ColaVentas.apartarConError(archivo, "Venta " + v.getEtiqueta() + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            subiendo = false;
        }
        if (subidas > 0) {
            ultimoRefresco = 0;   // el inventario cambió: se vuelve a leer en el siguiente ciclo
        }
        avisar();
        return subidas;
    }

    private static void avisar() {
        for (Runnable o : OYENTES) {
            try {
                o.run();
            } catch (RuntimeException e) {
                // un oyente con error no debe afectar a los demás
            }
        }
    }
}
