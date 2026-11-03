package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Venta {

    private int id;
    private int idCaja; //en que turno de caja se hizo
    private int idUsuario;
    private Integer idCliente; //integer porque puede ser null =cliente general
    private LocalDateTime fecha;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal descuento = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String metodoPago;

    private String estado;

    // Solo para mostrar en pantallas e historial (vienen de un JOIN, no son columnas de "ventas").
    private String nombreCliente;
    private String nombreUsuario;

    private List<DetalleVenta> detalles = new ArrayList<>();

    // Con qué se pagó. Una venta puede tener varios pagos (efectivo + tarjeta, etc.).
    private List<Pago> pagos = new ArrayList<>();

    public Venta(){
    }
    // recalcula subtotal y total a partir de las lineas y el descuento.

    //se llama cada vez que cambia el carrito (agregar, quitar, cambiar cantidad)

    public void calcularTotales(){
        BigDecimal suma =BigDecimal.ZERO;
        for (DetalleVenta d: detalles){
            suma= suma.add(d.getSubtotal());
        }
        subtotal = suma;
        total = subtotal.subtract(descuento);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdCaja() {
        return idCaja;
    }

    public void setIdCaja(int idCaja) {
        this.idCaja = idCaja;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public Integer getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(Integer idCliente) {
        this.idCliente = idCliente;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public void setDescuento(BigDecimal descuento) {
        this.descuento = descuento;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public List<DetalleVenta> getDetalles(){
        return detalles;
    }

    public void setDetalles(List<DetalleVenta>detalles){
        this.detalles = detalles;
    }

    public List<Pago> getPagos() { return pagos; }
    public void setPagos(List<Pago> pagos) { this.pagos = pagos; }

    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String nombreCliente) { this.nombreCliente = nombreCliente; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    @Override
    public String toString() {
        return "Venta #" + id + " - total " + total + " (" + estado + ")";
    }
}

