package com.pos.servicio;

import com.pos.dao.CajaDAO;
import com.pos.modelo.Caja;
import com.pos.util.TextoTicket;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Arma el comprobante del cierre de caja (el "ticket del arqueo"): cómo se llegó al total esperado,
 * lo que contó el cajero y si cuadra, falta o sobra. Está pensado para imprimirse en la térmica
 * y guardarse con las firmas.
 */
public class ComprobanteCajaService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CajaDAO cajaDAO = new CajaDAO();
    private final ConfiguracionService config = new ConfiguracionService();

    // La caja debe estar ya cerrada (con esperado, contado y diferencia calculados).
    public String generar(Caja caja, String nombreCajero) throws SQLException {
        int ancho = "58".equals(config.obtener(ConfiguracionService.IMPRESORA_ANCHO_MM, "80")) ? 32 : 48;
        Map<String, BigDecimal> ventas = cajaDAO.ventasPorMetodo(caja.getId());
        Map<String, BigDecimal> abonos = cajaDAO.abonosPorMetodo(caja.getId());
        StringBuilder sb = new StringBuilder();

        // Encabezado con los datos del negocio
        sb.append(TextoTicket.centrar(config.nombreNegocio().toUpperCase(), ancho)).append('\n');
        sb.append(TextoTicket.centrar("CIERRE DE CAJA No. " + caja.getId(), ancho)).append('\n');
        sb.append(TextoTicket.raya(ancho)).append('\n');
        sb.append("Cajero:   ").append(nombreCajero).append('\n');
        sb.append("Apertura: ").append(caja.getAbiertoEn().format(FECHA_HORA)).append('\n');
        LocalDateTime cierre = caja.getCerradaEn() != null ? caja.getCerradaEn() : LocalDateTime.now();
        sb.append("Cierre:   ").append(cierre.format(FECHA_HORA)).append('\n');
        sb.append(TextoTicket.raya(ancho)).append('\n');

        // Cómo se llegó al esperado
        fila(sb, "Monto inicial", caja.getMontoInicial(), ancho);
        sb.append(TextoTicket.izqDer("Ventas realizadas", String.valueOf(cajaDAO.contarVentas(caja.getId())), ancho)).append('\n');
        fila(sb, "Vendido en efectivo", ventas.get("EFECTIVO"), ancho);
        fila(sb, "Vendido con tarjeta", ventas.get("TARJETA"), ancho);
        fila(sb, "Vendido con transferencia", ventas.get("TRANSFERENCIA"), ancho);
        fila(sb, "Vendido a fiado (por cobrar)", ventas.get("FIADO"), ancho);
        if (!abonos.isEmpty()) {
            sb.append(TextoTicket.raya(ancho)).append('\n');
            fila(sb, "Abonos en efectivo", abonos.get("EFECTIVO"), ancho);
            fila(sb, "Abonos con tarjeta", abonos.get("TARJETA"), ancho);
            fila(sb, "Abonos con transferencia", abonos.get("TRANSFERENCIA"), ancho);
        }
        sb.append(TextoTicket.raya(ancho)).append('\n');

        // El arqueo
        sb.append(TextoTicket.izqDer("ESPERADO EN CAJA", TextoTicket.dinero(caja.getMontoEsperado()), ancho)).append('\n');
        sb.append(TextoTicket.izqDer("CONTADO", TextoTicket.dinero(caja.getMontoContado()), ancho)).append('\n');
        BigDecimal dif = caja.getDiferencia();
        String veredicto = dif.signum() == 0 ? "CAJA CUADRADA"
                : dif.signum() < 0 ? "FALTAN " + TextoTicket.dinero(dif.negate()) : "SOBRAN " + TextoTicket.dinero(dif);
        sb.append(TextoTicket.centrar(veredicto, ancho)).append('\n');
        sb.append(TextoTicket.raya(ancho)).append("\n\n\n");

        // Firmas
        sb.append("Firma cajero: ").append("_".repeat(Math.max(8, ancho - 14))).append("\n\n");
        sb.append("Firma admin:  ").append("_".repeat(Math.max(8, ancho - 14))).append('\n');
        return sb.toString();
    }

    // Agrega la fila solo si hubo algo con ese método (si no, el ticket se llena de ceros).
    private void fila(StringBuilder sb, String etiqueta, BigDecimal valor, int ancho) {
        if (valor != null && valor.signum() != 0) {
            sb.append(TextoTicket.izqDer(etiqueta, TextoTicket.dinero(valor), ancho)).append('\n');
        }
    }
}
