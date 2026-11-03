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
        // La primera vez crea el usuario admin; si falla, MySQL no está encendido o la clave es otra.
        try {
            Migraciones.aplicar();   // deja la base de datos al día (tablas nuevas, etc.)
            new AutenticacionService().crearAdminSiNoExiste();
        } catch (Exception e) {
            Mensajes.error("No se pudo conectar con la base de datos.\n"
                    + "Revisa que MySQL esté encendido y la clave en Conexion.java.\n\n" + e.getMessage());
        }

        escena = new Scene(new LoginView(this::mostrarPrincipal), 1280, 780);
        escena.getStylesheets().add(Mensajes.CSS);

        ventana.setTitle("POS 2.0");
        ventana.setMinWidth(1100);
        ventana.setMinHeight(680);
        ventana.setScene(escena);
        ventana.show();
    }

    // Se llama cuando el login es correcto: cambia el contenido de la ventana, no la ventana.
    private void mostrarPrincipal() {
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
