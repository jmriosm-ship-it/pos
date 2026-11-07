package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un ingreso o egreso manual de efectivo en una caja (ej: sacar para el almuerzo, meter cambio). */
public record MovimientoCaja(int id, String tipo, BigDecimal monto, String motivo, LocalDateTime fecha, String usuario) {
}
