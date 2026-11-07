package com.pos.ui;

import com.pos.modelo.Caja;
import com.pos.servicio.CajaService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/** Ventana para meter plata a la caja (INGRESO), sacarla (EGRESO) o devolverle dinero a un cliente (DEVOLUCION). */
public class MovimientoCajaDialog {

    private MovimientoCajaDialog() {
    }

    public static boolean abrir(String tipo, Caja caja) {
        String titulo = switch (tipo) {
            case "INGRESO" -> "Ingreso de efectivo";
            case "EGRESO" -> "Egreso de efectivo";
            default -> "Devolución de dinero";
        };
        String ayuda = switch (tipo) {
            case "INGRESO" -> "Plata que entra a la caja y no es una venta (ej: cambio, préstamo).";
            case "EGRESO" -> "Plata que sale de la caja (ej: almuerzo, mandado). Queda anotada con su motivo.";
            default -> "Dinero que le devuelves a un cliente. Sale de la caja y queda como gasto \"Devoluciones\".";
        };
        TextField monto = CamposDinero.compacto();
        monto.setPromptText("Valor, ej: 20.000");
        TextField motivo = new TextField();
        motivo.setPromptText("Motivo");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        Label a = new Label(ayuda);
        a.getStyleClass().add("texto-suave");
        a.setWrapText(true);
        VBox contenido = new VBox(8, a, etiqueta("Valor"), monto, etiqueta("Motivo"), motivo, error);
        contenido.setPrefWidth(420);

        ButtonType guardar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(titulo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(monto::requestFocus);
        motivo.setOnAction(e -> ((javafx.scene.control.Button) dialogo.getDialogPane().lookupButton(guardar)).fire());
        monto.setOnAction(e -> motivo.requestFocus());

        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                CajaService servicio = new CajaService();
                if ("DEVOLUCION".equals(tipo)) {
                    servicio.devolucionDeDinero(Sesion.usuario(), caja, CamposDinero.valor(monto), motivo.getText());
                } else {
                    servicio.registrarMovimiento(Sesion.usuario(), caja, tipo, CamposDinero.valor(monto), motivo.getText());
                }
                hecho[0] = true;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                e.consume();
            }
        });
        dialogo.showAndWait();
        return hecho[0];
    }

    private static Label etiqueta(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
