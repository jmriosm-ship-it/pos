package com.pos.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Un interruptor de encendido/apagado (como los del celular). Se cambia con clic, Espacio o Enter. */
public class Interruptor extends StackPane {

    private final BooleanProperty activo = new SimpleBooleanProperty();
    private final Circle perilla = new Circle(8, Color.WHITE);

    public Interruptor(boolean inicial) {
        getStyleClass().add("interruptor");
        setMinSize(40, 22);
        setPrefSize(40, 22);
        setMaxSize(40, 22);
        setPadding(new Insets(0, 3, 0, 3));
        getChildren().add(perilla);
        setFocusTraversable(true);

        setOnMouseClicked(e -> alternar());
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                alternar();
                e.consume();
            }
        });
        activo.addListener((o, antes, ahora) -> dibujar());
        activo.set(inicial);
        dibujar();
    }

    // La perilla va a la derecha cuando está encendido y a la izquierda cuando está apagado.
    private void dibujar() {
        getStyleClass().remove("on");
        if (activo.get()) {
            getStyleClass().add("on");
        }
        StackPane.setAlignment(perilla, activo.get() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        requestLayout();
    }

    private void alternar() {
        if (!isDisabled()) {
            activo.set(!activo.get());
        }
    }

    public BooleanProperty activoProperty() {
        return activo;
    }

    public boolean isActivo() {
        return activo.get();
    }

    public void setActivo(boolean valor) {
        activo.set(valor);
    }
}
