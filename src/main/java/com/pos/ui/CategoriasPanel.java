package com.pos.ui;

import com.pos.modelo.Categoria;
import com.pos.modelo.Producto;
import com.pos.modelo.Usuario;
import com.pos.servicio.CategoriaService;
import com.pos.util.Dinero;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pestaña "Categorías" (diseño E): a la izquierda la lista de categorías con su número
 * de productos; a la derecha, los productos de la categoría elegida, con opciones para
 * renombrarla, eliminarla o pasar un producto a otra categoría.
 */
public class CategoriasPanel extends HBox {

    private final CategoriaService servicio = new CategoriaService();
    private final Usuario usuario = Sesion.usuario();

    // Columna izquierda
    private final ListView<Categoria> lista = new ListView<>();
    private final TextField campoNueva = new TextField();
    private final Label errorNueva = new Label();

    // Columna derecha
    private final Label titulo = new Label();
    private final Label resumen = new Label();
    private final TableView<Producto> tabla = new TableView<>();
    private final Button renombrar = new Button("Renombrar");
    private final Button eliminar = new Button("Eliminar");
    private final Button mover = new Button("Cambiar de categoría");

    private Categoria actual;   // la categoría que se está viendo a la derecha

    public CategoriasPanel() {
        setSpacing(16);
        VBox izquierda = crearColumnaIzquierda();
        VBox derecha = crearColumnaDerecha();
        HBox.setHgrow(derecha, Priority.ALWAYS);
        getChildren().addAll(izquierda, derecha);
        cargar(null);
    }

    // ---------------------------------------------------------------- izquierda

