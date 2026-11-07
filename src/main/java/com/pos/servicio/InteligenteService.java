package com.pos.servicio;

import com.pos.dao.ProductoDAO;
import com.pos.dao.ReporteDAO;
import com.pos.modelo.Producto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Inventario inteligente: mira lo que se vendió en los últimos 30 días y avisa qué hay que reponer.
 *   ritmo = unidades vendidas en 30 días / 30        días que alcanza = stock / ritmo
 *   AGOTADO: stock 0 · BAJO: stock <= mínimo · PRONTO: alcanza menos de 7 días
 *   sugerido pedir = lo mayor entre (ritmo x 30 - stock), (mínimo x 2 - stock) y 1
 */
public class InteligenteService {

    public static final int DIAS = 30;

    public record Fila(Producto producto, int vendidas, Double diasQueAlcanza, String nivel, int sugerido) {
    }

    private final ProductoDAO productos = new ProductoDAO();
    private final ReporteDAO reportes = new ReporteDAO();

    // Los productos que necesitan atención, los más urgentes primero.
    public List<Fila> porReponer() throws SQLException {
        LocalDate hoy = LocalDate.now();
        Map<Integer, Integer> vendidas = reportes.unidadesVendidas(hoy.minusDays(DIAS - 1L), hoy);
        List<Fila> lista = new ArrayList<>();
        for (Producto p : productos.listar()) {
            int v = vendidas.getOrDefault(p.getId(), 0);
            double ritmo = v / (double) DIAS;
            Double dias = ritmo > 0 ? p.getStock() / ritmo : null;
            String nivel;
            if (p.getStock() <= 0) {
                if (v == 0 && p.getStockMinimo() == 0) {
                    continue;   // un agotado que nunca se vende y sin mínimo no interesa
                }
                nivel = "AGOTADO";
            } else if (p.getStock() <= p.getStockMinimo()) {
                nivel = "BAJO";
            } else if (dias != null && dias < 7) {
                nivel = "PRONTO";
            } else {
                continue;
            }
            int sugerido = (int) Math.max(Math.max(Math.ceil(ritmo * DIAS) - p.getStock(), p.getStockMinimo() * 2 - p.getStock()), 1);
            lista.add(new Fila(p, v, dias, nivel, sugerido));
        }
        lista.sort(Comparator.comparingInt((Fila f) -> orden(f.nivel())).thenComparing(f -> f.diasQueAlcanza() == null ? 9999.0 : f.diasQueAlcanza()));
        return lista;
    }

    private int orden(String nivel) {
        return switch (nivel) {
            case "AGOTADO" -> 0;
            case "BAJO" -> 1;
            default -> 2;
        };
    }
}
