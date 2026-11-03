package com.pos.ui;

import com.pos.dao.CategoriaDAO;
import com.pos.dao.ProveedorDAO;
import com.pos.modelo.Categoria;
import com.pos.modelo.Producto;
import com.pos.modelo.Proveedor;
import com.pos.servicio.ProductoService;
import com.pos.util.Dinero;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;

/** Formulario para crear o editar un producto. Devuelve true si se guardó. */
public class ProductoDialog {

    private ProductoDialog() {
    }

    public static boolean abrir(Producto original) {
        boolean nuevo = original == null;
        ProductoService servicio = new ProductoService();

        TextField codigo = new TextField();
        TextField nombre = new TextField();
        TextField compra = new TextField();
        TextField venta = new TextField();
        TextField stock = new TextField();
        TextField minimo = new TextField();
        ComboBox<Categoria> categoria = new ComboBox<>();
        ComboBox<Proveedor> proveedor = new ComboBox<>();
        categoria.setPromptText("Sin categoría");
        proveedor.setPromptText("Sin proveedor");
        categoria.setMaxWidth(Double.MAX_VALUE);
        proveedor.setMaxWidth(Double.MAX_VALUE);
        try {
            categoria.getItems().setAll(new CategoriaDAO().listar());
            proveedor.getItems().setAll(new ProveedorDAO().listar());
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar las categorías y proveedores.\n" + e.getMessage());
        }

        if (nuevo) {
            stock.setText("0");
            minimo.setText("0");
        } else {
            codigo.setText(original.getCodigo());
            nombre.setText(original.getNombre());
            compra.setText(Dinero.plano(original.getPrecioCompra()));
            venta.setText(Dinero.plano(original.getPrecioVenta()));
            stock.setText(String.valueOf(original.getStock()));
            minimo.setText(String.valueOf(original.getStockMinimo()));
            for (Categoria c : categoria.getItems()) {
                if (original.getIdCategoria() != null && c.getId() == original.getIdCategoria()) {
                    categoria.setValue(c);
                }
            }
            for (Proveedor p : proveedor.getItems()) {
                if (original.getIdProveedor() != null && p.getId() == original.getIdProveedor()) {
                    proveedor.setValue(p);
                }
            }
            // El stock de un producto existente solo cambia con compras, ventas o ajustes de inventario.
            stock.setDisable(true);
        }

        GridPane formulario = new GridPane();
        formulario.setHgap(12);
        formulario.setVgap(10);
        formulario.setPadding(new Insets(6, 0, 0, 0));
        agregar(formulario, 0, "Código", codigo, "Nombre", nombre);
        agregar(formulario, 1, "Categoría", categoria, "Proveedor", proveedor);
        agregar(formulario, 2, "Precio de compra", compra, "Precio de venta", venta);
        agregar(formulario, 3, nuevo ? "Stock inicial" : "Stock actual", stock, "Stock mínimo", minimo);

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        VBox contenido = new VBox(10, formulario, error);
        contenido.setPrefWidth(520);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("POS 2.0");
        dialogo.setHeaderText(nuevo ? "Nuevo producto" : "Editar producto");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);

        boolean[] guardado = {false};
        // Al pulsar Guardar se valida; si hay un error se muestra y la ventana NO se cierra.
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                Producto p = new Producto();
                if (!nuevo) {
                    p.setId(original.getId());
                }
                p.setCodigo(codigo.getText());
                p.setNombre(nombre.getText());
                Categoria c = categoria.getValue();
                Proveedor pv = proveedor.getValue();
                p.setIdCategoria(c == null ? null : c.getId());
                p.setIdProveedor(pv == null ? null : pv.getId());
                p.setPrecioCompra(compra.getText().isBlank() ? BigDecimal.ZERO : Dinero.parsear(compra.getText()));
                p.setPrecioVenta(Dinero.parsear(venta.getText()));
                p.setStock(entero(stock.getText(), "El stock"));
                p.setStockMinimo(entero(minimo.getText(), "El stock mínimo"));
                p.setActivo(true);
                servicio.guardar(p);
                guardado[0] = true;
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return guardado[0];
    }

    // Una fila del formulario con dos campos lado a lado, cada uno con su etiqueta encima.
    private static void agregar(GridPane g, int fila, String etiqueta1, javafx.scene.Node campo1,
                                String etiqueta2, javafx.scene.Node campo2) {
        g.add(conEtiqueta(etiqueta1, campo1), 0, fila);
        g.add(conEtiqueta(etiqueta2, campo2), 1, fila);
        GridPane.setHgrow(g.getChildren().get(g.getChildren().size() - 2), Priority.ALWAYS);
        GridPane.setHgrow(g.getChildren().get(g.getChildren().size() - 1), Priority.ALWAYS);
    }

    private static VBox conEtiqueta(String texto, javafx.scene.Node campo) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        VBox caja = new VBox(4, l, campo);
        caja.setPrefWidth(250);
        return caja;
    }

    private static int entero(String texto, String nombre) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " debe ser un número entero.");
        }
    }
}
