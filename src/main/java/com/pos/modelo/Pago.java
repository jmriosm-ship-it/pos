package com.pos.modelo;

import java.math.BigDecimal;

/**
 * Un pago de una venta. Una venta puede pagarse con varios medios a la vez
 * (por ejemplo, $20.000 en efectivo y el resto con tarjeta): cada uno es un Pago.
 * "monto" es lo que se aplicó a la venta; el vuelto del efectivo NO está incluido.
 *
 * Es un "record": una clase corta que solo guarda datos (Java crea solo el constructor,
 * pago.metodo(), pago.monto() y el toString).
 */
public record Pago(String metodo, BigDecimal monto) {

    // El nombre para mostrar: EFECTIVO -> Efectivo.
    public String etiqueta() {
        return switch (metodo) {
            case "EFECTIVO" -> "Efectivo";
            case "TARJETA" -> "Tarjeta";
            case "TRANSFERENCIA" -> "Transferencia";
            case "FIADO" -> "Fiado";
            default -> metodo;
        };
    }
}
