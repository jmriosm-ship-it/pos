package com.pos.ui;

import com.pos.modelo.Pago;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;
import com.pos.servicio.ReciboService;
import com.pos.servicio.VentaService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Pestaña "Ventas": todas las ventas del periodo (también las anuladas). Se puede ver el recibo de una
 * y, si eres administrador, anularla (los productos vuelven al inventario).
 * Teclas: F5 cambiar filtro · F7 ver recibo · F9 anular · flechas para moverse.
 */
public class VentasHistorialPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VentaService ventaService = new VentaService();
    private final Usuario usuario = Sesion.usuario();
    private final PeriodoReporte periodo;

    private final TableView<Venta> tabla = new TableView<>();
    private final ToggleGroup filtro = new ToggleGroup();
    private final Label contador = new Label();
    private List<Venta> todas = List.of();

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public VentasHistorialPanel(PeriodoReporte periodo) {
        this.periodo = periodo;
        setSpacing(12);

        HBox chips = new HBox(6,
                Chips.crear("Todas", "TODAS", filtro, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")),
                Chips.crear("Completadas", "COMPLETADA", filtro, new ColoresCategoria.Color("#15803d", "#dcfce7", "#166534")),
                Chips.crear("Anuladas", "ANULADA", filtro, new ColoresCategoria.Color("#6b7280", "#f3f4f6", "#374151")));
        chips.setAlignment(Pos.CENTER_LEFT);
        filtro.selectToggle(filtro.getToggles().get(0));
        Chips.alElegir(filtro, this::filtrar);

        contador.getStyleClass().add("texto-suave");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, chips, contador, espacio, boton("Ver recibo", "F7", "btn-secundario", this::verRecibo));
        if (usuario.esAdmin()) {   // solo el administrador anula ventas
            barra.getChildren().add(boton("Anular", "F9", "btn-peligro", this::anular));
        }
        barra.setAlignment(Pos.CENTER_LEFT);

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F5", "Cambiar filtro"), Atajos.etiqueta("↑ ↓", "Moverse"),
                Atajos.etiqueta("F7", "Ver recibo"));
        if (usuario.esAdmin()) {
            teclas.getChildren().add(Atajos.etiqueta("F9", "Anular venta"));
        }
        getChildren().addAll(barra, tabla, teclas);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        periodo.alCambiar(this::recargar);
        recargar();
    }

    private Button boton(String texto, String tecla, String estilo, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add(estilo);
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay ventas en este periodo con ese filtro."));
        TableColumn<Venta, String> estado = Tablas.columna("Estado", 110,
                (Venta v) -> "ANULADA".equals(v.getEstado()) ? "Anulada" : "Completada");
        Tablas.colorear(estado, v -> "ANULADA".equals(v.getEstado()) ? "texto-suave" : "texto-ok");

        tabla.getColumns().add(Tablas.columna("Venta", 80, (Venta v) -> "#" + v.getId()));
        tabla.getColumns().add(Tablas.columna("Fecha", 150, (Venta v) -> v.getFecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Cliente", 200, (Venta v) -> v.getNombreCliente()));
        tabla.getColumns().add(Tablas.columna("Vendió", 150, (Venta v) -> v.getNombreUsuario()));
        tabla.getColumns().add(Tablas.columna("Total", 110, (Venta v) -> Dinero.formato(v.getTotal())));
        tabla.getColumns().add(Tablas.columna("Pago", 110, (Venta v) -> nombreMetodo(v.getMetodoPago())));
        tabla.getColumns().add(estado);
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                verRecibo();
            }
        });
    }

    private String nombreMetodo(String metodo) {
        return "MIXTO".equals(metodo) ? "Mixto" : new Pago(metodo, null).etiqueta();
    }

    // ---------------------------------------------------------------- datos

    public void recargar() {
        try {
            todas = ventaService.listar(periodo.desde(), periodo.hasta()).stream()
                    .sorted(Comparator.comparingInt(Venta::getId).reversed())
                    .toList();
            filtrar();
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar el historial de ventas.\n" + e.getMessage());
        }
    }

    private void filtrar() {
        String estado = (String) Chips.valor(filtro);
        Venta previa = tabla.getSelectionModel().getSelectedItem();
        List<Venta> visibles = todas.stream()
                .filter(v -> "TODAS".equals(estado) || v.getEstado().equals(estado))
                .toList();
        tabla.getItems().setAll(visibles);
        if (previa != null) {
            visibles.stream().filter(v -> v.getId() == previa.getId()).findFirst()
                    .ifPresent(v -> tabla.getSelectionModel().select(v));
        }
        contador.setText(visibles.size() + (visibles.size() == 1 ? " venta" : " ventas"));
    }

    // ---------------------------------------------------------------- acciones

    private void tecla(KeyEvent e) {
        switch (e.getCode()) {
            case F5 -> Chips.siguiente(filtro);
            case F7 -> verRecibo();
            case F9 -> anular();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private Venta elegida() {
        Venta v = tabla.getSelectionModel().getSelectedItem();
        if (v == null) {
            Mensajes.info("Elige una venta", "Muévete a la tabla con la flecha ↓ y elige una venta.");
        }
        return v;
    }

    private void verRecibo() {
        Venta v = elegida();
        if (v == null) {
            return;
        }
        try {
            Venta completa = ventaService.detalle(v.getId());
            TicketDialog.mostrar("Recibo de la venta #" + v.getId(), new ReciboService().generar(completa));
        } catch (SQLException e) {
            Mensajes.error("No se pudo mostrar el recibo.\n" + e.getMessage());
        }
    }

    private void anular() {
        if (!usuario.esAdmin()) {
            return;
        }
        Venta v = elegida();
        if (v == null) {
            return;
        }
        if ("ANULADA".equals(v.getEstado())) {
            Mensajes.info("Ya está anulada", "La venta #" + v.getId() + " ya estaba anulada.");
            return;
        }
        if (!Mensajes.confirmar("Anular venta", "¿Anular la venta #" + v.getId() + " por "
                + Dinero.formato(v.getTotal()) + "?\nLos productos vuelven al inventario.")) {
            return;
        }
        try {
            ventaService.anular(usuario, v);
            recargar();
        } catch (IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error(e.getMessage());   // por ejemplo: "tiene parte fiada y ya hay abonos"
        }
    }
}
