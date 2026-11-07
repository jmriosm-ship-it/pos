package com.pos.ui;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Módulo Clientes, en pestañas: Clientes (Alt+1), Créditos en cuotas (Alt+2) y Financiera (Alt+3). */
public class ClientesView extends VBox {

    private final TabPane pestanas = new TabPane();
    private final CreditosPanel creditos = new CreditosPanel();
    private final FinancieraPanel financiera = new FinancieraPanel();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public ClientesView() {
        Tab pClientes = pestana("Clientes", "Alt+1", new ClientesPanel());
        Tab pCreditos = pestana("Créditos en cuotas", "Alt+2", creditos);
        Tab pFinanciera = pestana("Financiera", "Alt+3", financiera);
        pestanas.getTabs().addAll(pClientes, pCreditos, pFinanciera);
        pFinanciera.setOnSelectionChanged(e -> { if (pFinanciera.isSelected()) financiera.recargar(); });
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pCreditos.setOnSelectionChanged(e -> { if (pCreditos.isSelected()) creditos.recargar(); });
        VBox.setVgrow(pestanas, Priority.ALWAYS);
        getChildren().add(pestanas);

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

    private void tecla(KeyEvent e) {
        if (e.isAltDown() && e.getCode().ordinal() >= KeyCode.DIGIT1.ordinal() && e.getCode().ordinal() <= KeyCode.DIGIT3.ordinal()) {
            pestanas.getSelectionModel().select(e.getCode().ordinal() - KeyCode.DIGIT1.ordinal());
            e.consume();
        }
    }
}
