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

import com.pos.util.Dinero;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class VentaService {

    private final VentaDAO ventaDAO = new VentaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CajaService cajaService = new CajaService();
    private final PromocionService promociones = new PromocionService();
    private final ConfiguracionService config = new ConfiguracionService();

    // Empieza una venta nueva (carrito vacío). Sin caja abierta no se puede vender.
    public Venta nuevaVenta(Usuario usuario) throws SQLException {
        Caja caja = cajaService.cajaAbierta(usuario);
        if (caja == null) {
            throw new IllegalStateException("Debes abrir una caja antes de vender.");
        }

        Venta v = new Venta();
        v.setIdCaja(caja.getId());
        v.setIdUsuario(usuario.getId());
        v.setPrefijoCaja(prefijoCaja());
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
                recalcular(v);
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
        d.setPrecioOriginal(p.getPrecioVenta());
        d.setCostoUnit(p.getPrecioCompra());   // y el costo, para calcular la ganancia después
        v.getDetalles().add(d);
        recalcular(v);
    }

    // Quita un producto del carrito.
    public void quitarProducto(Venta v, int idProducto) {
        // removeIf elimina de la lista todo elemento que cumpla la condición.
        v.getDetalles().removeIf(d -> d.getIdProducto() == idProducto);
        recalcular(v);
    }

    // Aplica un descuento en pesos. No puede ser negativo ni mayor al subtotal.
    public void aplicarDescuento(Venta v, BigDecimal descuento) {
        if (descuento == null || descuento.signum() < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo.");
        }
        // compareTo: devuelve >0 si el primero es mayor, 0 si son iguales, <0 si es menor.
        if (descuento.compareTo(v.getSubtotal().subtract(v.getDescuentoPromo())) > 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al subtotal.");
        }
        v.setDescuento(descuento);
        recalcular(v);
    }

    // Cobra y guarda la venta pagada completa con UN solo método (atajo de la versión de múltiples pagos).
    public void finalizar(Venta v, String metodoPago) throws SQLException {
        recalcular(v);
        finalizar(v, List.of(new Pago(metodoPago, v.getTotal())));
    }

    // Cobra y guarda la venta. "pagos" son los medios con que se pagó (uno o varios) y deben
    // sumar EXACTAMENTE el total. El vuelto del efectivo no cuenta: aquí va solo lo aplicado.
    public void finalizar(Venta v, List<Pago> pagos) throws SQLException {
        if (v.getDetalles().isEmpty()) {
            throw new IllegalStateException("El carrito está vacío.");
        }
        recalcular(v);
        if (pagos == null || pagos.isEmpty()) {
            throw new IllegalArgumentException("Falta registrar el pago.");
        }

        BigDecimal suma = BigDecimal.ZERO;
        for (Pago p : pagos) {
            if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA", "FIADO", "CREDITO", "FINANCIERA").contains(p.metodo())) {
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

        // Financiera: hace falta saber cuál es.
        boolean hayFinanciera = pagos.stream().anyMatch(p -> "FINANCIERA".equals(p.metodo()));
        if (hayFinanciera && (v.getFinancieraNombre() == null || v.getFinancieraNombre().isBlank())) {
            throw new IllegalArgumentException("Escribe el nombre de la financiera.");
        }
        if (!hayFinanciera) {
            v.setFinancieraNombre(null);
        }

        // Crédito en cuotas: exige cliente con cédula, el plan armado y una cuota inicial suficiente.
        Pago pagoCredito = pagos.stream().filter(p -> "CREDITO".equals(p.metodo())).findFirst().orElse(null);
        if (pagoCredito == null) {
            v.setPlanCredito(null);
        } else {
            if (v.getIdCliente() == null) {
                throw new IllegalArgumentException("Para vender a crédito tienes que elegir un cliente.");
            }
            com.pos.modelo.Cliente cliente = new com.pos.dao.ClienteDAO().listar().stream()
                    .filter(c -> c.getId() == v.getIdCliente()).findFirst().orElse(null);
            if (cliente == null || cliente.getDocumento() == null || cliente.getDocumento().isBlank()) {
                throw new IllegalArgumentException("El cliente necesita su cédula para un crédito. Agrégala en Clientes.");
            }
            if (v.getPlanCredito() == null || v.getPlanCredito().financiado().compareTo(pagoCredito.monto()) != 0) {
                throw new IllegalArgumentException("Falta armar el plan de cuotas del crédito.");
            }
            try {
                new CreditoService().validarInicial(v.getTotal(), pagoCredito.monto());
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo revisar la cuota inicial: " + e.getMessage());
            }
        }

        v.setPagos(new ArrayList<>(pagos));
        v.setMetodoPago(pagos.size() == 1 ? pagos.get(0).metodo() : "MIXTO");
        // La rebaja (precios bajados + descuento) no puede pasar del límite de quien vende.
        try {
            new com.pos.dao.UsuarioDAO().listar().stream().filter(u -> u.getId() == v.getIdUsuario()).findFirst()
                    .ifPresent(u -> validarRebaja(v, u));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo revisar el límite de rebaja: " + e.getMessage());
        }

        // Puntos que gana el cliente con esta compra: total/1.000 x puntos por mil (hacia abajo).
        int ganados = 0;
        if (v.getIdCliente() != null) {
            ganados = v.getTotal().divide(new BigDecimal("1000"), 6, java.math.RoundingMode.DOWN)
                    .multiply(puntosPorMil()).setScale(0, java.math.RoundingMode.DOWN).intValue();
        }
        v.setPuntosGanados(ganados);
        ventaDAO.guardar(v);     // la transacción: venta + líneas + stock + movimientos + pagos
        v.setEstado("COMPLETADA");
    }

    // Baja el precio de una línea al cobrar. Nunca se puede subir sobre el precio normal, y el total de rebajas
    // (precios bajados + descuento) no puede pasar del % que el administrador le fijó a ese usuario. Sin PIN: si se
    // pasa, se bloquea y se le dice que pida al administrador.
    public void cambiarPrecio(Venta v, int idProducto, BigDecimal nuevoPrecio, Usuario usuario) {
        DetalleVenta linea = v.getDetalles().stream().filter(d -> d.getIdProducto() == idProducto).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ese producto no está en la venta."));
        if (nuevoPrecio == null || nuevoPrecio.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo.");
        }
        if (nuevoPrecio.compareTo(linea.getPrecioOriginal()) > 0) {
            throw new IllegalArgumentException("El precio no puede ser mayor al normal (" + Dinero.formato(linea.getPrecioOriginal()) + ").");
        }
        BigDecimal anterior = linea.getPrecioUnit();
        linea.setPrecioUnit(nuevoPrecio);
        recalcular(v);
        try {
            validarRebaja(v, usuario);
        } catch (IllegalStateException e) {
            linea.setPrecioUnit(anterior);   // se deshace: se pasó del límite
            recalcular(v);
            throw e;
        }
    }

    // Lo que se ha rebajado en total: precios bajados + descuento manual (sin contar promociones ni puntos).
    public BigDecimal rebajaTotal(Venta v) {
        BigDecimal rebaja = v.getDescuento();
        for (DetalleVenta d : v.getDetalles()) {
            rebaja = rebaja.add(d.getPrecioOriginal().subtract(d.getPrecioUnit()).multiply(BigDecimal.valueOf(d.getCantidad())));
        }
        return rebaja;
    }

    // Revisa que la rebaja no pase del límite del usuario (el administrador no tiene límite).
    public void validarRebaja(Venta v, Usuario usuario) {
        int max = usuario.getDescuentoMax();
        if (max >= 100) {
            return;
        }
        BigDecimal normal = BigDecimal.ZERO;
        for (DetalleVenta d : v.getDetalles()) {
            normal = normal.add(d.getPrecioOriginal().multiply(BigDecimal.valueOf(d.getCantidad())));
        }
        BigDecimal tope = normal.multiply(BigDecimal.valueOf(max)).divide(new BigDecimal("100"), 0, java.math.RoundingMode.DOWN);
        if (rebajaTotal(v).compareTo(tope) > 0) {
            throw new IllegalStateException("La rebaja pasa del " + max + " % que tienes permitido (" + Dinero.formato(tope)
                    + " en esta venta). Pídele al administrador.");
        }
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
                recalcular(v);
                return;
            }
        }
    }

    // Asigna un cliente a la venta. null = cliente general (sin cliente).
    public void asignarCliente(Venta v, Cliente cliente) {
        Integer nuevo = cliente == null ? null : cliente.getId();
        boolean cambio = !java.util.Objects.equals(v.getIdCliente(), nuevo);
        v.setIdCliente(nuevo);
        if (cambio && v.isUsarPuntos()) {   // otro cliente: los puntos elegidos ya no valen
            v.setUsarPuntos(false);
            recalcular(v);
        }
        v.setNombreCliente(cliente == null ? "Cliente general" : cliente.getNombre());
    }

    // Historial de ventas de un rango de fechas.
    public List<Venta> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        return ventaDAO.listar(desde, hasta);
    }

    // Recibos que coinciden con un texto (número, cliente, vendedor o producto), los más recientes primero.
    public List<Venta> buscarRecibos(String texto) throws SQLException {
        return ventaDAO.buscarRecibos(texto, 200);
    }

    // Una venta con todas sus líneas (para ver el detalle o reimprimir el recibo).
    public Venta detalle(int idVenta) throws SQLException {
        return ventaDAO.buscarPorId(idVenta);
    }

    // Anula una venta y devuelve las unidades al inventario. Solo lo puede hacer un ADMIN.
    public void anular(Usuario usuario, Venta v) throws SQLException {
        if (!usuario.puedeAnularVentas()) {
            throw new IllegalStateException("No tienes permiso para anular ventas.");
        }
        if ("ANULADA".equals(v.getEstado())) {
            throw new IllegalStateException("La venta ya está anulada.");
        }
        ventaDAO.anular(v.getId(), usuario.getId());
        AuditoriaService.registrar(usuario, "Venta anulada", v.getNumero() + " por " + v.getTotal().toPlainString());
        v.setEstado("ANULADA");
    }

    // Recalcula todo el carrito: primero las promociones, luego los puntos (si se usan) y por último el total.
    private void recalcular(Venta v) {
        try {
            promociones.aplicar(v);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron calcular las promociones: " + e.getMessage());
        }
        v.setPuntosUsados(0);
        v.setDescuentoPuntos(BigDecimal.ZERO);
        if (v.isUsarPuntos() && v.getIdCliente() != null) {
            BigDecimal valorPunto = valorPunto();
            BigDecimal base = v.getSubtotal().subtract(v.getDescuentoPromo()).subtract(v.getDescuento());
            if (valorPunto.signum() > 0 && base.signum() > 0) {
                // Con puntos no se paga todo: siempre queda algo en dinero (y el recibo nunca sale en $0).
                int maxPorVenta = base.subtract(BigDecimal.ONE).divideToIntegralValue(valorPunto).intValue();
                int usar = Math.min(v.getPuntosDisponibles(), maxPorVenta);
                v.setPuntosUsados(usar);
                v.setDescuentoPuntos(valorPunto.multiply(BigDecimal.valueOf(usar)));
            }
        }
        v.calcularTotales();
    }

    // Los puntos que tiene un cliente (ganados en ventas menos los que ya usó).
    public int puntosDeCliente(Integer idCliente) throws SQLException {
        return idCliente == null ? 0 : ventaDAO.puntosDeCliente(idCliente);
    }

    // Pagar (o dejar de pagar) parte de la venta con los puntos del cliente.
    public void usarPuntos(Venta v, boolean usar) throws SQLException {
        if (usar) {
            if (v.getIdCliente() == null) {
                throw new IllegalStateException("Elige un cliente para usar puntos.");
            }
            v.setPuntosDisponibles(puntosDeCliente(v.getIdCliente()));
            if (v.getPuntosDisponibles() <= 0) {
                throw new IllegalStateException("Este cliente no tiene puntos todavía.");
            }
        }
        v.setUsarPuntos(usar);
        recalcular(v);
    }

    // Iniciales del nombre de la caja: "Caja 1" -> C1, "Caja Principal" -> CP.
    private String prefijoCaja() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String palabra : config.obtener(ConfiguracionService.CAJA_NOMBRE, "Caja 1").trim().split("\s+")) {
                sb.append(Character.toUpperCase(palabra.charAt(0)));
                if (palabra.chars().allMatch(Character::isDigit) && palabra.length() > 1) {
                    sb.append(palabra, 1, palabra.length());
                }
            }
            return sb.length() == 0 ? "C1" : sb.toString();
        } catch (SQLException e) {
            return "C1";
        }
    }

    public BigDecimal valorPunto() {
        return numero(ConfiguracionService.VALOR_PUNTO, "100");
    }

    // Puntos que gana el cliente por cada $1.000 (0 = el negocio no maneja puntos).
    public BigDecimal puntosPorMil() {
        return numero(ConfiguracionService.PUNTOS_POR_MIL, "0.1");
    }

    private BigDecimal numero(String clave, String porDefecto) {
        try {
            return new BigDecimal(config.obtener(clave, porDefecto).replace(',', '.'));
        } catch (SQLException | NumberFormatException e) {
            return new BigDecimal(porDefecto);
        }
    }

    // Revisa que alcance el stock; si no, avisa cuántas unidades quedan.
    private void validarStock(Producto p, int cantidad) {
        if (cantidad > p.getStock()) {
            throw new IllegalStateException(
                    "Stock insuficiente de " + p.getNombre() + ": quedan " + p.getStock());
        }
    }
}
