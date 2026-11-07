package com.pos.ui;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/**
 * El historial de ventas y facturas, dentro de Ventas (como en el otro POS): las ventas del periodo con su estado
 * (ver recibo F7, devolver F8, anular F9) y las facturas (recibos buscables, fotos y papelera).
 * Teclas: Alt+1 ventas · Alt+2 facturas · F10 cambiar periodo.
 */
public class HistorialVentasView extends VBox {

    private static final int HOY = 0, SIETE = 1, MES = 2, OTRO = 3;

    private final PeriodoReporte periodo = new PeriodoReporte();
    private final ToggleGroup chipsPeriodo = new ToggleGroup();
    private final TabPane pestanas = new TabPane();
    private boolean ignorar = false;
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public HistorialVentasView() {
        setSpacing(12);

        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chips = new HBox(6, Chips.crear("Hoy", HOY, chipsPeriodo, gris), Chips.crear("7 días", SIETE, chipsPeriodo, gris),
                Chips.crear("Este mes", MES, chipsPeriodo, gris), Chips.crear("Otro rango…", OTRO, chipsPeriodo, gris));
        chips.getChildren().forEach(c -> ((Region) c).setMinWidth(Region.USE_PREF_SIZE));
        chips.setAlignment(Pos.CENTER_LEFT);
        chipsPeriodo.selectToggle(chipsPeriodo.getToggles().get(0));
        chipsPeriodo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                antes.setSelected(true);
            } else if (!ignorar) {
                elegirPeriodo((Integer) ahora.getUserData(), antes);
            }
        });
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, chips, espacio, Atajos.etiqueta("F10", "Cambiar periodo"));
        barra.setAlignment(Pos.CENTER_LEFT);

        Tab pVentas = pestana("Ventas", "Alt+1", new VentasHistorialPanel(periodo));
        Tab pFacturas = pestana("Facturas", "Alt+2", new FacturasPanel());
        pestanas.getTabs().addAll(pVentas, pFacturas);
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(pestanas, Priority.ALWAYS);
        getChildren().addAll(barra, pestanas);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
    }

    private Tab pestana(String titulo, String tecla, Node contenido) {
        Tab t = new Tab(titulo, contenido);
        t.setGraphic(Atajos.tecla(tecla));
        return t;
    }

    private void elegirPeriodo(int cual, Toggle anterior) {
        switch (cual) {
            case HOY -> periodo.hoy();
            case SIETE -> periodo.ultimosSieteDias();
            case MES -> periodo.esteMes();
            default -> {
                LocalDate[] rango = RangoDialog.abrir(periodo.desde(), periodo.hasta());
                if (rango == null) {
                    ignorar = true;
                    if (anterior != null) {
                        anterior.setSelected(true);
                    }
                    ignorar = false;
                } else {
                    periodo.rango(rango[0], rango[1]);
                }
            }
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        if (e.isAltDown() && (e.getCode() == KeyCode.DIGIT1 || e.getCode() == KeyCode.DIGIT2)) {
            pestanas.getSelectionModel().select(e.getCode() == KeyCode.DIGIT1 ? 0 : 1);
            e.consume();
        } else if (e.getCode() == KeyCode.F10) {
            int actual = (Integer) Chips.valor(chipsPeriodo);
            chipsPeriodo.selectToggle(chipsPeriodo.getToggles().get(actual >= MES ? HOY : actual + 1));
            e.consume();
        }
    }
}
