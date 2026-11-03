package com.pos.modelo;

/** Categoria <-> tabla "categorias". Agrupa productos (ej: Cargadores, Fundas). */
public class Categoria {

    private int id;
    private String nombre;
    private int totalProductos;   // solo para mostrar en pantalla (viene de un conteo, no es columna)

    public Categoria() {
    }

    // Constructor con datos: útil para crear una categoría nueva en una línea.
    public Categoria(String nombre) {
        this.nombre = nombre;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getTotalProductos() { return totalProductos; }
    public void setTotalProductos(int totalProductos) { this.totalProductos = totalProductos; }

    // Importante: un ComboBox (lista desplegable) de JavaFX muestra el toString().
    @Override
    public String toString() {
        return nombre;
    }
}
