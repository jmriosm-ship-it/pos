package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Gasto <-> tabla "gastos". Plata que sale del negocio (arriendo, servicios, nómina...). */
public class Gasto {

    private int id;
    private int idUsuario;
    private String nombreUsuario;   // solo para mostrar (viene de un JOIN)
    private String concepto;
    private String categoria = "Otros";
    private BigDecimal monto;
    private LocalDateTime fecha;

    public Gasto() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    @Override
    public String toString() {
        return concepto + " - " + monto;
    }
}
