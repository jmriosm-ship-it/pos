package com.pos.modelo;

import java.math.BigDecimal;

/** Un producto con lo que se ha vendido de él (para el ranking de "más vendidos"). */
public record ProductoVendido(String nombre, int unidades, BigDecimal total) {
}
