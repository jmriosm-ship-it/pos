package com.pos.ui;

import com.pos.dao.CreditoDAO;
import com.pos.servicio.CreditoService;
import com.pos.util.Dinero;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/** Todo de un crédito: sus datos, cada cuota (pagada, vencida...) y los pagos recibidos. */
public class CreditoDetalleDialog {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private CreditoDetalleDialog() {
    }

    public static void abrir(CreditoDAO.Fila c) {
        CreditoService servicio = new CreditoService();
        CreditoService.Estado e;
        java.util.List<CreditoDAO.Pago> pagos;
        try {
            e = servicio.estado(c);
            pagos = servicio.pagos(c.id());
        } catch (SQLException ex) {
            Mensajes.error("No se pudo leer el crédito.\n" + ex.getMessage());
            return;
        }

        Label titulo = new Label(c.cliente() + "  ·  C.C. " + (c.documento() == null ? "—" : c.documento()));
        titulo.getStyleClass().add("linea-nombre");
        Label datos = new Label(c.descripcion() + "\nVenta " + c.ventaNumero() + "  ·  financiado " + Dinero.formato(c.financiado())
                + "  ·  " + c.plazo() + " cuotas " + ("QUINCENAL".equals(c.frecuencia()) ? "quincenales" : "mensuales")
                + " de " + Dinero.formato(c.cuotaValor()) + "  ·  tasa " + c.tasa().toPlainString() + " %");
        datos.setWrapText(true);
        datos.getStyleClass().add("texto-suave");

        Label estado = new Label(e.texto() + "   ·   pagado " + Dinero.formato(e.pagado()) + "   ·   saldo " + Dinero.formato(e.saldo())
                + (e.mora().signum() > 0 ? "   ·   mora " + Dinero.formato(e.mora()) + " (" + e.diasMora() + " días)" : ""));
        estado.getStyleClass().add("linea-nombre");
        estado.getStyleClass().add(e.mora().signum() > 0 ? "texto-peligro" : "texto-ok");

        TableView<CreditoService.CuotaEstado> cuotas = new TableView<>();
        cuotas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cuotas.getColumns().add(Tablas.columna("Cuota", 70, (CreditoService.CuotaEstado q) -> String.valueOf(q.n())));
        cuotas.getColumns().add(Tablas.columna("Vence", 110, (CreditoService.CuotaEstado q) -> q.vence().format(FECHA)));
        cuotas.getColumns().add(Tablas.columna("Valor", 110, (CreditoService.CuotaEstado q) -> Dinero.formato(q.valor())));
        cuotas.getColumns().add(Tablas.columna("Pagado", 110, (CreditoService.CuotaEstado q) -> Dinero.formato(q.pagado())));
        cuotas.getColumns().add(Tablas.columna("Falta", 110, (CreditoService.CuotaEstado q) -> Dinero.formato(q.pendiente())));
        var colEstado = Tablas.columna("Estado", 100, (CreditoService.CuotaEstado q) -> q.estado());
        Tablas.colorear(colEstado, q -> switch (q.estado()) {
            case "Pagada" -> "texto-ok";
            case "Vencida" -> "texto-peligro";
            case "Parcial" -> "texto-aviso";
            default -> null;
        });
        cuotas.getColumns().add(colEstado);
        cuotas.getItems().setAll(e.cuotas());
        cuotas.setPrefHeight(230);

        TableView<CreditoDAO.Pago> tablaPagos = new TableView<>();
        tablaPagos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaPagos.setPlaceholder(new Label("Todavía no hay pagos."));
        tablaPagos.getColumns().add(Tablas.columna("Fecha", 150, (CreditoDAO.Pago p) -> p.fecha().format(FECHA_HORA)));
        tablaPagos.getColumns().add(Tablas.columna("Cómo", 120, (CreditoDAO.Pago p) -> new com.pos.modelo.Pago(p.metodo(), null).etiqueta()));
        tablaPagos.getColumns().add(Tablas.columna("Valor", 110, (CreditoDAO.Pago p) -> Dinero.formato(p.valor())));
        tablaPagos.getColumns().add(Tablas.columna("Recibió", 150, (CreditoDAO.Pago p) -> p.usuario()));
        tablaPagos.getItems().setAll(pagos);
        tablaPagos.setPrefHeight(120);

        Label t1 = new Label("Cuotas");
        t1.getStyleClass().add("linea-nombre");
        Label t2 = new Label("Pagos recibidos");
        t2.getStyleClass().add("linea-nombre");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        VBox contenido = new VBox(8, titulo, datos, estado, t1, cuotas, t2, tablaPagos);
        contenido.setPrefWidth(720);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Crédito de la venta " + c.ventaNumero());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.showAndWait();
    }
}
