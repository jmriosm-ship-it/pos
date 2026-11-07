package com.pos.modelo;

import java.math.BigDecimal;

/**
 * Los números clave de un periodo. Es un "record": una clase corta que solo guarda datos
 * y que Java arma sola (constructor, getters como resumen.ingresos(), toString...).
 *
 * ingresos      = lo vendido (ventas COMPLETADAS, ya con descuentos)
 * costoVendido  = lo que nos costó la mercancía que se vendió
 * gananciaBruta = ingresos - costoVendido
 * gastos        = arriendo, servicios, etc.
 * gananciaNeta  = gananciaBruta - gastos
 */
public record ResumenFinanciero(
        int numeroVentas,
        BigDecimal ingresos,
        BigDecimal costoVendido,
        BigDecimal gananciaBruta,
        BigDecimal gastos,
        BigDecimal gananciaNeta,
        BigDecimal ticketPromedio,
        BigDecimal intereses) {   // intereses de créditos cobrados en el periodo (suman a la ganancia neta)
}
