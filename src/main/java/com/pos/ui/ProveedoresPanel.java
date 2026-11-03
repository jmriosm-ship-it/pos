package com.pos.ui;

import com.pos.modelo.Compra;
import com.pos.modelo.Producto;
import com.pos.modelo.Proveedor;
import com.pos.modelo.Usuario;
import com.pos.servicio.ProveedorService;
import com.pos.util.Dinero;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Pestaña "Proveedores" (diseño C): a la izquierda la lista de proveedores; a la derecha la
 * ficha del elegido, con su contacto, lo que se le ha comprado, los productos que nos surte
 * y el historial de compras. Los datos de dinero solo los ve el administrador.
 */
public class ProveedoresPanel extends HBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProveedorService servicio = new ProveedorService();
    private final boolean esAdmin = Sesion.usuario().esAdmin();

    // Columna izquierda
    private final ListView<Proveedor> lista = new ListView<>();
    private final TextField buscador = new TextField();
    private List<Proveedor> todos = List.of();

    // Columna derecha (la ficha)
    private final Label titulo = new Label();
    private final Label contacto = new Label();
    private final Label lblTelefono = new Label();
    private final Label lblComprado = new Label();
    private final Label lblUltima = new Label();
    private final TableView<Producto> tablaProductos = new TableView<>();
    private final TableView<Compra> tablaCompras = new TableView<>();
    private final Button editar = new Button("Editar");
    private final Button desactivar = new Button("Desactivar");

    private Proveedor actual;

    public ProveedoresPanel() {
        setSpacing(16);
        VBox izquierda = crearColumnaIzquierda();
        VBox derecha = crearFicha();
        HBox.setHgrow(derecha, Priority.ALWAYS);
        getChildren().addAll(izquierda, derecha);
        cargar(null);
    }

    // ---------------------------------------------------------------- izquierda

    private VBox crearColumnaIzquierda() {
        buscador.setPromptText("Buscar proveedor");
        buscador.textProperty().addListener((o, antes, ahora) -> filtrar(null));
        HBox.setHgrow(buscador, Priority.ALWAYS);

        Button nuevo = new Button("", Iconos.crear(Iconos.MAS, 14));
        nuevo.getStyleClass().add("btn-primario");
        nuevo.setOnAction(e -> nuevoProveedor());
        HBox fila = new HBox(6, buscador, nuevo);

        // Se reutiliza el estilo de la lista de categorías.
        lista.getStyleClass().add("lista-categorias");
        lista.setPlaceholder(new Label("No hay proveedores.\nCrea el primero con el botón +."));
        lista.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Proveedor p, boolean vacio) {
                super.updateItem(p, vacio);
                if (vacio || p == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label nombre = new Label(p.getNombre());
                String tel = p.getTelefono() == null ? "Sin teléfono" : p.getTelefono();
                Label sub = new Label(tel);
                sub.getStyleClass().addAll("texto-suave", "texto-pequeno");
                setGraphic(new VBox(1, nombre, sub));
                setText(null);
            }
        });
        lista.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) -> {
            if (ahora != null) {
                mostrarFicha(ahora);
            }
        });
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox columna = new VBox(8, fila, lista);
        columna.getStyleClass().add("card");
        columna.setPrefWidth(320);
        columna.setMinWidth(320);
        columna.setMaxWidth(320);
        return columna;
    }

    // ---------------------------------------------------------------- ficha

    private VBox crearFicha() {
        titulo.getStyleClass().add("titulo-detalle");
        editar.getStyleClass().add("btn-secundario");
        editar.setOnAction(e -> editar());
        desactivar.getStyleClass().add("btn-peligro");
        desactivar.setOnAction(e -> desactivar());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cabecera = new HBox(8, titulo, espacio, editar, desactivar);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        contacto.getStyleClass().add("texto-suave");

        // Tres tarjetitas con los datos clave. El dinero solo se muestra al administrador.
        HBox datos = new HBox(10, mini("Teléfono", lblTelefono));
        if (esAdmin) {
            datos.getChildren().add(mini("Total comprado", lblComprado));
            datos.getChildren().add(mini("Última compra", lblUltima));
        }

        VBox productos = seccion("Productos que nos surte", tablaProductos);
        configurarTablaProductos();
        // Una tabla encima de la otra, para que cada una use todo el ancho de la ficha.
        VBox secciones = new VBox(14, productos);
        if (esAdmin) {
            configurarTablaCompras();
            secciones.getChildren().add(seccion("Historial de compras", tablaCompras));
        }
        VBox.setVgrow(secciones, Priority.ALWAYS);

        VBox ficha = new VBox(10, cabecera, contacto, datos, secciones);
        ficha.getStyleClass().add("card");
        return ficha;
    }

    // Una tarjetita gris con una etiqueta pequeña arriba y el valor abajo.
    private VBox mini(String etiqueta, Label valor) {
        Label e = new Label(etiqueta);
        e.getStyleClass().add("metrica-etiqueta");
        valor.getStyleClass().add("mini-valor");
        VBox caja = new VBox(2, e, valor);
        caja.getStyleClass().add("mini");
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setPrefWidth(10);   // todas parten del mismo ancho y se reparten el espacio igual
        return caja;
    }

    private VBox seccion(String titulo, TableView<?> tabla) {
        Label t = new Label(titulo);
        t.getStyleClass().add("card-titulo");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox caja = new VBox(8, t, tabla);
        VBox.setVgrow(caja, Priority.ALWAYS);
        return caja;
    }

    private void configurarTablaProductos() {
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaProductos.setPlaceholder(new Label("Aún no tiene productos asignados.\nSe asignan en el formulario del producto."));
        tablaProductos.getColumns().add(columna("Código", 90, (Producto p) -> p.getCodigo()));
        tablaProductos.getColumns().add(columna("Producto", 170, (Producto p) -> p.getNombre()));
        tablaProductos.getColumns().add(columna("Stock", 70, (Producto p) -> String.valueOf(p.getStock())));
        if (esAdmin) {
            tablaProductos.getColumns().add(columna("Costo", 90, (Producto p) -> Dinero.formato(p.getPrecioCompra())));
        }
    }

    private void configurarTablaCompras() {
        tablaCompras.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaCompras.setPlaceholder(new Label("Aún no se le ha comprado."));
        tablaCompras.getColumns().add(columna("Compra", 80, (Compra c) -> "#" + c.getId()));
        tablaCompras.getColumns().add(columna("Fecha", 110, (Compra c) -> c.getFecha().format(FECHA)));
        tablaCompras.getColumns().add(columna("Unidades", 90, (Compra c) -> String.valueOf(c.getTotalUnidades())));
        tablaCompras.getColumns().add(columna("Total", 100, (Compra c) -> Dinero.formato(c.getTotal())));
    }

    private <T> TableColumn<T, String> columna(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        return c;
    }

    // ---------------------------------------------------------------- datos

    // Vuelve a leer los proveedores. Deja seleccionado el de ese id (o el primero con compras).
    private void cargar(Integer idASeleccionar) {
        try {
            todos = servicio.listar();
            filtrar(idASeleccionar);
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los proveedores.\n" + e.getMessage());
        }
    }

    // Aplica el buscador sobre la lista ya cargada y elige qué mostrar en la ficha.
    private void filtrar(Integer idASeleccionar) {
        String texto = buscador.getText() == null ? "" : buscador.getText().trim().toLowerCase();
        List<Proveedor> visibles = todos.stream()
                .filter(p -> p.getNombre().toLowerCase().contains(texto)
                        || (p.getTelefono() != null && p.getTelefono().contains(texto)))
                .toList();
        lista.getItems().setAll(visibles);

        Integer idActual = idASeleccionar != null ? idASeleccionar : (actual == null ? null : actual.getId());
        Proveedor elegido = visibles.stream()
                .filter(p -> idActual != null && p.getId() == idActual)
                .findFirst()
                // Si no hay ninguno elegido, se abre el primero que tenga compras (o el primero).
                .orElse(visibles.isEmpty() ? null : visibles.stream()
                        .filter(p -> p.getUltimaCompra() != null)
                        .findFirst()
                        .orElse(visibles.get(0)));
        if (elegido == null) {
            mostrarFicha(null);
        } else {
            lista.getSelectionModel().select(elegido);
            mostrarFicha(elegido);
        }
    }

    private void mostrarFicha(Proveedor p) {
        actual = p;
        editar.setDisable(p == null);
        desactivar.setDisable(p == null);
        if (p == null) {
            titulo.setText("Sin proveedores");
            contacto.setText("Crea el primero con el botón + de la izquierda.");
            lblTelefono.setText("—");
            lblComprado.setText("—");
            lblUltima.setText("—");
            tablaProductos.getItems().clear();
            tablaCompras.getItems().clear();
            return;
        }
        titulo.setText(p.getNombre());
        contacto.setText(textoContacto(p));
        lblTelefono.setText(p.getTelefono() == null ? "—" : p.getTelefono());
        lblComprado.setText(Dinero.formato(p.getTotalComprado()));
        lblUltima.setText(p.getUltimaCompra() == null ? "Sin compras" : p.getUltimaCompra().format(FECHA));
        try {
            tablaProductos.getItems().setAll(servicio.productosDe(p));
            if (esAdmin) {
                tablaCompras.getItems().setAll(servicio.comprasDe(p));
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la ficha del proveedor.\n" + e.getMessage());
        }
    }

    // "ventas@tech.co  ·  Calle 10 # 5-20", o un aviso si no hay nada registrado.
    private String textoContacto(Proveedor p) {
        String correo = p.getCorreo();
        String direccion = p.getDireccion();
        if (correo == null && direccion == null) {
            return "Sin correo ni dirección registrados.";
        }
        if (correo != null && direccion != null) {
            return correo + "  ·  " + direccion;
        }
        return correo != null ? correo : direccion;
    }

    // ---------------------------------------------------------------- acciones

    private void nuevoProveedor() {
        Proveedor creado = ProveedorDialog.abrir(null);
        if (creado != null) {
            buscador.clear();
            cargar(creado.getId());
        }
    }

    private void editar() {
        if (actual == null) {
            return;
        }
        Proveedor editado = ProveedorDialog.abrir(actual);
        if (editado != null) {
            cargar(editado.getId());
        }
    }

    private void desactivar() {
        if (actual == null || !Mensajes.confirmar("Desactivar proveedor",
                "\"" + actual.getNombre() + "\" dejará de aparecer al hacer compras.\nSu historial se conserva.")) {
            return;
        }
        try {
            servicio.desactivar(actual);
            actual = null;
            cargar(null);
        } catch (SQLException e) {
            Mensajes.error("No se pudo desactivar el proveedor.\n" + e.getMessage());
        }
    }
}
