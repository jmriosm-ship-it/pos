package com.pos.ui;

import com.pos.dao.CreditoDAO;
import com.pos.servicio.CreditoService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;

/** Recibir un pago de un crédito: método y valor (por defecto lo que falta de la cuota que toca). */
public class PagoCreditoDialog {

    private static final String[][] METODOS = {{"EFECTIVO", "Efectivo"}, {"TRANSFERENCIA", "Transferencia"}, {"TARJETA", "Tarjeta"}};

    private PagoCreditoDialog() {
    }

    public static boolean abrir(CreditoDAO.Fila credito) {
        CreditoService servicio = new CreditoService();
        CreditoService.Estado estado;
        try {
            estado = servicio.estado(credito);
        } catch (SQLException e) {
            Mensajes.error("No se pudo leer el crédito.\n" + e.getMessage());
            return false;
        }
        BigDecimal sugerido = estado.cuotas().stream().filter(q -> q.pendiente().signum() > 0).map(CreditoService.CuotaEstado::pendiente)
                .findFirst().orElse(BigDecimal.ZERO);

        ComboBox<String> metodo = new ComboBox<>();
        for (String[] m : METODOS) {
            metodo.getItems().add(m[1]);
        }
        metodo.getSelectionModel().select(0);
        metodo.setMaxWidth(Double.MAX_VALUE);
        TextField valor = CamposDinero.compacto();
        valor.setText(sugerido.setScale(0, RoundingMode.HALF_UP).toPlainString());
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        Label info = new Label(credito.cliente() + "  ·  saldo " + Dinero.formato(estado.saldo())
                + (estado.mora().signum() > 0 ? "  ·  en mora " + Dinero.formato(estado.mora()) : ""));
        info.getStyleClass().add("linea-nombre");
        info.setWrapText(true);
        VBox contenido = new VBox(8, info, etiqueta("Cómo paga"), metodo, etiqueta("Valor"), valor, error);
        contenido.setPrefWidth(440);

        ButtonType guardar = new ButtonType("Registrar pago", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Pago del crédito " + credito.ventaNumero());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(() -> {
            valor.requestFocus();
            valor.selectAll();
        });
        valor.setOnAction(e -> ((Button) dialogo.getDialogPane().lookupButton(guardar)).fire());

        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                servicio.pagar(Sesion.usuario(), credito, METODOS[metodo.getSelectionModel().getSelectedIndex()][0], CamposDinero.valor(valor));
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
