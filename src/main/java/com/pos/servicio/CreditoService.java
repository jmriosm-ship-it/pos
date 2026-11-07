package com.pos.servicio;

import com.pos.dao.CreditoDAO;
import com.pos.modelo.Caja;
import com.pos.modelo.PlanCredito;
import com.pos.modelo.Usuario;
import com.pos.util.CalculoCredito;
import com.pos.util.Dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Créditos en cuotas: las opciones que ofrece el negocio, el estado de cada crédito (al día, en mora...),
 * los pagos, el contrato y los avisos por WhatsApp.
 */
public class CreditoService {

    // Estado de una cuota después de aplicar los pagos de la más vieja a la más nueva.
    public record CuotaEstado(int n, LocalDate vence, BigDecimal valor, BigDecimal pagado, BigDecimal pendiente, String estado) {
    }

    // Estado de un crédito: lo pagado, el saldo, la mora y cómo va cada cuota.
    public record Estado(BigDecimal pagado, BigDecimal saldo, BigDecimal mora, int diasMora, String texto,
                         List<CuotaEstado> cuotas) {
    }

    // Un aviso por mandar al cliente (por WhatsApp).
    public record Aviso(CreditoDAO.Fila credito, String tipo, String clave, String texto, String url) {
    }

    private static final int REPETIR_MORA_DIAS = 3;
    private static final String PLANTILLA = "Hola {nombre}, te escribe {negocio}. Te recordamos que la cuota {n} de tu crédito "
            + "({valor}) vence {cuando}. Saldo pendiente: {saldo}. ¡Gracias!";
    private static final String PLANTILLA_MORA = "Hola {nombre}, te escribe {negocio}. Tienes {cuotas} cuota(s) vencida(s) por {valor} "
            + "(la más vieja venció el {fecha}). Saldo pendiente: {saldo}. Por favor acércate o escríbenos para ponerte al día.";

    private final CreditoDAO dao = new CreditoDAO();
    private final ConfiguracionService config = new ConfiguracionService();
    private final CajaService cajaService = new CajaService();

    // ------------------------------------------------------------ opciones del negocio

    public List<BigDecimal> tasas() throws SQLException {
        List<BigDecimal> lista = new ArrayList<>();
        for (String t : config.obtener(ConfiguracionService.CREDITO_TASAS, "2.3").split("[,;\\s]+")) {
            try {
                lista.add(new BigDecimal(t.replace(',', '.')));
            } catch (NumberFormatException e) {
                // se ignora lo que no sea número
            }
        }
        return lista.isEmpty() ? List.of(new BigDecimal("2.3")) : lista;
    }

    public List<Integer> plazos(String frecuencia) throws SQLException {
        String texto = "QUINCENAL".equals(frecuencia)
                ? config.obtener(ConfiguracionService.CREDITO_PLAZOS_QUINCENAL, "24,36,48")
                : config.obtener(ConfiguracionService.CREDITO_PLAZOS, "6,12,18,24,36");
        List<Integer> lista = new ArrayList<>();
        for (String t : texto.split("[,;\\s]+")) {
            try {
                lista.add(Integer.parseInt(t.trim()));
            } catch (NumberFormatException e) {
                // se ignora lo que no sea número
            }
        }
        return lista;
    }

    public BigDecimal inicialMinimaPct() throws SQLException {
        try {
            return new BigDecimal(config.obtener(ConfiguracionService.CREDITO_INICIAL_MIN, "30").replace(',', '.'));
        } catch (NumberFormatException e) {
            return new BigDecimal("30");
        }
    }

