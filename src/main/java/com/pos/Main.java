package com.pos;

import com.pos.config.Migraciones;
import com.pos.servicio.AutenticacionService;
import com.pos.ui.LoginView;
import com.pos.ui.MainView;
import com.pos.ui.Mensajes;
import com.pos.ui.Sesion;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** La aplicación JavaFX: una sola ventana que cambia entre el login y la pantalla principal. */
public class Main extends Application {

    private Scene escena;

    @Override
    public void start(Stage ventana) {
        // Si falla, no hay conexión a la base de datos o los datos de conexión son otros.
        boolean primerUso = false;
        try {
            Migraciones.aplicar();   // deja la base de datos al día (tablas nuevas, etc.)
            primerUso = new com.pos.dao.UsuarioDAO().contar() == 0;   // sin usuarios: es la primera vez, el dueño crea el suyo
        } catch (Exception e) {
            if (com.pos.config.Conectividad.esCaida(e) && com.pos.offline.CacheLocal.hayDatos()) {
                // Sin internet, pero este PC ya trabajó antes: se abre en modo sin conexión (se puede vender).
                com.pos.config.Conectividad.marcarCaida();
            } else {
                // Sin base de datos y sin copia local no hay nada que hacer: se avisa y el programa se cierra.
                Mensajes.error("No se pudo conectar con la base de datos.\n"
                        + "Revisa tu conexión a internet y que los datos de conexión (Supabase) sean correctos en:\n"
                        + com.pos.config.Conexion.ARCHIVO + "\n\n" + e.getMessage());
                javafx.application.Platform.exit();
                return;
            }
        }
        com.pos.offline.Sincronizador.iniciar();   // vigila la conexión, sube las ventas pendientes y mantiene la copia local

        // Primera vez: se aceptan los términos y el dueño crea su usuario; después, entra directo.
        escena = new Scene(primerUso
                ? new com.pos.ui.PrimerUsoView(u -> {
                    com.pos.ui.Sesion.iniciar(u);
                    mostrarPrincipal();
                })
                : new LoginView(this::mostrarPrincipal), 1280, 780);
        escena.getStylesheets().add(Mensajes.CSS);

        ventana.setTitle(com.pos.ui.Marca.PRODUCTO + (System.getProperty("sas.conexion") != null ? "  —  MODO PRUEBA (no se guarda en Supabase)" : ""));
        ventana.setMinWidth(1100);
        ventana.setMinHeight(680);
        ventana.setScene(escena);
        ventana.show();
    }

    // Se llama cuando el login es correcto: cambia el contenido de la ventana, no la ventana.
    private void mostrarPrincipal() {
        // Con la clave inicial del sistema (admin123) no se puede trabajar: hay que cambiarla primero.
        if (new AutenticacionService().usaClaveInicial(Sesion.usuario())) {
            Mensajes.info("Cambia tu clave", "Estás usando la clave inicial del sistema.\n"
                    + "Por seguridad debes cambiarla ahora, antes de empezar a trabajar.");
            if (!com.pos.ui.ClaveDialog.cambiarMia()) {
                cerrarSesion();   // si no la cambia, vuelve al login
                return;
            }
        }
        escena.setRoot(new MainView(this::cerrarSesion));
    }

    private void cerrarSesion() {
        Sesion.cerrar();
        escena.setRoot(new LoginView(this::mostrarPrincipal));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
