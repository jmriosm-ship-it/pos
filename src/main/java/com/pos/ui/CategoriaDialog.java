package com.pos.ui;

import com.pos.modelo.Categoria;
import com.pos.servicio.CategoriaService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/** Formulario para crear o renombrar una categoría. Devuelve true si se guardó. */
public class CategoriaDialog {

    private CategoriaDialog() {
    }

    public static boolean abrir(Categoria original) {
        boolean nueva = original == null;
        CategoriaService servicio = new CategoriaService();

        Label etiqueta = new Label("Nombre de la categoría");
        etiqueta.getStyleClass().add("etiqueta-campo");
        TextField nombre = new TextField(nueva ? "" : original.getNombre());
        nombre.setPromptText("Ej: Cargadores");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        VBox contenido = new VBox(6, etiqueta, nombre, error);
        contenido.setPrefWidth(380);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(nueva ? "Nueva categoría" : "Editar categoría");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(nombre::requestFocus);

        boolean[] guardado = {false};
        // Si hay un error se muestra aquí mismo y la ventana NO se cierra.
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                Categoria c = new Categoria(nombre.getText());
                if (!nueva) {
                    c.setId(original.getId());
                }
                servicio.guardar(c);
                guardado[0] = true;
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return guardado[0];
    }
}
