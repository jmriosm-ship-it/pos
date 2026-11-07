package com.pos.ui;

import com.pos.dao.MovimientosDAO;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
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

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Pestaña Movimientos (Reportes): todo lo que pasó en el periodo en una sola lista, con filtro por grupo y búsqueda.
 * Teclas: F2 buscar · F5 siguiente grupo · F6 más resultados (se cargan de 150 en 150).
 */
public class HistorialPanel extends VBox {

    private static final int PAGINA = 150;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MovimientosDAO dao = new MovimientosDAO();
    private final PeriodoReporte periodo;
    private final TableView<MovimientosDAO.Fila> tabla = new TableView<>();
    private final ToggleGroup grupos = new ToggleGroup();
    private final TextField buscador = new TextField();
    private final Label contador = new Label();
    private final Button masBoton = new Button("Cargar más");
    private int cargados = 0;
    private boolean hayMas = false;
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public HistorialPanel(PeriodoReporte periodo) {
        this.periodo = periodo;
        setSpacing(12);

        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        FlowPane chips = new FlowPane(6, 6, Chips.crear("Todo", "TODO", grupos, gris));
        for (String g : MovimientosDAO.GRUPOS) {
            chips.getChildren().add(Chips.crear(g, g, grupos, gris));
        }
        grupos.selectToggle(grupos.getToggles().get(0));
        Chips.alElegir(grupos, () -> cargar(false));

        buscador.setPromptText("Buscar por detalle, tipo o usuario (F2)");
        buscador.setOnAction(e -> cargar(false));
        HBox.setHgrow(buscador, Priority.ALWAYS);
        contador.getStyleClass().add("texto-suave");
        masBoton.getStyleClass().add("btn-secundario");
        masBoton.setGraphic(Atajos.tecla("F6"));
        masBoton.setContentDisplay(ContentDisplay.RIGHT);
        masBoton.setGraphicTextGap(8);
        masBoton.setFocusTraversable(false);
        masBoton.setOnAction(e -> cargar(true));
        HBox barra = new HBox(10, buscador, contador, masBoton);
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay movimientos con ese filtro."));
        tabla.getColumns().add(Tablas.columna("Fecha", 140, (MovimientosDAO.Fila f) -> f.fecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Tipo", 150, (MovimientosDAO.Fila f) -> f.tipo()));
        tabla.getColumns().add(Tablas.columna("Detalle", 330, (MovimientosDAO.Fila f) -> f.detalle()));
        var valor = Tablas.columna("Valor", 120, (MovimientosDAO.Fila f) -> Dinero.formato(f.valor()));
        Tablas.colorear(valor, f -> f.valor().signum() < 0 ? "texto-peligro" : null);
        tabla.getColumns().add(valor);
        tabla.getColumns().add(Tablas.columna("Usuario", 140, (MovimientosDAO.Fila f) -> f.usuario()));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Region espacio = new Region();
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Buscar"), Atajos.etiqueta("F5", "Cambiar grupo"),
                Atajos.etiqueta("F6", "Cargar más"), Atajos.etiqueta("↑ ↓", "Moverse"));
        getChildren().addAll(chips, barra, tabla, teclas, espacio);
        VBox.setVgrow(espacio, Priority.NEVER);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        periodo.alCambiar(() -> cargar(false));
        cargar(false);
    }

    public void recargar() {
        cargar(false);
    }

    private void cargar(boolean masResultados) {
        try {
            String grupo = (String) Chips.valor(grupos);
            int salto = masResultados ? cargados : 0;
            List<MovimientosDAO.Fila> lista = dao.listar(periodo.desde(), periodo.hasta(), "TODO".equals(grupo) ? null : grupo,
                    buscador.getText(), PAGINA, salto);
            if (masResultados) {
                tabla.getItems().addAll(lista);
            } else {
                tabla.getItems().setAll(lista);
            }
            cargados = salto + lista.size();
            hayMas = lista.size() == PAGINA;
            masBoton.setDisable(!hayMas);
            contador.setText(cargados + (cargados == 1 ? " movimiento" : " movimientos") + (hayMas ? " (hay más)" : ""));
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los movimientos.\n" + e.getMessage());
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
            case F5 -> Chips.siguiente(grupos);
            case F6 -> {
                if (hayMas) {
                    cargar(true);
                }
            }
            default -> {
                return;
            }
        }
        e.consume();
    }
}
