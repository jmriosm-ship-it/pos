package com.pos.servicio;

import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Arma el texto del recibo de una venta, listo para una impresora térmica.
 * La impresora térmica solo entiende texto con ancho fijo: 80 mm = 48 caracteres, 58 mm = 32.
 */
public class ReciboService {

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

        sb.append(centrar(config.nombreNegocio().toUpperCase(), ancho)).append('\n');
        String nit = config.obtener(ConfiguracionService.NEGOCIO_NIT, "");
        if (!nit.isEmpty()) {
            sb.append(centrar("NIT " + nit, ancho)).append('\n');
        }
        String tel = config.obtener(ConfiguracionService.NEGOCIO_TELEFONO, "");
        if (!tel.isEmpty()) {
            sb.append(centrar("Tel " + tel, ancho)).append('\n');
        }
        sb.append(raya(ancho)).append('\n');

        sb.append("Recibo No. ").append(v.getId()).append('\n');
        if (v.getFecha() != null) {
            sb.append("Fecha: ").append(v.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append('\n');
        }
        sb.append("Atendió: ").append(v.getNombreUsuario()).append('\n');
        sb.append("Cliente: ").append(v.getNombreCliente() != null ? v.getNombreCliente() : "Cliente general").append('\n');
        sb.append(raya(ancho)).append('\n');

        // Cada producto: primero su nombre, y abajo "cantidad x precio" con el subtotal a la derecha.
        for (DetalleVenta d : v.getDetalles()) {
            sb.append(recortar(d.getNombreProducto(), ancho)).append('\n');
            sb.append(izqDer("  " + d.getCantidad() + " x " + dinero(d.getPrecioUnit()), dinero(d.getSubtotal()), ancho)).append('\n');
        }
        sb.append(raya(ancho)).append('\n');

        sb.append(izqDer("Subtotal", dinero(v.getSubtotal()), ancho)).append('\n');
        if (v.getDescuento().signum() > 0) {
            sb.append(izqDer("Descuento", "-" + dinero(v.getDescuento()), ancho)).append('\n');
        }
        sb.append(izqDer("TOTAL", dinero(v.getTotal()), ancho)).append('\n');
        if (v.getPagos().isEmpty()) {
            sb.append(izqDer("Pago", v.getMetodoPago(), ancho)).append('\n');
        } else {
            for (com.pos.modelo.Pago pago : v.getPagos()) {
                sb.append(izqDer(pago.etiqueta(), dinero(pago.monto()), ancho)).append('\n');
            }
        }
        if (devolucion != null && devolucion.signum() > 0) {
            sb.append(izqDer("Recibido en efectivo", dinero(efectivoRecibido), ancho)).append('\n');
            sb.append(izqDer("DEVOLUCIÓN", dinero(devolucion), ancho)).append('\n');
        }
        sb.append(raya(ancho)).append('\n');
        sb.append(centrar("¡Gracias por su compra!", ancho)).append('\n');
        return sb.toString();
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
