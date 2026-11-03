package com.pos.ui;

import com.pos.dao.ProductoDAO;
import com.pos.modelo.Compra;
import com.pos.modelo.Producto;
import com.pos.modelo.Proveedor;
import com.pos.modelo.Usuario;
import com.pos.servicio.CompraService;
import com.pos.servicio.ProductoService;
import com.pos.servicio.ProveedorService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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

/**
 * Pestaña "Nueva compra" (diseño A): se elige el proveedor y se cargan los productos por teclado,
 * línea por línea: producto → Enter → cantidad → Enter → costo → Enter (se agrega la línea).
 * Al confirmar sube el stock de cada producto y su costo queda actualizado.
 * Teclas: F2 producto · F3 proveedor · F12 confirmar · Supr quitar la línea elegida · Esc cancelar la línea.
 */
public class CompraPanel extends VBox {

    // Una línea de la compra que se está armando.
    public static class Linea {
        final Producto producto;
        int cantidad;
        BigDecimal costo;

        Linea(Producto producto, int cantidad, BigDecimal costo) {
            this.producto = producto;
            this.cantidad = cantidad;
            this.costo = costo;
        }

        BigDecimal subtotal() {
            return costo.multiply(BigDecimal.valueOf(cantidad));
        }
    }

    private final CompraService compraService = new CompraService();
    private final ProveedorService proveedorService = new ProveedorService();
    private final ProductoService productoService = new ProductoService();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final Usuario usuario = Sesion.usuario();

    private final ComboBox<Proveedor> comboProveedor = new ComboBox<>();
    private final TextField buscador = new TextField();
    private final ListView<Producto> sugerencias = new ListView<>();
    private final Label lblElegido = new Label();
    private final Label lblError = new Label();
    private final TextField cantidad = new TextField();
    private final TextField costo = CamposDinero.crear();
    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private final TableView<Linea> tabla = new TableView<>(lineas);
    private final Label lblTotal = new Label();
    private final Label lblResumen = new Label();
    private final Button confirmar = new Button("Confirmar compra");
    private final HBox aviso = new HBox(12);

    private Producto elegido;            // el producto que se está cargando ahora
    private boolean sinSugerir = false;  // para no buscar sugerencias cuando el programa llena el campo

    // Las teclas se escuchan en toda la ventana mientras esta pestaña está a la vista.
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public CompraPanel() {
        setSpacing(12);

        // Aviso verde de "compra registrada" (oculto hasta que se confirme una)
        aviso.getStyleClass().add("aviso-ok");
        aviso.setAlignment(Pos.CENTER_LEFT);
        aviso.setVisible(false);
        aviso.setManaged(false);

        HBox cuerpo = new HBox(14, crearColumnaIzquierda(), crearResumen());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        getChildren().addAll(aviso, cuerpo);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        actualizarTotales();
        recargar();
    }

    // ---------------------------------------------------------------- diseño

