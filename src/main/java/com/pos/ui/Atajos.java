package com.pos.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Ayudas visuales para los atajos de teclado: una "tecla" (el cuadrito gris con F4, Esc...)
 * y una etiqueta tecla + descripción ("F4 Cobrar"). Así el cajero siempre ve qué tecla usar.
 */
public class Atajos {

    private Atajos() {
    }

    // El cuadrito con el nombre de la tecla, como una tecla de teclado.
    public static Label tecla(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("tecla");
        return l;
    }

    // "F4  Cobrar": la tecla seguida de lo que hace.
    public static HBox etiqueta(String tecla, String descripcion) {
        Label d = new Label(descripcion);
        d.getStyleClass().add("atajo-texto");
        HBox fila = new HBox(5, tecla(tecla), d);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }
}
