package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.servicio.NominaService;
import com.pos.servicio.UsuarioService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Pagar el sueldo de un trabajador (solo administrador): sueldo = días × valor del día; del sueldo se descuenta
 * lo que haya sacado del cupo (por defecto todo lo pendiente) y lo que queda (neto) es lo que se le entrega.
 */
public class PagarSueldoDialog {

    private PagarSueldoDialog() {
    }

    public static boolean abrir(Usuario preseleccionado) {
        NominaService servicio = new NominaService();
        Usuario yo = Sesion.usuario();
        if (!yo.esAdmin()) {
            Mensajes.info("Solo administrador", "Solo un administrador puede pagar sueldos.");
            return false;
        }

        ComboBox<Usuario> quien = new ComboBox<>();
        quien.setMaxWidth(Double.MAX_VALUE);
        try {
            quien.getItems().setAll(new UsuarioService().listar().stream().filter(Usuario::isActivo).toList());
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la lista.\n" + e.getMessage());
            return false;
        }
        if (quien.getItems().isEmpty()) {
            return false;
        }
        quien.getSelectionModel().select(0);
        if (preseleccionado != null) {
            quien.getItems().stream().filter(u -> u.getId() == preseleccionado.getId()).findFirst().ifPresent(quien::setValue);
        }

        YearMonth mes = YearMonth.from(LocalDate.now());
        TextField periodo = new TextField(nombreMes(mes));
        periodo.setPrefWidth(190);
        TextField dias = new TextField();
        TextField valorDia = CamposDinero.compacto();
        TextField descuento = CamposDinero.compacto();
        Label ayuda = new Label();
        ayuda.getStyleClass().add("texto-suave");
        ayuda.setWrapText(true);
        Label pendiente = new Label();
        pendiente.getStyleClass().add("linea-nombre");
        Label sueldoTxt = new Label();
        Label netoTxt = new Label();
        netoTxt.getStyleClass().add("linea-nombre");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        BigDecimal[] pend = {BigDecimal.ZERO};
        Runnable calcular = () -> {
            int d = entero(dias.getText());
            BigDecimal vd = CamposDinero.valor(valorDia);
            BigDecimal sueldo = vd.multiply(BigDecimal.valueOf(d)).setScale(0, RoundingMode.HALF_UP);
            sueldoTxt.setText("Sueldo: " + Dinero.formato(sueldo));
            netoTxt.setText("Se le entregan " + Dinero.formato(sueldo.subtract(CamposDinero.valor(descuento))));
        };
        Runnable cargarTrabajador = () -> {
            Usuario u = quien.getValue();
            if (u == null) {
                return;
            }
            try {
                pend[0] = servicio.estado(u).pendiente();
                int vendidos = servicio.diasVendidos(u, mes);
                dias.setText(vendidos > 0 ? String.valueOf(vendidos) : "");
                ayuda.setText("Este mes vendió en " + vendidos + (vendidos == 1 ? " día." : " días.") + " Cambia los días si hace falta.");
                valorDia.setText(u.getPagoDia().setScale(0, RoundingMode.HALF_UP).toPlainString());
                pendiente.setText("Debe del cupo: " + Dinero.formato(pend[0]));
                descuento.setText(pend[0].setScale(0, RoundingMode.HALF_UP).toPlainString());
            } catch (SQLException e) {
                error.setText("No se pudo leer al trabajador: " + e.getMessage());
            }
            calcular.run();
        };
        quien.valueProperty().addListener((o, a, b) -> cargarTrabajador.run());
        dias.textProperty().addListener((o, a, b) -> calcular.run());
        valorDia.textProperty().addListener((o, a, b) -> calcular.run());
        descuento.textProperty().addListener((o, a, b) -> calcular.run());
        cargarTrabajador.run();

        VBox contenido = new VBox(8, etiqueta("Trabajador"), quien, pendiente,
                new HBox(12, columna("Periodo", periodo), columna("Días trabajados", dias), columna("Valor del día", valorDia)),
                ayuda, etiqueta("Descuento del cupo (0 hasta lo pendiente)"), descuento, sueldoTxt, netoTxt, error);
        contenido.setPrefWidth(560);

        ButtonType pagar = new ButtonType("Pagar sueldo", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Pagar sueldo");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, pagar);
        Platform.runLater(dias::requestFocus);

        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(pagar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                BigDecimal sueldo = CamposDinero.valor(valorDia).multiply(BigDecimal.valueOf(entero(dias.getText())));
                BigDecimal desc = CamposDinero.valor(descuento);
                if (desc.compareTo(sueldo) > 0 && !Mensajes.confirmar("Descuento mayor al sueldo",
                        "El descuento es mayor al sueldo y el trabajador quedaría debiendo. ¿Continuar?")) {
                    e.consume();
                    return;
                }
                BigDecimal neto = servicio.pagarSueldo(yo, quien.getValue(), entero(dias.getText()), CamposDinero.valor(valorDia),
                        desc, periodo.getText());
                hecho[0] = true;
                Mensajes.info("Sueldo pagado", neto.signum() > 0 ? "Se entregan " + Dinero.formato(neto) + " (quedó como gasto de Nómina)."
                        : "El sueldo se cubrió con el cupo; no se entrega dinero.");
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

    private static String nombreMes(YearMonth m) {
        String n = m.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "CO"));
        return Character.toUpperCase(n.charAt(0)) + n.substring(1) + " " + m.getYear();
    }

    private static int entero(String t) {
        try {
            return Integer.parseInt(Normalizer.normalize(t.trim(), Normalizer.Form.NFC));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static VBox columna(String titulo, javafx.scene.Node campo) {
        VBox v = new VBox(4, etiqueta(titulo), campo);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }

    private static Label etiqueta(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
