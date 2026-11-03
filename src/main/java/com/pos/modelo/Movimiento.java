package com.pos.modelo;

import java.time.LocalDateTime;

/**
 * Movimiento <-> tabla "movimientos_inventario".
 * Cada vez que el stock cambia (venta, compra, ajuste) queda una fila aquí,
 * para saber siempre POR QUÉ cambió una cantidad.
 */
public class Movimiento {

    private int id;
    private int idProducto;
    private String nombreProducto;   // solo para mostrar (viene de un JOIN)
    private int idUsuario;
    private String nombreUsuario;    // solo para mostrar (viene de un JOIN)
    private String tipo;             // ENTRADA, SALIDA o AJUSTE
    private int cantidad;            // en AJUSTE puede ser negativa (ej: -2 = faltaban 2 unidades)
    private String motivo;           // "Venta #12", "Compra #3", "Merma"...
    private LocalDateTime fecha;

    public Movimiento() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    @Override
    public String toString() {
        return tipo + " " + cantidad + " de " + nombreProducto + " (" + motivo + ")";
    }
}
