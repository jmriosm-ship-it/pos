package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Abono <-> tabla "abonos_fiado": un pago del cliente sobre una deuda. */
public class Abono {

    private int id;
    private int idFiado;
    private int idVenta;               // solo para mostrar (de qué venta era la deuda)
    private String metodo;             // EFECTIVO, TARJETA o TRANSFERENCIA
    private BigDecimal monto;
    private LocalDateTime fecha;
    private String nombreUsuario;      // solo para mostrar: quién recibió el abono

    public Abono() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdFiado() { return idFiado; }
    public void setIdFiado(int idFiado) { this.idFiado = idFiado; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    // El nombre para mostrar: EFECTIVO -> Efectivo.
    public String etiquetaMetodo() {
        return new Pago(metodo, monto).etiqueta();
    }
}