    // null = sin tope de tasa.
    public BigDecimal tasaMaxima() throws SQLException {
        String t = config.obtener(ConfiguracionService.CREDITO_TASA_MAXIMA, "");
        try {
            return t.isBlank() ? null : new BigDecimal(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ armar y validar un plan

    public PlanCredito simular(BigDecimal financiado, String frecuencia, int plazo, BigDecimal tasa, LocalDate primera) throws SQLException {
        BigDecimal tope = tasaMaxima();
        if (tope != null && tasa.compareTo(tope) > 0) {
            throw new IllegalArgumentException("La tasa " + tasa.toPlainString() + " % supera el tope permitido (" + tope.toPlainString() + " %).");
        }
        return CalculoCredito.simular(financiado, frecuencia, plazo, tasa, primera);
    }

    // La cuota inicial (lo que se paga hoy) debe ser al menos el mínimo % del total de la venta.
    public void validarInicial(BigDecimal totalVenta, BigDecimal financiado) throws SQLException {
        BigDecimal inicial = totalVenta.subtract(financiado);
        BigDecimal pct = totalVenta.signum() == 0 ? BigDecimal.ZERO
                : inicial.multiply(new BigDecimal("100")).divide(totalVenta, 1, RoundingMode.HALF_UP);
        if (pct.compareTo(inicialMinimaPct()) < 0) {
            throw new IllegalArgumentException("La cuota inicial es del " + pct.toPlainString() + " % y el mínimo es "
                    + inicialMinimaPct().toPlainString() + " %. Sube lo que se paga hoy.");
        }
    }

    // ------------------------------------------------------------ consultas

    public List<CreditoDAO.Fila> listar(boolean soloActivos) throws SQLException {
        List<CreditoDAO.Fila> lista = new ArrayList<>(dao.listar(soloActivos));
        // Primero los que están en mora (más días arriba), luego por saldo.
        lista.sort(Comparator.comparing((CreditoDAO.Fila f) -> -diasMoraSeguro(f)).thenComparing(f -> saldoSeguro(f).negate()));
        return lista;
    }

    private int diasMoraSeguro(CreditoDAO.Fila f) {
        try {
            return "ACTIVO".equals(f.estado()) ? estado(f).diasMora() : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private BigDecimal saldoSeguro(CreditoDAO.Fila f) {
        try {
            return estado(f).saldo();
        } catch (SQLException e) {
            return BigDecimal.ZERO;
        }
    }

    public Estado estado(CreditoDAO.Fila c) throws SQLException {
        BigDecimal pagado = dao.totalPagado(c.id());
        BigDecimal saldo = c.totalCredito().subtract(pagado).max(BigDecimal.ZERO);
        BigDecimal resto = pagado;
        LocalDate hoy = LocalDate.now();
        List<CuotaEstado> cuotas = new ArrayList<>();
        BigDecimal mora = BigDecimal.ZERO;
        int diasMora = 0;
        for (PlanCredito.Cuota q : dao.cuotas(c.id())) {
            BigDecimal abono = resto.min(q.valor());
            resto = resto.subtract(abono);
            BigDecimal pendiente = q.valor().subtract(abono);
            boolean vencida = pendiente.signum() > 0 && q.vence().isBefore(hoy);
            String texto = pendiente.signum() == 0 ? "Pagada" : vencida ? "Vencida" : abono.signum() > 0 ? "Parcial" : "Pendiente";
            if (vencida) {
                mora = mora.add(pendiente);
                diasMora = Math.max(diasMora, (int) ChronoUnit.DAYS.between(q.vence(), hoy));
            }
            cuotas.add(new CuotaEstado(q.n(), q.vence(), q.valor(), abono, pendiente, texto));
        }
        String texto = "CANCELADO".equals(c.estado()) ? "Cancelado" : saldo.signum() == 0 ? "Pagado" : mora.signum() > 0 ? "En mora" : "Al día";
        return new Estado(pagado, saldo, mora, diasMora, texto, cuotas);
    }

    public List<CreditoDAO.Pago> pagos(int idCredito) throws SQLException {
        return dao.pagos(idCredito);
    }

    // ------------------------------------------------------------ acciones

    // Registra un pago del cliente (no puede pasar del saldo). El efectivo entra a la caja abierta de quien lo recibe.
    public void pagar(Usuario usuario, CreditoDAO.Fila c, String metodo, BigDecimal valor) throws SQLException {
        if (!"ACTIVO".equals(c.estado())) {
            throw new IllegalStateException("Este crédito está cancelado.");
        }
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA").contains(metodo)) {
            throw new IllegalArgumentException("Método de pago no válido.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El valor debe ser mayor a cero.");
        }
        Estado e = estado(c);
        if (valor.compareTo(e.saldo()) > 0) {
            throw new IllegalArgumentException("El pago supera el saldo (" + Dinero.formato(e.saldo()) + ").");
        }
        Caja caja = cajaService.cajaAbierta(usuario);
        if ("EFECTIVO".equals(metodo) && caja == null) {
            throw new IllegalStateException("Abre tu caja para recibir efectivo.");
        }
        dao.insertarPago(c.id(), usuario.getId(), caja == null ? null : caja.getId(), metodo, valor);
        AuditoriaService.registrar(usuario, "Pago de crédito", c.ventaNumero() + " · " + c.cliente() + " · " + valor.toPlainString());
    }

    // Cancela el crédito (solo admin). Lo que el cliente ya pagó se devuelve aparte.
    public void cancelar(Usuario usuario, CreditoDAO.Fila c) throws SQLException {
        if (!usuario.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede cancelar un crédito.");
        }
        dao.cancelar(c.id());
        AuditoriaService.registrar(usuario, "Crédito cancelado", c.ventaNumero() + " · " + c.cliente());
    }

    // ------------------------------------------------------------ contrato

    public String contrato(CreditoDAO.Fila c) throws SQLException {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String negocio = config.nombreNegocio();
        String nit = config.obtener(ConfiguracionService.NEGOCIO_NIT, "");
        StringBuilder sb = new StringBuilder();
        sb.append("CONTRATO DE VENTA A CRÉDITO EN CUOTAS\n");
        sb.append("=====================================\n\n");
        sb.append("ACREEDOR: ").append(negocio.toUpperCase()).append(nit.isBlank() ? "" : "  (NIT " + nit + ")").append('\n');
        sb.append("DEUDOR:   ").append(c.cliente()).append("   C.C. ").append(nz(c.documento()))
                .append("   Tel. ").append(nz(c.telefono())).append("\n\n");
        sb.append("Venta: ").append(c.ventaNumero()).append("    Fecha: ").append(c.creado().format(f)).append('\n');
        sb.append("Producto(s): ").append(c.descripcion()).append("\n\n");
        sb.append("Precio de la venta:        ").append(Dinero.formato(c.precio())).append('\n');
        sb.append("Cuota inicial pagada:      ").append(Dinero.formato(c.cuotaInicial())).append('\n');
        sb.append("Valor financiado:          ").append(Dinero.formato(c.financiado())).append('\n');
        sb.append("Plazo:                     ").append(c.plazo()).append(" cuotas ")
                .append("QUINCENAL".equals(c.frecuencia()) ? "quincenales" : "mensuales").append('\n');
        sb.append("Tasa:                      ").append(c.tasa().toPlainString()).append(" % mensual\n");
        sb.append("Valor de la cuota:         ").append(Dinero.formato(c.cuotaValor())).append('\n');
        sb.append("Total del crédito:         ").append(Dinero.formato(c.totalCredito())).append('\n');
        sb.append("Intereses:                 ").append(Dinero.formato(c.intereses())).append("\n\n");
        sb.append("PLAN DE PAGOS\n");
        for (PlanCredito.Cuota q : dao.cuotas(c.id())) {
            sb.append(String.format(Locale.ROOT, "  Cuota %2d   %s   %s%n", q.n(), q.vence().format(f), Dinero.formato(q.valor())));
        }
        sb.append("\nCLÁUSULAS\n");
        sb.append("1. El deudor puede pagar por anticipado; el abono se aplica primero a las cuotas más viejas.\n");
        sb.append("2. En caso de mora se cobrarán intereses a la tasa máxima legal vigente, sin superar la usura.\n");
        sb.append("3. Si el deudor incumple dos o más cuotas, el acreedor puede exigir el saldo total.\n");
        sb.append("4. El deudor autoriza el tratamiento de sus datos personales (Ley 1581 de 2012).\n");
        sb.append("5. Consulta y reporte a centrales de riesgo (Ley 1266 de 2008):  AUTORIZO [  ] SI   [  ] NO\n");
        sb.append("6. Este modelo debe ser revisado por un abogado y el pagaré se firma por aparte.\n\n\n");
        sb.append("Firma del deudor: ______________________   C.C. ____________\n\n");
        sb.append("Firma del acreedor: ____________________\n");
        return sb.toString();
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "________" : s;
    }

    // ------------------------------------------------------------ avisos por WhatsApp

    // Los avisos que toca mandar hoy: cuotas que vencen pronto (una vez por cuota) y mora (cada 3 días).
    public List<Aviso> avisosPendientes() throws SQLException {
        LocalDate hoy = LocalDate.now();
        int diasAntes = entero(config.obtener(ConfiguracionService.WA_DIAS_ANTES, "1"), 1);
        String plantilla = config.obtener(ConfiguracionService.WA_PLANTILLA, PLANTILLA);
        String plantillaMora = config.obtener(ConfiguracionService.WA_PLANTILLA_MORA, PLANTILLA_MORA);
        String negocio = config.nombreNegocio();
        List<Aviso> lista = new ArrayList<>();
        for (CreditoDAO.Fila c : dao.listar(true)) {
            Estado e = estado(c);
            if (e.saldo().signum() == 0) {
                continue;
            }
            Set<String> enviados = dao.avisos(c.id());
            for (CuotaEstado q : e.cuotas()) {
                boolean pronto = q.pendiente().signum() > 0 && !q.vence().isBefore(hoy) && !q.vence().isAfter(hoy.plusDays(diasAntes));
                String clave = "aviso:" + q.n();
                if (pronto && !enviados.contains(clave)) {
                    String cuando = q.vence().equals(hoy) ? "hoy" : q.vence().equals(hoy.plusDays(1)) ? "mañana"
                            : "el " + q.vence().format(DateTimeFormatter.ofPattern("dd/MM"));
                    lista.add(armar(c, "aviso", clave, plantilla, negocio, q.n(), 1, q.pendiente(), q.vence(), cuando, e.saldo()));
                }
            }
            if (e.mora().signum() > 0) {
                LocalDate ultimo = dao.ultimoAvisoMora(c.id());
                if (ultimo == null || ChronoUnit.DAYS.between(ultimo, hoy) >= REPETIR_MORA_DIAS) {
                    CuotaEstado vieja = e.cuotas().stream().filter(q -> q.pendiente().signum() > 0 && q.vence().isBefore(hoy)).findFirst().get();
                    long vencidas = e.cuotas().stream().filter(q -> q.pendiente().signum() > 0 && q.vence().isBefore(hoy)).count();
                    lista.add(armar(c, "mora", "mora:" + hoy, plantillaMora, negocio, vieja.n(), (int) vencidas, e.mora(), vieja.vence(), "", e.saldo()));
                }
            }
        }
        return lista;
    }

    // El aviso de UN crédito (botón "Recordar por WhatsApp"): mora si la hay; si no, la próxima cuota.
    public Aviso avisoDe(CreditoDAO.Fila c) throws SQLException {
        Estado e = estado(c);
        LocalDate hoy = LocalDate.now();
        String negocio = config.nombreNegocio();
        long vencidas = e.cuotas().stream().filter(q -> q.pendiente().signum() > 0 && q.vence().isBefore(hoy)).count();
        if (vencidas > 0) {
            CuotaEstado vieja = e.cuotas().stream().filter(q -> q.pendiente().signum() > 0 && q.vence().isBefore(hoy)).findFirst().get();
            return armar(c, "mora", "mora:" + hoy, config.obtener(ConfiguracionService.WA_PLANTILLA_MORA, PLANTILLA_MORA), negocio,
                    vieja.n(), (int) vencidas, e.mora(), vieja.vence(), "", e.saldo());
        }
        CuotaEstado proxima = e.cuotas().stream().filter(q -> q.pendiente().signum() > 0).findFirst()
                .orElseThrow(() -> new IllegalStateException("Este crédito no tiene cuotas pendientes."));
        String cuando = proxima.vence().equals(hoy) ? "hoy" : "el " + proxima.vence().format(DateTimeFormatter.ofPattern("dd/MM"));
        return armar(c, "aviso", "aviso:" + proxima.n(), config.obtener(ConfiguracionService.WA_PLANTILLA, PLANTILLA), negocio,
                proxima.n(), 1, proxima.pendiente(), proxima.vence(), cuando, e.saldo());
    }

    public void marcarAviso(Aviso a) throws SQLException {
        dao.marcarAviso(a.credito().id(), a.clave());
    }

    private Aviso armar(CreditoDAO.Fila c, String tipo, String clave, String plantilla, String negocio, int n, int cuotas,
                        BigDecimal valor, LocalDate fecha, String cuando, BigDecimal saldo) {
        String nombre = c.cliente().split("\\s+")[0];
        String texto = plantilla.replace("{nombre}", nombre).replace("{negocio}", negocio).replace("{n}", String.valueOf(n))
                .replace("{cuotas}", String.valueOf(cuotas)).replace("{valor}", Dinero.formato(valor))
                .replace("{fecha}", fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).replace("{cuando}", cuando)
                .replace("{saldo}", Dinero.formato(saldo));
        return new Aviso(c, tipo, clave, texto, urlWhatsApp(c.telefono(), texto));
    }

    // wa.me/57XXXXXXXXXX?text=...  (si el número tiene 10 dígitos se le pone el 57 de Colombia).
    public static String urlWhatsApp(String telefono, String texto) {
        String d = telefono == null ? "" : telefono.replaceAll("\\D", "");
        if (d.length() == 10) {
            d = "57" + d;
        }
        return "https://wa.me/" + d + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private int entero(String t, int defecto) {
        try {
            return Integer.parseInt(t.trim());
        } catch (NumberFormatException e) {
            return defecto;
        }
    }
}
