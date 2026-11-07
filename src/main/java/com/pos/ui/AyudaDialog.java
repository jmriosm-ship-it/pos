package com.pos.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

import java.sql.SQLException;

/** Ctrl + H: la guía de uso y los documentos legales (privacidad, términos, garantía, crédito y autorización). */
public class AyudaDialog {

    private AyudaDialog() {
    }

    public static void abrir() {
        TabPane pestanas = new TabPane();
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        try {
            pestanas.getTabs().addAll(
                    tab("Guía de uso", AyudaTextos.guia()),
                    tab("Privacidad", AyudaTextos.completar(AyudaTextos.privacidad())),
                    tab("Términos", AyudaTextos.completar(AyudaTextos.terminos())),
                    tab("Garantía", AyudaTextos.completar(AyudaTextos.garantia())),
                    tab("Crédito", AyudaTextos.completar(AyudaTextos.credito())),
                    tab("Autorización", AyudaTextos.completar(AyudaTextos.autorizacion())));
        } catch (SQLException e) {
            pestanas.getTabs().add(tab("Guía de uso", AyudaTextos.guia()));
        }
        pestanas.setPrefSize(760, 520);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Ayuda");
        dialogo.getDialogPane().setContent(pestanas);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.showAndWait();
    }

    private static Tab tab(String titulo, String texto) {
        TextArea area = new TextArea(texto);
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle("-fx-font-size: 13px;");
        return new Tab(titulo, area);
    }
}
