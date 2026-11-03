package com.pos.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.InputStream;

/**
 * El logo del negocio. Si existe el archivo  src/main/resources/imagenes/logo.png  lo muestra;
 * si no, dibuja un cuadro de color con la inicial del negocio.
 * Para poner el logo real basta con copiar ese archivo (PNG) con ese nombre y volver a ejecutar.
 */
public class Logo {

    private Logo() {
    }

    public static Node crear(double tamano, String nombreNegocio) {
        try (InputStream in = Logo.class.getResourceAsStream("/imagenes/logo.png")) {
            if (in != null) {
                ImageView vista = new ImageView(new Image(in));
                vista.setFitWidth(tamano);
                vista.setFitHeight(tamano);
                vista.setPreserveRatio(true);
                return vista;
            }
        } catch (Exception e) {
            // si el archivo está dañado se usa el logo de letra
        }
        String inicial = (nombreNegocio == null || nombreNegocio.isBlank())
                ? "P" : nombreNegocio.trim().substring(0, 1).toUpperCase();
        Label letra = new Label(inicial);
        letra.getStyleClass().add("logo-letra");
        StackPane cuadro = new StackPane(letra);
        cuadro.getStyleClass().add("logo-cuadro");
        cuadro.setMinSize(tamano, tamano);
        cuadro.setPrefSize(tamano, tamano);
        cuadro.setMaxSize(tamano, tamano);
        return cuadro;
    }
}
