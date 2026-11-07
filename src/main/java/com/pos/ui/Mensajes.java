package com.pos.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** Ventanitas de aviso (error, información, confirmación) con el tema del sistema. */
public class Mensajes {

    // Ruta del archivo de estilos; todas las ventanas lo cargan de aquí.
    public static final String CSS = Mensajes.class.getResource("/css/app.css").toExternalForm();

    private Mensajes() {
    }

    public static void error(String mensaje) {
        crear(Alert.AlertType.ERROR, "Algo salió mal", mensaje).showAndWait();
    }

    public static void info(String titulo, String mensaje) {
        crear(Alert.AlertType.INFORMATION, titulo, mensaje).showAndWait();
    }

    // Pregunta Aceptar / Cancelar. Devuelve true si el usuario aceptó.
    public static boolean confirmar(String titulo, String mensaje) {
        return crear(Alert.AlertType.CONFIRMATION, titulo, mensaje)
                .showAndWait()
                .filter(b -> b == ButtonType.OK)
                .isPresent();
    }

    private static Alert crear(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo);
        a.setTitle(Marca.PRODUCTO);
        a.setHeaderText(titulo);
        a.setContentText(mensaje);
        a.getDialogPane().getStylesheets().add(CSS);
        return a;
    }
}
