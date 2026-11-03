package com.pos.modelo;

import java.math.BigDecimal;

public class DetalleCompra {

    private int id;
    private int idCompra;
    private int idProducto;
    private String nombreProducto;
    private int cantidad;
    private BigDecimal costoUnit;

    public DetalleCompra(){
    }

    public BigDecimal getSubtotal() {
        return costoUnit.multiply(BigDecimal.valueOf(cantidad));
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdCompra() {
        return idCompra;
    }

    public void setIdCompra(int idCompra) {
        this.idCompra = idCompra;
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

    public BigDecimal getCostoUnit() {
        return costoUnit;
    }

    public void setCostoUnit(BigDecimal costoUnit) {
        this.costoUnit = costoUnit;
    }

    @Override
    public String toString() {
        return cantidad + " x " + nombreProducto + " = " + getSubtotal();
    }
}
