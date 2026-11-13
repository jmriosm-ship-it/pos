package com.pos.servicio;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * El logo del negocio listo para imprimir en el recibo (archivo /imagenes/logo_ticket.png). El papel térmico solo
 * imprime negro sobre blanco: si el logo viene claro sobre fondo oscuro se invierte, y se agranda en números enteros
 * (sin suavizar) para que los trazos no se pierdan.
 */
public final class LogoRecibo {

    private LogoRecibo() {
    }

    private static BufferedImage original() {
        try (InputStream in = LogoRecibo.class.getResourceAsStream("/imagenes/logo_ticket.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static int brillo(int argb) {
        return (((argb >> 16) & 255) * 30 + ((argb >> 8) & 255) * 59 + (argb & 255) * 11) / 100;
    }

    /** El logo en negro sobre blanco, de a lo sumo anchoMax puntos de ancho; null si no hay logo. */
    public static BufferedImage paraPapel(int anchoMax) {
        BufferedImage im = original();
        if (im == null) {
            return null;
        }
        int w = im.getWidth(), h = im.getHeight();
        boolean fondoOscuro = (im.getRGB(0, 0) >>> 24) > 128 && brillo(im.getRGB(0, 0)) < 128;
        int k = Math.max(1, anchoMax / w);   // se agranda en enteros
        BufferedImage salida = new BufferedImage(w * k, h * k, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = im.getRGB(x, y);
                boolean tinta;
                if ((argb >>> 24) < 128) {
                    tinta = false;                       // transparente: papel
                } else if (fondoOscuro) {
                    tinta = brillo(argb) > 50;           // lo que no es fondo oscuro es el dibujo
                } else {
                    tinta = brillo(argb) < 160;          // dibujo oscuro sobre fondo claro
                }
                int color = tinta ? 0x000000 : 0xFFFFFF;
                for (int dy = 0; dy < k; dy++) {
                    for (int dx = 0; dx < k; dx++) {
                        salida.setRGB(x * k + dx, y * k + dy, color);
                    }
                }
            }
        }
        return salida;
    }

    /** Los comandos ESC/POS para imprimir esa imagen (GS v 0), centrada. Vacío si no hay logo. */
    public static byte[] escpos(int anchoMax) {
        BufferedImage im = paraPapel(anchoMax);
        if (im == null) {
            return new byte[0];
        }
        int w = im.getWidth(), h = im.getHeight(), bytesPorFila = (w + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0x1B, 0x61, 0x01});   // ESC a 1 = centrar
        out.writeBytes(new byte[]{0x1D, 0x76, 0x30, 0x00, (byte) (bytesPorFila & 255), (byte) (bytesPorFila >> 8),
                (byte) (h & 255), (byte) (h >> 8)});
        for (int y = 0; y < h; y++) {
            for (int bx = 0; bx < bytesPorFila; bx++) {
                int b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = bx * 8 + bit;
                    boolean negro = x < w && (im.getRGB(x, y) & 0xFFFFFF) == 0;
                    b = (b << 1) | (negro ? 1 : 0);
                }
                out.write(b);
            }
        }
        out.writeBytes(new byte[]{0x1B, 0x61, 0x00});   // ESC a 0 = volver a la izquierda
        return out.toByteArray();
    }
}
