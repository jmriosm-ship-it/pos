package com.pos.util;

import java.math.BigDecimal;

/** Ayudas para armar tickets de texto de ancho fijo (impresora térmica): centrar, línea, y "izquierda ... derecha". */
public class TextoTicket {

    private TextoTicket() {
    }

    public static String raya(int ancho) {
        return "-".repeat(ancho);
    }

    public static String centrar(String texto, int ancho) {
        texto = recortar(texto, ancho);
        return " ".repeat((ancho - texto.length()) / 2) + texto;
    }

    // Texto a la izquierda y otro pegado a la derecha, rellenando el medio con espacios.
    public static String izqDer(String izq, String der, int ancho) {
        int relleno = Math.max(1, ancho - izq.length() - der.length());
        return izq + " ".repeat(relleno) + der;
    }

    public static String recortar(String texto, int ancho) {
        return texto.length() <= ancho ? texto : texto.substring(0, ancho);
    }

    public static String dinero(BigDecimal valor) {
        return Dinero.formato(valor);
    }
}
