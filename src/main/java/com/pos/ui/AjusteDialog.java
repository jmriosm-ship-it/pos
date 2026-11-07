package com.pos.ui;

import com.pos.modelo.Producto;
import com.pos.servicio.InventarioService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/**
 * Ventana para corregir el stock de un producto después de contarlo (conteo físico, merma, daño...).
 * Se escribe cuántas unidades HAY de verdad y el motivo; el sistema calcula la diferencia.
 * Teclas: F1 a F4 llenan el motivo · Enter guarda · Esc cancela. Devuelve true si se guardó el ajuste.
 */
public class AjusteDialog {

    private static final String[] MOTIVOS = {"Conteo físico", "Merma", "Producto dañado", "Error de registro"};

    private AjusteDialog() {
    }

    public static boolean abrir(Producto producto) {
        InventarioService servicio = new InventarioService();

        Label etiquetaActual = new Label("Stock en el sistema");
        etiquetaActual.getStyleClass().add("etiqueta-campo");
        Label valorActual = new Label(String.valueOf(producto.getStock()) + " unidades");
        valorActual.getStyleClass().add("cobro-fila-valor");

        Label etiquetaContado = new Label("Unidades que hay de verdad (lo que contaste)");
        etiquetaContado.getStyleClass().add("etiqueta-campo");
        TextField contado = new TextField();
        contado.setPromptText("0");
        contado.getStyleClass().add("campo-monto");
        // Solo números enteros.
        contado.textProperty().addListener((o, antes, ahora) -> {
            String limpio = ahora.replaceAll("[^0-9]", "");
            if (limpio.length() > 6) {
                limpio = limpio.substring(0, 6);
            }
            if (!limpio.equals(ahora)) {
                contado.setText(limpio);
            }
        });

        Label diferencia = new Label();
        diferencia.getStyleClass().add("cobro-fila-valor");

        Label etiquetaMotivo = new Label("Motivo del ajuste");
        etiquetaMotivo.getStyleClass().add("etiqueta-campo");
        TextField motivo = new TextField();
        motivo.setPromptText("Escribe el motivo o elige uno con F1 a F4");
        FlowPane atajos = new FlowPane(8, 8);
        for (int i = 0; i < MOTIVOS.length; i++) {
            String texto = MOTIVOS[i];
            Button b = new Button(texto);
            b.getStyleClass().add("btn-rapido");
            b.setGraphic(Atajos.tecla("F" + (i + 1)));
            b.setContentDisplay(ContentDisplay.RIGHT);
            b.setGraphicTextGap(8);
            b.setFocusTraversable(false);
            b.setOnAction(e -> motivo.setText(texto));
            atajos.getChildren().add(b);
        }

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());

        VBox contenido = new VBox(8, etiquetaActual, valorActual, etiquetaContado, contado, diferencia,
                etiquetaMotivo, motivo, atajos, error);
        contenido.setPrefWidth(480);

        ButtonType guardar = new ButtonType("Guardar ajuste", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Ajustar stock de " + producto.getNombre());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Button botonGuardar = (Button) dialogo.getDialogPane().lookupButton(guardar);

        // Muestra en vivo cuánto sobra o falta frente a lo que dice el sistema.
        Runnable actualizar = () -> {
            diferencia.getStyleClass().removeAll("texto-peligro", "texto-ok", "texto-aviso", "texto-suave");
            error.setText("");
            if (contado.getText().isBlank()) {
                diferencia.setText("Escribe cuántas unidades contaste");
                diferencia.getStyleClass().add("texto-suave");
                botonGuardar.setDisable(true);
                return;
            }
            int dif = Integer.parseInt(contado.getText()) - producto.getStock();
            if (dif == 0) {
                diferencia.setText("Sin cambios: coincide con el sistema");
                diferencia.getStyleClass().add("texto-suave");
                botonGuardar.setDisable(true);
            } else if (dif < 0) {
                diferencia.setText("Faltan " + (-dif) + (dif == -1 ? " unidad" : " unidades"));
                diferencia.getStyleClass().add("texto-peligro");
                botonGuardar.setDisable(false);
            } else {
                diferencia.setText("Sobran " + dif + (dif == 1 ? " unidad" : " unidades"));
                diferencia.getStyleClass().add("texto-aviso");
                botonGuardar.setDisable(false);
            }
        };
        contado.textProperty().addListener((o, antes, ahora) -> actualizar.run());
        actualizar.run();
        Platform.runLater(contado::requestFocus);

        dialogo.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            int indice = switch (e.getCode()) {
                case F1 -> 0;
                case F2 -> 1;
                case F3 -> 2;
                case F4 -> 3;
                default -> -1;
            };
            if (indice >= 0) {
                motivo.setText(MOTIVOS[indice]);
                e.consume();
            }
        });

        boolean[] guardado = {false};
        botonGuardar.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                servicio.ajustarStock(Sesion.usuario(), producto, Integer.parseInt(contado.getText()), motivo.getText());
                guardado[0] = true;
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("No se pudo guardar el ajuste: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return guardado[0];
    }
}
