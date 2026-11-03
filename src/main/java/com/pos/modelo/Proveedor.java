package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Proveedor <-> tabla "proveedores". Quien nos vende la mercancía. */
public class Proveedor {

    private int id;
    private String nombre;
    private String telefono;
    private String correo;
    private String direccion;
    private boolean activo;

    // Solo para mostrar en la ficha (vienen de un cálculo sobre las compras, no son columnas).
    private BigDecimal totalComprado = BigDecimal.ZERO;
    private LocalDateTime ultimaCompra;   // null si nunca se le ha comprado

    public Proveedor() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public BigDecimal getTotalComprado() { return totalComprado; }
    public void setTotalComprado(BigDecimal totalComprado) { this.totalComprado = totalComprado; }

    public LocalDateTime getUltimaCompra() { return ultimaCompra; }
    public void setUltimaCompra(LocalDateTime ultimaCompra) { this.ultimaCompra = ultimaCompra; }

    @Override
    public String toString() {
        return nombre;
    }
}
