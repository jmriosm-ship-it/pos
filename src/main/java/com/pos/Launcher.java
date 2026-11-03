package com.pos;

import javafx.application.Application;

/**
 * Punto de arranque real del programa. Existe porque, al ejecutar desde el IDE,
 * una clase que "extends Application" no siempre encuentra JavaFX; una clase normal que la
 * lanza sí. Ejecuta ESTA clase (o Main con "mvn javafx:run").
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
