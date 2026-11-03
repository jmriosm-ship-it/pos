package com.pos.modelo;

/**
 * Usuario <-> tabla "usuarios". Quien entra al sistema (admin, cajero...).
 * Guardamos el HASH de la clave, nunca la clave en texto: si alguien ve la
 * base de datos, no puede leer las contraseñas.
 */
public class Usuario {

    private int id;
    private String nombre;     // nombre real: "Juan Pérez"
    private String usuario;    // nombre para entrar: "juan"
    private String claveHash;  // clave cifrada con BCrypt
    private int idRol;         // 1 = ADMIN, 2 = CAJERO, 3 = BODEGUERO
    private String rol;        // texto del rol, se llena al consultar (JOIN con roles)
    private boolean activo;

    public Usuario() {
    }

    // Permisos simples basados en el rol; más adelante controlan qué módulos ve cada quien.
    public boolean esAdmin() {
        return "ADMIN".equals(rol);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getClaveHash() { return claveHash; }
    public void setClaveHash(String claveHash) { this.claveHash = claveHash; }

    public int getIdRol() { return idRol; }
    public void setIdRol(int idRol) { this.idRol = idRol; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return nombre + " (" + rol + ")";
    }
}
