package com.pos.ui;

import com.pos.modelo.Modulo;
import com.pos.modelo.Accion;
import com.pos.dao.ProductoDAO;
import com.pos.modelo.Cliente;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Producto;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;
import com.pos.servicio.CajaService;
import com.pos.servicio.ClienteService;
import com.pos.servicio.ImpresoraService;
import com.pos.servicio.ProductoService;
import com.pos.servicio.ReciboService;
import com.pos.servicio.VentaService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La pantalla de ventas, en pantalla completa:
 *   arriba la fila de producto (buscar o escanear + cantidad + Agregar), al centro el ticket de venta,
 *   a la derecha el cliente, las promociones, el descuento y los puntos, y abajo el total con Cancelar y Cobrar.
 * Al cobrar se abre un recuadro encima con los precios de cada producto (se pueden bajar, nunca subir) y las
 * formas de pago. La barra de la caja (estado, movimientos, cerrar caja) la pone la pantalla que contiene a esta.
 *
 * Teclas: F2 buscar · F3 cliente · F4 cobrar · F5 descuento · F6 cancelar · F10 puntos · + y − cantidad ·
 * Ctrl+R / Ctrl+P ver o imprimir el recibo de la última venta.
 */
public class VentasView extends StackPane {

    private final VentaService ventaService = new VentaService();
    private final CajaService cajaService = new CajaService();
    private final ProductoService productoService = new ProductoService();
    private final ClienteService clienteService = new ClienteService();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final Usuario usuario = Sesion.usuario();
    private final Runnable alCambiarCaja;

    private Venta venta;                           // el ticket actual
    private List<Producto> visibles = List.of();   // coincidencias del buscador
    private Node pantalla;                         // la pantalla de venta (el cobro se pone encima)
    private StackPane cobroEncima;                 // el recuadro de cobro, mientras está abierto
    private boolean cobroAbierto = false;

    private final TextField buscador = new TextField();
    private final TextField campoCantidad = new TextField("1");
    private final ListView<Producto> sugerencias = new ListView<>();
    private final TableView<DetalleVenta> tabla = new TableView<>();
    private final Label lblArticulos = new Label();
    private final Label lblSubtotal = new Label();
    private final Label lblTotal = new Label();
    private final Label lblTotalPedido = new Label();
    private final Label lblVuelto = new Label("$0");
    private final Label lblErrorDescuento = new Label();
    private final TextField campoDescuento = new TextField();
    private final Label lblPromo = new Label();
    private final HBox filaPromo = new HBox(8);
    private final Label lblPuntos = new Label();
    private final Button btnPuntos = new Button();
    private final HBox filaPuntos = new HBox(8);
    private final ComboBox<Cliente> comboCliente = new ComboBox<>();

    // Aviso verde que aparece al volver de cobrar (con la devolución).
    private final HBox aviso = new HBox(16);

    // Atajos de teclado: se escuchan en toda la ventana mientras esta pantalla está a la vista.
    private final EventHandler<KeyEvent> manejadorAtajos = this::atajo;
    // Pistola de códigos: detecta ráfagas de teclas muy rápidas aunque el cursor no esté en el buscador.
    private final Lector lector = new Lector();
    private final EventHandler<KeyEvent> manejadorTecleo = e -> {
        if (Atajos.visible(this) && !cobroAbierto && e.getCharacter() != null && !e.getCharacter().isBlank()) {
            lector.caracter(e.getCharacter());
        }
    };
    private boolean escaneado = false;       // el código que se está procesando vino de la pistola
    private boolean sonidoLector = true;     // pitar al escanear (se cambia en Configuración)
    private Venta ultimaVenta;               // la última venta cobrada (para Ctrl+R / Ctrl+P)
    private CobroView.Resultado ultimoResultado;

