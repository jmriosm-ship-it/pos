package com.pos.ui;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/** Módulo "Categorías y proveedores": dos pestañas, una para cada cosa. */
public class CategoriasProveedoresView extends TabPane {

    public CategoriasProveedoresView() {
        // Las pestañas no se pueden cerrar (por defecto JavaFX les pone una X).
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        Tab pProveedores = new Tab("Proveedores");
        getTabs().addAll(new Tab("Categorías", new CategoriasPanel()), pProveedores);
        new Perezosa<ProveedoresPanel>(ProveedoresPanel::new).enPestana(pProveedores, null);   // se lee al abrir esa pestaña
    }
}
