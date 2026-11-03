package com.pos.ui;

import com.pos.modelo.Abono;
import com.pos.modelo.Cliente;
import com.pos.modelo.Fiado;
import com.pos.modelo.Pago;
import com.pos.modelo.Venta;
import com.pos.servicio.ClienteService;
import com.pos.servicio.FiadoService;
import com.pos.util.Dinero;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Módulo Clientes (mismo diseño de lista + ficha de Proveedores).
 * Izquierda: los clientes, y a quien debe plata se le ve "Debe $X". Derecha: su ficha con lo comprado,
 * lo que debe, y tres pestañas (Compras, Fiados, Abonos). Desde aquí se registran los abonos.
 * Teclas: F2 buscar · F3 cliente nuevo · F4 editar · F5 registrar abono.
 */
public class ClientesView extends HBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ClienteService clienteService = new ClienteService();
    private final FiadoService fiadoService = new FiadoService();

    // Columna izquierda
    private final ListView<Cliente> lista = new ListView<>();
    private final TextField buscador = new TextField();
    private List<Cliente> todos = List.of();

    // Columna derecha (la ficha)
    private final Label titulo = new Label();
    private final Label contacto = new Label();
    private final Label lblTelefono = new Label();
    private final Label lblComprado = new Label();
    private final Label lblDebe = new Label();
    private final TableView<Venta> tablaCompras = new TableView<>();
    private final TableView<Fiado> tablaFiados = new TableView<>();
    private final TableView<Abono> tablaAbonos = new TableView<>();
    private final Button editar = new Button("Editar");
    private final Button abonar = new Button("Registrar abono");

    private Cliente actual;

    // Las teclas se escuchan en toda la ventana mientras esta pantalla está a la vista.
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public ClientesView() {
        setSpacing(16);
        VBox izquierda = crearColumnaIzquierda();
        VBox derecha = crearFicha();
        HBox.setHgrow(derecha, Priority.ALWAYS);
        getChildren().addAll(izquierda, derecha);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        cargar(null);
    }

    private void tecla(KeyEvent e) {
        switch (e.getCode()) {
            case F2 -> {
                buscador.requestFocus();
                buscador.selectAll();
            }
            case F3 -> nuevoCliente();
            case F4 -> editar();
            case F5 -> registrarAbono();
            default -> {
                return;
            }
        }
        e.consume();
    }

    // ---------------------------------------------------------------- izquierda

    private VBox crearColumnaIzquierda() {
        buscador.setPromptText("Buscar cliente (F2)");
        buscador.textProperty().addListener((o, antes, ahora) -> filtrar(null));
        HBox.setHgrow(buscador, Priority.ALWAYS);

        Button nuevo = new Button("", Iconos.crear(Iconos.MAS, 14));
        nuevo.getStyleClass().add("btn-primario");
        nuevo.setFocusTraversable(false);
        nuevo.setOnAction(e -> nuevoCliente());
        HBox fila = new HBox(6, buscador, nuevo);

        FlowPane teclas = new FlowPane(12, 4,
                Atajos.etiqueta("F3", "Nuevo"), Atajos.etiqueta("F4", "Editar"), Atajos.etiqueta("F5", "Abono"));

        // Se reutiliza el estilo de la lista de categorías.
        lista.getStyleClass().add("lista-categorias");
        lista.setPlaceholder(new Label("No hay clientes.\nCrea el primero con el botón +."));
        lista.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Cliente c, boolean vacio) {
                super.updateItem(c, vacio);
                if (vacio || c == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label nombre = new Label(c.getNombre());
                Label sub;
                if (c.getSaldoPendiente().signum() > 0) {
                    sub = new Label("Debe " + Dinero.formato(c.getSaldoPendiente()));
                    sub.getStyleClass().addAll("texto-peligro", "texto-pequeno");
                } else {
                    sub = new Label(c.getTelefono() == null ? "Sin teléfono" : c.getTelefono());
                    sub.getStyleClass().addAll("texto-suave", "texto-pequeno");
                }
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

        VBox columna = new VBox(8, fila, teclas, lista);
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
        conTecla(editar, "F4");
        editar.setOnAction(e -> editar());
        abonar.getStyleClass().add("btn-primario");
        conTecla(abonar, "F5");
        abonar.setOnAction(e -> registrarAbono());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cabecera = new HBox(8, titulo, espacio, editar, abonar);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        contacto.getStyleClass().add("texto-suave");

        HBox datos = new HBox(10, mini("Teléfono", lblTelefono), mini("Total comprado", lblComprado), mini("Por cobrar", lblDebe));

        configurarTablaCompras();
        configurarTablaFiados();
        configurarTablaAbonos();
        TabPane pestanas = new TabPane(
                new Tab("Compras", tablaCompras), new Tab("Fiados", tablaFiados), new Tab("Abonos", tablaAbonos));
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(pestanas, Priority.ALWAYS);

        VBox ficha = new VBox(10, cabecera, contacto, datos, pestanas);
        ficha.getStyleClass().add("card");
        return ficha;
    }

    private void conTecla(Button b, String tecla) {
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
    }

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

    private void configurarTablaCompras() {
        tablaCompras.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaCompras.setPlaceholder(new Label("Este cliente aún no ha comprado."));
        tablaCompras.getColumns().add(columna("Venta", 80, (Venta v) -> "#" + v.getId()));
        tablaCompras.getColumns().add(columna("Fecha", 150, (Venta v) -> v.getFecha().format(FECHA_HORA)));
        tablaCompras.getColumns().add(columna("Total", 110, (Venta v) -> Dinero.formato(v.getTotal())));
        tablaCompras.getColumns().add(columna("Pago", 120, (Venta v) -> nombreMetodo(v.getMetodoPago())));
        tablaCompras.getColumns().add(columna("Estado", 110, (Venta v) -> "ANULADA".equals(v.getEstado()) ? "Anulada" : "Completada"));
    }

    private void configurarTablaFiados() {
        tablaFiados.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaFiados.setPlaceholder(new Label("Este cliente no tiene fiados."));
        tablaFiados.getColumns().add(columna("Venta", 80, (Fiado f) -> "#" + f.getIdVenta()));
        tablaFiados.getColumns().add(columna("Fecha", 150, (Fiado f) -> f.getCreadoEn().format(FECHA_HORA)));
        tablaFiados.getColumns().add(columna("Fiado", 120, (Fiado f) -> Dinero.formato(f.getMonto())));
        tablaFiados.getColumns().add(columna("Debe", 120, (Fiado f) -> Dinero.formato(f.getSaldo())));
        tablaFiados.getColumns().add(columna("Estado", 110, (Fiado f) -> "PAGADO".equals(f.getEstado()) ? "Pagado" : "Pendiente"));
    }

    private void configurarTablaAbonos() {
        tablaAbonos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaAbonos.setPlaceholder(new Label("Este cliente aún no ha hecho abonos."));
        tablaAbonos.getColumns().add(columna("Fecha", 150, (Abono a) -> a.getFecha().format(FECHA_HORA)));
        tablaAbonos.getColumns().add(columna("Venta", 80, (Abono a) -> "#" + a.getIdVenta()));
        tablaAbonos.getColumns().add(columna("Pagó con", 130, (Abono a) -> a.etiquetaMetodo()));
        tablaAbonos.getColumns().add(columna("Monto", 120, (Abono a) -> Dinero.formato(a.getMonto())));
        tablaAbonos.getColumns().add(columna("Recibió", 140, (Abono a) -> a.getNombreUsuario()));
    }

    private <T> TableColumn<T, String> columna(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        return c;
    }

    // El método de pago de una venta, con nombre para mostrar.
    private String nombreMetodo(String metodo) {
        return "MIXTO".equals(metodo) ? "Mixto" : new Pago(metodo, null).etiqueta();
    }

    // ---------------------------------------------------------------- datos

    // Vuelve a leer los clientes. Deja seleccionado el de ese id (o el que ya estaba, o el primero).
    private void cargar(Integer idASeleccionar) {
        try {
            todos = clienteService.listarConResumen();
            filtrar(idASeleccionar);
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los clientes.\n" + e.getMessage());
        }
    }

    private void filtrar(Integer idASeleccionar) {
        String texto = buscador.getText() == null ? "" : buscador.getText().trim().toLowerCase();
        List<Cliente> visibles = todos.stream()
                .filter(c -> c.getNombre().toLowerCase().contains(texto)
                        || (c.getDocumento() != null && c.getDocumento().toLowerCase().contains(texto))
                        || (c.getTelefono() != null && c.getTelefono().contains(texto)))
                .toList();
        lista.getItems().setAll(visibles);

        Integer idActual = idASeleccionar != null ? idASeleccionar : (actual == null ? null : actual.getId());
        Cliente elegido = visibles.stream()
                .filter(c -> idActual != null && c.getId() == idActual)
                .findFirst()
                // Si no hay ninguno elegido, se abre el primero que deba plata (o el primero de la lista).
                .orElse(visibles.isEmpty() ? null : visibles.stream()
                        .filter(c -> c.getSaldoPendiente().signum() > 0)
                        .findFirst()
                        .orElse(visibles.get(0)));
        if (elegido == null) {
            mostrarFicha(null);
        } else {
            lista.getSelectionModel().select(elegido);
            mostrarFicha(elegido);
        }
    }

    private void mostrarFicha(Cliente c) {
        actual = c;
        editar.setDisable(c == null);
        abonar.setDisable(c == null || c.getSaldoPendiente().signum() <= 0);
        lblDebe.getStyleClass().remove("texto-peligro");
        if (c == null) {
            titulo.setText("Sin clientes");
            contacto.setText("Crea el primero con el botón + de la izquierda.");
            lblTelefono.setText("—");
            lblComprado.setText("—");
            lblDebe.setText("—");
            tablaCompras.getItems().clear();
            tablaFiados.getItems().clear();
            tablaAbonos.getItems().clear();
            return;
        }
        titulo.setText(c.getNombre());
        contacto.setText(textoContacto(c));
        lblTelefono.setText(c.getTelefono() == null ? "—" : c.getTelefono());
        lblComprado.setText(Dinero.formato(c.getTotalComprado()));
        lblDebe.setText(Dinero.formato(c.getSaldoPendiente()));
        if (c.getSaldoPendiente().signum() > 0) {
            lblDebe.getStyleClass().add("texto-peligro");
        }
        try {
            tablaCompras.getItems().setAll(clienteService.historial(c));
            tablaFiados.getItems().setAll(fiadoService.fiadosDe(c));
            tablaAbonos.getItems().setAll(fiadoService.abonosDe(c));
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la ficha del cliente.\n" + e.getMessage());
        }
    }

    // "Doc. 1234  ·  correo@x.com  ·  Calle 1", o un aviso si no hay nada registrado.
    private String textoContacto(Cliente c) {
        StringBuilder sb = new StringBuilder();
        if (c.getDocumento() != null) {
            sb.append("Doc. ").append(c.getDocumento());
        }
        if (c.getCorreo() != null) {
            sb.append(sb.length() > 0 ? "  ·  " : "").append(c.getCorreo());
        }
        if (c.getDireccion() != null) {
            sb.append(sb.length() > 0 ? "  ·  " : "").append(c.getDireccion());
        }
        return sb.length() == 0 ? "Sin documento, correo ni dirección registrados." : sb.toString();
    }

    // ---------------------------------------------------------------- acciones

    private void nuevoCliente() {
        Cliente creado = ClienteDialog.abrir(null);
        if (creado != null) {
            buscador.clear();
            cargar(creado.getId());
        }
    }

    private void editar() {
        if (actual == null) {
            return;
        }
        Cliente editado = ClienteDialog.abrir(actual);
        if (editado != null) {
            cargar(editado.getId());
        }
    }

    private void registrarAbono() {
        if (actual == null) {
            return;
        }
        if (actual.getSaldoPendiente().signum() <= 0) {
            Mensajes.info("Sin deuda", actual.getNombre() + " no tiene nada pendiente por pagar.");
            return;
        }
        if (AbonoDialog.abrir(actual, actual.getSaldoPendiente())) {
            cargar(actual.getId());
        }
    }
}
