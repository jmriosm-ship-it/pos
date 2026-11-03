package com.pos.servicio;

import com.pos.dao.ProductoDAO;
import com.pos.dao.VentaDAO;
import com.pos.modelo.Caja;
import com.pos.modelo.Cliente;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Pago;
import com.pos.modelo.Producto;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class VentaService {

    private final VentaDAO ventaDAO = new VentaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CajaService cajaService = new CajaService();

    // Empieza una venta nueva (carrito vacío). Sin caja abierta no se puede vender.
    public Venta nuevaVenta(Usuario usuario) throws SQLException {
        Caja caja = cajaService.cajaAbierta(usuario);
        if (caja == null) {
            throw new IllegalStateException("Debes abrir una caja antes de vender.");
        }

        Venta v = new Venta();
        v.setIdCaja(caja.getId());
        v.setIdUsuario(usuario.getId());
        return v;
    }

    // Agrega un producto al carrito escaneando/escribiendo su código.
    // Si ya estaba en el carrito, suma la cantidad en vez de repetir la línea.
    public void agregarProducto(Venta v, String codigo, int cantidad) throws SQLException {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        Producto p = productoDAO.buscarPorCodigo(codigo);
        if (p == null) {
            throw new IllegalArgumentException("No existe un producto con el código " + codigo);
        }

        // ¿Ya está en el carrito?
        for (DetalleVenta d : v.getDetalles()) {
            if (d.getIdProducto() == p.getId()) {
                int nuevaCantidad = d.getCantidad() + cantidad;
                validarStock(p, nuevaCantidad);
                d.setCantidad(nuevaCantidad);
                v.calcularTotales();
                return;
            }
        }

        // Es un producto nuevo en el carrito: se crea su línea.
        validarStock(p, cantidad);
        DetalleVenta d = new DetalleVenta();
        d.setIdProducto(p.getId());
        d.setNombreProducto(p.getNombre());
        d.setCantidad(cantidad);
        d.setPrecioUnit(p.getPrecioVenta());   // se "congela" el precio de este momento
        d.setCostoUnit(p.getPrecioCompra());   // y el costo, para calcular la ganancia después
        v.getDetalles().add(d);
        v.calcularTotales();
    }

    // Quita un producto del carrito.
    public void quitarProducto(Venta v, int idProducto) {
        // removeIf elimina de la lista todo elemento que cumpla la condición.
        v.getDetalles().removeIf(d -> d.getIdProducto() == idProducto);
        v.calcularTotales();
    }

    // Aplica un descuento en pesos. No puede ser negativo ni mayor al subtotal.
    public void aplicarDescuento(Venta v, BigDecimal descuento) {
        if (descuento == null || descuento.signum() < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo.");
        }
        // compareTo: devuelve >0 si el primero es mayor, 0 si son iguales, <0 si es menor.
        if (descuento.compareTo(v.getSubtotal()) > 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al subtotal.");
        }
        v.setDescuento(descuento);
        v.calcularTotales();
    }

    // Cobra y guarda la venta pagada completa con UN solo método (atajo de la versión de múltiples pagos).
    public void finalizar(Venta v, String metodoPago) throws SQLException {
        v.calcularTotales();
        finalizar(v, List.of(new Pago(metodoPago, v.getTotal())));
    }

    // Cobra y guarda la venta. "pagos" son los medios con que se pagó (uno o varios) y deben
    // sumar EXACTAMENTE el total. El vuelto del efectivo no cuenta: aquí va solo lo aplicado.
    public void finalizar(Venta v, List<Pago> pagos) throws SQLException {
        if (v.getDetalles().isEmpty()) {
            throw new IllegalStateException("El carrito está vacío.");
        }
        v.calcularTotales();
        if (pagos == null || pagos.isEmpty()) {
            throw new IllegalArgumentException("Falta registrar el pago.");
        }

        BigDecimal suma = BigDecimal.ZERO;
        for (Pago p : pagos) {
            if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA", "FIADO").contains(p.metodo())) {
                throw new IllegalArgumentException("Método de pago no válido: " + p.metodo());
            }
            if (p.monto() == null || p.monto().signum() <= 0) {
                throw new IllegalArgumentException("Cada pago debe ser mayor a cero.");
            }
            suma = suma.add(p.monto());
        }
        if (suma.compareTo(v.getTotal()) != 0) {
            throw new IllegalArgumentException("Los pagos suman " + suma.toPlainString()
                    + " y el total es " + v.getTotal().toPlainString() + ".");
        }

        // Para fiar hay que saber a quién: el "cliente general" no puede quedar debiendo.
        boolean hayFiado = pagos.stream().anyMatch(p -> "FIADO".equals(p.metodo()));
        if (hayFiado && v.getIdCliente() == null) {
            throw new IllegalArgumentException("Para fiar tienes que elegir un cliente; el cliente general no puede quedar debiendo.");
        }

        v.setPagos(new ArrayList<>(pagos));
        v.setMetodoPago(pagos.size() == 1 ? pagos.get(0).metodo() : "MIXTO");
        ventaDAO.guardar(v);     // la transacción: venta + líneas + stock + movimientos + pagos
        v.setEstado("COMPLETADA");
    }

    // Cambia la cantidad de una línea del carrito (los botones + y - de la pantalla).
    // Si la nueva cantidad es 0 o menos, la línea se quita.
    public void cambiarCantidad(Venta v, int idProducto, int nuevaCantidad) throws SQLException {
        if (nuevaCantidad <= 0) {
            quitarProducto(v, idProducto);
            return;
        }
        for (DetalleVenta d : v.getDetalles()) {
            if (d.getIdProducto() == idProducto) {
                Producto p = productoDAO.buscarPorId(idProducto);
                validarStock(p, nuevaCantidad);
                d.setCantidad(nuevaCantidad);
                v.calcularTotales();
                return;
            }
        }
    }

    // Asigna un cliente a la venta. null = cliente general (sin cliente).
    public void asignarCliente(Venta v, Cliente cliente) {
        v.setIdCliente(cliente == null ? null : cliente.getId());
        v.setNombreCliente(cliente == null ? "Cliente general" : cliente.getNombre());
    }

    // Historial de ventas de un rango de fechas.
    public List<Venta> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        return ventaDAO.listar(desde, hasta);
    }

    // Una venta con todas sus líneas (para ver el detalle o reimprimir el recibo).
    public Venta detalle(int idVenta) throws SQLException {
        return ventaDAO.buscarPorId(idVenta);
    }

    // Anula una venta y devuelve las unidades al inventario. Solo lo puede hacer un ADMIN.
    public void anular(Usuario usuario, Venta v) throws SQLException {
        if (!usuario.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede anular ventas.");
        }
        if ("ANULADA".equals(v.getEstado())) {
            throw new IllegalStateException("La venta ya está anulada.");
        }
        ventaDAO.anular(v.getId(), usuario.getId());
        v.setEstado("ANULADA");
    }

    // Revisa que alcance el stock; si no, avisa cuántas unidades quedan.
    private void validarStock(Producto p, int cantidad) {
        if (cantidad > p.getStock()) {
            throw new IllegalStateException(
                    "Stock insuficiente de " + p.getNombre() + ": quedan " + p.getStock());
        }
    }
}
