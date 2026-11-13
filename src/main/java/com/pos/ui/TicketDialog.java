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

    // La vista del ticket para mostrar en pantalla: el texto sin las marcas de la impresora y, si es un recibo, el logo arriba.
    public static javafx.scene.Node vista(String texto) {
        boolean conLogo = texto.indexOf(com.pos.servicio.ReciboService.LOGO) >= 0;
        String paraVer = com.pos.servicio.ReciboService.paraPantalla(texto);
        TextArea area = new TextArea(paraVer);
        area.setEditable(false);
        area.getStyleClass().add("recibo");
        // Ancho fijo: los tickets tienen 48 caracteres por línea y deben verse completos.
        area.setPrefWidth(480);
        area.setMinWidth(480);
        area.setPrefRowCount(Math.min(34, paraVer.split("\n").length + 1));
        if (conLogo) {
            java.awt.image.BufferedImage logo = com.pos.servicio.LogoRecibo.paraPapel(240);
            if (logo != null) {
                javafx.scene.image.WritableImage imagen = new javafx.scene.image.WritableImage(logo.getWidth(), logo.getHeight());
                for (int y = 0; y < logo.getHeight(); y++) {
                    for (int x = 0; x < logo.getWidth(); x++) {
                        imagen.getPixelWriter().setArgb(x, y, 0xFF000000 | logo.getRGB(x, y));
                    }
                }
                javafx.scene.layout.VBox caja = new javafx.scene.layout.VBox(6, new javafx.scene.image.ImageView(imagen), area);
                caja.setAlignment(javafx.geometry.Pos.TOP_CENTER);
                return caja;
            }
        }
        return area;
    }

    public static void mostrar(String encabezado, String texto) {
        javafx.scene.Node contenido = vista(texto);

        ButtonType imprimir = new ButtonType("Imprimir", ButtonBar.ButtonData.OTHER);
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(encabezado);
        dialogo.getDialogPane().setContent(contenido);
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
