package com.pos.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Convierte dinero a texto para mostrar ($15.000) y texto escrito por el usuario a dinero. */
public class Dinero {

    // "es-CO" separa los miles con punto: 15.000
    private static final NumberFormat FORMATO = NumberFormat.getNumberInstance(Locale.of("es", "CO"));

    static {
        FORMATO.setMaximumFractionDigits(0);   // pesos sin centavos
    }

    private Dinero() {
    }

    // BigDecimal -> "$15.000"
    public static String formato(BigDecimal valor) {
        return "$" + FORMATO.format(valor == null ? BigDecimal.ZERO : valor);
    }

    // "15.000", "$ 15000" o "15000" -> BigDecimal 15000. Se quedan solo los dígitos.
    public static BigDecimal parsear(String texto) {
        String digitos = texto == null ? "" : texto.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            throw new IllegalArgumentException("Escribe un monto válido.");
        }
        return new BigDecimal(digitos);
    }

    // BigDecimal -> "15000" (sin símbolo ni puntos), para rellenar campos de edición.
    public static String plano(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
