package com.pos.modelo;

import java.time.LocalDateTime;

/** Cliente <-> tabla "clientes". Su historial de compras sale de la tabla "ventas". */
public class Cliente {

    private int id;
    private String nombre;
    private String documento;   // cédula o NIT
    private String telefono;
    private String correo;
    private String direccion;
    private LocalDateTime creadoEn;  // LocalDateTime = fecha + hora (equivale a DATETIME de MySQL)

    // Solo para mostrar en la ficha (vienen de un cálculo, no son columnas de "clientes").
    private java.math.BigDecimal totalComprado = java.math.BigDecimal.ZERO;
    private java.math.BigDecimal saldoPendiente = java.math.BigDecimal.ZERO;   // lo que debe por fiados

    public Cliente() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public java.math.BigDecimal getTotalComprado() { return totalComprado; }
    public void setTotalComprado(java.math.BigDecimal totalComprado) { this.totalComprado = totalComprado; }

    public java.math.BigDecimal getSaldoPendiente() { return saldoPendiente; }
    public void setSaldoPendiente(java.math.BigDecimal saldoPendiente) { this.saldoPendiente = saldoPendiente; }

    @Override
    public String toString() {
        return nombre;
    }
}
