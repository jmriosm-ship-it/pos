package com.pos.servicio;

import com.pos.dao.DevolucionDAO;
import com.pos.modelo.Caja;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Devolver productos de una venta: se reembolsa lo proporcional al descuento que tuvo la venta. */
public class DevolucionService {

    // Una línea de la venta y cuántas unidades todavía se pueden devolver.
    public record Linea(int idProducto, String nombre, BigDecimal precioUnit, int vendidas, int yaDevueltas) {
        public int disponibles() {
            return vendidas - yaDevueltas;
        }
    }

    private final DevolucionDAO dao = new DevolucionDAO();
    private final CajaService cajaService = new CajaService();

    public List<Linea> lineas(Venta v) throws SQLException {
        Map<Integer, Integer> hechas = dao.yaDevueltas(v.getId());
        List<Linea> lista = new ArrayList<>();
        for (DetalleVenta d : v.getDetalles()) {
            lista.add(new Linea(d.getIdProducto(), d.getNombreProducto(), d.getPrecioUnit(), d.getCantidad(),
                    hechas.getOrDefault(d.getIdProducto(), 0)));
        }
        return lista;
    }

    // reembolso = round( Σ precio x cantidad x (total / subtotal) )  → respeta promociones y descuentos de la venta.
    public BigDecimal reembolso(Venta v, List<Linea> lineas, Map<Integer, Integer> cantidades) {
        if (v.getSubtotal().signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (Linea l : lineas) {
            int c = cantidades.getOrDefault(l.idProducto(), 0);
            suma = suma.add(l.precioUnit().multiply(BigDecimal.valueOf(c)));
        }
        return suma.multiply(v.getTotal()).divide(v.getSubtotal(), 0, RoundingMode.HALF_UP);
    }

    public BigDecimal devolver(Usuario usuario, Venta v, Map<Integer, Integer> cantidades, String metodo) throws SQLException {
        if (!usuario.puedeAnularVentas()) {
            throw new IllegalStateException("No tienes permiso para hacer devoluciones.");
        }
        if (new com.pos.dao.CreditoDAO().tieneCredito(v.getId())) {
            throw new IllegalStateException("La venta es a crédito en cuotas: no se puede devolver desde aquí (anúlala o cancela el crédito).");
        }
        if ("ANULADA".equals(v.getEstado())) {
            throw new IllegalStateException("La venta está anulada; no se puede devolver nada de ella.");
        }
        List<Linea> lineas = lineas(v);
        int total = 0;
        for (Linea l : lineas) {
            int c = cantidades.getOrDefault(l.idProducto(), 0);
            if (c < 0 || c > l.disponibles()) {
                throw new IllegalArgumentException("De " + l.nombre() + " solo se pueden devolver " + l.disponibles() + ".");
            }
            total += c;
        }
        if (total == 0) {
            throw new IllegalArgumentException("Elige al menos una unidad para devolver.");
        }
        if ("FIADO".equals(metodo) && v.getIdCliente() == null) {
            throw new IllegalArgumentException("La venta no tiene cliente para descontar de su deuda.");
        }
        BigDecimal monto = reembolso(v, lineas, cantidades);
        Integer idCaja = null;
        if ("EFECTIVO".equals(metodo)) {   // el efectivo sale de la caja de quien devuelve
            Caja caja = cajaService.cajaAbierta(usuario);
            if (caja == null) {
                throw new IllegalStateException("Abre tu caja para devolver en efectivo.");
            }
            idCaja = caja.getId();
        }
        dao.guardar(v.getId(), usuario.getId(), idCaja, metodo, monto, cantidades);
        AuditoriaService.registrar(usuario, "Devolución de venta", v.getNumero() + " · " + monto.toPlainString() + " en " + metodo);
        return monto;
    }
}
