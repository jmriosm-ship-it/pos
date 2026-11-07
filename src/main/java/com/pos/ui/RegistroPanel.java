package com.pos.ui;

import com.pos.dao.AuditoriaDAO;
import com.pos.servicio.AuditoriaService;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/** Pestaña Registro (Configuración): quién hizo qué y cuándo (inicios de sesión, anulaciones, sueldos...). F2 busca. */
public class RegistroPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AuditoriaService servicio = new AuditoriaService();
    private final TableView<AuditoriaDAO.Fila> tabla = new TableView<>();
    private final TextField buscador = new TextField();
    private final Label contador = new Label();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public RegistroPanel() {
        setSpacing(12);
        buscador.setPromptText("Buscar por acción, detalle o usuario (F2)");
        buscador.setOnAction(e -> recargar());
        HBox.setHgrow(buscador, Priority.ALWAYS);
        contador.getStyleClass().add("texto-suave");
        HBox barra = new HBox(12, buscador, contador);
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Todavía no hay acciones registradas."));
        tabla.getColumns().add(Tablas.columna("Fecha", 160, (AuditoriaDAO.Fila f) -> f.fecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Usuario", 150, (AuditoriaDAO.Fila f) -> f.usuario()));
        tabla.getColumns().add(Tablas.columna("Acción", 190, (AuditoriaDAO.Fila f) -> f.accion()));
        tabla.getColumns().add(Tablas.columna("Detalle", 420, (AuditoriaDAO.Fila f) -> f.detalle()));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Buscar"), Atajos.etiqueta("↑ ↓", "Moverse"));
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

    public void recargar() {
        try {
            var lista = servicio.listar(Sesion.usuario(), buscador.getText());
            tabla.getItems().setAll(lista);
            contador.setText(lista.size() + " (las últimas 400)");
        } catch (IllegalStateException | SQLException e) {
            Mensajes.error(e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (Atajos.visible(this) && e.getCode() == javafx.scene.input.KeyCode.F2) {
            buscador.requestFocus();
            buscador.selectAll();
            e.consume();
        }
    }
}
