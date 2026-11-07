package com.pos.ui;

import com.pos.modelo.Venta;
import com.pos.servicio.DevolucionService;
import com.pos.util.Dinero;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ventana para devolver productos de una venta: se elige cuántas unidades y cómo se devuelve el dinero. */
public class DevolucionDialog {

    private static final String[][] METODOS = {{"EFECTIVO", "Efectivo (sale de tu caja)"}, {"TRANSFERENCIA", "Transferencia"},
            {"TARJETA", "Tarjeta"}, {"FIADO", "Descontar de lo que debe el cliente"}};

    private DevolucionDialog() {
    }

    // Devuelve true si se hizo una devolución.
    public static boolean abrir(Venta venta) {
        DevolucionService servicio = new DevolucionService();
        List<DevolucionService.Linea> lineas;
        try {
            lineas = servicio.lineas(venta);
        } catch (SQLException e) {
            Mensajes.error("No se pudo leer la venta.\n" + e.getMessage());
            return false;
        }
        if (lineas.stream().noneMatch(l -> l.disponibles() > 0)) {
            Mensajes.info("Nada para devolver", "De esta venta ya se devolvió todo.");
            return false;
        }

        GridPane tabla = new GridPane();
        tabla.setHgap(14);
        tabla.setVgap(8);
        tabla.add(titulo("Producto"), 0, 0);
        tabla.add(titulo("Vendidas"), 1, 0);
        tabla.add(titulo("Ya devueltas"), 2, 0);
        tabla.add(titulo("Devolver"), 3, 0);
        Label reembolso = new Label();
        reembolso.getStyleClass().add("linea-nombre");
        Map<Integer, Spinner<Integer>> spinners = new LinkedHashMap<>();
        Runnable recalcular = () -> {
            Map<Integer, Integer> cantidades = cantidades(spinners);
            reembolso.setText(Dinero.formato(servicio.reembolso(venta, lineas, cantidades)));
        };
        int fila = 1;
        for (DevolucionService.Linea l : lineas) {
            Label nombre = new Label(l.nombre() + "  (" + Dinero.formato(l.precioUnit()) + ")");
            Spinner<Integer> spinner = new Spinner<>();
            spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Math.max(0, l.disponibles()), 0));
            spinner.setEditable(true);
            spinner.setPrefWidth(90);
            spinner.setDisable(l.disponibles() <= 0);
            spinner.valueProperty().addListener((o, a, b) -> recalcular.run());
            spinners.put(l.idProducto(), spinner);
            GridPane.setHgrow(nombre, Priority.ALWAYS);
            tabla.add(nombre, 0, fila);
            tabla.add(new Label(String.valueOf(l.vendidas())), 1, fila);
            tabla.add(new Label(String.valueOf(l.yaDevueltas())), 2, fila);
            tabla.add(spinner, 3, fila);
            fila++;
        }

        ComboBox<String> metodo = new ComboBox<>();
        for (String[] m : METODOS) {
            if (!"FIADO".equals(m[0]) || venta.getIdCliente() != null) {
                metodo.getItems().add(m[1]);
            }
        }
        metodo.getSelectionModel().select(0);
        metodo.setMaxWidth(Double.MAX_VALUE);

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        Label nota = new Label("Se devuelve lo proporcional a lo que se pagó (con sus descuentos y promociones).");
        nota.getStyleClass().add("texto-suave");
        nota.setWrapText(true);
        Label etMetodo = new Label("¿Cómo se devuelve el dinero?");
        etMetodo.getStyleClass().add("etiqueta-campo");
        Label etReembolso = new Label("Se devuelve");
        etReembolso.getStyleClass().add("etiqueta-campo");
        VBox contenido = new VBox(10, tabla, etMetodo, metodo, etReembolso, reembolso, nota, error);
        contenido.setPadding(new Insets(4, 0, 0, 0));
        contenido.setPrefWidth(560);
        recalcular.run();

        ButtonType confirmar = new ButtonType("Devolver", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Devolver de la venta " + venta.getNumero());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirmar);

        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(confirmar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                spinners.values().forEach(s -> s.increment(0));   // aplica lo escrito a mano en cada casilla
                String codigo = METODOS[metodo.getSelectionModel().getSelectedIndex()][0];
                BigDecimal monto = servicio.devolver(Sesion.usuario(), venta, cantidades(spinners), codigo);
                Mensajes.info("Devolución hecha", "Se devolvieron " + Dinero.formato(monto) + ".");
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

    private static Map<Integer, Integer> cantidades(Map<Integer, Spinner<Integer>> spinners) {
        Map<Integer, Integer> mapa = new LinkedHashMap<>();
        spinners.forEach((id, s) -> mapa.put(id, s.getValue() == null ? 0 : s.getValue()));
        return mapa;
    }

    private static Label titulo(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("etiqueta-campo");
        l.setAlignment(Pos.CENTER_LEFT);
        return l;
    }
}
