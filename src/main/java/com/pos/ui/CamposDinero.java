package com.pos.ui;

import com.pos.util.Dinero;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

/** Campos de texto para escribir dinero: solo aceptan números y los muestran con puntos de miles (50.000). */
public class CamposDinero {

    private CamposDinero() {
    }

    // Crea un campo de monto grande (el mismo estilo del cobro).
    public static TextField crear() {
        TextField campo = new TextField();
        campo.setPromptText("0");
        campo.getStyleClass().add("campo-monto");
        campo.textProperty().addListener((o, antes, ahora) -> {
            String digitos = ahora.replaceAll("[^0-9]", "");
            if (digitos.length() > 9) {
                digitos = digitos.substring(0, 9);
            }
            String formateado = digitos.isEmpty() ? "" : Dinero.formato(new BigDecimal(digitos)).substring(1);
            if (!formateado.equals(ahora)) {
                campo.setText(formateado);
                campo.positionCaret(formateado.length());
            }
        });
        return campo;
    }

    // El valor escrito en el campo (0 si está vacío).
    public static BigDecimal valor(TextField campo) {
        String digitos = campo.getText() == null ? "" : campo.getText().replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digitos);
    }
}
