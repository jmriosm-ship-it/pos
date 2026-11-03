package com.pos.ui;

import com.pos.dao.ProductoDAO;
import com.pos.modelo.Categoria;
import com.pos.modelo.Cliente;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Pago;
import com.pos.modelo.Producto;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;
import com.pos.servicio.CajaService;
import com.pos.servicio.CategoriaService;
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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
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
 * La pantalla de ventas (la más importante).
 * Izquierda: buscador, categorías y productos. Derecha: el carrito con el total y el botón Cobrar.
 * Al cobrar se abre la pantalla de cobro (CobroView), donde se reparte el pago entre efectivo,
 * transferencia y tarjeta. Al confirmar se vuelve aquí, con un aviso que muestra la DEVOLUCIÓN.
 * Atajos: F2 = buscar / escanear, F4 = cobrar. Enter en el buscador agrega el producto.
 * Si el usuario no tiene la caja abierta, primero le pide abrirla.
 */
public class VentasView extends StackPane {

    private final VentaService ventaService = new VentaService();
    private final CajaService cajaService = new CajaService();
    private final ProductoService productoService = new ProductoService();
    private final ClienteService clienteService = new ClienteService();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final Usuario usuario = Sesion.usuario();
    private final Runnable alCambiarCaja;

    private Venta venta;                           // el carrito actual
    private List<Producto> visibles = List.of();   // productos que se ven en la grilla
    private Node pantalla;                         // la pantalla de venta (para volver desde el cobro)

    private final TextField buscador = new TextField();
    private final FlowPane grilla = new FlowPane(12, 12);
    private final VBox lineas = new VBox();
    private final Label lblSubtotal = new Label();
    private final Label lblTotal = new Label();
    private final Label lblErrorDescuento = new Label();
    private final TextField campoDescuento = new TextField();
    private final ComboBox<Cliente> comboCliente = new ComboBox<>();

    // Aviso verde que aparece al volver de cobrar (con la devolución).
    private final HBox aviso = new HBox(16);

    // Filtro por categoría: una fila de "chips" (botones redondeados) donde solo uno está elegido.
    private static final int TODAS = -1;   // valor del chip "Todos"; 0 es "Sin categoría"
    private final CategoriaService categoriaService = new CategoriaService();
    private final FlowPane chips = new FlowPane(8, 8);
    private final ToggleGroup grupoCategorias = new ToggleGroup();
    private boolean reconstruyendoChips = false;

    // Atajos de teclado: se escuchan en toda la ventana mientras esta pantalla está a la vista.
    private final EventHandler<KeyEvent> manejadorAtajos = this::atajo;
    private Venta ultimaVenta;                    // la última venta cobrada (para Ver recibo / Imprimir con F7 y F8)
    private CobroView.Resultado ultimoResultado;

