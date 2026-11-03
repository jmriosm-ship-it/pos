package com.pos.ui;

import com.pos.modelo.Producto;
import com.pos.servicio.ProductoService;
import com.pos.servicio.ReporteService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pestaña "Existencias" (diseño A): cuatro números arriba, filtros y la tabla de productos con su
 * estado en color (OK / Bajo / Agotado). Desde aquí se ajusta el stock después de un conteo y se
 * salta a comprar un producto.
 * Teclas: F2 buscar · F5 ajustar stock · F6 cambiar filtro · F7 comprar · Enter (en la tabla) ajustar.
 */
public class ExistenciasPanel extends VBox {

    private static final int TODOS = 0, BAJO = 1, AGOTADOS = 2;

    private final ProductoService productoService = new ProductoService();
    private final ReporteService reporteService = new ReporteService();
    private final boolean esAdmin = Sesion.usuario().esAdmin();
    private final Consumer<Producto> alComprar;

    private final TextField buscador = new TextField();
    private final TableView<Producto> tabla = new TableView<>();
    private final ToggleGroup filtro = new ToggleGroup();
    private final Label lblProductos = new Label();
    private final Label lblBajo = new Label();
    private final Label lblAgotados = new Label();
    private final Label lblValor = new Label();
    private List<Producto> todos = List.of();

