package com.pos.ui;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;

/**
 * Módulo Reportes: arriba el periodo (Hoy, 7 días, Este mes u otro rango) y debajo tres pestañas que lo
 * comparten: Resumen (Alt+1), Ventas (Alt+2), Gastos (Alt+3), Movimientos (Alt+4), Calendario (Alt+5) y Facturas (Alt+6).
 * Teclas: F10 cambia el periodo · F11 exporta el resumen · Alt+1..3 cambia de pestaña.
 */
public class ReportesView extends VBox {

    private static final int HOY = 0, SIETE = 1, MES = 2, OTRO = 3;

    private final PeriodoReporte periodo = new PeriodoReporte();
    private final ToggleGroup chipsPeriodo = new ToggleGroup();
    private final ResumenPanel resumen = new ResumenPanel(periodo);
    private final VentasHistorialPanel ventas = new VentasHistorialPanel(periodo);
    private final GastosPanel gastos = new GastosPanel(periodo);
    private final HistorialPanel historial = new HistorialPanel(periodo);
    private final CalendarioPanel calendario = new CalendarioPanel();
    private final FacturasPanel facturas = new FacturasPanel();
    private final TabPane pestanas = new TabPane();
    private boolean ignorar = false;

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public ReportesView() {
        setSpacing(12);

        // Periodo
        HBox chips = new HBox(6,
                Chips.crear("Hoy", HOY, chipsPeriodo, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")),
                Chips.crear("7 días", SIETE, chipsPeriodo, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")),
                Chips.crear("Este mes", MES, chipsPeriodo, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")),
                Chips.crear("Otro rango…", OTRO, chipsPeriodo, new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827")));
        chips.setAlignment(Pos.CENTER_LEFT);
        chipsPeriodo.selectToggle(chipsPeriodo.getToggles().get(0));
        chipsPeriodo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                antes.setSelected(true);   // siempre debe haber un periodo elegido
            } else if (!ignorar) {
                elegirPeriodo((Integer) ahora.getUserData(), antes);
            }
        });
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, chips, espacio, Atajos.etiqueta("F10", "Cambiar periodo"), Atajos.etiqueta("F11", "Exportar"));
        barra.setAlignment(Pos.CENTER_LEFT);

        // Pestañas
        Tab pResumen = pestana("Resumen", "Alt+1", resumen);
        Tab pVentas = pestana("Ventas", "Alt+2", ventas);
        Tab pGastos = pestana("Gastos", "Alt+3", gastos);
        Tab pMovimientos = pestana("Movimientos", "Alt+4", historial);
        Tab pCalendario = pestana("Calendario", "Alt+5", calendario);
        Tab pFacturas = pestana("Facturas", "Alt+6", facturas);
        pestanas.getTabs().addAll(pResumen, pVentas, pGastos, pMovimientos, pCalendario, pFacturas);
        pMovimientos.setOnSelectionChanged(e -> { if (pMovimientos.isSelected()) historial.recargar(); });
        pCalendario.setOnSelectionChanged(e -> { if (pCalendario.isSelected()) calendario.recargar(); });
        pFacturas.setOnSelectionChanged(e -> { if (pFacturas.isSelected()) facturas.recargar(); });
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pResumen.setOnSelectionChanged(e -> { if (pResumen.isSelected()) resumen.recargar(); });
        pVentas.setOnSelectionChanged(e -> { if (pVentas.isSelected()) ventas.recargar(); });
        pGastos.setOnSelectionChanged(e -> { if (pGastos.isSelected()) gastos.recargar(); });
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

    private Tab pestana(String titulo, String tecla, javafx.scene.Node contenido) {
        Tab t = new Tab(titulo, contenido);
        t.setGraphic(Atajos.tecla(tecla));
        return t;
    }

    // Aplica el periodo del chip elegido. "Otro rango…" pide las fechas; si se cancela, vuelve al chip anterior.
    private void elegirPeriodo(int cual, javafx.scene.control.Toggle anterior) {
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
        if (e.isAltDown() && e.getCode().ordinal() >= KeyCode.DIGIT1.ordinal()
                && e.getCode().ordinal() < KeyCode.DIGIT1.ordinal() + pestanas.getTabs().size()) {
            pestanas.getSelectionModel().select(e.getCode().ordinal() - KeyCode.DIGIT1.ordinal());
            e.consume();
            return;
        }
        switch (e.getCode()) {
            case F10 -> {
                // Cicla Hoy → 7 días → Este mes → Hoy (el rango a mano se elige con el chip).
                int actual = (Integer) Chips.valor(chipsPeriodo);
                chipsPeriodo.selectToggle(chipsPeriodo.getToggles().get(actual >= MES ? HOY : actual + 1));
            }
            case F11 -> exportar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    // Guarda el resumen del periodo en un archivo que se abre con Excel.
    private void exportar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar el resumen");
        selector.setInitialFileName("resumen_" + periodo.desde() + "_a_" + periodo.hasta() + ".csv");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo para Excel (*.csv)", "*.csv"));
        File archivo = selector.showSaveDialog(getScene().getWindow());
        if (archivo == null) {
            return;
        }
        try {
            resumen.exportar(archivo.toPath());
            Mensajes.info("Resumen guardado", "El archivo quedó en:\n" + archivo.getAbsolutePath());
        } catch (Exception ex) {
            Mensajes.error("No se pudo guardar el archivo.\n" + ex.getMessage());
        }
    }
}