    public VentasView(Runnable alCambiarCaja) {
        this.alCambiarCaja = alCambiarCaja;
        // Al elegir otro chip se vuelven a mostrar los productos filtrados.
        grupoCategorias.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (reconstruyendoChips) {
                return;
            }
            if (ahora == null) {
                antes.setSelected(true);   // siempre debe haber uno elegido
            } else {
                cargarProductos(buscador.getText());
            }
        });
        // Los atajos se conectan a la ventana cuando esta pantalla entra en ella y se sueltan cuando sale.
        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorAtajos);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorAtajos);
            }
        });
        iniciar();
    }

    // Decide qué mostrar: el aviso de "abre la caja" o la pantalla de venta.
    private void iniciar() {
        try {
            if (cajaService.cajaAbierta(usuario) == null) {
                getChildren().setAll(avisoSinCaja());
                return;
            }
            venta = ventaService.nuevaVenta(usuario);
            pantalla = construirPantalla();
            getChildren().setAll(pantalla);
            cargarProductos("");
            refrescarCarrito();
            Platform.runLater(buscador::requestFocus);
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la pantalla de ventas.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- sin caja

    private Node avisoSinCaja() {
        Label titulo = new Label("Primero abre la caja");
        titulo.getStyleClass().add("card-titulo");
        Label texto = new Label("Para vender necesitas un turno de caja abierto. Escribe con cuánto efectivo empiezas.");
        texto.setWrapText(true);
        texto.getStyleClass().add("texto-suave");
        TextField monto = new TextField();
        monto.setPromptText("Monto inicial, ej: 50000");
        Button abrir = new Button("Abrir caja");
        abrir.getStyleClass().addAll("btn-primario", "btn-grande");
        abrir.setMaxWidth(Double.MAX_VALUE);
        abrir.setDefaultButton(true);
        abrir.setOnAction(e -> {
            try {
                cajaService.abrirCaja(usuario, Dinero.parsear(monto.getText()));
                alCambiarCaja.run();
                iniciar();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Mensajes.error(ex.getMessage());
            } catch (SQLException ex) {
                Mensajes.error("No se pudo abrir la caja.\n" + ex.getMessage());
            }
        });

        VBox tarjeta = new VBox(10, titulo, texto, monto, abrir);
        tarjeta.getStyleClass().add("card");
        tarjeta.setMaxWidth(420);
        tarjeta.setMaxHeight(Region.USE_PREF_SIZE);
        tarjeta.setPadding(new Insets(24));
        return tarjeta;
    }

    // ---------------------------------------------------------------- pantalla

    private Node construirPantalla() {
        buscador.setPromptText("Buscar por nombre o escanear código (F2)");
        buscador.getStyleClass().add("campo-buscar");
        buscador.textProperty().addListener((o, antes, ahora) -> cargarProductos(ahora));
        buscador.setOnAction(e -> agregarDesdeBuscador());

        grilla.setPadding(new Insets(2));
        ScrollPane scroll = new ScrollPane(grilla);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Debajo del buscador, las teclas de la pantalla (el placeholder del campo se oculta al tener el foco).
        FlowPane teclas = new FlowPane(16, 4);
        teclas.getChildren().addAll(
                Atajos.etiqueta("F2", "Buscar"), Atajos.etiqueta("F3", "Cliente"), Atajos.etiqueta("F4", "Cobrar"),
                Atajos.etiqueta("F5", "Descuento"), Atajos.etiqueta("F6", "Vaciar"), Atajos.etiqueta("F9", "Categoría"),
                Atajos.etiqueta("+  −", "Cantidad"));
        VBox izquierda = new VBox(12, new VBox(6, buscador, teclas), construirChips(), scroll);
        HBox.setHgrow(izquierda, Priority.ALWAYS);

        HBox raiz = new HBox(16, izquierda, construirCarrito());
        VBox.setVgrow(raiz, Priority.ALWAYS);

        // El aviso de devolución va arriba de todo; oculto hasta que se cobre una venta.
        aviso.getStyleClass().add("aviso-ok");
        aviso.setAlignment(Pos.CENTER_LEFT);
        aviso.setVisible(false);
        aviso.setManaged(false);

        return new VBox(12, aviso, raiz);
    }

    // ---------------------------------------------------------------- atajos de teclado

    // F2 buscar · F3 cliente · F4 cobrar · F5 descuento · F6 vaciar · F7 ver recibo · F8 imprimir
    // F9 siguiente categoría · "+" y "−" suben o bajan la cantidad del último producto agregado.
    private void atajo(KeyEvent e) {
        // Solo actúa cuando la pantalla de venta está a la vista (no mientras se está cobrando).
        if (pantalla == null || getScene() == null || !getChildren().contains(pantalla)) {
            return;
        }
        Node foco = getScene().getFocusOwner();
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
            case F7 -> {
                if (aviso.isVisible() && ultimaVenta != null) {
                    verRecibo(ultimaVenta, ultimoResultado);
                }
            }
            case F8 -> {
                if (aviso.isVisible() && ultimaVenta != null) {
                    imprimirRecibo(ultimaVenta, ultimoResultado);
                }
            }
            case F9 -> siguienteCategoria();
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

    // Pasa al siguiente chip de categoría (y da la vuelta al llegar al último).
    private void siguienteCategoria() {
        List<javafx.scene.control.Toggle> chipsLista = grupoCategorias.getToggles();
        if (chipsLista.size() < 2) {
            return;
        }
        int actual = chipsLista.indexOf(grupoCategorias.getSelectedToggle());
        grupoCategorias.selectToggle(chipsLista.get((actual + 1) % chipsLista.size()));
    }

    // Vaciar la venta: si hay productos, primero pregunta (Enter acepta, Esc cancela).
    private void vaciarConConfirmacion() {
        if (venta.getDetalles().isEmpty()
                || Mensajes.confirmar("Vaciar venta", "¿Quitar todos los productos del carrito?")) {
            nuevaVenta();
        }
    }

    private VBox construirCarrito() {
        Label titulo = new Label("Venta actual");
        titulo.getStyleClass().add("card-titulo");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Button vaciar = new Button("Vaciar");
        vaciar.getStyleClass().add("btn-secundario");
        vaciar.setGraphic(Atajos.tecla("F6"));
        vaciar.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        vaciar.setGraphicTextGap(8);
        vaciar.setFocusTraversable(false);
        vaciar.setOnAction(e -> vaciarConConfirmacion());
        HBox cabecera = new HBox(titulo, espacio, vaciar);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        // Cliente (opcional)
        comboCliente.setPromptText("Cliente general  (F3 para elegir)");
        // Sin esto, al quitar el cliente el cuadro queda en blanco en vez de mostrar "Cliente general".
        comboCliente.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Cliente c, boolean vacio) {
                super.updateItem(c, vacio);
                setText(vacio || c == null ? "Cliente general  (F3 para elegir)" : c.getNombre());
            }
        });
        comboCliente.setMaxWidth(Double.MAX_VALUE);
        try {
            comboCliente.getItems().setAll(clienteService.listar());
        } catch (SQLException e) {
            // sin clientes la venta sigue funcionando como "cliente general"
        }
        Button quitarCliente = new Button("×");
        quitarCliente.getStyleClass().add("btn-mini");
        quitarCliente.setOnAction(e -> comboCliente.setValue(null));
        HBox.setHgrow(comboCliente, Priority.ALWAYS);
        HBox filaCliente = new HBox(6, comboCliente, quitarCliente);
        filaCliente.setAlignment(Pos.CENTER_LEFT);

        // Líneas del carrito
        ScrollPane scroll = new ScrollPane(lineas);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Totales
        campoDescuento.setPromptText("0");
        campoDescuento.setPrefWidth(110);
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
        lblTotal.getStyleClass().add("total-valor");

        HBox filaSubtotal = filaEtiquetaValor("Subtotal", lblSubtotal);
        HBox filaDescuento = new HBox(8, new Label("Descuento"), Atajos.tecla("F5"), expansor(), campoDescuento);
        filaDescuento.setAlignment(Pos.CENTER_LEFT);
        HBox filaTotal = new HBox(8, new Label("Total"), expansor(), lblTotal);
        filaTotal.setAlignment(Pos.BOTTOM_LEFT);

        Button cobrar = new Button("Cobrar");
        cobrar.setGraphic(Atajos.tecla("F4"));
        cobrar.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        cobrar.setGraphicTextGap(12);
        cobrar.getStyleClass().addAll("btn-primario", "btn-grande");
        cobrar.setMaxWidth(Double.MAX_VALUE);
        cobrar.setOnAction(e -> cobrar());

        VBox carrito = new VBox(10, cabecera, filaCliente, scroll, filaSubtotal, filaDescuento,
                lblErrorDescuento, filaTotal, cobrar);
        carrito.getStyleClass().add("card");
        carrito.setPrefWidth(370);
        carrito.setMinWidth(370);
        carrito.setMaxWidth(370);
        return carrito;
    }

    // ---------------------------------------------------------------- categorías

    // Arma la fila de chips: "Todos" y una por cada categoría que tenga productos.
    private FlowPane construirChips() {
        reconstruyendoChips = true;   // mientras se arman, el listener no debe reaccionar
        chips.getChildren().clear();
        grupoCategorias.getToggles().clear();

        chips.getChildren().add(crearChip("Todos", TODAS, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")));
        try {
            for (Categoria c : categoriaService.listarConSinCategoria()) {
                if (c.getTotalProductos() > 0) {
                    chips.getChildren().add(crearChip(c.getNombre(), c.getId(), ColoresCategoria.de(c.getId())));
                }
            }
        } catch (SQLException e) {
            // sin categorías la pantalla funciona igual, solo con el chip "Todos"
        }
        grupoCategorias.selectToggle(grupoCategorias.getToggles().get(0));
        reconstruyendoChips = false;
        return chips;
    }

    // Un chip: de color suave cuando no está elegido y de color fuerte cuando sí.
    private ToggleButton crearChip(String texto, int valor, ColoresCategoria.Color color) {
        ToggleButton chip = new ToggleButton(texto);
        chip.setUserData(valor);
        chip.setToggleGroup(grupoCategorias);
        chip.getStyleClass().add("chip-cat");
        Runnable pintar = () -> chip.setStyle(chip.isSelected()
                ? "-fx-background-color: " + color.fuerte() + "; -fx-text-fill: white;"
                : "-fx-background-color: " + color.suave() + "; -fx-text-fill: " + color.texto() + ";");
        chip.selectedProperty().addListener((o, antes, ahora) -> pintar.run());
        pintar.run();
        return chip;
    }

    // Qué categoría está elegida: TODAS (-1), 0 = sin categoría, o el id de la categoría.
    private int filtroActual() {
        return grupoCategorias.getSelectedToggle() == null
                ? TODAS : (Integer) grupoCategorias.getSelectedToggle().getUserData();
    }

    // ---------------------------------------------------------------- productos

    // Llena la grilla con los productos que coinciden con el texto (vacío = todos).
    private void cargarProductos(String texto) {
        grilla.getChildren().clear();
        try {
            visibles = productoService.buscar(texto);
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los productos.\n" + e.getMessage());
            return;
        }
        // Si hay un chip de categoría elegido, solo se muestran los productos de esa categoría.
        int filtro = filtroActual();
        if (filtro != TODAS) {
            visibles = visibles.stream()
                    .filter(p -> filtro == 0 ? p.getIdCategoria() == null
                            : p.getIdCategoria() != null && p.getIdCategoria() == filtro)
                    .toList();
        }
        if (visibles.isEmpty()) {
            Label vacio = new Label("No se encontraron productos.");
            vacio.getStyleClass().add("texto-suave");
            grilla.getChildren().add(vacio);
        }
        for (Producto p : visibles) {
            grilla.getChildren().add(crearTile(p));
        }
    }

    private VBox crearTile(Producto p) {
        ColoresCategoria.Color color = ColoresCategoria.de(p.getIdCategoria());

        // Franja fina de color arriba de la tarjeta: es el color de su categoría.
        Region franja = new Region();
        franja.setMinHeight(4);
        franja.setPrefHeight(4);
        franja.setMaxHeight(4);
        franja.setStyle("-fx-background-color: " + color.fuerte() + "; -fx-background-radius: 9 9 0 0;");

        Region formaIcono = Iconos.crear(Iconos.PRODUCTOS, 22);
        formaIcono.setStyle(formaIcono.getStyle() + "-fx-background-color: " + color.fuerte() + ";");
        StackPane icono = new StackPane(formaIcono);
        icono.getStyleClass().add("tile-icono");
        icono.setStyle("-fx-background-color: " + color.suave() + ";");

        Label nombre = new Label(p.getNombre());
        nombre.getStyleClass().add("tile-nombre");
        nombre.setWrapText(true);
        nombre.setMinHeight(34);
        nombre.setMaxHeight(34);

        Label precio = new Label(Dinero.formato(p.getPrecioVenta()));
        precio.getStyleClass().add("tile-precio");

        Label stock = new Label(p.getStock() > 0 ? p.getStock() + " en stock" : "Agotado");
        stock.getStyleClass().add("tile-stock");
        if (p.getStock() <= 0 || p.tieneStockBajo()) {
            stock.getStyleClass().add("texto-peligro");
        }

        VBox contenido = new VBox(5, icono, nombre, precio, stock);
        contenido.setPadding(new Insets(10, 12, 12, 12));
        VBox tile = new VBox(franja, contenido);
        tile.getStyleClass().add("tile");
        tile.setStyle("-fx-padding: 0;");   // el relleno lo lleva "contenido"; así la franja llega hasta los bordes
        tile.setPrefWidth(160);
        tile.setMinWidth(160);
        if (p.getStock() <= 0) {
            tile.getStyleClass().add("tile-agotado");
        } else {
            tile.setOnMouseClicked(e -> agregar(p.getCodigo()));
        }
        return tile;
    }

    // Enter en el buscador: si el texto es un código exacto (lector de barras) o solo queda
    // un producto en la lista, lo agrega al carrito.
    private void agregarDesdeBuscador() {
        String texto = buscador.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        try {
            Producto exacto = productoDAO.buscarPorCodigo(texto);
            if (exacto != null) {
                agregar(exacto.getCodigo());
                buscador.clear();
            } else if (visibles.size() == 1) {
                agregar(visibles.get(0).getCodigo());
                buscador.clear();
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo buscar el producto.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- carrito

    private void agregar(String codigo) {
        ocultarAviso();   // empezó una venta nueva: el aviso de la anterior ya no hace falta
        try {
            ventaService.agregarProducto(venta, codigo, 1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo agregar el producto.\n" + e.getMessage());
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

    // Vuelve a dibujar las líneas del carrito y los totales.
    private void refrescarCarrito() {
        lineas.getChildren().clear();
        if (venta.getDetalles().isEmpty()) {
            Label vacio = new Label("El carrito está vacío.\nToca un producto o escanea su código.");
            vacio.getStyleClass().add("texto-suave");
            vacio.setWrapText(true);
            vacio.setPadding(new Insets(14, 0, 0, 0));
            lineas.getChildren().add(vacio);
        }
        for (DetalleVenta d : venta.getDetalles()) {
            lineas.getChildren().add(crearLinea(d));
        }
        refrescarTotales();
    }

    private HBox crearLinea(DetalleVenta d) {
        Label nombre = new Label(d.getNombreProducto());
        nombre.getStyleClass().add("linea-nombre");
        nombre.setWrapText(true);
        Label unitario = new Label(Dinero.formato(d.getPrecioUnit()) + " c/u");
        unitario.getStyleClass().addAll("texto-suave", "texto-pequeno");
        VBox info = new VBox(1, nombre, unitario);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button menos = new Button("−");
        menos.getStyleClass().add("btn-mini");
        menos.setOnAction(e -> cambiarCantidad(d, d.getCantidad() - 1));
        Label cantidad = new Label(String.valueOf(d.getCantidad()));
        cantidad.setMinWidth(22);
        cantidad.setAlignment(Pos.CENTER);
        Button mas = new Button("+");
        mas.getStyleClass().add("btn-mini");
        mas.setOnAction(e -> cambiarCantidad(d, d.getCantidad() + 1));

        Label subtotal = new Label(Dinero.formato(d.getSubtotal()));
        subtotal.setMinWidth(74);
        subtotal.setAlignment(Pos.CENTER_RIGHT);
        subtotal.getStyleClass().add("linea-nombre");

        Button quitar = new Button("×");
        quitar.getStyleClass().add("btn-mini");
        quitar.setOnAction(e -> cambiarCantidad(d, 0));

        HBox fila = new HBox(6, info, menos, cantidad, mas, subtotal, quitar);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("linea");
        return fila;
    }

    private void refrescarTotales() {
        lblSubtotal.setText(Dinero.formato(venta.getSubtotal()));
        lblTotal.setText(Dinero.formato(venta.getTotal()));
    }

    // Lee el campo de descuento y lo aplica a la venta. Si es inválido, muestra el aviso debajo del campo.
    private boolean aplicarDescuento() {
        lblErrorDescuento.setText("");
        try {
            String texto = campoDescuento.getText();
            BigDecimal descuento = (texto == null || texto.isBlank()) ? BigDecimal.ZERO : Dinero.parsear(texto);
            ventaService.aplicarDescuento(venta, descuento);
            return true;
        } catch (IllegalArgumentException e) {
            lblErrorDescuento.setText(e.getMessage());
            venta.setDescuento(BigDecimal.ZERO);
            venta.calcularTotales();
            return false;
        }
    }

    // ---------------------------------------------------------------- cobrar

    // Abre la pantalla de cobro en lugar de la de venta. Con Esc o el botón se vuelve a la venta.
    private void cobrar() {
        if (venta.getDetalles().isEmpty()) {
            Mensajes.error("El carrito está vacío.");
            return;
        }
        if (!aplicarDescuento()) {
            return;
        }
        refrescarTotales();
        ventaService.asignarCliente(venta, comboCliente.getValue());

        int articulos = venta.getDetalles().stream().mapToInt(DetalleVenta::getCantidad).sum();
        CobroView cobro = new CobroView(venta.getTotal(), articulos, venta.getNombreCliente(),
                venta.getIdCliente() != null,   // solo se puede fiar si hay un cliente elegido
                this::volverALaVenta, this::confirmarCobro);
        getChildren().setAll(cobro);
    }

    private void volverALaVenta() {
        getChildren().setAll(pantalla);
        buscador.requestFocus();
    }

    // El cajero confirmó el cobro: se guarda la venta y se vuelve a la pantalla de venta con el aviso.
    private void confirmarCobro(CobroView.Resultado resultado) {
        try {
            ventaService.finalizar(venta, resultado.pagos());
            Venta completa = ventaService.detalle(venta.getId());
            nuevaVenta();                         // carrito nuevo y limpio
            getChildren().setAll(pantalla);       // de vuelta a la pantalla de venta
            mostrarAviso(completa, resultado);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo guardar la venta; no se descontó nada del inventario.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- aviso de devolución

    // Muestra arriba de la pantalla de venta la venta recién cobrada y, bien grande, la devolución.
    private void mostrarAviso(Venta completa, CobroView.Resultado r) {
        ultimaVenta = completa;      // para F7 (ver recibo) y F8 (imprimir)
        ultimoResultado = r;
        Label titulo = new Label("Venta #" + completa.getId() + " registrada");
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
            Label etiqueta = new Label("Devolución");
            etiqueta.getStyleClass().add("aviso-etiqueta");
            Label valor = new Label(Dinero.formato(r.devolucion()));
            valor.getStyleClass().add("aviso-devolucion-valor");
            Label recibido = new Label("Recibido " + Dinero.formato(r.efectivoRecibido()));
            recibido.getStyleClass().add("aviso-detalle");
            centro.getChildren().addAll(etiqueta, valor, recibido);
        } else {
            Label exacto = new Label("Pago exacto, sin devolución");
            exacto.getStyleClass().add("aviso-titulo");
            centro.getChildren().add(exacto);
        }

        Button ver = new Button("Ver recibo");
        ver.getStyleClass().add("btn-secundario");
        ver.setGraphic(Atajos.tecla("F7"));
        ver.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        ver.setGraphicTextGap(8);
        ver.setFocusTraversable(false);
        ver.setOnAction(e -> verRecibo(completa, r));
        Button imprimir = new Button("Imprimir");
        imprimir.getStyleClass().add("btn-secundario");
        imprimir.setGraphic(Atajos.tecla("F8"));
        imprimir.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        imprimir.setGraphicTextGap(8);
        imprimir.setFocusTraversable(false);
        imprimir.setOnAction(e -> imprimirRecibo(completa, r));
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

    private void ocultarAviso() {
        aviso.setVisible(false);
        aviso.setManaged(false);
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
        dialogo.setTitle("POS 2.0");
        dialogo.setHeaderText("Recibo de la venta #" + completa.getId());
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

    // Empieza de cero: carrito nuevo y campos limpios.
    private void nuevaVenta() {
        try {
            venta = ventaService.nuevaVenta(usuario);
        } catch (IllegalStateException | SQLException e) {
            iniciar();   // por ejemplo, si la caja se cerró: vuelve a pedir abrirla
            return;
        }
        campoDescuento.clear();
        lblErrorDescuento.setText("");
        comboCliente.setValue(null);
        buscador.clear();
        cargarProductos("");
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
