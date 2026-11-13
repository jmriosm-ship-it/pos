package com.pos.servicio;

import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Pago;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Arma el texto del recibo de una venta, listo para una impresora térmica.
 * La impresora térmica solo entiende texto con ancho fijo: 80 mm = 48 caracteres, 58 mm = 32.
 *
 * Además del texto lleva tres marcas al inicio de línea que solo entiende la impresora (la vista previa las quita):
 *   LOGO       = aquí va el logo del negocio (imagen)
 *   GRANDE     = esa línea en letra doble de alto y negrita (el TOTAL, el cambio)
 *   MUY_GRANDE = esa línea en letra doble de alto y de ancho (el nombre del negocio; caben la mitad de caracteres)
 */
public class ReciboService {

    public static final char LOGO = '\u0003';
    public static final char GRANDE = '\u0001';
    public static final char MUY_GRANDE = '\u0002';

    private final ConfiguracionService config = new ConfiguracionService();

    // "es-CO" para que los miles se separen con punto: 44.000
    private final NumberFormat formatoDinero = NumberFormat.getNumberInstance(Locale.of("es", "CO"));

    public ReciboService() {
        formatoDinero.setMaximumFractionDigits(0);
    }

    // Devuelve el recibo completo como texto. La venta debe traer sus líneas (VentaService.detalle).
    public String generar(Venta v) throws SQLException {
        return generar(v, null, null);
    }

