package com.pos.ui;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Módulo Configuración, en pestañas: Negocio (datos, impresora, puntos), Promociones, Crédito y Registro.
 * Teclas: Alt+1 a Alt+4 cambian de pestaña; cada pestaña muestra las suyas.
 */
public class ConfiguracionView extends VBox {

    private final TabPane pestanas = new TabPane();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public ConfiguracionView() {
        ScrollPane negocio = new ScrollPane(new NegocioPanel());
        negocio.setFitToWidth(true);
        Tab pPromos = pestana("Promociones", "Alt+2", null);
        Tab pCredito = pestana("Crédito", "Alt+3", null);
        Tab pRegistro = pestana("Registro", "Alt+4", null);
        pestanas.getTabs().addAll(pestana("Negocio", "Alt+1", negocio), pPromos, pCredito, pRegistro);
        // Solo Negocio se arma al abrir; las demás pestañas, la primera vez que se abren.
        new Perezosa<Node>(PromocionesPanel::new).enPestana(pPromos, null);
        new Perezosa<Node>(() -> conScroll(new CreditoConfigPanel())).enPestana(pCredito, null);
        new Perezosa<Node>(RegistroPanel::new).enPestana(pRegistro, null);
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
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

    private ScrollPane conScroll(Node contenido) {
        ScrollPane s = new ScrollPane(contenido);
        s.setFitToWidth(true);
        return s;
    }

    private Tab pestana(String titulo, String tecla, Node contenido) {
        Tab t = new Tab(titulo, contenido);
        t.setGraphic(Atajos.tecla(tecla));
        return t;
    }

    private void tecla(KeyEvent e) {
        if (e.isAltDown() && e.getCode().ordinal() >= KeyCode.DIGIT1.ordinal()
                && e.getCode().ordinal() < KeyCode.DIGIT1.ordinal() + pestanas.getTabs().size()) {
            pestanas.getSelectionModel().select(e.getCode().ordinal() - KeyCode.DIGIT1.ordinal());
            e.consume();
        }
    }
}
