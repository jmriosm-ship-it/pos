package com.pos.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Sabe si hay conexión con la base de datos. Un hilo la revisa cada pocos segundos; el resto del programa
 * pregunta enLinea() sin esperar. Cuando se cae, el programa pasa a "modo sin conexión" (ver CacheLocal y
 * ColaVentas) y cuando vuelve se suben solas las ventas guardadas.
 */
public final class Conectividad {

    private static volatile boolean enLinea = true;
    private static volatile boolean forzadoSinConexion = Boolean.getBoolean("sas.sinconexion");   // para pruebas
    private static final List<Consumer<Boolean>> OYENTES = new CopyOnWriteArrayList<>();
    private static Thread hilo;

    private Conectividad() {
    }

    public static boolean enLinea() {
        return enLinea && !forzadoSinConexion;
    }

    // Solo para pruebas: simula quitar o devolver el internet.
    public static synchronized void forzarSinConexion(boolean sinConexion) {
        boolean antes = enLinea();
        forzadoSinConexion = sinConexion;
        if (!sinConexion) {
            enLinea = true;   // se supone que volvió; el vigilante lo corrige si no
        }
        avisarSiCambio(antes);
    }

    // Quien se entera de que la conexión se cayó (una consulta falló) lo avisa aquí para reaccionar ya.
    public static void marcarCaida() {
        cambiar(false);
    }

    // Se llama cuando el programa sabe que sí hay conexión (por ejemplo, al conectarse por primera vez).
    public static void marcarEnLinea() {
        cambiar(true);
    }

    public static void alCambiar(Consumer<Boolean> oyente) {
        OYENTES.add(oyente);
    }

    // ¿Este error es de conexión (y no un error normal de la base de datos)?
    public static boolean esCaida(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SinConexionException || t instanceof java.net.ConnectException
                    || t instanceof java.net.UnknownHostException || t instanceof java.net.SocketException
                    || t instanceof java.net.SocketTimeoutException || t instanceof java.io.EOFException
                    || t instanceof java.sql.SQLTransientConnectionException || t instanceof java.sql.SQLNonTransientConnectionException) {
                return true;
            }
            if (t instanceof SQLException s && s.getSQLState() != null
                    && (s.getSQLState().startsWith("08") || s.getSQLState().startsWith("57P0") || s.getSQLState().equals("53300"))) {   // conexión caída, servidor arrancando o saturado
                return true;
            }
        }
        return false;
    }

    /** Empieza a vigilar la conexión (una sola vez). */
    public static synchronized void iniciar() {
        if (hilo != null) {
            return;
        }
        hilo = new Thread(() -> {
            while (true) {
                boolean ok = sondear();
                cambiar(ok);
                try {
                    Thread.sleep(ok ? 5000 : 2000);   // sin conexión se revisa más seguido
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "vigilante-conexion");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static boolean sondear() {
        if (forzadoSinConexion) {
            return false;
        }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(Conexion.host(), Integer.parseInt(Conexion.puerto())), 3000);
        } catch (Exception e) {
            return false;   // no se llega ni al servidor
        }
        try (Connection con = Conexion.probar()) {
            return con.isValid(4);
        } catch (SQLException e) {
            return false;
        }
    }

    private static synchronized void cambiar(boolean nuevo) {
        boolean antes = enLinea();
        enLinea = nuevo;
        avisarSiCambio(antes);
    }

    private static void avisarSiCambio(boolean antes) {
        boolean ahora = enLinea();
        if (antes != ahora) {
            for (Consumer<Boolean> o : OYENTES) {
                try {
                    o.accept(ahora);
                } catch (RuntimeException e) {
                    // un oyente con error no debe afectar a los demás
                }
            }
        }
    }
}
