package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.servicio.UsuarioService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/**
 * Ventanas para cambiar claves.
 *  - cambiarMia(): cada usuario cambia SU clave (debe saber la actual).
 *  - restablecer(): el administrador le pone una clave nueva a otro usuario (por si la olvidó).
 * Devuelven true si se cambió la clave.
 */
public class ClaveDialog {

    private ClaveDialog() {
    }

    public static boolean cambiarMia() {
        return abrir("Cambiar mi clave", null);
    }

    public static boolean restablecer(Usuario objetivo) {
        return abrir("Nueva clave de " + objetivo.getNombre(), objetivo);
    }

    // objetivo == null significa "mi propia clave".
    private static boolean abrir(String titulo, Usuario objetivo) {
        UsuarioService servicio = new UsuarioService();
        boolean propia = objetivo == null;

        PasswordField actual = new PasswordField();
        actual.setPromptText("Tu clave de ahora");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Mínimo 6 caracteres");
        PasswordField repetir = new PasswordField();
        repetir.setPromptText("Repite la clave nueva");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        VBox contenido = new VBox(8);
        if (propia) {
            contenido.getChildren().addAll(etiqueta("Clave actual"), actual);
        }
        contenido.getChildren().addAll(etiqueta("Clave nueva"), nueva, etiqueta("Repetir clave nueva"), repetir, error);
        contenido.setPrefWidth(420);

        ButtonType guardar = new ButtonType("Cambiar clave", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(titulo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(() -> (propia ? actual : nueva).requestFocus());

        boolean[] cambiada = {false};
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                if (!nueva.getText().equals(repetir.getText())) {
                    throw new IllegalArgumentException("Las dos claves nuevas no coinciden.");
                }
                if (propia) {
                    servicio.cambiarMiClave(Sesion.usuario(), actual.getText(), nueva.getText());
                } else {
                    servicio.restablecerClave(Sesion.usuario(), objetivo, nueva.getText());
                }
                cambiada[0] = true;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return cambiada[0];
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
