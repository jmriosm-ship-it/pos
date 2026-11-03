package com.pos.ui;

import com.pos.modelo.Movimiento;
import com.pos.servicio.InventarioService;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
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
import java.util.List;

/**
 * Pestaña "Movimientos" (diseño A): el historial del inventario. Cada vez que el stock cambia
 * (venta, compra, ajuste, anulación) queda aquí con la fecha, el motivo y quién lo hizo.
 * Tecla: F6 cambia el filtro (Todos / Entradas / Salidas / Ajustes).
 */
public class MovimientosPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventarioService servicio = new InventarioService();
    private final TableView<Movimiento> tabla = new TableView<>();
    private final ToggleGroup filtro = new ToggleGroup();
    private final Label contador = new Label();
    private List<Movimiento> todos = List.of();

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public MovimientosPanel() {
        setSpacing(12);

        HBox chips = new HBox(6,
                Chips.crear("Todos", "TODOS", filtro, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")),
                Chips.crear("Entradas", "ENTRADA", filtro, new ColoresCategoria.Color("#15803d", "#dcfce7", "#166534")),
                Chips.crear("Salidas", "SALIDA", filtro, new ColoresCategoria.Color("#b91c1c", "#fee2e2", "#991b1b")),
                Chips.crear("Ajustes", "AJUSTE", filtro, new ColoresCategoria.Color("#b45309", "#fef3c7", "#92400e")));
        chips.setAlignment(Pos.CENTER_LEFT);
        filtro.selectToggle(filtro.getToggles().get(0));
        Chips.alElegir(filtro, this::filtrar);

        contador.getStyleClass().add("texto-suave");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, chips, espacio, contador);
        barra.setAlignment(Pos.CENTER_LEFT);

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F6", "Cambiar filtro"), Atajos.etiqueta("↑ ↓", "Moverse"));
        getChildren().addAll(barra, tabla, teclas);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        recargar();
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Aún no hay movimientos con ese filtro."));

        TableColumn<Movimiento, String> tipo = Tablas.columna("Tipo", 90, (Movimiento m) -> nombreTipo(m.getTipo()));
        TableColumn<Movimiento, String> cantidad = Tablas.columna("Cant.", 70, (Movimiento m) -> cantidadConSigno(m));
        Tablas.colorear(tipo, m -> estilo(m.getTipo()));
        Tablas.colorear(cantidad, m -> estilo(m.getTipo()));

        tabla.getColumns().add(Tablas.columna("Fecha", 140, (Movimiento m) -> m.getFecha().format(FECHA)));
        tabla.getColumns().add(tipo);
        tabla.getColumns().add(cantidad);
        tabla.getColumns().add(Tablas.columna("Producto", 220, (Movimiento m) -> m.getNombreProducto()));
        tabla.getColumns().add(Tablas.columna("Motivo", 240, (Movimiento m) -> m.getMotivo() == null ? "" : m.getMotivo()));
        tabla.getColumns().add(Tablas.columna("Hecho por", 140, (Movimiento m) -> m.getNombreUsuario()));
    }

    private static String nombreTipo(String tipo) {
        return switch (tipo) {
            case "ENTRADA" -> "Entrada";
            case "SALIDA" -> "Salida";
            default -> "Ajuste";
        };
    }

    // Las entradas y salidas se guardan siempre en positivo; aquí se muestran con su signo.
    private static String cantidadConSigno(Movimiento m) {
        return switch (m.getTipo()) {
            case "ENTRADA" -> "+" + m.getCantidad();
            case "SALIDA" -> "−" + m.getCantidad();
            default -> (m.getCantidad() > 0 ? "+" : m.getCantidad() < 0 ? "−" : "") + Math.abs(m.getCantidad());
        };
    }

    private static String estilo(String tipo) {
        return switch (tipo) {
            case "ENTRADA" -> "texto-ok";
            case "SALIDA" -> "texto-peligro";
            default -> "texto-aviso";
        };
    }

    // Se llama al entrar a la pestaña: vuelve a leer los últimos movimientos.
    public void recargar() {
        try {
            todos = servicio.historialReciente(300);
            filtrar();
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar el historial.\n" + e.getMessage());
        }
    }

    private void filtrar() {
        String tipo = (String) Chips.valor(filtro);
        List<Movimiento> visibles = todos.stream()
                .filter(m -> "TODOS".equals(tipo) || m.getTipo().equals(tipo))
                .toList();
        tabla.getItems().setAll(visibles);
        contador.setText(visibles.size() + (visibles.size() == 1 ? " movimiento" : " movimientos") + " (los últimos 300)");
    }

    private void tecla(KeyEvent e) {
        if (e.getCode() == javafx.scene.input.KeyCode.F6) {
            Chips.siguiente(filtro);
            e.consume();
        }
    }
}
