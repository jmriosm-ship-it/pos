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
    // Solo Existencias (la primera pestaña) lee sus datos al abrir el módulo; las demás, la primera vez que se abren.
    private final Perezosa<CompraPanel> compra = new Perezosa<>(CompraPanel::new);
    private final Perezosa<MovimientosPanel> movimientos = new Perezosa<>(MovimientosPanel::new);
    private final Perezosa<ComprasHistorialPanel> compras = new Perezosa<>(ComprasHistorialPanel::new);
    private final Perezosa<PorReponerPanel> porReponer = new Perezosa<>(() -> new PorReponerPanel((p, n) -> irAComprar(p, n)));
    private final Tab pestanaCompra;

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public InventarioView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

        // "Comprar" en Existencias lleva el producto a la pestaña de compra, listo para poner la cantidad.
        existencias = new ExistenciasPanel(this::irAComprar);

        Tab pExistencias = pestana("Existencias", "Alt+1", existencias);
        pestanaCompra = pestana("Nueva compra", "Alt+2", null);
        Tab pMovimientos = pestana("Movimientos", "Alt+3", null);
        Tab pCompras = pestana("Compras", "Alt+4", null);
        Tab pReponer = pestana("Por reponer", "Alt+5", null);
        getTabs().addAll(pExistencias, pestanaCompra, pMovimientos, pCompras, pReponer);

        // La primera vez que se abre una pestaña se arma y lee sus datos; las siguientes veces se actualiza (por si cambiaron en otra).
        pExistencias.setOnSelectionChanged(e -> { if (pExistencias.isSelected()) existencias.recargar(); });
        compra.enPestana(pestanaCompra, CompraPanel::recargar);
        movimientos.enPestana(pMovimientos, MovimientosPanel::recargar);
        compras.enPestana(pCompras, ComprasHistorialPanel::recargar);
        porReponer.enPestana(pReponer, PorReponerPanel::recargar);

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
        compra.get().agregarDesdeExistencias(p);
    }

    // Desde "Por reponer": además del producto, lleva la cantidad que se sugiere pedir.
    private void irAComprar(Producto p, int sugerido) {
        getSelectionModel().select(pestanaCompra);
        compra.get().agregarDesdeExistencias(p, sugerido);
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
