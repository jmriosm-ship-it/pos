package com.pos.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Ventana para elegir un rango de fechas. Las fechas se escriben como 31/10/2026. Devuelve {desde, hasta} o null. */
public class RangoDialog {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private RangoDialog() {
    }

    public static LocalDate[] abrir(LocalDate desdeInicial, LocalDate hastaInicial) {
        DatePicker desde = selector(desdeInicial);
        DatePicker hasta = selector(hastaInicial);
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        VBox contenido = new VBox(10, new HBox(12, campo("Desde", desde), campo("Hasta", hasta)), error);
        contenido.setPrefWidth(440);

        ButtonType aceptar = new ButtonType("Ver este rango", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("POS 2.0");
        dialogo.setHeaderText("Elige el rango de fechas");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, aceptar);
        Platform.runLater(desde.getEditor()::requestFocus);

        LocalDate[][] resultado = {null};
        dialogo.getDialogPane().lookupButton(aceptar).addEventFilter(ActionEvent.ACTION, e -> {
            LocalDate d = desde.getValue();
            LocalDate h = hasta.getValue();
            if (d == null || h == null) {
                error.setText("Escribe las dos fechas (por ejemplo 31/10/2026).");
                e.consume();
            } else if (h.isBefore(d)) {
                error.setText("La fecha final no puede ser anterior a la inicial.");
                e.consume();
            } else {
                resultado[0] = new LocalDate[]{d, h};
            }
        });
        dialogo.showAndWait();
        return resultado[0];
    }

    private static DatePicker selector(LocalDate inicial) {
        DatePicker p = new DatePicker(inicial);
        p.setMaxWidth(Double.MAX_VALUE);
        p.setPromptText("dd/mm/aaaa");
        p.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate fecha) {
                return fecha == null ? "" : fecha.format(FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {
                try {
                    return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.trim(), FECHA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        return p;
    }

    private static VBox campo(String titulo, DatePicker selector) {
        Label l = new Label(titulo);
        l.getStyleClass().add("etiqueta-campo");
        VBox caja = new VBox(4, l, selector);
        HBox.setHgrow(caja, Priority.ALWAYS);
        return caja;
    }
}
