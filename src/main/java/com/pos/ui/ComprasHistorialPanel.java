package com.pos.ui;

import com.pos.modelo.Accion;
import com.pos.modelo.Compra;
import com.pos.modelo.Modulo;
import com.pos.servicio.CompraService;
import com.pos.servicio.Permisos;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Pestaña "Compras": las compras hechas a proveedores en los últimos 90 días, la más reciente primero.
 * Se pueden ver y editar (F6 o Enter) y anular (F9): al anular, el stock que subió se devuelve.
 */
public class ComprasHistorialPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CompraService servicio = new CompraService();
    private final TableView<Compra> tabla = new TableView<>();
    private final Label contador = new Label();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public ComprasHistorialPanel() {
        setSpacing(12);
        contador.getStyleClass().add("texto-suave");

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Aún no hay compras en los últimos 90 días."));
        tabla.getColumns().add(Tablas.columna("Compra", 90, (Compra c) -> "#" + c.getId()));
        tabla.getColumns().add(Tablas.columna("Fecha", 160, (Compra c) -> c.getFecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Proveedor", 280, (Compra c) -> c.getNombreProveedor()));
        tabla.getColumns().add(Tablas.columna("Total", 140, (Compra c) -> Dinero.formato(c.getTotal())));
        tabla.getColumns().add(Tablas.columna("Pagado", 120, (Compra c) -> c.getPagado() == null ? "—" : Dinero.formato(c.getPagado())));
        tabla.getColumns().add(Tablas.columna("Debe", 120, (Compra c) -> c.getSaldo().signum() > 0 ? Dinero.formato(c.getSaldo()) : ""));
        tabla.getColumns().add(Tablas.columna("Estado", 110, (Compra c) -> c.isAnulada() ? "Anulada" : "Registrada"));
        tabla.setRowFactory(t -> {
            javafx.scene.control.TableRow<Compra> fila = new javafx.scene.control.TableRow<>();
            fila.itemProperty().addListener((o, antes, c) -> fila.setStyle(c != null && c.isAnulada() ? "-fx-opacity: 0.55;" : ""));
            fila.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !fila.isEmpty()) {
                    verEditar();
                }
            });
            return fila;
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Button ver = new Button("Ver / Editar");
        ver.setGraphic(Atajos.tecla("F6"));
        ver.getStyleClass().add("btn-secundario");
        ver.setOnAction(e -> verEditar());
        Button abonar = new Button("Pagar al proveedor");
        abonar.setGraphic(Atajos.tecla("F7"));
        abonar.getStyleClass().add("btn-secundario");
        abonar.setOnAction(e -> abonar());
        Button anular = new Button("Anular compra");
        anular.setGraphic(Atajos.tecla("F9"));
        anular.getStyleClass().add("btn-peligro");
        anular.setOnAction(e -> anular());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, contador, espacio, abonar, ver, anular);
        barra.setAlignment(Pos.CENTER_LEFT);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F6", "Ver o editar"), Atajos.etiqueta("F7", "Pagar al proveedor"), Atajos.etiqueta("F9", "Anular"),
                Atajos.etiqueta("↑ ↓", "Elegir compra"));

        getChildren().addAll(barra, tabla, teclas);

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

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        if (e.getCode() == KeyCode.F6) {
            verEditar();
            e.consume();
        } else if (e.getCode() == KeyCode.F7) {
            abonar();
            e.consume();
        } else if (e.getCode() == KeyCode.F9) {
            anular();
            e.consume();
        }
    }

    private Compra elegida() {
        Compra c = tabla.getSelectionModel().getSelectedItem();
        if (c == null) {
            Mensajes.info("Elige una compra", "Selecciona una compra de la tabla primero.");
        }
        return c;
    }

    private void verEditar() {
        Compra c = elegida();
        if (c != null && CompraEditarDialog.abrir(c)) {
            recargar();
        }
    }

    // Paga (o abona) a un proveedor una compra que quedó debiéndose.
    private void abonar() {
        if (!Seguridad.permitido(Modulo.INVENTARIO, Accion.CREAR, "registrar pagos a proveedores")) {
            return;
        }
        Compra c = elegida();
        if (c == null) {
            return;
        }
        if (c.getSaldo().signum() <= 0) {
            Mensajes.info("Sin deuda", "Esa compra no tiene saldo pendiente con el proveedor.");
            return;
        }
        if (PagoProveedorDialog.abrir(c)) {
            recargar();
        }
    }

    private void anular() {
        if (!Seguridad.permitido(Modulo.INVENTARIO, Accion.BORRAR, "anular compras")) {
            return;
        }
        Compra c = elegida();
        if (c == null) {
            return;
        }
        if (c.isAnulada()) {
            Mensajes.info("Ya está anulada", "Esa compra ya estaba anulada.");
            return;
        }
        TextInputDialog pregunta = new TextInputDialog();
        pregunta.setTitle(Marca.PRODUCTO);
        pregunta.setHeaderText("Anular la compra #" + c.getId() + " (" + Dinero.formato(c.getTotal()) + ")");
        pregunta.setContentText("Motivo:");
        pregunta.getDialogPane().getStylesheets().add(Mensajes.CSS);
        Optional<String> motivo = pregunta.showAndWait();
        if (motivo.isEmpty()) {
            return;
        }
        try {
            servicio.anular(Sesion.usuario(), c, motivo.get());
            Mensajes.info("Compra anulada", "La compra #" + c.getId() + " quedó anulada y el stock que había subido se devolvió.");
            recargar();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Mensajes.error(ex.getMessage());
        } catch (SQLException ex) {
            Mensajes.error("No se pudo anular la compra.\n" + ex.getMessage());
        }
    }

    // Se llama al entrar a la pestaña.
    public void recargar() {
        try {
            LocalDate hoy = LocalDate.now();
            List<Compra> compras = servicio.listar(hoy.minusDays(90), hoy);
            tabla.getItems().setAll(compras);
            contador.setText(compras.size() + (compras.size() == 1 ? " compra" : " compras") + " en los últimos 90 días");
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar las compras.\n" + e.getMessage());
        }
    }
}
