package com.pos.ui;

import com.pos.modelo.Movimiento;
import com.pos.modelo.Producto;
import com.pos.servicio.InventarioService;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Todo lo que le ha pasado a un producto: compras, ventas, ajustes y devoluciones, del más reciente al más antiguo. */
public class HistorialProductoDialog {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private HistorialProductoDialog() {
    }

    public static void abrir(Producto producto) {
        List<Movimiento> movimientos;
        try {
            movimientos = new InventarioService().historialDeProducto(producto);
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar el historial.\n" + e.getMessage());
            return;
        }
        TableView<Movimiento> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Este producto aún no tiene movimientos."));
        tabla.getColumns().add(Tablas.columna("Fecha", 140, (Movimiento m) -> m.getFecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Tipo", 80, (Movimiento m) -> switch (m.getTipo()) {
            case "ENTRADA" -> "Entrada";
            case "SALIDA" -> "Salida";
            default -> "Ajuste";
        }));
        tabla.getColumns().add(Tablas.columna("Cant.", 70, (Movimiento m) ->
                ("SALIDA".equals(m.getTipo()) ? "-" : m.getCantidad() > 0 ? "+" : "") + m.getCantidad()));
        tabla.getColumns().add(Tablas.columna("Motivo", 240, (Movimiento m) -> m.getMotivo() == null ? "" : m.getMotivo()));
        tabla.getColumns().add(Tablas.columna("Hecho por", 130, (Movimiento m) -> m.getNombreUsuario()));
        tabla.getItems().setAll(movimientos);
        tabla.setPrefSize(680, 380);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Historial de " + producto.getNombre() + "  ·  stock actual " + producto.getStock());
        dialogo.getDialogPane().setContent(tabla);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.showAndWait();
    }
}
