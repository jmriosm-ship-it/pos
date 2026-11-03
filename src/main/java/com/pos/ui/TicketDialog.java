package com.pos.ui;

import com.pos.servicio.ImpresoraService;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Ventana que muestra un ticket de texto (recibo, comprobante de cierre...) y permite imprimirlo con F8. */
public class TicketDialog {

    private TicketDialog() {
    }

    public static void mostrar(String encabezado, String texto) {
        TextArea area = new TextArea(texto);
        area.setEditable(false);
        area.getStyleClass().add("recibo");
        // Ancho fijo: los tickets tienen 48 caracteres por línea y deben verse completos.
        area.setPrefWidth(480);
        area.setMinWidth(480);
        area.setPrefRowCount(Math.min(34, texto.split("\n").length + 1));

        ButtonType imprimir = new ButtonType("Imprimir", ButtonBar.ButtonData.OTHER);
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("POS 2.0");
        dialogo.setHeaderText(encabezado);
        dialogo.getDialogPane().setContent(area);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(imprimir, cerrar);

        javafx.scene.control.Button botonImprimir = (javafx.scene.control.Button) dialogo.getDialogPane().lookupButton(imprimir);
        botonImprimir.setGraphic(Atajos.tecla("F8"));
        botonImprimir.setContentDisplay(ContentDisplay.RIGHT);
        // El botón Imprimir no cierra la ventana: imprime y se queda.
        botonImprimir.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            enviar(texto);
        });
        dialogo.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F8) {
                enviar(texto);
                e.consume();
            }
        });
        dialogo.showAndWait();
    }

    public static void enviar(String texto) {
        try {
            new ImpresoraService().imprimir(texto);
        } catch (Exception ex) {
            Mensajes.error("No se pudo imprimir.\n" + ex.getMessage());
        }
    }
}
