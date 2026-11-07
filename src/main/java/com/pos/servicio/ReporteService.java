package com.pos.servicio;

import com.pos.dao.GastoDAO;
import com.pos.dao.ReporteDAO;
import com.pos.modelo.ProductoVendido;
import com.pos.modelo.ResumenFinanciero;
import com.pos.modelo.VentaDia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ReporteService {

    private final ReporteDAO reporteDAO = new ReporteDAO();
    private final GastoDAO gastoDAO = new GastoDAO();

    // El resumen de ingresos, ganancias y gastos de un periodo.
    public ResumenFinanciero resumen(LocalDate desde, LocalDate hasta) throws SQLException {
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }

        Object[] totales = reporteDAO.ventasTotales(desde, hasta);
        int numeroVentas = (Integer) totales[0];
        // Ingresos y costo ya sin lo que se devolvió a los clientes.
        // La parte que paga una financiera cuenta cuando la financiera paga, no el día de la venta.
        BigDecimal ingresos = ((BigDecimal) totales[1]).subtract(reporteDAO.devoluciones(desde, hasta))
                .subtract(reporteDAO.financieraVendida(desde, hasta)).add(reporteDAO.financieraCobrada(desde, hasta));

        BigDecimal costo = reporteDAO.costoVendido(desde, hasta).subtract(reporteDAO.costoDevuelto(desde, hasta))
                .subtract(reporteDAO.financieraCostoVendido(desde, hasta)).add(reporteDAO.financieraCostoCobrado(desde, hasta))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal gananciaBruta = ingresos.subtract(costo);
        BigDecimal gastos = gastoDAO.total(desde, hasta);
        BigDecimal intereses = reporteDAO.interesesCobrados(desde, hasta).setScale(0, RoundingMode.HALF_UP);
        // ganancia neta = bruta + intereses de créditos cobrados - gastos
        BigDecimal gananciaNeta = gananciaBruta.add(intereses).subtract(gastos);

        // Ticket promedio = ingresos / número de ventas (cuidado: no dividir entre cero).
        BigDecimal ticket = numeroVentas == 0
                ? BigDecimal.ZERO
                : ingresos.divide(BigDecimal.valueOf(numeroVentas), 2, RoundingMode.HALF_UP);

        return new ResumenFinanciero(numeroVentas, ingresos, costo, gananciaBruta, gastos, gananciaNeta, ticket, intereses);
    }

    // Atajo para el dashboard: cómo va el día de hoy.
    public ResumenFinanciero resumenHoy() throws SQLException {
        LocalDate hoy = LocalDate.now();
        return resumen(hoy, hoy);
    }

    // Ventas de los últimos N días (para la gráfica del dashboard).
    public List<VentaDia> ventasUltimosDias(int dias) throws SQLException {
        LocalDate hoy = LocalDate.now();
        return reporteDAO.ventasPorDia(hoy.minusDays(dias - 1L), hoy);
    }

    // Total vendido en cada día de un rango (solo los días con ventas).
    public List<VentaDia> ventasPorDia(LocalDate desde, LocalDate hasta) throws SQLException {
        return reporteDAO.ventasPorDia(desde, hasta);
    }

    public Map<String, BigDecimal> ventasPorMetodoPago(LocalDate desde, LocalDate hasta) throws SQLException {
        return reporteDAO.ventasPorMetodoPago(desde, hasta);
    }

    public List<ProductoVendido> masVendidos(LocalDate desde, LocalDate hasta, int limite) throws SQLException {
        return reporteDAO.masVendidos(desde, hasta, limite);
    }

    public BigDecimal valorInventarioAlCosto() throws SQLException {
        return reporteDAO.valorInventarioAlCosto();
    }

    public List<ReporteDAO.FilaCategoria> gananciaPorCategoria(LocalDate desde, LocalDate hasta) throws SQLException {
        return reporteDAO.gananciaPorCategoria(desde, hasta);
    }

    // Ventas por día de un vendedor (idUsuario = null: de todos), para el calendario.
    public List<VentaDia> ventasPorDiaDe(LocalDate desde, LocalDate hasta, Integer idUsuario) throws SQLException {
        return reporteDAO.ventasPorDiaDe(desde, hasta, idUsuario);
    }
}
