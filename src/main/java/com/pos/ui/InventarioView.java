package com.pos.ui;

import com.pos.modelo.Producto;
import javafx.event.EventHandler;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Módulo "Inventario y compras": cinco pestañas.
 *   Existencias · Nueva compra · Movimientos (historial del inventario) · Compras (historial de compras) · Por reponer
 * Alt + 1...5 cambia de pestaña. Cada pestaña vuelve a leer sus datos al abrirse.
 */
public class InventarioView extends TabPane {

    private final ExistenciasPanel existencias;
    private final CompraPanel compra = new CompraPanel();
    private final MovimientosPanel movimientos = new MovimientosPanel();
    private final ComprasHistorialPanel compras = new ComprasHistorialPanel();
    private final PorReponerPanel porReponer = new PorReponerPanel(this::irAComprar);
    private final Tab pestanaCompra;

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public InventarioView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        // "Comprar" en Existencias lleva el producto a la pestaña de compra, listo para poner la cantidad.
        existencias = new ExistenciasPanel(this::irAComprar);

        Tab pExistencias = pestana("Existencias", "Alt+1", existencias);
        pestanaCompra = pestana("Nueva compra", "Alt+2", compra);
        Tab pMovimientos = pestana("Movimientos", "Alt+3", movimientos);
        Tab pCompras = pestana("Compras", "Alt+4", compras);
        Tab pReponer = pestana("Por reponer", "Alt+5", porReponer);
        getTabs().addAll(pExistencias, pestanaCompra, pMovimientos, pCompras, pReponer);
        pReponer.setOnSelectionChanged(e -> { if (pReponer.isSelected()) porReponer.recargar(); });

        // Al abrir una pestaña se vuelven a leer sus datos (por si cambiaron en otra).
        pExistencias.setOnSelectionChanged(e -> { if (pExistencias.isSelected()) existencias.recargar(); });
        pestanaCompra.setOnSelectionChanged(e -> { if (pestanaCompra.isSelected()) compra.recargar(); });
        pMovimientos.setOnSelectionChanged(e -> { if (pMovimientos.isSelected()) movimientos.recargar(); });
        pCompras.setOnSelectionChanged(e -> { if (pCompras.isSelected()) compras.recargar(); });

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

    private void irAComprar(Producto p) {
        getSelectionModel().select(pestanaCompra);
        compra.agregarDesdeExistencias(p);
    }

    // Alt + 1...4 = ir a esa pestaña.
    private void tecla(KeyEvent e) {
        if (!e.isAltDown() || e.getCode().ordinal() < KeyCode.DIGIT1.ordinal()
                || e.getCode().ordinal() > KeyCode.DIGIT5.ordinal()) {
            return;
        }
        getSelectionModel().select(e.getCode().ordinal() - KeyCode.DIGIT1.ordinal());
        e.consume();
    }
}