    private VBox crearColumnaIzquierda() {
        // Proveedor
        comboProveedor.setPromptText("Elige el proveedor");
        comboProveedor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(comboProveedor, Priority.ALWAYS);
        Label etiquetaProveedor = new Label("Proveedor");
        etiquetaProveedor.getStyleClass().add("texto-suave");
        HBox filaProveedor = new HBox(10, etiquetaProveedor, Atajos.tecla("F3"), comboProveedor);
        filaProveedor.setAlignment(Pos.CENTER_LEFT);
        VBox cardProveedor = new VBox(filaProveedor);
        cardProveedor.getStyleClass().add("card");

        // Producto, cantidad y costo
        buscador.setPromptText("Código o nombre");
        buscador.textProperty().addListener((o, antes, ahora) -> {
            if (!sinSugerir) {
                elegido = null;
                lblElegido.setText("");
                buscarSugerencias(ahora);
            }
        });
        buscador.setOnAction(e -> alEnterEnProducto());
        buscador.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && !sugerencias.getItems().isEmpty()) {
                sugerencias.requestFocus();
                sugerencias.getSelectionModel().selectFirst();
                e.consume();
            }
        });

        sugerencias.setPrefHeight(150);
        sugerencias.setMaxHeight(150);
        sugerencias.getStyleClass().add("lista-categorias");
        sugerencias.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                setText(vacio || p == null ? null : p.getCodigo() + "  ·  " + p.getNombre() + "   (stock " + p.getStock() + ")");
            }
        });
        sugerencias.setVisible(false);
        sugerencias.setManaged(false);
        sugerencias.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && sugerencias.getSelectionModel().getSelectedItem() != null) {
                elegir(sugerencias.getSelectionModel().getSelectedItem());
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                buscador.requestFocus();
                e.consume();
            }
        });
        sugerencias.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && sugerencias.getSelectionModel().getSelectedItem() != null) {
                elegir(sugerencias.getSelectionModel().getSelectedItem());
            }
        });

        cantidad.setPromptText("0");
        cantidad.setPrefWidth(90);
        // Solo números enteros.
        cantidad.textProperty().addListener((o, antes, ahora) -> {
            String limpio = ahora.replaceAll("[^0-9]", "");
            if (!limpio.equals(ahora)) {
                cantidad.setText(limpio);
            }
        });
        cantidad.setOnAction(e -> {
            if (elegido == null) {
                lblError.setText("Primero elige un producto.");
                buscador.requestFocus();
            } else if (cantidad.getText().isBlank() || Integer.parseInt(cantidad.getText()) <= 0) {
                lblError.setText("Escribe cuántas unidades llegaron.");
            } else {
                lblError.setText("");
                costo.requestFocus();
                costo.selectAll();
            }
        });
        costo.setPrefWidth(140);
        // El campo de dinero es grande para el cobro; aquí se deja del mismo tamaño que los demás campos.
        costo.setStyle("-fx-font-size: 13px; -fx-font-weight: normal; -fx-padding: 8 10;");
        costo.setOnAction(e -> agregarLinea());

        VBox colProducto = campo("Producto", Atajos.tecla("F2"), new VBox(4, buscador, sugerencias));
        HBox.setHgrow(colProducto, Priority.ALWAYS);
        HBox entrada = new HBox(10, colProducto, campo("Cantidad", null, cantidad), campo("Costo por unidad", null, costo));
        entrada.setAlignment(Pos.TOP_LEFT);

        lblElegido.getStyleClass().add("texto-suave");
        lblError.getStyleClass().add("error-texto");
        lblError.managedProperty().bind(lblError.textProperty().isNotEmpty());
        Label ayuda = new Label("Enter pasa de un campo al siguiente; en el costo, Enter agrega la línea.");
        ayuda.getStyleClass().addAll("texto-suave", "texto-pequeno");
        VBox cardEntrada = new VBox(8, entrada, lblElegido, lblError, ayuda);
        cardEntrada.getStyleClass().add("card");

        // Líneas de la compra
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Aún no hay productos en esta compra."));
        tabla.getColumns().add(Tablas.columna("Producto", 280, (Linea l) -> l.producto.getNombre()));
        tabla.getColumns().add(Tablas.columna("Cant.", 70, (Linea l) -> String.valueOf(l.cantidad)));
        tabla.getColumns().add(Tablas.columna("Costo", 110, (Linea l) -> Dinero.formato(l.costo)));
        tabla.getColumns().add(Tablas.columna("Subtotal", 120, (Linea l) -> Dinero.formato(l.subtotal())));
        tabla.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                quitarElegida();
                e.consume();
            }
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Producto"), Atajos.etiqueta("F3", "Proveedor"),
                Atajos.etiqueta("Supr", "Quitar línea"), Atajos.etiqueta("Esc", "Cancelar la línea"),
                Atajos.etiqueta("F12", "Confirmar"));

        VBox columna = new VBox(10, cardProveedor, cardEntrada, tabla, teclas);
        HBox.setHgrow(columna, Priority.ALWAYS);
        return columna;
    }

    // Un campo con su etiqueta (y, si se da, su tecla) encima.
    private VBox campo(String titulo, javafx.scene.Node tecla, javafx.scene.Node contenido) {
        Label l = new Label(titulo);
        l.getStyleClass().add("etiqueta-campo");
        HBox cabecera = tecla == null ? new HBox(l) : new HBox(6, l, tecla);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        VBox caja = new VBox(4, cabecera, contenido);
        caja.setMaxWidth(Double.MAX_VALUE);
        return caja;
    }

    private VBox crearResumen() {
        Label etiqueta = new Label("Total de la compra");
        etiqueta.getStyleClass().add("texto-suave");
        lblTotal.getStyleClass().add("total-valor");
        lblResumen.getStyleClass().add("texto-suave");

        confirmar.getStyleClass().addAll("btn-primario", "btn-grande");
        confirmar.setGraphic(Atajos.tecla("F12"));
        confirmar.setContentDisplay(ContentDisplay.RIGHT);
        confirmar.setGraphicTextGap(12);
        confirmar.setMaxWidth(Double.MAX_VALUE);
        confirmar.setFocusTraversable(false);
        confirmar.setOnAction(e -> confirmar());

        Region relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        VBox resumen = new VBox(8, etiqueta, lblTotal, lblResumen, relleno, confirmar);
        resumen.getStyleClass().add("card");
        resumen.setPrefWidth(300);
        resumen.setMinWidth(300);
        resumen.setMaxWidth(300);
        return resumen;
    }

    // ---------------------------------------------------------------- datos

    // Se llama al entrar a la pestaña: vuelve a leer los proveedores (conserva el elegido).
    public void recargar() {
        try {
            Proveedor actual = comboProveedor.getValue();
            comboProveedor.getItems().setAll(proveedorService.listar());
            if (actual != null) {
                comboProveedor.getItems().stream().filter(p -> p.getId() == actual.getId()).findFirst()
                        .ifPresent(comboProveedor::setValue);
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los proveedores.\n" + e.getMessage());
        }
    }

    // Lo llama Existencias al pulsar "Comprar": deja el producto listo para poner la cantidad.
    public void agregarDesdeExistencias(Producto p) {
        elegir(p);
    }

    // ---------------------------------------------------------------- cargar un producto

    private void buscarSugerencias(String texto) {
        List<Producto> lista = List.of();
        if (texto != null && !texto.isBlank()) {
            try {
                lista = productoService.buscar(texto.trim()).stream().limit(6).toList();
            } catch (SQLException e) {
                Mensajes.error("No se pudo buscar el producto.\n" + e.getMessage());
            }
        }
        sugerencias.getItems().setAll(lista);
        sugerencias.setVisible(!lista.isEmpty());
        sugerencias.setManaged(!lista.isEmpty());
    }

    // Enter en el campo de producto: si es un código exacto o solo hay una sugerencia, se elige;
    // si hay varias, el cursor pasa a la lista para escoger con las flechas.
    private void alEnterEnProducto() {
        String texto = buscador.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        try {
            Producto exacto = productoDAO.buscarPorCodigo(texto);
            if (exacto != null) {
                elegir(exacto);
            } else if (sugerencias.getItems().size() == 1) {
                elegir(sugerencias.getItems().get(0));
            } else if (sugerencias.getItems().size() > 1) {
                sugerencias.requestFocus();
                sugerencias.getSelectionModel().selectFirst();
            } else {
                lblError.setText("No se encontró ese producto.");
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo buscar el producto.\n" + e.getMessage());
        }
    }

    private void elegir(Producto p) {
        elegido = p;
        sinSugerir = true;
        buscador.setText(p.getNombre());
        sinSugerir = false;
        sugerencias.getItems().clear();
        sugerencias.setVisible(false);
        sugerencias.setManaged(false);
        lblError.setText("");
        lblElegido.setText(p.getCodigo() + "  ·  stock actual " + p.getStock() + "  ·  último costo " + Dinero.formato(p.getPrecioCompra()));
        cantidad.clear();
        costo.setText(Dinero.plano(p.getPrecioCompra()));   // el último costo ya viene escrito; se puede cambiar
        Platform.runLater(() -> {
            cantidad.requestFocus();
            cantidad.selectAll();
        });
    }

    // Enter en el costo: agrega la línea (si el producto ya estaba, suma la cantidad y deja el costo nuevo).
    private void agregarLinea() {
        if (elegido == null) {
            lblError.setText("Primero elige un producto.");
            buscador.requestFocus();
            return;
        }
        if (cantidad.getText().isBlank() || Integer.parseInt(cantidad.getText()) <= 0) {
            lblError.setText("Escribe cuántas unidades llegaron.");
            cantidad.requestFocus();
            return;
        }
        BigDecimal costoUnidad = CamposDinero.valor(costo);
        if (costoUnidad.signum() <= 0) {
            lblError.setText("El costo debe ser mayor a cero.");
            costo.requestFocus();
            return;
        }
        int unidades = Integer.parseInt(cantidad.getText());

        Linea existente = lineas.stream().filter(l -> l.producto.getId() == elegido.getId()).findFirst().orElse(null);
        if (existente != null) {
            existente.cantidad += unidades;
            existente.costo = costoUnidad;
            tabla.refresh();
        } else {
            lineas.add(new Linea(elegido, unidades, costoUnidad));
        }
        ocultarAviso();
        limpiarLinea();
        actualizarTotales();
        buscador.requestFocus();
    }

    // Deja los tres campos vacíos para la siguiente línea.
    private void limpiarLinea() {
        elegido = null;
        sinSugerir = true;
        buscador.clear();
        sinSugerir = false;
        cantidad.clear();
        costo.clear();
        lblElegido.setText("");
        lblError.setText("");
        sugerencias.getItems().clear();
        sugerencias.setVisible(false);
        sugerencias.setManaged(false);
    }

    private void quitarElegida() {
        Linea l = tabla.getSelectionModel().getSelectedItem();
        if (l != null) {
            lineas.remove(l);
            actualizarTotales();
        }
    }

    private void actualizarTotales() {
        BigDecimal total = BigDecimal.ZERO;
        int unidades = 0;
        for (Linea l : lineas) {
            total = total.add(l.subtotal());
            unidades += l.cantidad;
        }
        lblTotal.setText(Dinero.formato(total));
        lblResumen.setText(lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + "  ·  " + unidades
                + (unidades == 1 ? " unidad" : " unidades"));
        confirmar.setDisable(lineas.isEmpty());
    }

    // ---------------------------------------------------------------- confirmar

    private void confirmar() {
        if (comboProveedor.getValue() == null) {
            Mensajes.error("Elige el proveedor (F3) antes de confirmar la compra.");
            return;
        }
        if (lineas.isEmpty()) {
            return;
        }
        try {
            Compra compra = compraService.nuevaCompra(usuario, comboProveedor.getValue());
            int unidades = 0;
            for (Linea l : lineas) {
                compraService.agregarProducto(compra, l.producto.getCodigo(), l.cantidad, l.costo);
                unidades += l.cantidad;
            }
            compraService.finalizar(compra);
            mostrarAviso(compra, unidades, lineas.size());
            lineas.clear();
            limpiarLinea();
            actualizarTotales();
            buscador.requestFocus();
        } catch (IllegalArgumentException | IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo guardar la compra; no se cambió el inventario.\n" + e.getMessage());
        }
    }

    private void mostrarAviso(Compra compra, int unidades, int productos) {
        Label titulo = new Label("Compra #" + compra.getId() + " registrada");
        titulo.getStyleClass().add("aviso-titulo");
        Label detalle = new Label(unidades + (unidades == 1 ? " unidad" : " unidades") + " de " + productos
                + (productos == 1 ? " producto" : " productos") + "  ·  Total " + Dinero.formato(compra.getTotal())
                + "  ·  " + compra.getNombreProveedor() + "  ·  el stock ya subió");
        detalle.getStyleClass().add("aviso-detalle");
        Button cerrar = new Button("×");
        cerrar.getStyleClass().add("btn-mini");
        cerrar.setFocusTraversable(false);
        cerrar.setOnAction(e -> ocultarAviso());
        VBox texto = new VBox(2, titulo, detalle);
        HBox.setHgrow(texto, Priority.ALWAYS);
        aviso.getChildren().setAll(texto, cerrar);
        aviso.setPadding(new Insets(12, 16, 12, 16));
        aviso.setVisible(true);
        aviso.setManaged(true);
    }

    private void ocultarAviso() {
        aviso.setVisible(false);
        aviso.setManaged(false);
    }

    // ---------------------------------------------------------------- teclas

    private void tecla(KeyEvent e) {
        switch (e.getCode()) {
            case F2 -> {
                buscador.requestFocus();
                buscador.selectAll();
            }
            case F3 -> {
                comboProveedor.requestFocus();
                comboProveedor.show();
            }
            case F12 -> confirmar();
            case ESCAPE -> {
                // Esc solo cancela la línea en curso si no hay una lista abierta.
                if (comboProveedor.isShowing()) {
                    return;
                }
                limpiarLinea();
                buscador.requestFocus();
            }
            default -> {
                return;
            }
        }
        e.consume();
    }
}
