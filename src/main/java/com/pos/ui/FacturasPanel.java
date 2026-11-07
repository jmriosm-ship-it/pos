package com.pos.ui;

import com.pos.dao.FacturaDAO;
import com.pos.modelo.Venta;
import com.pos.servicio.ReciboService;
import com.pos.servicio.VentaService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Pestaña Facturas (Reportes): los recibos de venta (se buscan por número, cliente, vendedor o producto),
 * las fotos de facturas que se suben, y una papelera para recuperar las que se borraron.
 * Teclas: F2 buscar · F4 cambiar vista · F3 subir foto · F7 ver · F8 restaurar (papelera) · Supr a la papelera.
 */
public class FacturasPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VentaService ventas = new VentaService();
    private final FacturaDAO facturas = new FacturaDAO();
    private final TextField buscador = new TextField();
    private final ToggleGroup vistas = new ToggleGroup();
    private final TableView<Venta> tablaRecibos = new TableView<>();
    private final TableView<FacturaDAO.Fila> tablaFotos = new TableView<>();
    private final Button subir = new Button("Subir foto");
    private final Button restaurar = new Button("Restaurar");
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public FacturasPanel() {
        setSpacing(12);

        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chips = new HBox(6, Chips.crear("Recibos de venta", "RECIBOS", vistas, gris),
                Chips.crear("Fotos de facturas", "FOTOS", vistas, gris), Chips.crear("Papelera", "PAPELERA", vistas, gris));
        chips.getChildren().forEach(c -> ((Region) c).setMinWidth(Region.USE_PREF_SIZE));
        vistas.selectToggle(vistas.getToggles().get(0));

        buscador.setPromptText("Buscar por número, cliente, vendedor, producto o nota (F2)");
        buscador.setOnAction(e -> recargar());
        HBox.setHgrow(buscador, Priority.ALWAYS);
        configurarBoton(subir, "F3", "btn-primario", this::subirFoto);
        configurarBoton(restaurar, "F8", "btn-secundario", this::restaurar);
        Button ver = new Button("Ver");
        configurarBoton(ver, "F7", "btn-secundario", this::ver);
        Button imprimir = new Button("Imprimir");
        configurarBoton(imprimir, "F6", "btn-secundario", this::imprimirRecibo);
        HBox barra = new HBox(10, chips, buscador, subir, restaurar, imprimir, ver);
        barra.setAlignment(Pos.CENTER_LEFT);

        tablaRecibos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaRecibos.setPlaceholder(new Label("No hay recibos con ese texto."));
        tablaRecibos.getColumns().add(Tablas.columna("Recibo", 110, (Venta v) -> v.getNumero()));
        tablaRecibos.getColumns().add(Tablas.columna("Fecha", 140, (Venta v) -> v.getFecha().format(FECHA)));
        tablaRecibos.getColumns().add(Tablas.columna("Cliente", 200, (Venta v) -> v.getNombreCliente()));
        tablaRecibos.getColumns().add(Tablas.columna("Vendió", 150, (Venta v) -> v.getNombreUsuario()));
        tablaRecibos.getColumns().add(Tablas.columna("Total", 110, (Venta v) -> Dinero.formato(v.getTotal())));
        tablaRecibos.getColumns().add(Tablas.columna("Estado", 110, (Venta v) -> "ANULADA".equals(v.getEstado()) ? "Anulada" : "Completada"));
        tablaRecibos.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ver();
            }
        });

        tablaFotos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaFotos.setPlaceholder(new Label("No hay fotos de facturas."));
        tablaFotos.getColumns().add(Tablas.columna("Fecha", 140, (FacturaDAO.Fila f) -> f.fecha().format(FECHA)));
        tablaFotos.getColumns().add(Tablas.columna("Cliente / proveedor", 180, (FacturaDAO.Fila f) -> f.cliente() == null ? "" : f.cliente()));
        tablaFotos.getColumns().add(Tablas.columna("Nota", 220, (FacturaDAO.Fila f) -> f.nota() == null ? "" : f.nota()));
        tablaFotos.getColumns().add(Tablas.columna("Subió", 130, (FacturaDAO.Fila f) -> f.subidaPor()));
        tablaFotos.getColumns().add(Tablas.columna("Archivo", 200, (FacturaDAO.Fila f) -> Path.of(f.archivo()).getFileName().toString()));
        tablaFotos.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ver();
            }
        });
        tablaFotos.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) {
                aPapelera();
                e.consume();
            }
        });
        VBox.setVgrow(tablaRecibos, Priority.ALWAYS);
        VBox.setVgrow(tablaFotos, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Buscar"), Atajos.etiqueta("F4", "Cambiar vista"),
                Atajos.etiqueta("F3", "Subir foto"), Atajos.etiqueta("F6", "Imprimir recibo"), Atajos.etiqueta("F7", "Ver"), Atajos.etiqueta("Supr", "A la papelera"),
                Atajos.etiqueta("F8", "Restaurar"));
        getChildren().addAll(barra, tablaRecibos, tablaFotos, teclas);
        Chips.alElegir(vistas, this::recargar);

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

    private void configurarBoton(Button b, String tecla, String estilo, Runnable accion) {
        b.getStyleClass().add(estilo);
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
    }

    private String vista() {
        return (String) Chips.valor(vistas);
    }

    public void recargar() {
        String v = vista();
        boolean recibos = "RECIBOS".equals(v);
        tablaRecibos.setVisible(recibos);
        tablaRecibos.setManaged(recibos);
        tablaFotos.setVisible(!recibos);
        tablaFotos.setManaged(!recibos);
        subir.setVisible("FOTOS".equals(v));
        subir.setManaged("FOTOS".equals(v));
        restaurar.setVisible("PAPELERA".equals(v));
        restaurar.setManaged("PAPELERA".equals(v));
        try {
            if (recibos) {
                tablaRecibos.getItems().setAll(ventas.buscarRecibos(buscador.getText()));
            } else {
                tablaFotos.getItems().setAll(facturas.listar("PAPELERA".equals(v), buscador.getText()));
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la lista.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F2 -> {
                buscador.requestFocus();
                buscador.selectAll();
            }
            case F3 -> subirFoto();
            case F4 -> Chips.siguiente(vistas);
            case F6 -> imprimirRecibo();
            case F7 -> ver();
            case F8 -> restaurar();
            default -> {
                return;
            }
        }
        e.consume();
    }


    // F6: imprime el recibo de la venta elegida (en la vista de recibos).
    private void imprimirRecibo() {
        if (!"RECIBOS".equals(vista())) {
            return;
        }
        Venta v = tablaRecibos.getSelectionModel().getSelectedItem();
        if (v == null) {
            return;
        }
        try {
            new com.pos.servicio.ImpresoraService().imprimir(new ReciboService().generar(ventas.detalle(v.getId())));
            Mensajes.info("Recibo enviado", "El recibo " + v.getNumero() + " se mandó a la impresora.");
        } catch (Exception e) {
            Mensajes.error("No se pudo imprimir el recibo.\n" + e.getMessage());
        }
    }

    // Ver el recibo de la venta, o abrir la foto con el programa del computador.
    private void ver() {
        if ("RECIBOS".equals(vista())) {
            Venta v = tablaRecibos.getSelectionModel().getSelectedItem();
            if (v == null) {
                return;
            }
            try {
                TicketDialog.mostrar("Recibo " + v.getNumero(), new ReciboService().generar(ventas.detalle(v.getId())));
            } catch (SQLException e) {
                Mensajes.error("No se pudo mostrar el recibo.\n" + e.getMessage());
            }
        } else {
            FacturaDAO.Fila f = tablaFotos.getSelectionModel().getSelectedItem();
            if (f == null) {
                return;
            }
            try {
                java.awt.Desktop.getDesktop().open(new File(f.archivo()));
            } catch (Exception e) {
                Mensajes.error("No se pudo abrir el archivo.\n" + f.archivo());
            }
        }
    }

    // F3: elegir una foto (o PDF), anotar de quién es y guardarla en la carpeta de facturas.
    private void subirFoto() {
        if (!"FOTOS".equals(vista())) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Foto o PDF de la factura");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes y PDF", "*.png", "*.jpg", "*.jpeg", "*.pdf"));
        File archivo = selector.showOpenDialog(getScene().getWindow());
        if (archivo == null) {
            return;
        }
        TextField cliente = new TextField();
        cliente.setPromptText("Cliente o proveedor");
        TextField nota = new TextField();
        nota.setPromptText("Nota (ej: compra de colonias)");
        Label e1 = new Label("Cliente o proveedor");
        e1.getStyleClass().add("etiqueta-campo");
        Label e2 = new Label("Nota");
        e2.getStyleClass().add("etiqueta-campo");
        VBox contenido = new VBox(6, e1, cliente, e2, nota);
        contenido.setPrefWidth(420);
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Datos de la factura");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != guardar) {
            return;
        }
        try {
            Path carpeta = Path.of(System.getProperty("user.home"), "Documents", "POS-facturas");
            Files.createDirectories(carpeta);
            String nombre = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + archivo.getName();
            Path destino = carpeta.resolve(nombre);
            Files.copy(archivo.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
            facturas.insertar(cliente.getText().isBlank() ? null : cliente.getText().trim(),
                    nota.getText().isBlank() ? null : nota.getText().trim(), destino.toString(), Sesion.usuario().getId());
            recargar();
        } catch (IOException | SQLException e) {
            Mensajes.error("No se pudo guardar la factura.\n" + e.getMessage());
        }
    }

    private void aPapelera() {
        FacturaDAO.Fila f = tablaFotos.getSelectionModel().getSelectedItem();
        if (f == null || !"FOTOS".equals(vista())) {
            return;
        }
        try {
            facturas.marcarEliminada(f.id(), true);
            recargar();
        } catch (SQLException e) {
            Mensajes.error("No se pudo mover a la papelera.\n" + e.getMessage());
        }
    }

    private void restaurar() {
        FacturaDAO.Fila f = tablaFotos.getSelectionModel().getSelectedItem();
        if (f == null || !"PAPELERA".equals(vista())) {
            return;
        }
        try {
            facturas.marcarEliminada(f.id(), false);
            recargar();
        } catch (SQLException e) {
            Mensajes.error("No se pudo restaurar.\n" + e.getMessage());
        }
    }
}
