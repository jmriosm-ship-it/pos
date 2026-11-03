package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fiado <-> tabla "fiados": lo que un cliente quedó debiendo de UNA venta. */
public class Fiado {

    private int id;
    private int idVenta;
    private int idCliente;
    private BigDecimal monto;       // lo que se fió originalmente
    private BigDecimal saldo;       // lo que todavía debe (baja con cada abono)
    private String estado;          // PENDIENTE, PAGADO o ANULADO
    private LocalDateTime creadoEn;

    public Fiado() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    @Override
    public String toString() {
        return "Fiado de la venta #" + idVenta + " (debe " + saldo + ")";
    }
}