    private VBox crearColumnaIzquierda() {
        campoNueva.setPromptText("Nueva categoría");
        campoNueva.setOnAction(e -> agregarRapido());
        campoNueva.textProperty().addListener((o, antes, ahora) -> errorNueva.setText(""));
        HBox.setHgrow(campoNueva, Priority.ALWAYS);

        Button agregar = new Button("", Iconos.crear(Iconos.MAS, 14));
        agregar.getStyleClass().add("btn-primario");
        agregar.setOnAction(e -> agregarRapido());
        HBox filaNueva = new HBox(6, campoNueva, agregar);

        errorNueva.getStyleClass().add("error-texto");
        errorNueva.setWrapText(true);
        errorNueva.managedProperty().bind(errorNueva.textProperty().isNotEmpty());

        lista.getStyleClass().add("lista-categorias");
        lista.setPlaceholder(new Label("Aún no hay categorías.\nEscribe una arriba."));
        lista.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Categoria c, boolean vacio) {
                super.updateItem(c, vacio);
                if (vacio || c == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label nombre = new Label(c.getNombre());
                if (c.getId() == 0) {
                    nombre.getStyleClass().add("texto-suave");   // "Sin categoría" en gris
                }
                Label conteo = new Label(String.valueOf(c.getTotalProductos()));
                conteo.getStyleClass().addAll("pill", c.getTotalProductos() == 0 ? "pill-gris" : "pill-azul");
                Region espacio = new Region();
                HBox.setHgrow(espacio, Priority.ALWAYS);
                HBox fila = new HBox(8, nombre, espacio, conteo);
                fila.setAlignment(Pos.CENTER_LEFT);
                setGraphic(fila);
                setText(null);
            }
        });
        lista.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) -> {
            if (ahora != null) {
                mostrarDetalle(ahora);
            }
        });
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox columna = new VBox(8, filaNueva, errorNueva, lista);
        columna.getStyleClass().add("card");
        columna.setPrefWidth(320);
        columna.setMinWidth(320);
        columna.setMaxWidth(320);
        return columna;
    }

    // Agrega una categoría escribiendo su nombre y pulsando Enter (o el botón +).
    private void agregarRapido() {
        String nombre = campoNueva.getText();
        if (nombre == null || nombre.isBlank()) {
            return;
        }
        try {
            Categoria nueva = new Categoria(nombre);
            servicio.guardar(nueva);
            campoNueva.clear();
            cargar(nueva.getId());   // queda seleccionada la nueva
        } catch (IllegalArgumentException e) {
            errorNueva.setText(e.getMessage());
        } catch (SQLException e) {
            errorNueva.setText("No se pudo guardar: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- derecha

    private VBox crearColumnaDerecha() {
        titulo.getStyleClass().add("titulo-detalle");
        resumen.getStyleClass().add("texto-suave");

        renombrar.getStyleClass().add("btn-secundario");
        renombrar.setOnAction(e -> renombrar());
        eliminar.getStyleClass().add("btn-peligro");
        eliminar.setOnAction(e -> eliminar());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cabecera = new HBox(8, titulo, espacio, renombrar, eliminar);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);

        mover.getStyleClass().add("btn-secundario");
        mover.setDisable(true);
        mover.setOnAction(e -> moverProductoElegido());
        Label ayuda = new Label("Elige un producto para pasarlo a otra categoría.");
        ayuda.getStyleClass().addAll("texto-suave", "texto-pequeno");
        HBox pie = new HBox(12, mover, ayuda);
        pie.setAlignment(Pos.CENTER_LEFT);

        VBox columna = new VBox(10, cabecera, resumen, tabla, pie);
        columna.getStyleClass().add("card");
        return columna;
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Esta categoría aún no tiene productos."));
        tabla.getColumns().add(columna("Código", 110, p -> p.getCodigo()));
        tabla.getColumns().add(columna("Producto", 260, p -> p.getNombre()));
        tabla.getColumns().add(columna("Stock", 90, p -> String.valueOf(p.getStock())));
        tabla.getColumns().add(columna("Precio venta", 130, p -> Dinero.formato(p.getPrecioVenta())));
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((o, antes, ahora) -> mover.setDisable(ahora == null));
    }

    private TableColumn<Producto, String> columna(String titulo, double ancho, Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        return c;
    }

    // ---------------------------------------------------------------- datos

    // Vuelve a leer las categorías. Deja seleccionada la que tenga ese id (o la primera).
    private void cargar(Integer idASeleccionar) {
        try {
            List<Categoria> categorias = servicio.listarConSinCategoria();
            lista.getItems().setAll(categorias);
            Categoria elegida = categorias.stream()
                    .filter(c -> idASeleccionar != null && c.getId() == idASeleccionar)
                    .findFirst()
                    // Si no se pidió ninguna, se abre la primera que tenga productos (o la primera, si todas están vacías).
                    .orElse(categorias.isEmpty() ? null : categorias.stream()
                            .filter(c -> c.getTotalProductos() > 0)
                            .findFirst()
                            .orElse(categorias.get(0)));
            if (elegida == null) {
                mostrarDetalle(null);
            } else {
                lista.getSelectionModel().select(elegida);
                mostrarDetalle(elegida);
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar las categorías.\n" + e.getMessage());
        }
    }

    // Llena la columna derecha con los productos de la categoría.
    private void mostrarDetalle(Categoria categoria) {
        actual = categoria;
        boolean real = categoria != null && categoria.getId() != 0;
        renombrar.setDisable(!real);
        eliminar.setDisable(!real);
        if (categoria == null) {
            titulo.setText("Sin categorías");
            resumen.setText("Crea la primera escribiendo su nombre a la izquierda.");
            tabla.getItems().clear();
            return;
        }
        try {
            List<Producto> productos = servicio.productosDe(categoria);
            tabla.getItems().setAll(productos);
            titulo.setText(categoria.getNombre());
            resumen.setText(textoResumen(productos));
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los productos.\n" + e.getMessage());
        }
    }

    // "8 productos · $412.000 en inventario" (el valor solo lo ve el administrador).
    private String textoResumen(List<Producto> productos) {
        int n = productos.size();
        String texto = n == 0 ? "Sin productos" : n == 1 ? "1 producto" : n + " productos";
        if (n > 0 && usuario.esAdmin()) {
            BigDecimal valor = BigDecimal.ZERO;
            for (Producto p : productos) {
                valor = valor.add(p.getPrecioCompra().multiply(BigDecimal.valueOf(p.getStock())));
            }
            texto += "  ·  " + Dinero.formato(valor) + " en inventario (al costo)";
        }
        return texto;
    }

    // ---------------------------------------------------------------- acciones

    private void renombrar() {
        if (actual != null && CategoriaDialog.abrir(actual)) {
            cargar(actual.getId());
        }
    }

    private void eliminar() {
        if (actual == null || !Mensajes.confirmar("Eliminar categoría", "¿Eliminar \"" + actual.getNombre() + "\"?")) {
            return;
        }
        try {
            servicio.eliminar(actual);
            cargar(null);
        } catch (IllegalStateException e) {
            Mensajes.error(e.getMessage());   // por ejemplo: "tiene 3 productos"
        } catch (SQLException e) {
            Mensajes.error("No se pudo eliminar la categoría.\n" + e.getMessage());
        }
    }

    // Pregunta a qué categoría pasar el producto seleccionado y lo mueve.
    private void moverProductoElegido() {
        Producto producto = tabla.getSelectionModel().getSelectedItem();
        if (producto == null || actual == null) {
            return;
        }
        try {
            // Destinos posibles: todas las categorías reales menos la actual, más "Sin categoría".
            List<Categoria> destinos = new ArrayList<>();
            for (Categoria c : servicio.listar()) {
                if (c.getId() != actual.getId()) {
                    destinos.add(c);
                }
            }
            if (actual.getId() != 0) {
                destinos.add(CategoriaService.sinCategoria());
            }
            if (destinos.isEmpty()) {
                Mensajes.info("No hay otra categoría", "Crea otra categoría primero para poder mover productos.");
                return;
            }

            ComboBox<Categoria> combo = new ComboBox<>();
            combo.getItems().setAll(destinos);
            combo.setPromptText("Elige la categoría");
            combo.setMaxWidth(Double.MAX_VALUE);
            Label etiqueta = new Label("Pasar \"" + producto.getNombre() + "\" a:");
            etiqueta.getStyleClass().add("etiqueta-campo");
            VBox contenido = new VBox(8, etiqueta, combo);
            contenido.setPadding(new Insets(4, 0, 0, 0));
            contenido.setPrefWidth(360);

            ButtonType mover = new ButtonType("Mover", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle("POS 2.0");
            dialogo.setHeaderText("Cambiar de categoría");
            dialogo.getDialogPane().setContent(contenido);
            dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
            dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, mover);
            dialogo.getDialogPane().lookupButton(mover).disableProperty().bind(combo.valueProperty().isNull());

            if (dialogo.showAndWait().filter(b -> b == mover).isPresent()) {
                servicio.moverProducto(producto, combo.getValue());
                cargar(actual.getId());
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo mover el producto.\n" + e.getMessage());
        }
    }
}
