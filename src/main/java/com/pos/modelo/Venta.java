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
    private BigDecimal descuentoPromo = BigDecimal.ZERO;    // lo que rebajan las promociones automáticas
    private BigDecimal descuentoPuntos = BigDecimal.ZERO;   // lo que se pagó con puntos del cliente
    private int puntosUsados;
    private int puntosGanados;
    private String etiqueta;          // número de recibo por caja, ej: C1-00005
    private PlanCredito planCredito;  // si parte de la venta se financia en cuotas
    private String financieraNombre;  // si parte de la venta la paga una financiera
    private String prefijoCaja = "C1"; // solo para armar la etiqueta al guardar
    private boolean usarPuntos;       // el cajero eligió pagar con puntos (solo mientras arma la venta)
    private int puntosDisponibles;    // puntos que tiene el cliente (solo para armar la venta)
    private String metodoPago;

    private String estado;

    // Solo para mostrar en pantallas e historial (vienen de un JOIN, no son columnas de "ventas").
    private String nombreCliente;
    private String nombreUsuario;
    private int unidades;        // total de unidades vendidas (solo para el historial)

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
        total = subtotal.subtract(descuentoPromo).subtract(descuento).subtract(descuentoPuntos);
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

    public BigDecimal getDescuentoPromo() { return descuentoPromo; }
    public void setDescuentoPromo(BigDecimal d) { this.descuentoPromo = d; }

    public BigDecimal getDescuentoPuntos() { return descuentoPuntos; }
    public void setDescuentoPuntos(BigDecimal d) { this.descuentoPuntos = d; }

    public int getPuntosUsados() { return puntosUsados; }
    public void setPuntosUsados(int p) { this.puntosUsados = p; }

    public String getFinancieraNombre() { return financieraNombre; }
    public void setFinancieraNombre(String f) { this.financieraNombre = f; }
    public PlanCredito getPlanCredito() { return planCredito; }
    public void setPlanCredito(PlanCredito p) { this.planCredito = p; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String e) { this.etiqueta = e; }
    public String getPrefijoCaja() { return prefijoCaja; }
    public void setPrefijoCaja(String p) { this.prefijoCaja = p; }
    // El número que se muestra: C1-00005 (o #id en ventas viejas).
    public String getNumero() { return etiqueta != null && !etiqueta.isBlank() ? etiqueta : "#" + id; }

    public boolean isUsarPuntos() { return usarPuntos; }
    public void setUsarPuntos(boolean u) { this.usarPuntos = u; }
    public int getPuntosDisponibles() { return puntosDisponibles; }
    public void setPuntosDisponibles(int p) { this.puntosDisponibles = p; }

    public int getPuntosGanados() { return puntosGanados; }
    public void setPuntosGanados(int p) { this.puntosGanados = p; }

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

    public int getUnidades() { return unidades; }
    public void setUnidades(int unidades) { this.unidades = unidades; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    @Override
    public String toString() {
        return "Venta #" + id + " - total " + total + " (" + estado + ")";
    }
}

