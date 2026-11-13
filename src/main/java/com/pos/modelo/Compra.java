package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Compra <-> tabla "compras". Mercancía que se le compra a un proveedor; hace SUBIR el stock. */
public class Compra {

    private int id;
    private int idProveedor;
    private String nombreProveedor;      // solo para mostrar (no es columna de la tabla)
    private int idUsuario;               // quién registró la compra
    private LocalDateTime fecha;
    private BigDecimal total = BigDecimal.ZERO;
    private int totalUnidades;           // solo para mostrar en listados (suma de las cantidades)
    private boolean anulada;            // true si la compra se anuló (el stock que subió ya se devolvió)
    private BigDecimal pagado;           // lo pagado al proveedor; null = compra de antes de este control (se da por pagada)
    private String metodoPago;           // solo al registrar: con qué se hizo el pago inicial (EFECTIVO, TRANSFERENCIA, TARJETA)
    private Integer idCaja;              // solo al registrar: la caja de donde sale el efectivo del pago inicial

    // Las líneas de la compra. Se crea vacía para poder hacer getDetalles().add(...)
    private List<DetalleCompra> detalles = new ArrayList<>();

    public Compra() {
    }

    // Recalcula el total sumando todas las líneas.
    // Se llama cada vez que cambia la lista de productos de la compra.
    public void calcularTotal() {
        BigDecimal suma = BigDecimal.ZERO;
        for (DetalleCompra d : detalles) {
            suma = suma.add(d.getSubtotal());
        }
        total = suma;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdProveedor() { return idProveedor; }
    public void setIdProveedor(int idProveedor) { this.idProveedor = idProveedor; }

    public String getNombreProveedor() { return nombreProveedor; }
    public void setNombreProveedor(String nombreProveedor) { this.nombreProveedor = nombreProveedor; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public BigDecimal getPagado() { return pagado; }
    public void setPagado(BigDecimal pagado) { this.pagado = pagado; }
    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public Integer getIdCaja() { return idCaja; }
    public void setIdCaja(Integer idCaja) { this.idCaja = idCaja; }

    // Lo que todavía se le debe al proveedor por esta compra (0 si está pagada, es de antes o se anuló).
    public BigDecimal getSaldo() {
        if (pagado == null || anulada || total == null) {
            return BigDecimal.ZERO;
        }
        return total.subtract(pagado).max(BigDecimal.ZERO);
    }

    public boolean isAnulada() { return anulada; }
    public void setAnulada(boolean anulada) { this.anulada = anulada; }

    public int getTotalUnidades() { return totalUnidades; }
    public void setTotalUnidades(int totalUnidades) { this.totalUnidades = totalUnidades; }

    public List<DetalleCompra> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleCompra> detalles) { this.detalles = detalles; }

    @Override
    public String toString() {
        return "Compra #" + id + " - total " + total;
    }
}
