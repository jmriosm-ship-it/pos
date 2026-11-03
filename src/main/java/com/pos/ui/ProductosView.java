package com.pos.ui;

import com.pos.modelo.Producto;
import com.pos.servicio.ProductoService;
import com.pos.util.Dinero;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/** Módulo Productos: tabla con buscador y botones para crear, editar y desactivar. */
public class ProductosView extends VBox {

    private final ProductoService servicio = new ProductoService();
    private final TableView<Producto> tabla = new TableView<>();
    private final TextField buscador = new TextField();
    private final Label contador = new Label();

    public ProductosView() {
        setSpacing(14);

        buscador.setPromptText("Buscar por nombre o código");
        buscador.getStyleClass().add("campo-buscar");
        buscador.setPrefWidth(340);
        buscador.textProperty().addListener((o, antes, ahora) -> cargar());

        Button nuevo = new Button("Nuevo producto", Iconos.crear(Iconos.MAS, 14));
        nuevo.getStyleClass().add("btn-primario");
        nuevo.getGraphic().setStyle(nuevo.getGraphic().getStyle() + "-fx-background-color: white;");
        nuevo.setOnAction(e -> abrirFormulario(null));

        Button editar = new Button("Editar");
        editar.getStyleClass().add("btn-secundario");
        editar.setOnAction(e -> abrirFormulario(tabla.getSelectionModel().getSelectedItem()));

        Button desactivar = new Button("Desactivar");
        desactivar.getStyleClass().add("btn-peligro");
        desactivar.setOnAction(e -> desactivar(tabla.getSelectionModel().getSelectedItem()));

        contador.getStyleClass().add("texto-suave");
        javafx.scene.layout.Region espacio = new javafx.scene.layout.Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, buscador, contador, espacio, editar, desactivar, nuevo);
        barra.setAlignment(Pos.CENTER_LEFT);

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);
        getChildren().addAll(barra, tabla);
        cargar();
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay productos. Crea el primero con \"Nuevo producto\"."));

        TableColumn<Producto, String> codigo = columna("Código", 130, p -> p.getCodigo());
        TableColumn<Producto, String> nombre = columna("Producto", 320, p -> p.getNombre());
        TableColumn<Producto, String> compra = columna("Precio compra", 130, p -> Dinero.formato(p.getPrecioCompra()));
        TableColumn<Producto, String> venta = columna("Precio venta", 130, p -> Dinero.formato(p.getPrecioVenta()));
        TableColumn<Producto, String> stock = columna("Stock", 90, p -> String.valueOf(p.getStock()));
        TableColumn<Producto, String> minimo = columna("Mínimo", 90, p -> String.valueOf(p.getStockMinimo()));

        // El stock se pinta en rojo cuando ya llegó al mínimo.
        stock.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio ? null : item);
                getStyleClass().remove("texto-peligro");
                Producto p = (vacio || getTableRow() == null) ? null : getTableRow().getItem();
                if (p != null && p.tieneStockBajo()) {
                    getStyleClass().add("texto-peligro");
                }
            }
        });

        tabla.getColumns().addAll(codigo, nombre, compra, venta, stock, minimo);
        // Doble clic en una fila = editar.
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                abrirFormulario(tabla.getSelectionModel().getSelectedItem());
            }
        });
    }

    // Crea una columna de texto sacando el valor de cada producto con la función "valor".
    private TableColumn<Producto, String> columna(String titulo, double ancho, java.util.function.Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        return c;
    }

    private void cargar() {
        try {
            tabla.getItems().setAll(servicio.buscar(buscador.getText()));
            int n = tabla.getItems().size();
            contador.setText(n == 1 ? "1 producto" : n + " productos");
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los productos.\n" + e.getMessage());
        }
    }

    private void abrirFormulario(Producto producto) {
        if (ProductoDialog.abrir(producto)) {
            cargar();
        }
    }

    private void desactivar(Producto producto) {
        if (producto == null) {
            Mensajes.info("Elige un producto", "Selecciona un producto de la tabla primero.");
            return;
        }
        if (!Mensajes.confirmar("Desactivar producto",
                "\"" + producto.getNombre() + "\" dejará de aparecer en ventas.\nSu historial se conserva.")) {
            return;
        }
        try {
            servicio.desactivar(producto);
            cargar();
        } catch (SQLException e) {
            Mensajes.error("No se pudo desactivar el producto.\n" + e.getMessage());
        }
    }
}
