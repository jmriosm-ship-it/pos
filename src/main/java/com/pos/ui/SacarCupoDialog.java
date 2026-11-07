package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.servicio.NominaService;
import com.pos.servicio.UsuarioService;
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
import java.sql.SQLException;
import java.util.List;

/**
 * "Sacar del cupo": un trabajador anota lo que se lleva del local a cuenta de su sueldo (para sí mismo);
 * el administrador puede anotarlo para cualquiera y, si se pasa del cupo, se le pide confirmar.
 */
public class SacarCupoDialog {

    private SacarCupoDialog() {
    }

    // "preseleccionado" puede ser null (se usa el usuario de la sesión). Devuelve true si se anotó.
    public static boolean abrir(Usuario preseleccionado) {
        NominaService servicio = new NominaService();
        Usuario yo = Sesion.usuario();
        boolean admin = yo.esAdmin();

        ComboBox<Usuario> quien = new ComboBox<>();
        quien.setMaxWidth(Double.MAX_VALUE);
        try {
            List<Usuario> todos = admin ? new UsuarioService().listar().stream().filter(Usuario::isActivo).toList() : List.of(yo);
            quien.getItems().setAll(todos);
        } catch (SQLException | IllegalStateException e) {
            quien.getItems().setAll(List.of(yo));
        }
        Usuario inicial = preseleccionado != null ? preseleccionado : yo;
        for (Usuario u : quien.getItems()) {
            if (u.getId() == inicial.getId()) {
                quien.setValue(u);
            }
        }
        quien.setDisable(!admin);

        Label disponible = new Label();
        disponible.getStyleClass().add("linea-nombre");
        Runnable actualizar = () -> {
            try {
                if (quien.getValue() != null) {
                    NominaService.Estado e = servicio.estado(quien.getValue());
                    disponible.setText("Cupo " + Dinero.formato(e.cupo()) + "  ·  pendiente " + Dinero.formato(e.pendiente())
                            + "  ·  disponible " + Dinero.formato(e.disponible().max(BigDecimal.ZERO)));
                }
            } catch (SQLException ex) {
                disponible.setText("");
            }
        };
        quien.valueProperty().addListener((o, a, b) -> actualizar.run());
        actualizar.run();

        TextField concepto = new TextField();
        concepto.setPromptText("Qué se lleva, ej: Perfume 100 ml");
        TextField valor = CamposDinero.compacto();
        valor.setPromptText("Valor");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        VBox contenido = new VBox(8, etiqueta("Para quién"), quien, disponible, etiqueta("Qué se lleva"), concepto,
                etiqueta("Valor"), valor, error);
        contenido.setPrefWidth(460);

        ButtonType guardar = new ButtonType("Anotar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Sacar del cupo");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(concepto::requestFocus);
        concepto.setOnAction(e -> valor.requestFocus());
        valor.setOnAction(e -> ((Button) dialogo.getDialogPane().lookupButton(guardar)).fire());

        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                boolean pasarse = false;
                BigDecimal v = CamposDinero.valor(valor);
                if (admin && quien.getValue() != null && v.compareTo(servicio.estado(quien.getValue()).disponible()) > 0) {
                    if (!Mensajes.confirmar("Se pasa del cupo", "Esto supera el cupo disponible de " + quien.getValue().getNombre()
                            + ". ¿Anotarlo de todas formas?")) {
                        e.consume();
                        return;
                    }
                    pasarse = true;
                }
                servicio.sacarDelCupo(yo, quien.getValue(), concepto.getText(), v, pasarse);
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
