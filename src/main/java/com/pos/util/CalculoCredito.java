package com.pos.util;

import com.pos.modelo.PlanCredito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Las cuentas de un crédito (sistema francés: cuota fija).
 *   i = tasa mensual / 100   (quincenal: la mitad)
 *   cuota = financiado x i / (1 - (1 + i)^-plazo)      (si i = 0: financiado / plazo)
 *   total = cuota x plazo;  intereses = total - financiado
 * La última cuota se ajusta para que la suma dé exactamente el total.
 */
public class CalculoCredito {

    private CalculoCredito() {
    }

    public static PlanCredito simular(BigDecimal financiado, String frecuencia, int plazo, BigDecimal tasaMensualPct,
                                      LocalDate primeraCuota) {
        if (financiado == null || financiado.signum() <= 0) {
            throw new IllegalArgumentException("El valor a financiar debe ser mayor a cero.");
        }
        if (plazo <= 0) {
            throw new IllegalArgumentException("El plazo debe ser de al menos una cuota.");
        }
        if (tasaMensualPct == null || tasaMensualPct.signum() < 0) {
            throw new IllegalArgumentException("La tasa no puede ser negativa.");
        }
        boolean quincenal = "QUINCENAL".equals(frecuencia);
        double i = tasaMensualPct.doubleValue() / 100.0 / (quincenal ? 2 : 1);
        BigDecimal cuota;
        if (i == 0) {
            cuota = financiado.divide(BigDecimal.valueOf(plazo), 0, RoundingMode.HALF_UP);
        } else {
            double c = financiado.doubleValue() * i / (1 - Math.pow(1 + i, -plazo));
            cuota = BigDecimal.valueOf(c).setScale(0, RoundingMode.HALF_UP);
        }
        BigDecimal total = cuota.multiply(BigDecimal.valueOf(plazo));
        // Con interés cero el redondeo puede dejar un pequeño desfase: la última cuota lo absorbe.
        BigDecimal totalReal = i == 0 ? financiado : total;
        List<PlanCredito.Cuota> cuotas = new ArrayList<>();
        for (int k = 1; k <= plazo; k++) {
            // Mensual: se suma de a meses desde la primera (respeta fin de mes). Quincenal: de a 15 días.
            LocalDate vence = quincenal ? primeraCuota.plusDays(15L * (k - 1)) : primeraCuota.plusMonths(k - 1);
            BigDecimal valor = k < plazo ? cuota : totalReal.subtract(cuota.multiply(BigDecimal.valueOf(plazo - 1)));
            cuotas.add(new PlanCredito.Cuota(k, vence, valor));
        }
        return new PlanCredito(quincenal ? "QUINCENAL" : "MENSUAL", plazo, tasaMensualPct, primeraCuota, financiado,
                cuota, totalReal, totalReal.subtract(financiado), cuotas);
    }
}
