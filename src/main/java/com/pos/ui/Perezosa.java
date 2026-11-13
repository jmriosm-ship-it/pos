package com.pos.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;

import java.util.function.Supplier;

/**
 * Una pestaña que arma su contenido (y por tanto lee sus datos de la base) solo la primera vez que se abre.
 * Con la base en internet cada lectura tarda, y abrir un módulo con seis pestañas leía los datos de las seis aunque
 * solo se viera una. Las veces siguientes se llama a "alAbrirDeNuevo" (normalmente recargar) como siempre.
 */
final class Perezosa<T extends Node> {

    private final Supplier<T> fabrica;
    private T panel;

    Perezosa(Supplier<T> fabrica) {
        this.fabrica = fabrica;
    }

    /** El panel; se crea la primera vez que se pide. */
    T get() {
        if (panel == null) {
            panel = fabrica.get();
        }
        return panel;
    }

    boolean creado() {
        return panel != null;
    }

    /** Conecta la pestaña: arma el panel al abrirla por primera vez y, después, hace lo que se pida al abrirla. */
    void enPestana(Tab tab, java.util.function.Consumer<T> alAbrirDeNuevo) {
        tab.setOnSelectionChanged(e -> {
            if (!tab.isSelected()) {
                return;
            }
            boolean primera = !creado();
            T p = get();
            if (primera) {
                tab.setContent(p);          // el panel recién armado ya leyó sus datos
            } else if (alAbrirDeNuevo != null) {
                alAbrirDeNuevo.accept(p);   // volvió a esta pestaña: se actualiza
            }
        });
    }
}