    // Igual, pero si se dan el efectivo recibido y la devolución, también los imprime
    // (solo se conocen justo al cobrar; no se guardan en la base de datos).
    public String generar(Venta v, BigDecimal efectivoRecibido, BigDecimal devolucion) throws SQLException {
        int ancho = "58".equals(config.obtener(ConfiguracionService.IMPRESORA_ANCHO_MM, "80")) ? 32 : 48;
        StringBuilder sb = new StringBuilder();

        // ---- Encabezado: logo, nombre grande y datos del negocio, centrados
        sb.append(LOGO).append('\n');
        String nombre = config.nombreNegocio().toUpperCase();
        if (nombre.length() <= ancho / 2) {
            sb.append(MUY_GRANDE).append(centrar(nombre, ancho / 2)).append('\n');
        } else {
            sb.append(GRANDE).append(centrar(nombre, ancho)).append('\n');
        }
        linea(sb, config.obtener(ConfiguracionService.NEGOCIO_ACTIVIDAD, "").toUpperCase(), ancho);
        linea(sb, config.obtener(ConfiguracionService.NEGOCIO_TITULAR, ""), ancho);
        String nit = config.obtener(ConfiguracionService.NEGOCIO_NIT, "");
        if (!nit.isEmpty()) {
            linea(sb, "NIT " + nit, ancho);
        }
        linea(sb, config.obtener(ConfiguracionService.NEGOCIO_CIUDAD, ""), ancho);
        String tel = config.obtener(ConfiguracionService.NEGOCIO_TELEFONO, "");
        if (!tel.isEmpty()) {
            linea(sb, "WhatsApp " + tel, ancho);
        }
        sb.append(raya(ancho)).append('\n');

        // ---- Datos de la venta
        sb.append("Recibo No. ").append(v.getNumero().replace("#", "")).append('\n');
        if (v.getFecha() != null) {
            sb.append("Fecha: ").append(v.getFecha().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.of("es", "CO"))))
                    .append("  Hora: ").append(hora(v.getFecha())).append('\n');
        }
        sb.append("Caja: ").append(config.obtener(ConfiguracionService.CAJA_NOMBRE, "Caja 1")).append('\n');
        sb.append("Atendió: ").append(v.getNombreUsuario()).append('\n');
        sb.append("Cliente: ").append(v.getNombreCliente() != null ? v.getNombreCliente() : "Cliente general").append('\n');
        sb.append(raya(ancho)).append('\n');

        // ---- Cada producto: su nombre y, abajo a la derecha, "precio x cantidad = subtotal"
        int unidades = 0;
        for (DetalleVenta d : v.getDetalles()) {
            sb.append(recortar(d.getNombreProducto().toUpperCase(), ancho)).append('\n');
            String cuenta = dinero(d.getPrecioUnit()) + " x " + d.getCantidad() + " = " + dinero(d.getSubtotal());
            sb.append(" ".repeat(Math.max(0, ancho - cuenta.length()))).append(cuenta).append('\n');
            unidades += d.getCantidad();
        }
        sb.append(raya(ancho)).append('\n');

        // ---- Totales
        sb.append("Total ítems: ").append(v.getDetalles().size()).append('\n');
        sb.append("Total cantidad: ").append(unidades).append('\n');
        sb.append(izqDer("Subtotal:", dinero(v.getSubtotal()), ancho)).append('\n');
        if (v.getDescuentoPromo() != null && v.getDescuentoPromo().signum() > 0) {
            sb.append(izqDer("Promociones:", "-" + dinero(v.getDescuentoPromo()), ancho)).append('\n');
        }
        if (v.getDescuentoPuntos() != null && v.getDescuentoPuntos().signum() > 0) {
            sb.append(izqDer("Puntos (" + v.getPuntosUsados() + "):", "-" + dinero(v.getDescuentoPuntos()), ancho)).append('\n');
        }
        sb.append(izqDer("Descuento:", dinero(v.getDescuento()), ancho)).append('\n');
        sb.append(GRANDE).append(izqDer("TOTAL:", dinero(v.getTotal()), ancho)).append('\n');
        sb.append(raya(ancho)).append('\n');

        // ---- Cómo se pagó
        if (v.getPagos().isEmpty()) {
            sb.append(izqDer("Pago:", v.getMetodoPago(), ancho)).append('\n');
        } else {
            for (Pago pago : v.getPagos()) {
                sb.append(izqDer(pago.etiqueta() + ":", dinero(pago.monto()), ancho)).append('\n');
            }
        }
        if (efectivoRecibido != null && devolucion != null) {
            if (devolucion.signum() > 0) {
                sb.append(izqDer("Recibido en efectivo:", dinero(efectivoRecibido), ancho)).append('\n');
            }
            sb.append(GRANDE).append(izqDer("Cambio:", dinero(devolucion), ancho)).append('\n');
        }
        sb.append(raya(ancho)).append('\n');

        // ---- Pie
        if (v.isPendienteDeSubir()) {
            linea(sb, "Recibo provisional (sin internet).", ancho);
            linea(sb, "Su número definitivo se asigna al subir.", ancho);
        }
        linea(sb, config.obtener(ConfiguracionService.RECIBO_PIE, "¡Gracias por su compra!"), ancho);
        return sb.toString();
    }

    /** El recibo sin las marcas de la impresora, para mostrarlo en pantalla. */
    public static String paraPantalla(String texto) {
        String[] lineas = texto.split("\n", -1);
        int ancho = 0;
        for (String l : lineas) {
            ancho = Math.max(ancho, l.length());
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lineas) {
            if (l.length() > 0 && l.charAt(0) == LOGO) {
                continue;
            }
            if (l.length() > 0 && l.charAt(0) == MUY_GRANDE) {
                // En la impresora esta línea sale al doble de ancho y queda centrada; en pantalla se centra con el ancho normal.
                String t = l.substring(1).strip();
                sb.append(" ".repeat(Math.max(0, (ancho - t.length()) / 2))).append(t).append('\n');
                continue;
            }
            sb.append(l.length() > 0 && l.charAt(0) == GRANDE ? l.substring(1) : l).append('\n');
        }
        return sb.toString();
    }

    // "05:23 p. m." (con espacios normales: algunas impresoras no tienen el espacio especial de los idiomas).
    private static String hora(LocalDateTime f) {
        int h = f.getHour() % 12 == 0 ? 12 : f.getHour() % 12;
        return String.format("%02d:%02d %s", h, f.getMinute(), f.getHour() < 12 ? "a. m." : "p. m.");
    }

    // Una línea centrada, solo si tiene algo escrito.
    private void linea(StringBuilder sb, String texto, int ancho) {
        if (texto != null && !texto.isBlank()) {
            sb.append(centrar(texto.trim(), ancho)).append('\n');
        }
    }

    private String dinero(BigDecimal valor) {
        return "$" + formatoDinero.format(valor);
    }

    private String raya(int ancho) {
        return "-".repeat(ancho);
    }

    private String centrar(String texto, int ancho) {
        texto = recortar(texto, ancho);
        int espacios = (ancho - texto.length()) / 2;
        return " ".repeat(espacios) + texto;
    }

    // Texto a la izquierda y otro pegado a la derecha, rellenando el medio con espacios.
    private String izqDer(String izq, String der, int ancho) {
        int relleno = Math.max(1, ancho - izq.length() - der.length());
        return izq + " ".repeat(relleno) + der;
    }

    private String recortar(String texto, int ancho) {
        return texto.length() <= ancho ? texto : texto.substring(0, ancho);
    }
}
