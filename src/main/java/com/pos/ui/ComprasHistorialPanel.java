package com.pos.ui;

import com.pos.modelo.Compra;
import com.pos.servicio.CompraService;
import com.pos.util.Dinero;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Pestaña "Compras": las compras hechas a proveedores en los últimos 90 días, la más reciente primero. */
public class ComprasHistorialPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CompraService servicio = new CompraService();
    private final TableView<Compra> tabla = new TableView<>();
    private final Label contador = new Label();

    public ComprasHistorialPanel() {
        setSpacing(12);
        contador.getStyleClass().add("texto-suave");

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Aún no hay compras en los últimos 90 días."));
        tabla.getColumns().add(Tablas.columna("Compra", 90, (Compra c) -> "#" + c.getId()));
        tabla.getColumns().add(Tablas.columna("Fecha", 160, (Compra c) -> c.getFecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Proveedor", 300, (Compra c) -> c.getNombreProveedor()));
        tabla.getColumns().add(Tablas.columna("Total", 140, (Compra c) -> Dinero.formato(c.getTotal())));
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(contador, tabla);
        recargar();
    }

    // Se llama al entrar a la pestaña.
    public void recargar() {
        try {
            LocalDate hoy = LocalDate.now();
            List<Compra> compras = servicio.listar(hoy.minusDays(90), hoy);
            tabla.getItems().setAll(compras);
            contador.setText(compras.size() + (compras.size() == 1 ? " compra" : " compras") + " en los últimos 90 días");
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar las compras.\n" + e.getMessage());
        }
    }
}
