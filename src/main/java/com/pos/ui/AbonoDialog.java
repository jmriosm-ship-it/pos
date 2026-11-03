package com.pos.ui;

import com.pos.modelo.Cliente;
import com.pos.servicio.FiadoService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Ventana para registrar el abono de un cliente sobre lo que debe.
 * Teclas: F1 efectivo · F2 transferencia · F3 tarjeta · F5 pagar todo · Enter guardar · Esc cancelar.
 * Devuelve true si se registró el abono.
 */
public class AbonoDialog {

    private AbonoDialog() {
    }

    public static boolean abrir(Cliente cliente, BigDecimal deuda) {
        FiadoService servicio = new FiadoService();

        // Lo que debe, en grande
        Label etiquetaDeuda = new Label("Debe en total");
        etiquetaDeuda.getStyleClass().add("etiqueta-campo");
        Label valorDeuda = new Label(Dinero.formato(deuda));
        valorDeuda.getStyleClass().addAll("cobro-estado-valor", "texto-peligro");

        // Cuánto abona
        Label etiquetaMonto = new Label("Abono");
        etiquetaMonto.getStyleClass().add("etiqueta-campo");
        TextField monto = CamposDinero.crear();
        Button pagarTodo = new Button("Pagar todo");
        pagarTodo.getStyleClass().add("btn-rapido");
        pagarTodo.setGraphic(Atajos.tecla("F5"));
        pagarTodo.setContentDisplay(ContentDisplay.RIGHT);
        pagarTodo.setGraphicTextGap(8);
        pagarTodo.setFocusTraversable(false);
        pagarTodo.setOnAction(e -> {
            monto.setText(deuda.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
            monto.requestFocus();
            monto.selectAll();
        });

        // Cómo pagó
        ToggleGroup grupo = new ToggleGroup();
        ToggleButton efectivo = metodo("Efectivo", "EFECTIVO", "F1", grupo);
        ToggleButton transferencia = metodo("Transferencia", "TRANSFERENCIA", "F2", grupo);
        ToggleButton tarjeta = metodo("Tarjeta", "TARJETA", "F3", grupo);
        grupo.selectToggle(efectivo);
        // Siempre debe haber uno elegido.
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                antes.setSelected(true);
            }
        });
        HBox metodos = new HBox(8, efectivo, transferencia, tarjeta);
        for (ToggleButton b : new ToggleButton[]{efectivo, transferencia, tarjeta}) {
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setMaxWidth(Double.MAX_VALUE);
        }

        // Lo que quedará debiendo (se actualiza al escribir)
        Label resultado = new Label();
        resultado.getStyleClass().add("cobro-fila-valor");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());

        VBox contenido = new VBox(8,
                new HBox(10, new VBox(2, etiquetaDeuda, valorDeuda)),
                etiquetaMonto, monto, pagarTodo,
                etiqueta("Cómo pagó"), metodos, resultado, error);
        contenido.setPrefWidth(460);
        contenido.setAlignment(Pos.TOP_LEFT);

        ButtonType guardar = new ButtonType("Registrar abono", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("POS 2.0");
        dialogo.setHeaderText("Abono de " + cliente.getNombre());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Button botonGuardar = (Button) dialogo.getDialogPane().lookupButton(guardar);

        // Muestra en vivo cuánto quedará debiendo y activa el botón solo si el abono es válido.
        Runnable actualizar = () -> {
            BigDecimal abono = CamposDinero.valor(monto);
            resultado.getStyleClass().removeAll("texto-peligro", "texto-ok");
            error.setText("");
            if (abono.signum() == 0) {
                resultado.setText("Escribe cuánto abona");
                botonGuardar.setDisable(true);
            } else if (abono.compareTo(deuda) > 0) {
                resultado.setText("Sobra " + Dinero.formato(abono.subtract(deuda)));
                resultado.getStyleClass().add("texto-peligro");
                error.setText("El abono no puede ser mayor que lo que debe.");
                botonGuardar.setDisable(true);
            } else if (abono.compareTo(deuda) == 0) {
                resultado.setText("Deuda saldada ✓");
                resultado.getStyleClass().add("texto-ok");
                botonGuardar.setDisable(false);
            } else {
                resultado.setText("Quedará debiendo " + Dinero.formato(deuda.subtract(abono)));
                botonGuardar.setDisable(false);
            }
        };
        monto.textProperty().addListener((o, antes, ahora) -> actualizar.run());
        actualizar.run();
        Platform.runLater(monto::requestFocus);

        // Teclas de la ventana
        dialogo.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case F1 -> grupo.selectToggle(efectivo);
                case F2 -> grupo.selectToggle(transferencia);
                case F3 -> grupo.selectToggle(tarjeta);
                case F5 -> pagarTodo.fire();
                default -> {
                    return;
                }
            }
            e.consume();
        });

        boolean[] guardado = {false};
        botonGuardar.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                String elegido = (String) grupo.getSelectedToggle().getUserData();
                servicio.registrarAbono(Sesion.usuario(), cliente, elegido, CamposDinero.valor(monto));
                guardado[0] = true;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("No se pudo registrar el abono: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return guardado[0];
    }

    private static ToggleButton metodo(String texto, String valor, String tecla, ToggleGroup grupo) {
        ToggleButton b = new ToggleButton(texto);
        b.setUserData(valor);
        b.setToggleGroup(grupo);
        b.getStyleClass().add("metodo");
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        return b;
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
