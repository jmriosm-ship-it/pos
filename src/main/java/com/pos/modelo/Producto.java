package com.pos.modelo;

import java.math.BigDecimal;

/**
 * MODELO = una clase que representa UNA FILA de una tabla.
 * Producto <-> tabla "productos". Cada objeto Producto es un producto real
 * de la tienda (ej: "Cargador USB-C").
 *
 * Regla de oro: el modelo SOLO guarda datos. No habla con la base de datos
 * (eso lo hace el DAO) ni con la pantalla (eso lo hace el controlador).
 */
public class Producto {

    // Un atributo por cada columna de la tabla "productos".
    // private = nadie de afuera los toca directo, solo con getters/setters.
    private int id;
    private String codigo;            // código de barras o interno
    private String nombre;
    private Integer idCategoria;      // Integer (y no int) porque puede ser null = sin categoría
    private Integer idProveedor;
    private BigDecimal precioCompra;  // BigDecimal para dinero: double da errores de decimales
    private BigDecimal precioVenta;
    private int stock;
    private int stockMinimo;
    private boolean activo;

    // Constructor vacío: lo usa el DAO para crear el objeto y llenarlo con los setters.
    public Producto() {
    }

    // Método de ayuda: true si el stock ya está en el mínimo o por debajo (alerta de reposición).
    public boolean tieneStockBajo() {
        return stock <= stockMinimo;
    }

    // ----- Getters y setters -----
    // get = leer el valor, set = cambiarlo.
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getIdCategoria() { return idCategoria; }
    public void setIdCategoria(Integer idCategoria) { this.idCategoria = idCategoria; }

    public Integer getIdProveedor() { return idProveedor; }
    public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    // toString: cómo se muestra el objeto cuando lo imprimes o está en una lista.
    @Override
    public String toString() {
        return codigo + " - " + nombre;
    }
}
