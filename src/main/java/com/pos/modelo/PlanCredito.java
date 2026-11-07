package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El plan de un crédito en cuotas ya calculado: cuánto se financia, a qué tasa y plazo, y cada cuota con su fecha.
 * frecuencia: MENSUAL o QUINCENAL (cada 15 días). La tasa se anota como % mensual; en quincenal cada cuota cobra la mitad.
 */
public record PlanCredito(String frecuencia, int plazo, BigDecimal tasa, LocalDate primeraCuota, BigDecimal financiado,
                          BigDecimal cuotaValor, BigDecimal totalCredito, BigDecimal intereses, List<Cuota> cuotas) {

    // Una cuota: número, fecha de vencimiento y valor.
    public record Cuota(int n, LocalDate vence, BigDecimal valor) {
    }

    public String frecuenciaTexto() {
        return "QUINCENAL".equals(frecuencia) ? "quincenal" : "mensual";
    }
}
