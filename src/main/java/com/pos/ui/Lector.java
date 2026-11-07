package com.pos.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Reconoce a la pistola de códigos de barras: escribe muy rápido (menos de 60 ms entre letras) y termina con Enter.
 * Así se sabe que un código vino del lector aunque el cursor no esté en el buscador. También hace el pitido:
 * agudo si el producto se agregó, grave si no se encontró.
 */
public class Lector {

    private static final long ENTRE_TECLAS_MS = 60;
    private static final int LARGO_MINIMO = 4;

    private final StringBuilder texto = new StringBuilder();
    private long ultimaTecla = 0;
    private boolean rapido = true;

    // Se llama con cada carácter escrito.
    public void caracter(String c) {
        long ahora = System.nanoTime() / 1_000_000;
        if (texto.length() > 0 && ahora - ultimaTecla > ENTRE_TECLAS_MS) {
            reiniciar();   // pasó mucho tiempo: es una persona escribiendo, no la pistola
        }
        texto.append(c);
        ultimaTecla = ahora;
    }

    // Al pulsar Enter: devuelve el código si lo escribió la pistola (rápido y de largo suficiente), o null.
    public String tomar() {
        long ahora = System.nanoTime() / 1_000_000;
        boolean reciente = ahora - ultimaTecla <= 150;   // el Enter llega pegado al último dígito
        String codigo = texto.length() >= LARGO_MINIMO && rapido && reciente ? texto.toString() : null;
        reiniciar();
        return codigo;
    }

    private void reiniciar() {
        texto.setLength(0);
        rapido = true;
    }

    // Pitido corto en otro hilo para no frenar la pantalla.
    public static void pitido(boolean bueno) {
        double frecuencia = bueno ? 1500 : 320;
        Thread t = new Thread(() -> {
            try {
                float tasa = 22050f;
                AudioFormat formato = new AudioFormat(tasa, 8, 1, true, false);
                try (SourceDataLine linea = AudioSystem.getSourceDataLine(formato)) {
                    linea.open(formato);
                    linea.start();
                    byte[] datos = new byte[(int) (tasa * (bueno ? 0.09 : 0.22))];
                    for (int i = 0; i < datos.length; i++) {
                        datos[i] = (byte) (Math.sin(2 * Math.PI * i * frecuencia / tasa) * 60);
                    }
                    linea.write(datos, 0, datos.length);
                    linea.drain();
                }
            } catch (Exception e) {
                // sin parlantes no pasa nada
            }
        }, "pitido-lector");
        t.setDaemon(true);
        t.start();
    }
}
