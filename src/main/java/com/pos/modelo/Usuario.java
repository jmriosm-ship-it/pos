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
    private java.math.BigDecimal cupo = java.math.BigDecimal.ZERO;      // cuánto puede sacar del local a cuenta del sueldo
    private java.math.BigDecimal pagoDia = java.math.BigDecimal.ZERO;   // lo que gana por día trabajado
    private int descuentoMax = 100;               // % máximo de rebaja que puede dar al cobrar (100 = sin límite)
    private boolean puedeAnular;                  // anular ventas y hacer devoluciones
    private boolean puedeEditarProductos = true;  // crear y editar productos
    private java.util.Set<Modulo> modulosExtra = java.util.EnumSet.noneOf(Modulo.class);   // pantallas además de las de su rol

    public Usuario() {
    }

    // Permisos simples basados en el rol; más adelante controlan qué módulos ve cada quien.
    public boolean esAdmin() {
        return "ADMIN".equals(rol);
    }

    public java.math.BigDecimal getCupo() { return cupo; }
    public void setCupo(java.math.BigDecimal cupo) { this.cupo = cupo; }
    public java.math.BigDecimal getPagoDia() { return pagoDia; }
    public void setPagoDia(java.math.BigDecimal pagoDia) { this.pagoDia = pagoDia; }
    public int getDescuentoMax() { return esAdmin() ? 100 : descuentoMax; }
    public void setDescuentoMax(int d) { this.descuentoMax = Math.max(0, Math.min(100, d)); }
    public boolean isPuedeAnular() { return puedeAnular; }
    public void setPuedeAnular(boolean puedeAnular) { this.puedeAnular = puedeAnular; }
    public boolean isPuedeEditarProductos() { return puedeEditarProductos; }
    public void setPuedeEditarProductos(boolean puedeEditarProductos) { this.puedeEditarProductos = puedeEditarProductos; }
    public java.util.Set<Modulo> getModulosExtra() { return modulosExtra; }
    public void setModulosExtra(java.util.Set<Modulo> modulosExtra) { this.modulosExtra = modulosExtra; }

    // El admin siempre puede; los demás solo si tienen el permiso.
    public boolean puedeAnularVentas() { return esAdmin() || puedeAnular || com.pos.servicio.Permisos.puede(this, Modulo.HISTORIAL, Accion.BORRAR); }
    public boolean puedeEditarCatalogo() { return esAdmin() || puedeEditarProductos; }

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
