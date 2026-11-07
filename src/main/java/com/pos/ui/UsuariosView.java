package com.pos.ui;

import javafx.event.EventHandler;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Módulo "Usuarios y permisos": tres pestañas (Alt+1 Usuarios, Alt+2 Permisos, Alt+3 Trabajadores). Solo lo ve el administrador. */
public class UsuariosView extends TabPane {

    private final UsuariosPanel usuarios = new UsuariosPanel();
    private final PermisosPanel permisos = new PermisosPanel();
    private final TrabajadoresPanel trabajadores = new TrabajadoresPanel();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public UsuariosView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        Tab pUsuarios = new Tab("Usuarios", usuarios);
        pUsuarios.setGraphic(Atajos.tecla("Alt+1"));
        Tab pPermisos = new Tab("Permisos", permisos);
        pPermisos.setGraphic(Atajos.tecla("Alt+2"));
        Tab pTrabajadores = new Tab("Trabajadores", trabajadores);
        pTrabajadores.setGraphic(Atajos.tecla("Alt+3"));
        getTabs().addAll(pUsuarios, pPermisos, pTrabajadores);
        pTrabajadores.setOnSelectionChanged(e -> { if (pTrabajadores.isSelected()) trabajadores.recargar(); });
        pPermisos.setOnSelectionChanged(e -> { if (pPermisos.isSelected()) permisos.recargar(); });

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
    }

    private void tecla(KeyEvent e) {
        if (e.isAltDown() && (e.getCode() == KeyCode.DIGIT1 || e.getCode() == KeyCode.DIGIT2 || e.getCode() == KeyCode.DIGIT3)) {
            getSelectionModel().select(e.getCode().ordinal() - KeyCode.DIGIT1.ordinal());
            e.consume();
        }
    }
}
