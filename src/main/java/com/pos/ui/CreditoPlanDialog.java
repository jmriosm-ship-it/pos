package com.pos.ui;

import com.pos.modelo.PlanCredito;
import com.pos.servicio.CreditoService;
import com.pos.util.Dinero;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Ventana para armar el plan de un crédito: frecuencia, número de cuotas, tasa y fecha de la primera cuota. */
public class CreditoPlanDialog {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CreditoPlanDialog() {
    }

    // Plan que se propone cuando todavía no hay uno: mensual, primer plazo y primera tasa de las opciones.
    public static PlanCredito porDefecto(BigDecimal financiado) throws SQLException {
        CreditoService servicio = new CreditoService();
        List<Integer> plazos = servicio.plazos("MENSUAL");
        return servicio.simular(financiado, "MENSUAL", plazos.isEmpty() ? 6 : plazos.get(0), servicio.tasas().get(0),
                LocalDate.now().plusMonths(1));
    }

    // Devuelve el plan elegido, o null si se canceló.
    public static PlanCredito abrir(BigDecimal financiado, BigDecimal totalVenta, PlanCredito actual) {
        CreditoService servicio = new CreditoService();
        List<Integer> plazosMensual;
        List<Integer> plazosQuincenal;
        List<BigDecimal> tasas;
        try {
            plazosMensual = servicio.plazos("MENSUAL");
            plazosQuincenal = servicio.plazos("QUINCENAL");
            tasas = servicio.tasas();
        } catch (SQLException e) {
            Mensajes.error("No se pudieron leer las opciones de crédito.\n" + e.getMessage());
            return null;
        }

        ToggleGroup frecuencia = new ToggleGroup();
        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chips = new HBox(6, Chips.crear("Mensual", "MENSUAL", frecuencia, gris), Chips.crear("Quincenal (cada 15 días)", "QUINCENAL", frecuencia, gris));
        chips.setAlignment(Pos.CENTER_LEFT);
        ComboBox<Integer> plazo = new ComboBox<>();
        plazo.setMaxWidth(Double.MAX_VALUE);
        ComboBox<BigDecimal> tasa = new ComboBox<>();
        tasa.getItems().setAll(tasas);
        tasa.setMaxWidth(Double.MAX_VALUE);
        DatePicker primera = new DatePicker();
        primera.setMaxWidth(Double.MAX_VALUE);

        TableView<PlanCredito.Cuota> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getColumns().add(Tablas.columna("Cuota", 70, (PlanCredito.Cuota c) -> String.valueOf(c.n())));
        tabla.getColumns().add(Tablas.columna("Vence", 130, (PlanCredito.Cuota c) -> c.vence().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Valor", 120, (PlanCredito.Cuota c) -> Dinero.formato(c.valor())));
        tabla.setPrefHeight(190);

        Label resumen = new Label();
        resumen.getStyleClass().add("linea-nombre");
        resumen.setWrapText(true);
        Label inicial = new Label();
        inicial.getStyleClass().add("texto-suave");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        PlanCredito[] vigente = {null};
        Runnable recalcular = () -> {
            try {
                String f = (String) Chips.valor(frecuencia);
                if (plazo.getValue() == null || tasa.getValue() == null || primera.getValue() == null) {
                    return;
                }
                PlanCredito p = servicio.simular(financiado, f, plazo.getValue(), tasa.getValue(), primera.getValue());
                vigente[0] = p;
                tabla.getItems().setAll(p.cuotas());
                resumen.setText(p.plazo() + " cuotas " + p.frecuenciaTexto() + "s de " + Dinero.formato(p.cuotaValor())
                        + "  ·  total " + Dinero.formato(p.totalCredito()) + "  ·  intereses " + Dinero.formato(p.intereses()));
                error.setText("");
            } catch (IllegalArgumentException | SQLException ex) {
                vigente[0] = null;
                error.setText(ex.getMessage());
            }
        };
        Runnable cambiarFrecuencia = () -> {
            boolean quincenal = "QUINCENAL".equals(Chips.valor(frecuencia));
            Integer anterior = plazo.getValue();
            plazo.getItems().setAll(quincenal ? plazosQuincenal : plazosMensual);
            plazo.setValue(anterior != null && plazo.getItems().contains(anterior) ? anterior
                    : plazo.getItems().isEmpty() ? null : plazo.getItems().get(0));
            if (primera.getValue() == null || actual == null || !actual.frecuencia().equals(quincenal ? "QUINCENAL" : "MENSUAL")) {
                primera.setValue(quincenal ? LocalDate.now().plusDays(15) : LocalDate.now().plusMonths(1));
            }
            recalcular.run();
        };
        Chips.alElegir(frecuencia, cambiarFrecuencia);

        // Valores de partida: lo que ya había armado, o lo de siempre.
        frecuencia.selectToggle(frecuencia.getToggles().get(actual != null && "QUINCENAL".equals(actual.frecuencia()) ? 1 : 0));
        if (actual != null) {
            primera.setValue(actual.primeraCuota());
            tasa.setValue(tasas.stream().filter(t -> t.compareTo(actual.tasa()) == 0).findFirst().orElse(tasas.get(0)));
            if (plazo.getItems().contains(actual.plazo())) {
                plazo.setValue(actual.plazo());
            }
        } else {
            tasa.setValue(tasas.get(0));
        }
        cambiarFrecuencia.run();
        plazo.valueProperty().addListener((o, a, b) -> recalcular.run());
        tasa.valueProperty().addListener((o, a, b) -> recalcular.run());
        primera.valueProperty().addListener((o, a, b) -> recalcular.run());

        BigDecimal pagaHoy = totalVenta.subtract(financiado);
        inicial.setText("Se financian " + Dinero.formato(financiado) + "  ·  se paga hoy " + Dinero.formato(pagaHoy));

        VBox contenido = new VBox(8, inicial, etiqueta("Frecuencia"), chips,
                new HBox(12, columna("Número de cuotas", plazo), columna("Tasa mensual (%)", tasa), columna("Primera cuota", primera)),
                resumen, tabla, error);
        contenido.setPrefWidth(620);

        ButtonType usar = new ButtonType("Usar este plan", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Plan de cuotas");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, usar);

        PlanCredito[] resultado = {null};
        dialogo.getDialogPane().lookupButton(usar).addEventFilter(ActionEvent.ACTION, e -> {
            if (vigente[0] == null) {
                e.consume();
            } else {
                resultado[0] = vigente[0];
            }
        });
        dialogo.showAndWait();
        return resultado[0];
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
