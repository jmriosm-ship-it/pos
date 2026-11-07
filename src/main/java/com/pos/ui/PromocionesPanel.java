package com.pos.ui;

import com.pos.modelo.Promocion;
import com.pos.modelo.Usuario;
import com.pos.servicio.PromocionService;
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

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Pestaña Promociones (Configuración): descuentos automáticos que se aplican solos en el carrito.
 * Teclas: F3 nueva · F4 editar · F6 activar/desactivar · Supr eliminar · ↑ ↓ moverse.
 */
public class PromocionesPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PromocionService servicio = new PromocionService();
    private final Usuario usuario = Sesion.usuario();
    private final TableView<Promocion> tabla = new TableView<>();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public PromocionesPanel() {
        setSpacing(12);

        Label ayuda = new Label("Se aplican solas al agregar productos. Si varias coinciden en una línea, gana la que más rebaja.");
        ayuda.getStyleClass().add("texto-suave");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, espacio, boton("Nueva", "F3", "btn-primario", this::nueva),
                boton("Editar", "F4", "btn-secundario", this::editar),
                boton("Activar / desactivar", "F6", "btn-secundario", this::alternar));
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Todavía no hay promociones. Pulsa F3 para crear la primera."));
        TableViewHelper.columnas(tabla);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F3", "Nueva"), Atajos.etiqueta("F4", "Editar"),
                Atajos.etiqueta("F6", "Activar / desactivar"), Atajos.etiqueta("Supr", "Eliminar"),
                Atajos.etiqueta("↑ ↓", "Moverse"));
        getChildren().addAll(ayuda, barra, tabla, teclas);

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
        b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
        return b;
    }

    public void recargar() {
        Promocion previa = tabla.getSelectionModel().getSelectedItem();
        try {
            List<Promocion> lista = servicio.listar();
            tabla.getItems().setAll(lista);
            if (previa != null) {
                lista.stream().filter(p -> p.getId() == previa.getId()).findFirst()
                        .ifPresent(p -> tabla.getSelectionModel().select(p));
            } else if (!lista.isEmpty()) {
                tabla.getSelectionModel().select(0);
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar las promociones.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F3 -> nueva();
            case F4 -> editar();
            case F6 -> alternar();
            case DELETE -> eliminar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private Promocion elegida() {
        Promocion p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) {
            Mensajes.info("Elige una promoción", "Muévete a la tabla con la flecha ↓ y elige una.");
        }
        return p;
    }

    private void nueva() {
        if (PromocionDialog.abrir(null)) {
            recargar();
        }
    }

    private void editar() {
        Promocion p = elegida();
        if (p != null && PromocionDialog.abrir(p)) {
            recargar();
        }
    }

    private void alternar() {
        Promocion p = elegida();
        if (p == null) {
            return;
        }
        try {
            p.setActiva(!p.isActiva());
            servicio.guardar(usuario, p);
            recargar();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            p.setActiva(!p.isActiva());
            Mensajes.error(ex.getMessage());
        } catch (SQLException ex) {
            Mensajes.error("No se pudo guardar.\n" + ex.getMessage());
        }
    }

    private void eliminar() {
        Promocion p = tabla.getSelectionModel().getSelectedItem();
        if (p == null || !Mensajes.confirmar("Eliminar promoción", "¿Eliminar \"" + p.getNombre() + "\"?")) {
            return;
        }
        try {
            servicio.eliminar(usuario, p);
            recargar();
        } catch (IllegalStateException ex) {
            Mensajes.error(ex.getMessage());
        } catch (SQLException ex) {
            Mensajes.error("No se pudo eliminar.\n" + ex.getMessage());
        }
    }

    // Las columnas de la tabla.
    private static class TableViewHelper {
        static void columnas(TableView<Promocion> t) {
            t.getColumns().add(Tablas.columna("Promoción", 240, (Promocion p) -> p.getNombre()));
            t.getColumns().add(Tablas.columna("Qué hace", 170, (Promocion p) -> p.resumen()));
            t.getColumns().add(Tablas.columna("Dónde aplica", 220, (Promocion p) -> p.aplicaA()));
            t.getColumns().add(Tablas.columna("Vigencia", 190, (Promocion p) -> vigencia(p)));
            var estado = Tablas.columna("Estado", 100, (Promocion p) -> estado(p));
            Tablas.colorear(estado, p -> p.vigente(LocalDate.now()) ? "texto-ok" : "texto-suave");
            t.getColumns().add(estado);
        }

        static String vigencia(Promocion p) {
            if (p.getDesde() == null && p.getHasta() == null) {
                return "Siempre";
            }
            return (p.getDesde() == null ? "…" : p.getDesde().format(FECHA)) + " a "
                    + (p.getHasta() == null ? "…" : p.getHasta().format(FECHA));
        }

        static String estado(Promocion p) {
            if (!p.isActiva()) {
                return "Apagada";
            }
            return p.vigente(LocalDate.now()) ? "Vigente" : "Fuera de fecha";
        }
    }
}
