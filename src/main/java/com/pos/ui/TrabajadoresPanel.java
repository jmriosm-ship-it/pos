package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.servicio.NominaService;
import com.pos.servicio.UsuarioService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña Trabajadores: cuánto vendió cada uno, su cupo y lo que debe de él, y el pago del sueldo.
 * Teclas: F3 sacar del cupo · F4 pagar sueldo · F6 editar (cupo, pago por día, permisos) · F7 detalle.
 */
public class TrabajadoresPanel extends VBox {

    // Una fila de la tabla: el trabajador con sus cifras ya calculadas.
    private record Fila(Usuario u, NominaService.Estado estado, BigDecimal[] hoy, BigDecimal[] mes) {
    }

    private final NominaService servicio = new NominaService();
    private final UsuarioService usuarios = new UsuarioService();
    private final TableView<Fila> tabla = new TableView<>();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public TrabajadoresPanel() {
        setSpacing(12);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, espacio, boton("Sacar del cupo", "F3", "btn-secundario", this::sacar),
                boton("Pagar sueldo", "F4", "btn-primario", this::pagar),
                boton("Editar", "F6", "btn-secundario", this::editar),
                boton("Detalle", "F7", "btn-secundario", this::detalle));
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay trabajadores."));
        tabla.getColumns().add(Tablas.columna("Trabajador", 190, (Fila f) -> f.u().getNombre()));
        tabla.getColumns().add(Tablas.columna("Rol", 90, (Fila f) -> f.u().getRol()));
        tabla.getColumns().add(Tablas.columna("Vendió hoy", 130, (Fila f) -> f.hoy()[0].toBigInteger() + " · " + Dinero.formato(f.hoy()[1])));
        tabla.getColumns().add(Tablas.columna("Vendió en el mes", 150, (Fila f) -> f.mes()[0].toBigInteger() + " · " + Dinero.formato(f.mes()[1])));
        tabla.getColumns().add(Tablas.columna("Cupo", 100, (Fila f) -> Dinero.formato(f.estado().cupo())));
        tabla.getColumns().add(Tablas.columna("Debe del cupo", 120, (Fila f) -> Dinero.formato(f.estado().pendiente())));
        tabla.getColumns().add(Tablas.columna("Disponible", 110, (Fila f) -> Dinero.formato(f.estado().disponible().max(BigDecimal.ZERO))));
        tabla.getColumns().add(Tablas.columna("Pago por día", 110, (Fila f) -> Dinero.formato(f.u().getPagoDia())));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                detalle();
            }
        });

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F3", "Sacar del cupo"), Atajos.etiqueta("F4", "Pagar sueldo"),
                Atajos.etiqueta("F6", "Editar"), Atajos.etiqueta("F7", "Detalle"), Atajos.etiqueta("↑ ↓", "Moverse"),
                Atajos.etiqueta("Ctrl+U", "Sacar del cupo (en cualquier pantalla)"));
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

    private Button boton(String texto, String tecla, String estilo, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add(estilo);
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    public void recargar() {
        Fila previa = tabla.getSelectionModel().getSelectedItem();
        try {
            LocalDate hoy = LocalDate.now();
            List<Fila> filas = new ArrayList<>();
            for (Usuario u : usuarios.listar()) {
                if (u.isActivo()) {
                    filas.add(new Fila(u, servicio.estado(u), servicio.ventas(u, hoy, hoy), servicio.ventas(u, hoy.withDayOfMonth(1), hoy)));
                }
            }
            tabla.getItems().setAll(filas);
            if (!filas.isEmpty()) {
                int idx = 0;
                for (int i = 0; i < filas.size(); i++) {
                    if (previa != null && filas.get(i).u().getId() == previa.u().getId()) {
                        idx = i;
                    }
                }
                tabla.getSelectionModel().select(idx);
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los trabajadores.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F3 -> sacar();
            case F4 -> pagar();
            case F6 -> editar();
            case F7 -> detalle();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private Usuario elegido() {
        Fila f = tabla.getSelectionModel().getSelectedItem();
        return f == null ? null : f.u();
    }

    private void sacar() {
        if (SacarCupoDialog.abrir(elegido())) {
            recargar();
        }
    }

    private void pagar() {
        if (PagarSueldoDialog.abrir(elegido())) {
            recargar();
        }
    }

    private void editar() {
        Usuario u = elegido();
        if (u != null && UsuarioDialog.abrir(u) != null) {
            recargar();
        }
    }

    private void detalle() {
        Usuario u = elegido();
        if (u != null) {
            TrabajadorDetalleDialog.abrir(u);
            recargar();
        }
    }
}
