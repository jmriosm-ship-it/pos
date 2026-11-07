package com.pos.ui;

import com.pos.modelo.Producto;
import com.pos.servicio.InteligenteService;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Pestaña "Por reponer" (Inventario): qué productos se están acabando según lo que se vende, cuánto les alcanza
 * y cuánto conviene pedir. F7 lleva el producto elegido a una compra nueva.
 */
public class PorReponerPanel extends VBox {

    private final InteligenteService servicio = new InteligenteService();
    private final Consumer<Producto> alComprar;
    private final TableView<InteligenteService.Fila> tabla = new TableView<>();
    private final Label resumen = new Label();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public PorReponerPanel(Consumer<Producto> alComprar) {
        this.alComprar = alComprar;
        setSpacing(12);

        resumen.getStyleClass().add("texto-suave");
        Button comprar = new Button("Comprar");
        comprar.getStyleClass().add("btn-primario");
        comprar.setGraphic(Atajos.tecla("F7"));
        comprar.setContentDisplay(ContentDisplay.RIGHT);
        comprar.setGraphicTextGap(8);
        comprar.setFocusTraversable(false);
        comprar.setMinWidth(Region.USE_PREF_SIZE);
        comprar.setOnAction(e -> comprar());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, resumen, espacio, comprar);
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Todo en orden: nada urgente por reponer."));
        tabla.getColumns().add(Tablas.columna("Producto", 220, (InteligenteService.Fila f) -> f.producto().getNombre()));
        tabla.getColumns().add(Tablas.columna("Stock", 70, (InteligenteService.Fila f) -> String.valueOf(f.producto().getStock())));
        tabla.getColumns().add(Tablas.columna("Mínimo", 70, (InteligenteService.Fila f) -> String.valueOf(f.producto().getStockMinimo())));
        tabla.getColumns().add(Tablas.columna("Vendió (30 días)", 130, (InteligenteService.Fila f) -> String.valueOf(f.vendidas())));
        tabla.getColumns().add(Tablas.columna("Le alcanza", 110, (InteligenteService.Fila f) -> alcanza(f)));
        var nivel = Tablas.columna("Estado", 100, (InteligenteService.Fila f) -> switch (f.nivel()) {
            case "AGOTADO" -> "Agotado";
            case "BAJO" -> "Bajo";
            default -> "Pronto";
        });
        Tablas.colorear(nivel, f -> "AGOTADO".equals(f.nivel()) ? "texto-peligro" : "texto-aviso");
        tabla.getColumns().add(nivel);
        tabla.getColumns().add(Tablas.columna("Sugerido pedir", 120, (InteligenteService.Fila f) -> String.valueOf(f.sugerido())));
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                comprar();
            }
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F7", "Comprar el elegido"), Atajos.etiqueta("↑ ↓", "Moverse"));
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

    private String alcanza(InteligenteService.Fila f) {
        if (f.diasQueAlcanza() == null) {
            return f.producto().getStock() <= 0 ? "—" : "sin ventas";
        }
        long d = Math.round(f.diasQueAlcanza());
        return d + (d == 1 ? " día" : " días");
    }

    public void recargar() {
        try {
            List<InteligenteService.Fila> lista = servicio.porReponer();
            tabla.getItems().setAll(lista);
            if (!lista.isEmpty()) {
                tabla.getSelectionModel().select(0);
            }
            resumen.setText(lista.size() + (lista.size() == 1 ? " producto por reponer" : " productos por reponer")
                    + "  ·  según lo vendido en los últimos " + InteligenteService.DIAS + " días");
        } catch (SQLException e) {
            Mensajes.error("No se pudo calcular qué reponer.\n" + e.getMessage());
        }
    }

    private void comprar() {
        InteligenteService.Fila f = tabla.getSelectionModel().getSelectedItem();
        if (f != null) {
            alComprar.accept(f.producto());
        }
    }

    private void tecla(KeyEvent e) {
        if (Atajos.visible(this) && e.getCode() == javafx.scene.input.KeyCode.F7) {
            comprar();
            e.consume();
        }
    }
}
