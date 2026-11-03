package com.pos.modelo;

import java.math.BigDecimal;
import java.util.function.BiFunction;

public class DetalleVenta {
    //crear con clase private id, idVenta, idProducto, nombreProducto, cantidad, precioUnit, costoUnit los dos ultimo con BigDecimal
    private int id;
    private int idVenta;
    private int idProducto;
    private String nombreProducto;
    private int cantidad;
    private BigDecimal precioUnit;
    private BigDecimal costoUnit;

    public DetalleVenta(){
    }

    // El subtotal NO se guarda en el objeto: se calcula siempre a partir de precio y cantidad.
    // Así nunca puede quedar desactualizado si cambia la cantidad.

    public BigDecimal getSubtotal() {
        // BigDecimal.valueOf(int) convierte la cantidad para poder multiplicar.
        return precioUnit.multiply(BigDecimal.valueOf(cantidad));
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(int idVenta) {
        this.idVenta = idVenta;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnit() {
        return precioUnit;
    }

    public void setPrecioUnit(BigDecimal precioUnit) {
        this.precioUnit = precioUnit;
    }

    public BigDecimal getCostoUnit() {
        return costoUnit;
    }

    public void setCostoUnit(BigDecimal costoUnit) {
        this.costoUnit = costoUnit;
    }

    @Override
    public String toString(){
        return cantidad + " x " + nombreProducto + " = " + getSubtotal();
    }
}
