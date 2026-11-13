package com.pos.ui;

import com.pos.modelo.Compra;
import com.pos.servicio.CompraService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;

/** Registrar un pago (abono) a un proveedor por una compra que quedó debiéndose. El efectivo sale de la caja abierta. */
public class PagoProveedorDialog {

    private PagoProveedorDialog() {
    }

    // Devuelve true si se registró el pago.
    public static boolean abrir(Compra compra) {
        CompraService servicio = new CompraService();
        BigDecimal saldo = compra.getSaldo();

        TextField monto = CamposDinero.compacto();
        monto.setText(Dinero.plano(saldo));   // por defecto se paga todo lo que se debe
        ComboBox<String> medio = new ComboBox<>();
        medio.getItems().addAll("Transferencia", "Efectivo (sale de la caja)", "Tarjeta");
        medio.getSelectionModel().selectFirst();
        medio.setMaxWidth(Double.MAX_VALUE);
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        Label l1 = new Label("Valor a pagar");
        l1.getStyleClass().add("etiqueta-campo");
        Label l2 = new Label("Con qué se paga");
        l2.getStyleClass().add("etiqueta-campo");
        VBox contenido = new VBox(6,
                new Label(compra.getNombreProveedor() + "  ·  compra #" + compra.getId()
                        + "\nTotal " + Dinero.formato(compra.getTotal()) + "  ·  pagado " + Dinero.formato(compra.getPagado())
                        + "  ·  debes " + Dinero.formato(saldo)),
                l1, monto, l2, medio, error);
        contenido.setPrefWidth(420);

        ButtonType pagar = new ButtonType("Registrar pago", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Pagar al proveedor");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, pagar);
        Platform.runLater(() -> {
            monto.requestFocus();
            monto.selectAll();
        });

        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(pagar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                int i = medio.getSelectionModel().getSelectedIndex();
                servicio.abonar(Sesion.usuario(), compra, i == 1 ? "EFECTIVO" : i == 2 ? "TARJETA" : "TRANSFERENCIA", CamposDinero.valor(monto));
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
}
