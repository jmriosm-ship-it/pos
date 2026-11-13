package com.pos.servicio;

import com.pos.dao.CompraDAO;
import com.pos.dao.CompraExtra;
import com.pos.dao.ProductoDAO;
import com.pos.modelo.Compra;
import com.pos.modelo.DetalleCompra;
import com.pos.modelo.Producto;
import com.pos.modelo.Proveedor;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class CompraService {

    private final CompraDAO compraDAO = new CompraDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CompraExtra extra = new CompraExtra();

    // Empieza una compra nueva (lista vacía) a un proveedor.
    public Compra nuevaCompra(Usuario usuario, Proveedor proveedor) {
        if (proveedor == null) {
            throw new IllegalArgumentException("Debes elegir un proveedor.");
        }
        Compra c = new Compra();
        c.setIdUsuario(usuario.getId());
        c.setIdProveedor(proveedor.getId());
        c.setNombreProveedor(proveedor.getNombre());
        try {   // la caja abierta de quien compra: de ahí sale el efectivo si paga en efectivo
            com.pos.modelo.Caja caja = new CajaService().cajaAbierta(usuario);
            if (caja != null) {
                c.setIdCaja(caja.getId());
            }
        } catch (SQLException e) {
            // sin caja conocida: solo se podrá pagar con transferencia o tarjeta
        }
        return c;
    }

    // Agrega un producto a la compra, con la cantidad y el costo que pagamos por unidad.
    // Si el producto ya estaba en la lista, suma la cantidad y deja el costo nuevo.
    public void agregarProducto(Compra c, String codigo, int cantidad, BigDecimal costoUnit) throws SQLException {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        if (costoUnit == null || costoUnit.signum() <= 0) {
            throw new IllegalArgumentException("El costo debe ser mayor a cero.");
        }
        Producto p = productoDAO.buscarPorCodigo(codigo);
        if (p == null) {
            throw new IllegalArgumentException("No existe un producto con el código " + codigo);
        }

        for (DetalleCompra d : c.getDetalles()) {
            if (d.getIdProducto() == p.getId()) {
                d.setCantidad(d.getCantidad() + cantidad);
                d.setCostoUnit(costoUnit);
                c.calcularTotal();
                return;
            }
        }

        DetalleCompra d = new DetalleCompra();
        d.setIdProducto(p.getId());
        d.setNombreProducto(p.getNombre());
        d.setCantidad(cantidad);
        d.setCostoUnit(costoUnit);
        c.getDetalles().add(d);
        c.calcularTotal();
    }

    public void quitarProducto(Compra c, int idProducto) {
        c.getDetalles().removeIf(d -> d.getIdProducto() == idProducto);
        c.calcularTotal();
    }

    // Guarda la compra: sube el stock, actualiza costos y deja el historial.
    public void finalizar(Compra c) throws SQLException {
        if (c.getDetalles().isEmpty()) {
            throw new IllegalStateException("La compra no tiene productos.");
        }
        c.calcularTotal();
        // Lo que se paga al proveedor al comprar. Sin decir nada se da por pagada completa (por transferencia, que no toca la caja).
        if (c.getPagado() == null) {
            c.setPagado(c.getTotal());
        }
        if (c.getPagado().signum() < 0 || c.getPagado().compareTo(c.getTotal()) > 0) {
            throw new IllegalArgumentException("El pago inicial no puede ser negativo ni mayor al total de la compra.");
        }
        if (c.getPagado().signum() > 0) {
            if (c.getMetodoPago() == null) {
                c.setMetodoPago("TRANSFERENCIA");
            }
            validarMetodoProveedor(c.getMetodoPago());
            if ("EFECTIVO".equals(c.getMetodoPago()) && c.getIdCaja() == null) {
                throw new IllegalStateException("Para pagar en efectivo debes tener la caja abierta (el efectivo sale de la caja).");
            }
        }
        compraDAO.guardar(c);
        com.pos.offline.Sincronizador.pedirRefresco();
    }

    private static void validarMetodoProveedor(String metodo) {
        if (!java.util.List.of("EFECTIVO", "TRANSFERENCIA", "TARJETA").contains(metodo)) {
            throw new IllegalArgumentException("Método de pago no válido: " + metodo);
        }
    }

    // Los pagos que se han hecho al proveedor por una compra.
    public List<CompraExtra.Abono> abonos(int idCompra) throws SQLException {
        return extra.abonos(idCompra);
    }

    // Todo lo que se le debe a los proveedores.
    public BigDecimal totalPorPagar() throws SQLException {
        return extra.totalPorPagar();
    }

    // Paga (o abona) a un proveedor una compra que quedó debiéndose. El efectivo sale de la caja abierta de quien paga.
    public void abonar(Usuario usuario, Compra c, String metodo, BigDecimal monto) throws SQLException {
        if (!com.pos.servicio.Permisos.puede(usuario, com.pos.modelo.Modulo.INVENTARIO, com.pos.modelo.Accion.CREAR)) {
            throw new IllegalStateException("No tienes permiso para registrar pagos a proveedores.");
        }
        validarMetodoProveedor(metodo);
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El pago debe ser mayor a cero.");
        }
        Integer idCaja = null;
        if ("EFECTIVO".equals(metodo)) {
            com.pos.modelo.Caja caja = new CajaService().cajaAbierta(usuario);
            if (caja == null) {
                throw new IllegalStateException("Para pagar en efectivo debes tener la caja abierta (el efectivo sale de la caja).");
            }
            idCaja = caja.getId();
        }
        extra.abonar(c.getId(), usuario.getId(), idCaja, metodo, monto);
        AuditoriaService.registrar(usuario, "Pago a proveedor", "compra #" + c.getId() + " · " + monto.toPlainString() + " · " + metodo);
    }

    public List<Compra> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        return compraDAO.listar(desde, hasta);
    }

    // Las líneas de una compra ya registrada.
    public List<DetalleCompra> detalles(int idCompra) throws SQLException {
        return extra.detalles(idCompra);
    }

    // Anula una compra: el stock que subió se devuelve. No se puede si ya se vendió parte de esas unidades.
    public void anular(Usuario usuario, Compra c, String motivo) throws SQLException {
        if (!com.pos.servicio.Permisos.puede(usuario, com.pos.modelo.Modulo.INVENTARIO, com.pos.modelo.Accion.BORRAR)) {
            throw new IllegalStateException("No tienes permiso para anular compras.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe el motivo de la anulación.");
        }
        extra.anular(c.getId(), usuario.getId(), motivo.trim());
    }

    // Guarda los cambios de una compra (proveedor, cantidades y costos); el stock se corrige por la diferencia.
    public void editar(Usuario usuario, Compra c) throws SQLException {
        if (!com.pos.servicio.Permisos.puede(usuario, com.pos.modelo.Modulo.INVENTARIO, com.pos.modelo.Accion.EDITAR)) {
            throw new IllegalStateException("No tienes permiso para editar compras.");
        }
        if (c.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("La compra debe tener al menos un producto; si no la quieres, anúlala.");
        }
        for (DetalleCompra d : c.getDetalles()) {
            if (d.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad de \"" + d.getNombreProducto() + "\" debe ser mayor a cero.");
            }
            if (d.getCostoUnit() == null || d.getCostoUnit().signum() <= 0) {
                throw new IllegalArgumentException("El costo de \"" + d.getNombreProducto() + "\" debe ser mayor a cero.");
            }
        }
        c.calcularTotal();
        extra.editar(c, usuario.getId());
    }
}
