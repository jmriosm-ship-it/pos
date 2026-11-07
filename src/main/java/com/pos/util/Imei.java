package com.pos.util;

import java.util.regex.Pattern;

/** Reglas del IMEI / serial de un equipo (las mismas del POS anterior). */
public class Imei {

    private static final Pattern SERIAL = Pattern.compile("^[A-Z0-9]{8,17}$");

    private Imei() {
    }

    // Mayúsculas y sin espacios ni guiones.
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.toUpperCase().replaceAll("[\\s-]", "");
    }

    // Devuelve el IMEI normalizado o lanza el motivo por el que no sirve.
    public static String validar(String texto) {
        String imei = normalizar(texto);
        if (!SERIAL.matcher(imei).matches()) {
            throw new IllegalArgumentException("\"" + texto + "\" no es un IMEI/serial válido (8 a 17 letras o números).");
        }
        if (imei.matches("\\d{15}") && !luhnValido(imei)) {
            throw new IllegalArgumentException("El IMEI " + imei + " no es válido (revisa el último dígito).");
        }
        return imei;
    }

    // Dígito de control de Luhn: los IMEI de 15 dígitos lo cumplen.
    static boolean luhnValido(String digitos) {
        int suma = 0;
        boolean doble = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (doble) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
            doble = !doble;
        }
        return suma % 10 == 0;
    }
}
