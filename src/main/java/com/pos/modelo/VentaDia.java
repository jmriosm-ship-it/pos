package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo vendido en un día. Una lista de estos alimenta la gráfica de ventas del dashboard. */
public record VentaDia(LocalDate dia, BigDecimal total) {
}
