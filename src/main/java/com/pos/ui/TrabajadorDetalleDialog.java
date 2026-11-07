package com.pos.ui;

import com.pos.dao.NominaDAO;
import com.pos.modelo.Usuario;
import com.pos.servicio.NominaService;
import com.pos.util.Dinero;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Lo que ha sacado y lo que se le ha pagado a un trabajador, y las cajas que ha abierto este mes. Supr anula un consumo. */
public class TrabajadorDetalleDialog {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TrabajadorDetalleDialog() {
    }

    public static void abrir(Usuario u) {
        NominaService servicio = new NominaService();
        TableView<NominaDAO.Mov> consumos = new TableView<>();
        consumos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        consumos.setPlaceholder(new Label("Todavía no hay consumos ni pagos."));
        consumos.getColumns().add(Tablas.columna("Fecha", 130, (NominaDAO.Mov m) -> m.fecha().format(FECHA)));
        consumos.getColumns().add(Tablas.columna("Tipo", 90, (NominaDAO.Mov m) -> "CONSUMO".equals(m.tipo()) ? "Consumo" : "Sueldo"));
        consumos.getColumns().add(Tablas.columna("Detalle", 260, (NominaDAO.Mov m) -> m.concepto() + (m.anulado() ? "  (anulado)" : "")));
        consumos.getColumns().add(Tablas.columna("Valor", 110, (NominaDAO.Mov m) -> Dinero.formato(m.valor())));
        consumos.getColumns().add(Tablas.columna("Descuento", 110,
                (NominaDAO.Mov m) -> m.descuento() == null ? "" : Dinero.formato(m.descuento())));
        consumos.setPrefHeight(220);

        TableView<NominaDAO.CajaTurno> cajas = new TableView<>();
        cajas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cajas.setPlaceholder(new Label("No abrió cajas este mes."));
        cajas.getColumns().add(Tablas.columna("Caja", 70, (NominaDAO.CajaTurno c) -> "#" + c.id()));
        cajas.getColumns().add(Tablas.columna("Abrió", 130, (NominaDAO.CajaTurno c) -> c.abierta().format(FECHA)));
        cajas.getColumns().add(Tablas.columna("Cerró", 130, (NominaDAO.CajaTurno c) -> c.cerrada() == null ? "Abierta" : c.cerrada().format(FECHA)));
        cajas.getColumns().add(Tablas.columna("Empezó con", 120, (NominaDAO.CajaTurno c) -> Dinero.formato(c.inicial())));
        cajas.getColumns().add(Tablas.columna("Terminó con", 120,
                (NominaDAO.CajaTurno c) -> c.contado() == null ? "" : Dinero.formato(c.contado())));
        cajas.setPrefHeight(150);

        Runnable cargar = () -> {
            try {
                consumos.getItems().setAll(servicio.movimientos(u));
                LocalDate hoy = LocalDate.now();
                cajas.getItems().setAll(servicio.cajas(u, hoy.withDayOfMonth(1), hoy));
            } catch (SQLException e) {
                Mensajes.error("No se pudo leer el detalle.\n" + e.getMessage());
            }
        };
        cargar.run();
        consumos.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                NominaDAO.Mov m = consumos.getSelectionModel().getSelectedItem();
                if (m != null && "CONSUMO".equals(m.tipo()) && !m.anulado()
                        && Mensajes.confirmar("Anular consumo", "¿Anular \"" + m.concepto() + "\" por " + Dinero.formato(m.valor()) + "?")) {
                    try {
                        servicio.anularConsumo(Sesion.usuario(), m.id());
                        cargar.run();
                    } catch (IllegalStateException | SQLException ex) {
                        Mensajes.error(ex.getMessage());
                    }
                }
                e.consume();
            }
        });

        Label t1 = new Label("Consumos y sueldos  (Supr anula un consumo)");
        t1.getStyleClass().add("linea-nombre");
        Label t2 = new Label("Cajas de este mes");
        t2.getStyleClass().add("linea-nombre");
        VBox contenido = new VBox(8, t1, consumos, t2, cajas);
        VBox.setVgrow(consumos, Priority.ALWAYS);
        contenido.setPrefWidth(760);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(u.getNombre());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.showAndWait();
    }
}
