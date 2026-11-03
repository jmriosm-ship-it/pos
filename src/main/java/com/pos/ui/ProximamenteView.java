package com.pos.ui;

import com.pos.modelo.Modulo;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Pantalla temporal para los módulos que todavía no tienen diseño. */
public class ProximamenteView extends StackPane {

    public ProximamenteView(Modulo modulo) {
        Label titulo = new Label(modulo.getTitulo());
        titulo.getStyleClass().add("card-titulo");
        Label texto = new Label("Este módulo ya funciona por dentro (la lógica está lista);\nfalta diseñar su pantalla.");
        texto.getStyleClass().add("texto-suave");
        texto.setWrapText(true);

        VBox tarjeta = new VBox(8, titulo, texto);
        tarjeta.getStyleClass().add("card");
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.setMaxWidth(420);
        tarjeta.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        getChildren().add(tarjeta);
    }
}