    // Las teclas se escuchan en toda la ventana mientras esta pestaña está a la vista.
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public ExistenciasPanel(Consumer<Producto> alComprar) {
        this.alComprar = alComprar;
        setSpacing(12);

        // Los cuatro números de arriba (el valor del inventario al costo solo lo ve el administrador)
        HBox numeros = new HBox(10, mini("Productos", lblProductos), mini("Stock bajo", lblBajo), mini("Agotados", lblAgotados));
        lblBajo.getStyleClass().add("texto-aviso");
        lblAgotados.getStyleClass().add("texto-peligro");
        if (esAdmin) {
            numeros.getChildren().add(mini("Valor al costo", lblValor));
        }

        // Filtros y botones
        buscador.setPromptText("Buscar producto (F2)");
        buscador.setPrefWidth(280);
        buscador.textProperty().addListener((o, antes, ahora) -> filtrar());
        // Con la flecha abajo o Enter se salta de la búsqueda a la tabla.
        buscador.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.ENTER) {
                irATabla();
                e.consume();
            }
        });
        HBox chips = new HBox(6,
                Chips.crear("Todos", TODOS, filtro, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")),
                Chips.crear("Stock bajo", BAJO, filtro, new ColoresCategoria.Color("#b45309", "#fef3c7", "#92400e")),
                Chips.crear("Agotados", AGOTADOS, filtro, new ColoresCategoria.Color("#b91c1c", "#fee2e2", "#991b1b")));
        chips.setAlignment(Pos.CENTER_LEFT);
        filtro.selectToggle(filtro.getToggles().get(0));
        Chips.alElegir(filtro, this::filtrar);

        Button ajustar = boton("Ajustar stock", "F5", "btn-secundario", this::ajustar);
        Button comprar = boton("Comprar", "F7", "btn-primario", this::comprar);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, buscador, chips, espacio, ajustar, comprar);
        barra.setAlignment(Pos.CENTER_LEFT);

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Buscar"), Atajos.etiqueta("F6", "Cambiar filtro"),
                Atajos.etiqueta("F5", "Ajustar stock"), Atajos.etiqueta("F7", "Comprar"),
                Atajos.etiqueta("↑ ↓", "Moverse en la tabla"), Atajos.etiqueta("Enter", "Ajustar el elegido"));

        getChildren().addAll(numeros, barra, tabla, teclas);

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

    // ---------------------------------------------------------------- diseño

    private VBox mini(String etiqueta, Label valor) {
        Label e = new Label(etiqueta);
        e.getStyleClass().add("metrica-etiqueta");
        valor.getStyleClass().add("mini-valor");
        VBox caja = new VBox(2, e, valor);
        caja.getStyleClass().add("mini");
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setPrefWidth(10);
        return caja;
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
        tabla.setPlaceholder(new Label("No hay productos con ese filtro."));

        TableColumn<Producto, String> stock = Tablas.columna("Stock", 90, (Producto p) -> String.valueOf(p.getStock()));
        TableColumn<Producto, String> estado = Tablas.columna("Estado", 110, (Producto p) -> estado(p));
        Tablas.colorear(stock, ExistenciasPanel::estilo);
        Tablas.colorear(estado, ExistenciasPanel::estilo);

        tabla.getColumns().add(Tablas.columna("Código", 100, (Producto p) -> p.getCodigo()));
        tabla.getColumns().add(Tablas.columna("Producto", 300, (Producto p) -> p.getNombre()));
        tabla.getColumns().add(stock);
        tabla.getColumns().add(Tablas.columna("Mínimo", 90, (Producto p) -> String.valueOf(p.getStockMinimo())));
        tabla.getColumns().add(estado);
        if (esAdmin) {   // los costos solo los ve el administrador
            tabla.getColumns().add(Tablas.columna("Costo", 110, (Producto p) -> Dinero.formato(p.getPrecioCompra())));
            tabla.getColumns().add(Tablas.columna("Valor", 130, (Producto p) ->
                    Dinero.formato(p.getPrecioCompra().multiply(BigDecimal.valueOf(p.getStock())))));
        }
        // Enter sobre una fila = ajustar su stock; doble clic también.
        tabla.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                ajustar();
                e.consume();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ajustar();
            }
        });
    }

    // ---------------------------------------------------------------- estado de un producto

    private static String estado(Producto p) {
        return p.getStock() <= 0 ? "Agotado" : p.getStock() <= p.getStockMinimo() ? "Bajo" : "OK";
    }

    private static String estilo(Producto p) {
        return p.getStock() <= 0 ? "texto-peligro" : p.getStock() <= p.getStockMinimo() ? "texto-aviso" : "texto-ok";
    }

    // ---------------------------------------------------------------- datos

    // Vuelve a leer los productos y los números de arriba (se llama al entrar a la pestaña).
    public void recargar() {
        try {
            todos = productoService.buscar("");
            long bajos = todos.stream().filter(p -> p.getStock() > 0 && p.getStock() <= p.getStockMinimo()).count();
            long agotados = todos.stream().filter(p -> p.getStock() <= 0).count();
            lblProductos.setText(String.valueOf(todos.size()));
            lblBajo.setText(String.valueOf(bajos));
            lblAgotados.setText(String.valueOf(agotados));
            if (esAdmin) {
                lblValor.setText(Dinero.formato(reporteService.valorInventarioAlCosto()));
            }
            filtrar();
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar las existencias.\n" + e.getMessage());
        }
    }

    // Aplica el buscador y el chip elegido sobre la lista ya cargada.
    private void filtrar() {
        String texto = buscador.getText() == null ? "" : buscador.getText().trim().toLowerCase();
        int modo = (Integer) Chips.valor(filtro);
        List<Producto> visibles = todos.stream()
                .filter(p -> p.getNombre().toLowerCase().contains(texto) || p.getCodigo().toLowerCase().contains(texto))
                .filter(p -> modo == TODOS
                        || (modo == BAJO && p.getStock() > 0 && p.getStock() <= p.getStockMinimo())
                        || (modo == AGOTADOS && p.getStock() <= 0))
                .toList();
        // Al volver a llenar la tabla se pierde la fila elegida: se recuerda y se vuelve a elegir
        // (así, después de ajustar un producto, F7 "Comprar" todavía sabe de cuál se trata).
        Producto previo = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().setAll(visibles);
        if (previo != null) {
            visibles.stream().filter(p -> p.getId() == previo.getId()).findFirst()
                    .ifPresent(p -> tabla.getSelectionModel().select(p));
        }
    }

    // ---------------------------------------------------------------- acciones y teclas

    private void tecla(KeyEvent e) {
        switch (e.getCode()) {
            case F2 -> {
                buscador.requestFocus();
                buscador.selectAll();
            }
            case F5 -> ajustar();
            case F6 -> Chips.siguiente(filtro);
            case F7 -> comprar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void irATabla() {
        tabla.requestFocus();
        if (tabla.getSelectionModel().isEmpty()) {
            tabla.getSelectionModel().selectFirst();
        }
    }

    private Producto elegido() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) {
            Mensajes.info("Elige un producto", "Muévete a la tabla con la flecha ↓ y elige un producto.");
        }
        return p;
    }

    private void ajustar() {
        Producto p = elegido();
        if (p != null && AjusteDialog.abrir(p)) {
            recargar();
        }
    }

    private void comprar() {
        Producto p = elegido();
        if (p != null) {
            alComprar.accept(p);
        }
    }
}
