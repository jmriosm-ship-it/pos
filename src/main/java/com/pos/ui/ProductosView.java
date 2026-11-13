package com.pos.ui;

import com.pos.modelo.Accion;
import com.pos.modelo.Modulo;
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
        Button exportar = new Button("Exportar CSV");
        exportar.getStyleClass().add("btn-secundario");
        exportar.setGraphic(Atajos.tecla("F9"));
        exportar.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        exportar.setGraphicTextGap(8);
        exportar.setFocusTraversable(false);
        exportar.setOnAction(e -> exportarCsv());
        Button importar = new Button("Importar CSV");
        importar.getStyleClass().add("btn-secundario");
        importar.setGraphic(Atajos.tecla("F10"));
        importar.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        importar.setGraphicTextGap(8);
        importar.setFocusTraversable(false);
        importar.setOnAction(e -> importarCsv());
        HBox barra = new HBox(10, buscador, contador, espacio, exportar, importar, editar, desactivar, nuevo);
        barra.setAlignment(Pos.CENTER_LEFT);
        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, teclasCsv);
            }
            if (nueva != null) {
                nueva.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, teclasCsv);
            }
        });

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);
        getChildren().addAll(barra, tabla);
        cargar();
    }

    // F9 exportar · F10 importar (solo con esta pantalla a la vista).
    private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> teclasCsv = e -> {
        if (!Atajos.visible(this)) {
            return;
        }
        if (e.getCode() == javafx.scene.input.KeyCode.F9) {
            exportarCsv();
            e.consume();
        } else if (e.getCode() == javafx.scene.input.KeyCode.F10) {
            importarCsv();
            e.consume();
        }
    };

    // Guarda todos los productos en un archivo que se abre con Excel (también sirve de modelo para importar).
    private void exportarCsv() {
        javafx.stage.FileChooser selector = new javafx.stage.FileChooser();
        selector.setTitle("Guardar los productos");
        selector.setInitialFileName("productos_" + java.time.LocalDate.now() + ".csv");
        selector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivo para Excel (*.csv)", "*.csv"));
        java.io.File archivo = selector.showSaveDialog(getScene().getWindow());
        if (archivo == null) {
            return;
        }
        try {
            int n = new com.pos.servicio.ProductoCsvService().exportar(archivo.toPath());
            Mensajes.info("Productos guardados", (n == 0 ? "Se guardó un archivo de ejemplo (aún no hay productos)." : n + " productos guardados.")
                    + "\nEl archivo quedó en:\n" + archivo.getAbsolutePath()
                    + "\n\nÁbrelo con Excel, agrega o cambia productos y vuelve a subirlo con Importar CSV.");
        } catch (Exception ex) {
            Mensajes.error("No se pudo guardar el archivo.\n" + ex.getMessage());
        }
    }

    // Carga productos desde un archivo CSV: crea los nuevos y actualiza los que ya existen (por código).
    private void importarCsv() {
        if (!Seguridad.permitido(Modulo.PRODUCTOS, Accion.CREAR, "importar productos")) {
            return;
        }
        javafx.stage.FileChooser selector = new javafx.stage.FileChooser();
        selector.setTitle("Elegir el archivo de productos");
        selector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivo para Excel (*.csv)", "*.csv"));
        java.io.File archivo = selector.showOpenDialog(getScene().getWindow());
        if (archivo == null) {
            return;
        }
        if (!Mensajes.confirmar("Importar productos", "Se van a crear los productos nuevos y actualizar los que ya existan "
                + "(por código).\nEl stock de los que ya existen no se cambia.\n\n¿Seguir con \"" + archivo.getName() + "\"?")) {
            return;
        }
        try {
            var r = new com.pos.servicio.ProductoCsvService().importar(archivo.toPath());
            cargar();
            StringBuilder texto = new StringBuilder(r.creados() + " productos nuevos · " + r.actualizados() + " actualizados");
            if (!r.errores().isEmpty()) {
                texto.append("\n\n").append(r.errores().size()).append(" filas con problema (no se cargaron):");
                r.errores().stream().limit(12).forEach(x -> texto.append("\n• ").append(x));
                if (r.errores().size() > 12) {
                    texto.append("\n… y ").append(r.errores().size() - 12).append(" más.");
                }
            }
            Mensajes.info("Importación terminada", texto.toString());
        } catch (IllegalArgumentException ex) {
            Mensajes.error(ex.getMessage());
        } catch (Exception ex) {
            Mensajes.error("No se pudo importar el archivo.\n" + ex.getMessage());
        }
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
        if (!Seguridad.permitido(Modulo.PRODUCTOS, producto == null ? Accion.CREAR : Accion.EDITAR,
                producto == null ? "crear productos" : "editar productos")) {
            return;
        }
        if (ProductoDialog.abrir(producto)) {
            cargar();
        }
    }

    private void desactivar(Producto producto) {
        if (!Seguridad.permitido(Modulo.PRODUCTOS, Accion.BORRAR, "desactivar productos")) {
            return;
        }
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
