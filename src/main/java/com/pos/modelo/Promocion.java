package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una promoción automática. Se aplica sola en el carrito:
 *  - PORCENTAJE: rebaja un % del valor de la línea (ej: 10 % en una marca).
 *  - NXM: "lleva N, paga M" (ej: lleva 2 paga 1 = 2x1).
 * Alcance: toda la tienda, una categoría o un producto. Si varias aplican a una línea, gana la que más rebaja.
 */
public class Promocion {

    private int id;
    private String nombre;
    private String alcance = "TIENDA";   // TIENDA, CATEGORIA o PRODUCTO
    private Integer idObjetivo;          // id de la categoría o del producto (null si es toda la tienda)
    private String tipo = "PORCENTAJE";  // PORCENTAJE o NXM
    private BigDecimal valor = BigDecimal.ZERO;   // el % cuando es PORCENTAJE
    private int lleva;
    private int paga;
    private LocalDate desde;             // opcionales: sin fecha = siempre
    private LocalDate hasta;
    private boolean activa = true;
    private String nombreObjetivo;       // solo para mostrar en la lista

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getAlcance() { return alcance; }
    public void setAlcance(String alcance) { this.alcance = alcance; }
    public Integer getIdObjetivo() { return idObjetivo; }
    public void setIdObjetivo(Integer idObjetivo) { this.idObjetivo = idObjetivo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public int getLleva() { return lleva; }
    public void setLleva(int lleva) { this.lleva = lleva; }
    public int getPaga() { return paga; }
    public void setPaga(int paga) { this.paga = paga; }
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public String getNombreObjetivo() { return nombreObjetivo; }
    public void setNombreObjetivo(String nombreObjetivo) { this.nombreObjetivo = nombreObjetivo; }

    // ¿Está vigente en esta fecha? (activa y dentro del rango de fechas, si lo tiene)
    public boolean vigente(LocalDate hoy) {
        return activa && (desde == null || !hoy.isBefore(desde)) && (hasta == null || !hoy.isAfter(hasta));
    }

    // Texto corto de lo que hace: "10 %" o "Lleva 2, paga 1".
    public String resumen() {
        return "PORCENTAJE".equals(tipo) ? valor.stripTrailingZeros().toPlainString() + " % de descuento"
                : "Lleva " + lleva + ", paga " + paga;
    }

    // Texto corto de dónde aplica.
    public String aplicaA() {
        return switch (alcance) {
            case "CATEGORIA" -> "Categoría: " + (nombreObjetivo == null ? "?" : nombreObjetivo);
            case "PRODUCTO" -> "Producto: " + (nombreObjetivo == null ? "?" : nombreObjetivo);
            default -> "Toda la tienda";
        };
    }
}
