package com.pos.servicio;

import com.pos.dao.ProductoDAO;
import com.pos.dao.PromocionDAO;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Producto;
import com.pos.modelo.Promocion;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Reglas de las promociones: administrarlas (solo ADMIN) y calcular cuánto rebajan en un carrito. */
public class PromocionService {

    private final PromocionDAO dao = new PromocionDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    public List<Promocion> listar() throws SQLException {
        return dao.listar();
    }

    public void guardar(Usuario u, Promocion p) throws SQLException {
        exigirAdmin(u);
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("Ponle un nombre a la promoción.");
        }
        if (!"TIENDA".equals(p.getAlcance()) && p.getIdObjetivo() == null) {
            throw new IllegalArgumentException("Elige la categoría o el producto de la promoción.");
        }
        if ("PORCENTAJE".equals(p.getTipo())) {
            if (p.getValor().signum() <= 0 || p.getValor().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("El porcentaje debe estar entre 1 y 100.");
            }
        } else if (p.getLleva() < 2 || p.getPaga() < 1 || p.getLleva() <= p.getPaga()) {
            throw new IllegalArgumentException("En \"lleva N, paga M\", N debe ser mayor que M (ej: lleva 2, paga 1).");
        }
        if (p.getDesde() != null && p.getHasta() != null && p.getHasta().isBefore(p.getDesde())) {
            throw new IllegalArgumentException("La fecha final no puede ser antes de la inicial.");
        }
        p.setNombre(p.getNombre().trim());
        dao.guardar(p);
        AuditoriaService.registrar(u, "Promoción guardada", p.getNombre());
    }

    public void eliminar(Usuario u, Promocion p) throws SQLException {
        exigirAdmin(u);
        dao.eliminar(p.getId());
        AuditoriaService.registrar(u, "Promoción eliminada", p.getNombre());
    }

    // Calcula cuánto rebajan las promociones vigentes en el carrito (la mejor por línea) y lo deja en la venta.
    public void aplicar(Venta v) throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        if (!v.getDetalles().isEmpty()) {
            List<Promocion> vigentes = dao.listar().stream().filter(p -> p.vigente(LocalDate.now())).toList();
            if (!vigentes.isEmpty()) {
                for (DetalleVenta d : v.getDetalles()) {
                    if (d.getIdCategoria() == null) {
                        Producto p = productoDAO.buscarPorId(d.getIdProducto());
                        d.setIdCategoria(p == null ? null : p.getIdCategoria());
                    }
                    BigDecimal mejor = BigDecimal.ZERO;
                    for (Promocion pr : vigentes) {
                        if (aplicaALinea(pr, d)) {
                            BigDecimal rebaja = rebaja(pr, d);
                            if (rebaja.compareTo(mejor) > 0) {
                                mejor = rebaja;
                            }
                        }
                    }
                    total = total.add(mejor);
                }
            }
        }
        v.setDescuentoPromo(total);
    }

    private boolean aplicaALinea(Promocion pr, DetalleVenta d) {
        return switch (pr.getAlcance()) {
            case "CATEGORIA" -> pr.getIdObjetivo() != null && pr.getIdObjetivo().equals(d.getIdCategoria());
            case "PRODUCTO" -> pr.getIdObjetivo() != null && pr.getIdObjetivo() == d.getIdProducto();
            default -> true;
        };
    }

    // PORCENTAJE: precio x cantidad x %/100.  NXM: (cantidad / lleva) grupos x (lleva - paga) unidades gratis.
    private BigDecimal rebaja(Promocion pr, DetalleVenta d) {
        if ("PORCENTAJE".equals(pr.getTipo())) {
            return d.getSubtotal().multiply(pr.getValor()).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        }
        if (pr.getLleva() <= pr.getPaga()) {
            return BigDecimal.ZERO;
        }
        int gratis = (d.getCantidad() / pr.getLleva()) * (pr.getLleva() - pr.getPaga());
        return d.getPrecioUnit().multiply(BigDecimal.valueOf(gratis));
    }

    private void exigirAdmin(Usuario u) {
        if (!u.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede manejar promociones.");
        }
    }
}