    public VentasView(Runnable alCambiarCaja) {
        this.alCambiarCaja = alCambiarCaja;
        try {
            sonidoLector = !"false".equals(new com.pos.servicio.ConfiguracionService().obtener(
                    com.pos.servicio.ConfiguracionService.LECTOR_SONIDO, "true"));
        } catch (SQLException e) {
            sonidoLector = true;
        }
        // Los atajos se conectan a la ventana cuando esta pantalla entra en ella y se sueltan cuando sale.
        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorAtajos);
                antigua.removeEventFilter(KeyEvent.KEY_TYPED, manejadorTecleo);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorAtajos);
                nueva.addEventFilter(KeyEvent.KEY_TYPED, manejadorTecleo);
            }
        });
        iniciar();
    }

    // Arma la pantalla (si no hay caja abierta, lo avisa).
    private void iniciar() {
        try {
            if (cajaService.cajaAbierta(usuario) == null) {
                Label l = new Label("Abre la caja para poder vender.");
                l.getStyleClass().add("texto-suave");
                getChildren().setAll(l);
                return;
            }
            venta = ventaService.nuevaVenta(usuario);
            pantalla = construirPantalla();
            getChildren().setAll(pantalla);
            refrescarCarrito();
            Platform.runLater(buscador::requestFocus);
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la pantalla de ventas.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- diseño

    private Node construirPantalla() {
        // El aviso de devolución va arriba de todo; oculto hasta que se cobre una venta.
        aviso.getStyleClass().add("aviso-ok");
        aviso.setAlignment(Pos.CENTER_LEFT);
        aviso.setVisible(false);
        aviso.setManaged(false);

        VBox centro = new VBox(10, construirEntrada(), construirTicket());
        HBox.setHgrow(centro, Priority.ALWAYS);
        VBox.setVgrow(centro.getChildren().get(1), Priority.ALWAYS);
        HBox medio = new HBox(12, centro, construirPanelDerecho());
        VBox.setVgrow(medio, Priority.ALWAYS);

        return new VBox(10, aviso, medio, construirBarraInferior());
    }

    // Fila de producto: buscar o escanear, cantidad y botón Agregar; debajo, las coincidencias.
    private Node construirEntrada() {
        buscador.setPromptText("Escanea o escribe el nombre del producto…");
        buscador.getStyleClass().add("campo-buscar");
        buscador.textProperty().addListener((o, antes, ahora) -> cargarProductos(ahora));
        buscador.setOnAction(e -> agregarDesdeBuscador());
        buscador.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && sugerencias.isVisible()) {
                sugerencias.requestFocus();
                sugerencias.getSelectionModel().select(0);
                e.consume();
            }
        });
        HBox.setHgrow(buscador, Priority.ALWAYS);

        campoCantidad.setPrefWidth(70);
        campoCantidad.setAlignment(Pos.CENTER);
        campoCantidad.textProperty().addListener((o, antes, ahora) -> {
            String d = ahora.replaceAll("[^0-9]", "");
            if (!d.equals(ahora)) {
                campoCantidad.setText(d);
            }
        });
        campoCantidad.setOnAction(e -> agregarDesdeBuscador());

        Button agregar = new Button("Agregar");
        agregar.getStyleClass().add("btn-primario");
        agregar.setGraphic(Atajos.tecla("Enter"));
        agregar.setContentDisplay(ContentDisplay.RIGHT);
        agregar.setGraphicTextGap(8);
        agregar.setFocusTraversable(false);
        agregar.setOnAction(e -> agregarDesdeBuscador());

        VBox colBuscador = new VBox(3, etiqueta("Producto", "F2"), buscador);
        HBox.setHgrow(colBuscador, Priority.ALWAYS);
        VBox colCantidad = new VBox(3, etiqueta("Cant.", null), campoCantidad);
        HBox fila = new HBox(10, colBuscador, colCantidad, agregar);
        fila.setAlignment(Pos.BOTTOM_LEFT);

        sugerencias.setFixedCellSize(32);
        sugerencias.setVisible(false);
        sugerencias.setManaged(false);
        sugerencias.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                setText(vacio || p == null ? null : p.getNombre() + (p.getMarca() == null ? "" : "  ·  " + p.getMarca())
                        + "   —   " + Dinero.formato(p.getPrecioVenta()) + "   ·   " + (p.getStock() > 0 ? p.getStock() + " en stock" : "agotado"));
            }
        });
        sugerencias.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Producto p = sugerencias.getSelectionModel().getSelectedItem();
                if (p != null) {
                    agregarProductoElegido(p);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE || (e.getCode() == KeyCode.UP && sugerencias.getSelectionModel().getSelectedIndex() <= 0)) {
                buscador.requestFocus();
                e.consume();
            }
        });
        sugerencias.setOnMouseClicked(e -> {
            Producto p = sugerencias.getSelectionModel().getSelectedItem();
            if (p != null && e.getClickCount() >= 1) {
                agregarProductoElegido(p);
            }
        });

        VBox caja = new VBox(8, fila, sugerencias);
        caja.getStyleClass().add("card");
        return caja;
    }

    private Node etiqueta(String texto, String tecla) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        HBox h = tecla == null ? new HBox(l) : new HBox(6, l, Atajos.tecla(tecla));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // El ticket de venta: una tabla con cada producto y sus botones.
    @SuppressWarnings("unchecked")
    private Node construirTicket() {
        Label titulo = new Label("Ticket de venta");
        titulo.getStyleClass().add("card-titulo");
        lblArticulos.getStyleClass().add("texto-suave");
        HBox cabecera = new HBox(10, titulo, lblArticulos);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("El ticket está vacío. Busca o escanea un producto."));
        tabla.setFocusTraversable(false);
        TableColumn<DetalleVenta, String> num = Tablas.columna("#", 40, (DetalleVenta d) -> String.valueOf(venta.getDetalles().indexOf(d) + 1));
        TableColumn<DetalleVenta, String> producto = Tablas.columna("Producto", 260, (DetalleVenta d) -> d.getNombreProducto());
        TableColumn<DetalleVenta, String> cant = Tablas.columna("Cant.", 70, (DetalleVenta d) -> String.valueOf(d.getCantidad()));
        TableColumn<DetalleVenta, String> precio = Tablas.columna("Precio", 110, (DetalleVenta d) -> Dinero.formato(d.getPrecioUnit())
                + (d.getPrecioUnit().compareTo(d.getPrecioOriginal()) < 0 ? "  ↓" : ""));
        TableColumn<DetalleVenta, String> subtotal = Tablas.columna("Subtotal", 110, (DetalleVenta d) -> Dinero.formato(d.getSubtotal()));
        TableColumn<DetalleVenta, Void> acciones = new TableColumn<>("Acciones");
        acciones.setPrefWidth(140);
        acciones.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Void v, boolean vacio) {
                super.updateItem(v, vacio);
                DetalleVenta d = vacio || getTableRow() == null ? null : getTableRow().getItem();
                if (d == null) {
                    setGraphic(null);
                    return;
                }
                Button menos = mini("−", () -> cambiarCantidad(d, d.getCantidad() - 1));
                Button mas = mini("+", () -> cambiarCantidad(d, d.getCantidad() + 1));
                Button quitar = mini("×", () -> cambiarCantidad(d, 0));
                setGraphic(new HBox(4, menos, mas, quitar));
            }
        });
        tabla.getColumns().addAll(num, producto, cant, precio, subtotal, acciones);

        VBox caja = new VBox(8, cabecera, tabla);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        caja.getStyleClass().add("card");
        return caja;
    }

    private Button mini(String texto, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn-mini");
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
        return b;
    }

    // Panel de la derecha: cliente, subtotal, promociones, descuento y puntos.
    private VBox construirPanelDerecho() {
        comboCliente.setPromptText("Cliente general");
        comboCliente.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Cliente c, boolean vacio) {
                super.updateItem(c, vacio);
                setText(vacio || c == null ? "Cliente general" : c.getNombre());
            }
        });
        comboCliente.setMaxWidth(Double.MAX_VALUE);
        try {
            comboCliente.getItems().setAll(clienteService.listar());
        } catch (SQLException e) {
            // sin clientes la venta sigue funcionando como "cliente general"
        }
        Button quitarCliente = mini("×", () -> comboCliente.setValue(null));
        HBox.setHgrow(comboCliente, Priority.ALWAYS);
        comboCliente.valueProperty().addListener((o, antes, ahora) -> {   // otro cliente: sus puntos
            if (venta != null) {
                ventaService.asignarCliente(venta, ahora);
                actualizarPuntos();
                refrescarCarrito();
            }
        });
        HBox filaCliente = new HBox(6, comboCliente, quitarCliente);
        filaCliente.setAlignment(Pos.CENTER_LEFT);

        campoDescuento.setPromptText("0");
        campoDescuento.setAlignment(Pos.CENTER_RIGHT);
        campoDescuento.setOnAction(e -> {
            aplicarDescuento();
            refrescarTotales();
        });
        campoDescuento.focusedProperty().addListener((o, antes, ahora) -> {
            if (!ahora) {
                aplicarDescuento();
                refrescarTotales();
            }
        });
        lblErrorDescuento.getStyleClass().add("error-texto");
        lblErrorDescuento.setWrapText(true);
        lblErrorDescuento.managedProperty().bind(lblErrorDescuento.textProperty().isNotEmpty());

        lblPromo.getStyleClass().add("texto-ok");
        filaPromo.getChildren().addAll(new Label("Promociones"), expansor(), lblPromo);
        filaPromo.setAlignment(Pos.CENTER_LEFT);
        filaPromo.setVisible(false);
        filaPromo.setManaged(false);

        lblPuntos.setWrapText(true);
        btnPuntos.getStyleClass().add("btn-secundario");
        btnPuntos.setFocusTraversable(false);
        btnPuntos.setOnAction(e -> alternarPuntos());
        VBox cajaPuntos = new VBox(6, lblPuntos, btnPuntos);
        filaPuntos.getChildren().setAll(cajaPuntos);
        filaPuntos.setVisible(false);
        filaPuntos.setManaged(false);

        VBox panel = new VBox(12, etiqueta("Cliente", "F3"), filaCliente, filaEtiquetaValor("Subtotal", lblSubtotal), filaPromo,
                etiqueta("Descuento", "F5"), campoDescuento, lblErrorDescuento, filaPuntos);
        panel.getStyleClass().add("card");
        panel.setPrefWidth(230);
        panel.setMinWidth(230);
        panel.setMaxWidth(230);
        return panel;
    }

    // Abajo: el total grande, el total del pedido y el vuelto de la última venta, y Cancelar / Cobrar.
    private Node construirBarraInferior() {
        Label etiquetaTotal = new Label("TOTAL A COBRAR");
        etiquetaTotal.getStyleClass().add("total-oscuro-etiqueta");
        lblTotal.getStyleClass().add("total-oscuro-valor");
        Region e1 = new Region();
        HBox.setHgrow(e1, Priority.ALWAYS);
        HBox barraTotal = new HBox(etiquetaTotal, e1, lblTotal);
        barraTotal.setAlignment(Pos.CENTER_LEFT);
        barraTotal.getStyleClass().add("total-oscuro");

        HBox pedido = cajita("TOTAL PEDIDO", lblTotalPedido);
        HBox vuelto = cajita("VUELTO (última venta)", lblVuelto);
        lblVuelto.getStyleClass().add("texto-ok");
        HBox.setHgrow(pedido, Priority.ALWAYS);
        HBox.setHgrow(vuelto, Priority.ALWAYS);
        HBox cajitas = new HBox(10, pedido, vuelto);

        Button cancelar = new Button("Cancelar");
        cancelar.getStyleClass().add("btn-peligro");
        cancelar.setGraphic(Atajos.tecla("F6"));
        cancelar.setContentDisplay(ContentDisplay.RIGHT);
        cancelar.setGraphicTextGap(14);
        cancelar.setMaxWidth(Double.MAX_VALUE);
        cancelar.setFocusTraversable(false);
        cancelar.setOnAction(e -> vaciarConConfirmacion());
        Button cobrar = new Button("Cobrar");
        cobrar.getStyleClass().addAll("btn-primario", "btn-grande");
        cobrar.setGraphic(Atajos.tecla("F4"));
        cobrar.setContentDisplay(ContentDisplay.RIGHT);
        cobrar.setGraphicTextGap(14);
        cobrar.setMaxWidth(Double.MAX_VALUE);
        cobrar.setFocusTraversable(false);
        cobrar.setOnAction(e -> cobrar());
        HBox.setHgrow(cancelar, Priority.ALWAYS);
        HBox.setHgrow(cobrar, Priority.ALWAYS);
        cancelar.setPrefWidth(10);
        cobrar.setPrefWidth(10);
        HBox botones = new HBox(10, cancelar, cobrar);
        // Cobrar es el más ancho
        HBox.setHgrow(cobrar, Priority.ALWAYS);
        cobrar.setMaxWidth(Double.MAX_VALUE);
        cancelar.setMaxWidth(260);

        return new VBox(8, barraTotal, cajitas, botones);
    }

    private HBox cajita(String titulo, Label valor) {
        Label t = new Label(titulo);
        t.getStyleClass().add("texto-suave");
        valor.getStyleClass().add("linea-nombre");
        HBox h = new HBox(8, t, expansor(), valor);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("card");
        return h;
    }

    // ---------------------------------------------------------------- atajos de teclado

    private void atajo(KeyEvent e) {
        // Con el recuadro de cobro abierto, Ctrl+P lleva el cursor a los precios para bajarlos.
        if (cobroAbierto && e.isControlDown() && e.getCode() == KeyCode.P && Atajos.visible(this)) {
            if (!preciosCampos.isEmpty() && !preciosCampos.get(0).isDisabled()) {
                preciosCampos.get(0).requestFocus();
                preciosCampos.get(0).selectAll();
            }
            e.consume();
            return;
        }
        // Solo actúa cuando la pantalla de venta está a la vista y no se está cobrando.
        if (pantalla == null || cobroAbierto || getScene() == null || !getChildren().contains(pantalla) || !Atajos.visible(this)) {
            return;
        }
        Node foco = getScene().getFocusOwner();
        if (e.getCode() == KeyCode.ENTER) {
            // ¿Lo que se acaba de escribir vino de la pistola? Si el cursor no está en un campo, el código se agrega igual.
            String codigo = lector.tomar();
            if (codigo != null) {
                escaneado = true;
                if (!(foco instanceof TextInputControl) && !(foco instanceof ListView)) {
                    buscador.setText(codigo);
                    agregarDesdeBuscador();
                    e.consume();
                }
            }
            return;
        }
        switch (e.getCode()) {
            case F2 -> {
                buscador.requestFocus();
                buscador.selectAll();
            }
            case F3 -> {
                comboCliente.requestFocus();
                comboCliente.show();
            }
            case F4 -> cobrar();
            case F5 -> {
                campoDescuento.requestFocus();
                campoDescuento.selectAll();
            }
            case F6 -> vaciarConConfirmacion();
            case F10 -> alternarPuntos();
            case R -> {
                if (!e.isControlDown() || ultimaVenta == null) {
                    return;
                }
                verRecibo(ultimaVenta, ultimoResultado);
            }
            case P -> {
                if (!e.isControlDown() || ultimaVenta == null) {
                    return;
                }
                imprimirRecibo(ultimaVenta, ultimoResultado);
            }
            case PLUS, ADD -> {
                if (!sePuedeAjustarCantidad(foco)) {
                    return;
                }
                ajustarUltimaLinea(1);
            }
            case MINUS, SUBTRACT -> {
                if (!sePuedeAjustarCantidad(foco)) {
                    return;
                }
                ajustarUltimaLinea(-1);
            }
            default -> {
                return;   // cualquier otra tecla se deja pasar normalmente
            }
        }
        e.consume();
    }

    // "+" y "−" solo cambian la cantidad si no se está escribiendo en un campo
    // (o si el buscador está vacío), para no estorbar al escribir.
    private boolean sePuedeAjustarCantidad(Node foco) {
        return !(foco instanceof TextInputControl) || (foco == buscador && buscador.getText().isEmpty());
    }

    private void ajustarUltimaLinea(int delta) {
        if (venta.getDetalles().isEmpty()) {
            return;
        }
        DetalleVenta ultima = venta.getDetalles().get(venta.getDetalles().size() - 1);
        cambiarCantidad(ultima, ultima.getCantidad() + delta);
    }

    // Cancelar: si hay productos, primero pregunta (Enter acepta, Esc cancela).
    private void vaciarConConfirmacion() {
        if (venta.getDetalles().isEmpty()
                || Mensajes.confirmar("Cancelar venta", "¿Quitar todos los productos del ticket?")) {
            nuevaVenta();
        }
    }

    // ---------------------------------------------------------------- productos

    // Busca las coincidencias y las muestra debajo del buscador (vacío = sin lista).
    private void cargarProductos(String texto) {
        if (texto == null || texto.isBlank()) {
            visibles = List.of();
            sugerencias.getItems().clear();
            sugerencias.setVisible(false);
            sugerencias.setManaged(false);
            return;
        }
        try {
            visibles = productoService.buscar(texto);
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los productos.\n" + e.getMessage());
            return;
        }
        List<Producto> mostrar = visibles.stream().limit(8).toList();
        sugerencias.getItems().setAll(mostrar);
        boolean hay = !mostrar.isEmpty();
        sugerencias.setVisible(hay);
        sugerencias.setManaged(hay);
        double alto = mostrar.size() * 32 + 4;
        sugerencias.setMinHeight(alto);
        sugerencias.setPrefHeight(alto);
        sugerencias.setMaxHeight(alto);
    }

    private void ocultarSugerencias() {
        buscador.clear();   // limpia y esconde la lista
        buscador.requestFocus();
    }

    // Elegir un producto de la lista de coincidencias.
    private void agregarProductoElegido(Producto p) {
        if (p.getStock() <= 0) {
            Mensajes.error(p.getNombre() + " está agotado.");
            return;
        }
        agregar(p.getCodigo());
        ocultarSugerencias();
    }

    // Enter (o el botón Agregar): si el texto es un código exacto (pistola), un solo resultado, o el que está marcado en la lista.
    private void agregarDesdeBuscador() {
        String texto = buscador.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        try {
            Producto exacto = productoDAO.buscarPorCodigo(texto);
            if (exacto != null) {
                agregar(exacto.getCodigo());
                ocultarSugerencias();
            } else if (visibles.size() == 1) {
                agregar(visibles.get(0).getCodigo());
                ocultarSugerencias();
            } else if (!visibles.isEmpty() && sugerencias.getSelectionModel().getSelectedItem() != null) {
                agregarProductoElegido(sugerencias.getSelectionModel().getSelectedItem());
            } else if (escaneado && sonidoLector) {
                Lector.pitido(false);   // la pistola leyó un código que no existe
            } else if (visibles.size() > 1) {
                sugerencias.requestFocus();   // varias coincidencias: elige con las flechas
                sugerencias.getSelectionModel().select(0);
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo buscar el producto.\n" + e.getMessage());
        } finally {
            escaneado = false;
        }
    }

    // ---------------------------------------------------------------- ticket

    private int cantidadPedida() {
        try {
            return Math.max(1, Integer.parseInt(campoCantidad.getText().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void agregar(String codigo) {
        ocultarAviso();   // empezó una venta nueva: el aviso de la anterior ya no hace falta
        int cantidad = cantidadPedida();
        boolean bien = false;
        try {
            ventaService.agregarProducto(venta, codigo, cantidad);
            bien = true;
            campoCantidad.setText("1");
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (escaneado && sonidoLector) {
                Lector.pitido(false);
            }
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo agregar el producto.\n" + e.getMessage());
        }
        if (bien && escaneado && sonidoLector) {
            Lector.pitido(true);
        }
        refrescarCarrito();
    }

    private void cambiarCantidad(DetalleVenta d, int nueva) {
        try {
            ventaService.cambiarCantidad(venta, d.getIdProducto(), nueva);
        } catch (IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo cambiar la cantidad.\n" + e.getMessage());
        }
        refrescarCarrito();
    }

    // Vuelve a dibujar el ticket y los totales.
    private void refrescarCarrito() {
        tabla.getItems().setAll(venta.getDetalles());
        int articulos = venta.getDetalles().stream().mapToInt(DetalleVenta::getCantidad).sum();
        lblArticulos.setText(articulos == 0 ? "" : "· " + articulos + (articulos == 1 ? " artículo" : " artículos"));
        refrescarTotales();
    }

    private void refrescarTotales() {
        lblSubtotal.setText(Dinero.formato(venta.getSubtotal()));
        lblTotal.setText(Dinero.formato(venta.getTotal()));
        lblTotalPedido.setText(Dinero.formato(venta.getTotal()));
        boolean hayPromo = venta.getDescuentoPromo().signum() > 0;
        filaPromo.setVisible(hayPromo);
        filaPromo.setManaged(hayPromo);
        lblPromo.setText("-" + Dinero.formato(venta.getDescuentoPromo()));
        if (venta.isUsarPuntos()) {
            actualizarPuntos();
        }
    }

    // Muestra los puntos del cliente elegido y el botón para pagar con ellos (F10).
    private void actualizarPuntos() {
        boolean mostrar = false;
        try {
            if (venta != null && venta.getIdCliente() != null) {
                int puntos = ventaService.puntosDeCliente(venta.getIdCliente());
                if (puntos > 0 || venta.isUsarPuntos()) {
                    mostrar = true;
                    lblPuntos.setText(venta.isUsarPuntos()
                            ? "Puntos usados: " + venta.getPuntosUsados() + " de " + puntos + "  (-" + Dinero.formato(venta.getDescuentoPuntos()) + ")"
                            : "Puntos del cliente: " + puntos + "  (valen " + Dinero.formato(ventaService.valorPunto().multiply(BigDecimal.valueOf(puntos))) + ")");
                    btnPuntos.setText(venta.isUsarPuntos() ? "Quitar  F10" : "Usar  F10");
                }
            }
        } catch (SQLException e) {
            // sin puntos la venta funciona igual
        }
        filaPuntos.setVisible(mostrar);
        filaPuntos.setManaged(mostrar);
    }

    // F10: paga parte de la venta con los puntos del cliente (o los quita).
    private void alternarPuntos() {
        if (venta == null) {
            return;
        }
        try {
            ventaService.asignarCliente(venta, comboCliente.getValue());
            ventaService.usarPuntos(venta, !venta.isUsarPuntos());
        } catch (IllegalStateException e) {
            Mensajes.info("Puntos", e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudieron leer los puntos.\n" + e.getMessage());
        }
        actualizarPuntos();
        refrescarCarrito();
    }

    // Lee el campo de descuento y lo aplica a la venta. Si es inválido, muestra el aviso debajo del campo.
    private boolean aplicarDescuento() {
        lblErrorDescuento.setText("");
        try {
            String texto = campoDescuento.getText();
            BigDecimal descuento = (texto == null || texto.isBlank()) ? BigDecimal.ZERO : Dinero.parsear(texto);
            ventaService.aplicarDescuento(venta, descuento);
            ventaService.validarRebaja(venta, usuario);
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            lblErrorDescuento.setText(e.getMessage());
            venta.setDescuento(BigDecimal.ZERO);
            venta.calcularTotales();
            return false;
        }
    }

    // ---------------------------------------------------------------- cobrar

    // Abre el recuadro de cobro encima de la venta. Con Esc o el botón se vuelve a la venta.
    private void cobrar() {
        if (!Seguridad.permitido(Modulo.VENTAS, Accion.CREAR, "cobrar ventas")) {
            return;
        }
        if (venta.getDetalles().isEmpty()) {
            Mensajes.error("El ticket está vacío.");
            return;
        }
        if (!aplicarDescuento()) {
            return;
        }
        refrescarTotales();
        ventaService.asignarCliente(venta, comboCliente.getValue());
        cobroAbierto = true;
        cobroEncima = new StackPane();
        cobroEncima.setStyle("-fx-background-color: rgba(15, 23, 42, 0.45);");
        cobroEncima.setPadding(new Insets(24));
        getChildren().add(cobroEncima);
        dibujarCobro(false);
    }

    // Arma (o rearma, si cambió un precio) el recuadro: precios editables arriba y el cobro debajo.
    private void dibujarCobro(boolean enfocarPrecios) {
        VBox tarjeta = new VBox(12, construirPrecios(), crearCobro());
        tarjeta.getStyleClass().add("card");
        tarjeta.setMaxWidth(1180);
        tarjeta.setMaxHeight(Double.MAX_VALUE);
        tarjeta.setPadding(new Insets(16));
        VBox.setVgrow(tarjeta.getChildren().get(1), Priority.ALWAYS);
        cobroEncima.getChildren().setAll(tarjeta);
        StackPane.setAlignment(tarjeta, Pos.CENTER);
        if (enfocarPrecios && preciosCampos.size() > 0) {
            Platform.runLater(() -> preciosCampos.get(0).requestFocus());
        }
    }

    private final java.util.List<TextField> preciosCampos = new java.util.ArrayList<>();

    private CobroView crearCobro() {
        int articulos = venta.getDetalles().stream().mapToInt(DetalleVenta::getCantidad).sum();
        // El crédito en cuotas pide un cliente y que tenga cédula.
        Cliente elegido = comboCliente.getValue();
        boolean puedeCredito = elegido != null && elegido.getDocumento() != null && !elegido.getDocumento().isBlank();
        String notaCredito = elegido == null
                ? "Para vender a crédito, vuelve a la venta (Esc) y elige un cliente con F3."
                : "El cliente necesita su cédula: agrégala en Clientes y vuelve a cobrar.";
        return new CobroView(venta.getTotal(), articulos, venta.getNombreCliente(),
                venta.getIdCliente() != null,   // solo se puede fiar si hay un cliente elegido
                puedeCredito, notaCredito, this::volverALaVenta, this::confirmarCobro);
    }

    // Los productos con su precio normal y el precio a cobrar (editable: se puede bajar, nunca subir).
    private Node construirPrecios() {
        preciosCampos.clear();
        Label titulo = new Label("Precios a cobrar");
        titulo.getStyleClass().add("card-titulo");
        boolean puedeRebajar = usuario.getDescuentoMax() > 0 && Seguridad.puede(Modulo.VENTAS, Accion.EDITAR);
        Label ayuda = new Label(puedeRebajar
                ? "Se puede bajar el precio, nunca subirlo" + (usuario.getDescuentoMax() < 100 ? " (hasta " + usuario.getDescuentoMax() + " % en total)" : "") + ".  Ctrl+P para editar."
                : "No tienes permiso para bajar precios.");
        ayuda.getStyleClass().add("texto-suave");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());

        GridPane rejilla = new GridPane();
        rejilla.setHgap(14);
        rejilla.setVgap(6);
        String[] cab = {"Producto", "Cant.", "Precio normal", "Precio a cobrar"};
        for (int i = 0; i < cab.length; i++) {
            Label l = new Label(cab[i]);
            l.getStyleClass().add("etiqueta-campo");
            rejilla.add(l, i, 0);
        }
        int fila = 1;
        for (DetalleVenta d : venta.getDetalles()) {
            Label nombre = new Label(d.getNombreProducto());
            GridPane.setHgrow(nombre, Priority.ALWAYS);
            nombre.setMaxWidth(Double.MAX_VALUE);
            TextField precio = CamposDinero.compacto();
            precio.setText(d.getPrecioUnit().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
            precio.setPrefWidth(130);
            precio.setDisable(!puedeRebajar);
            Label rebaja = new Label();
            rebaja.getStyleClass().add("texto-ok");
            BigDecimal bajada = d.getPrecioOriginal().subtract(d.getPrecioUnit());
            rebaja.setText(bajada.signum() > 0 ? "-" + Dinero.formato(bajada) : "");
            Runnable aplicar = () -> {
                BigDecimal nuevo = CamposDinero.valor(precio);
                if (nuevo.compareTo(d.getPrecioUnit()) == 0) {
                    return;
                }
                try {
                    ventaService.cambiarPrecio(venta, d.getIdProducto(), nuevo, usuario);
                    refrescarCarrito();
                    dibujarCobro(false);   // el total cambió: se vuelve a armar el cobro
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    error.setText(ex.getMessage());
                    precio.setText(d.getPrecioUnit().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
                }
            };
            precio.setOnAction(e -> aplicar.run());
            precio.focusedProperty().addListener((o, antes, ahora) -> {
                if (!ahora) {
                    aplicar.run();
                }
            });
            preciosCampos.add(precio);
            rejilla.add(nombre, 0, fila);
            rejilla.add(new Label(String.valueOf(d.getCantidad())), 1, fila);
            rejilla.add(new Label(Dinero.formato(d.getPrecioOriginal())), 2, fila);
            rejilla.add(new HBox(8, precio, rebaja), 3, fila);
            fila++;
        }
        ScrollPaneCompacto contenedor = new ScrollPaneCompacto(rejilla);
        double alto = Math.min(150, (venta.getDetalles().size() + 1) * 36 + 10);   // cabecera + una fila por producto
        contenedor.setMinHeight(alto);
        contenedor.setPrefHeight(alto);
        VBox caja = new VBox(6, titulo, ayuda, contenedor, error);
        caja.setMinHeight(Region.USE_PREF_SIZE);
        return caja;
    }

    // Un ScrollPane que no pasa de unas 4 filas de alto.
    private static class ScrollPaneCompacto extends javafx.scene.control.ScrollPane {
        ScrollPaneCompacto(Node contenido) {
            super(contenido);
            setFitToWidth(true);
            setMaxHeight(150);
            setPrefViewportHeight(Math.min(150, contenido.prefHeight(-1) + 4));
            setStyle("-fx-background-color: transparent; -fx-background: white;");
        }
    }

    private void cerrarCobro() {
        cobroAbierto = false;
        if (cobroEncima != null) {
            getChildren().remove(cobroEncima);
            cobroEncima = null;
        }
    }

    private void volverALaVenta() {
        cerrarCobro();
        buscador.requestFocus();
    }

    // El cajero confirmó el cobro: se guarda la venta y se vuelve a la pantalla de venta con el aviso.
    private void confirmarCobro(CobroView.Resultado resultado) {
        try {
            venta.setPlanCredito(resultado.plan());   // null si no se financió nada
            venta.setFinancieraNombre(resultado.financieraNombre());
            ventaService.finalizar(venta, resultado.pagos());
            Venta completa = ventaService.detalle(venta.getId());
            cerrarCobro();
            nuevaVenta();                         // ticket nuevo y limpio
            mostrarAviso(completa, resultado);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo guardar la venta; no se descontó nada del inventario.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- aviso de devolución

    // Muestra arriba la venta recién cobrada y, bien grande, la devolución.
    private void mostrarAviso(Venta completa, CobroView.Resultado r) {
        ultimaVenta = completa;      // para Ctrl+R (ver recibo) y Ctrl+P (imprimir)
        ultimoResultado = r;
        lblVuelto.setText(Dinero.formato(r.devolucion()));
        Label titulo = new Label("Venta " + completa.getNumero() + " registrada");
        titulo.getStyleClass().add("aviso-titulo");
        String pagos = completa.getPagos().stream()
                .map(p -> p.etiqueta() + " " + Dinero.formato(p.monto()))
                .collect(Collectors.joining("  ·  "));
        Label detalle = new Label("Total " + Dinero.formato(completa.getTotal()) + "  ·  " + pagos);
        detalle.getStyleClass().add("aviso-detalle");
        VBox izquierda = new VBox(2, titulo, detalle);

        VBox centro = new VBox(0);
        centro.setAlignment(Pos.CENTER);
        if (r.devolucion().signum() > 0) {
            Label et = new Label("Devolución");
            et.getStyleClass().add("aviso-etiqueta");
            Label valor = new Label(Dinero.formato(r.devolucion()));
            valor.getStyleClass().add("aviso-devolucion-valor");
            Label recibido = new Label("Recibido " + Dinero.formato(r.efectivoRecibido()));
            recibido.getStyleClass().add("aviso-detalle");
            centro.getChildren().addAll(et, valor, recibido);
        } else {
            Label exacto = new Label("Pago exacto, sin devolución");
            exacto.getStyleClass().add("aviso-titulo");
            centro.getChildren().add(exacto);
        }

        Button ver = botonAviso("Ver recibo", "Ctrl+R", () -> verRecibo(completa, r));
        Button imprimir = botonAviso("Imprimir", "Ctrl+P", () -> imprimirRecibo(completa, r));
        Button cerrar = new Button("×");
        cerrar.getStyleClass().add("btn-mini");
        cerrar.setOnAction(e -> ocultarAviso());

        HBox.setHgrow(izquierda, Priority.ALWAYS);
        HBox.setHgrow(centro, Priority.ALWAYS);
        centro.setMaxWidth(Double.MAX_VALUE);
        aviso.getChildren().setAll(izquierda, centro, ver, imprimir, cerrar);
        aviso.setVisible(true);
        aviso.setManaged(true);
    }

    private Button botonAviso(String texto, String tecla, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn-secundario");
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void ocultarAviso() {
        aviso.setVisible(false);
        aviso.setManaged(false);
        lblVuelto.setText("$0");
    }

    // Ventana con el recibo (se puede imprimir desde ahí).
    private void verRecibo(Venta completa, CobroView.Resultado r) {
        String texto;
        try {
            texto = new ReciboService().generar(completa, r.efectivoRecibido(), r.devolucion());
        } catch (SQLException e) {
            Mensajes.error("No se pudo generar el recibo.\n" + e.getMessage());
            return;
        }

        TextArea area = new TextArea(texto);
        area.setEditable(false);
        area.getStyleClass().add("recibo");
        // Ancho fijo: el recibo tiene 48 caracteres por línea y debe verse completo.
        area.setPrefWidth(480);
        area.setMinWidth(480);
        area.setPrefRowCount(Math.min(26, texto.split("\n").length + 1));

        ButtonType imprimir = new ButtonType("Imprimir recibo", ButtonBar.ButtonData.OTHER);
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Recibo de la venta " + completa.getNumero());
        dialogo.getDialogPane().setContent(area);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(imprimir, cerrar);
        // El botón Imprimir no cierra la ventana: imprime y se queda.
        dialogo.getDialogPane().lookupButton(imprimir).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            enviarAImpresora(texto);
        });
        dialogo.showAndWait();
    }

    private void imprimirRecibo(Venta completa, CobroView.Resultado r) {
        try {
            enviarAImpresora(new ReciboService().generar(completa, r.efectivoRecibido(), r.devolucion()));
        } catch (SQLException e) {
            Mensajes.error("No se pudo generar el recibo.\n" + e.getMessage());
        }
    }

    private void enviarAImpresora(String texto) {
        try {
            new ImpresoraService().imprimir(texto);
        } catch (Exception ex) {
            Mensajes.error("No se pudo imprimir el recibo.\n" + ex.getMessage());
        }
    }

    // Empieza de cero: ticket nuevo y campos limpios.
    private void nuevaVenta() {
        try {
            venta = ventaService.nuevaVenta(usuario);
        } catch (IllegalStateException | SQLException e) {
            iniciar();   // por ejemplo, si la caja se cerró
            return;
        }
        campoDescuento.clear();
        lblErrorDescuento.setText("");
        comboCliente.setValue(null);
        campoCantidad.setText("1");
        buscador.clear();
        refrescarCarrito();
        buscador.requestFocus();
    }

    // ---------------------------------------------------------------- ayudas

    private HBox filaEtiquetaValor(String etiqueta, Label valor) {
        HBox fila = new HBox(8, new Label(etiqueta), expansor(), valor);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    // Un espacio elástico que empuja lo siguiente hacia la derecha.
    private Region expansor() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
