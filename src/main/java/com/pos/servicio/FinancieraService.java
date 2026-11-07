package com.pos.servicio;

import com.pos.dao.FinancieraDAO;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Financiera: parte de una venta la paga una financiera más tarde. Esa parte no entra a la caja ni a las ganancias hasta
 * que la financiera paga. Un "ajuste" cierra la cuenta cuando la financiera pagó de menos (por ejemplo, su comisión).
 */
public class FinancieraService {

    private final FinancieraDAO dao = new FinancieraDAO();

    public List<FinancieraDAO.Fila> listar() throws SQLException {
        return dao.listar();
    }

    public List<String> nombres() throws SQLException {
        return dao.nombres();
    }

    public BigDecimal totalPorCobrar() throws SQLException {
        return dao.totalPorCobrar();
    }

    // tipo: PAGO (entró plata) o AJUSTE (se cierra con diferencia; no suma ingresos).
    public void registrar(Usuario usuario, FinancieraDAO.Fila f, String tipo, BigDecimal valor, String nota) throws SQLException {
        if (f.anulada()) {
            throw new IllegalStateException("La venta está anulada; no hay nada por cobrar.");
        }
        if (!List.of("PAGO", "AJUSTE").contains(tipo)) {
            throw new IllegalArgumentException("Tipo no válido.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El valor debe ser mayor a cero.");
        }
        if (valor.compareTo(f.falta()) > 0) {
            throw new IllegalArgumentException("El valor supera lo que falta por cobrar (" + f.falta().toPlainString() + ").");
        }
        dao.registrar(f.idVenta(), tipo, valor, nota == null || nota.isBlank() ? null : nota.trim(), usuario.getId());
        AuditoriaService.registrar(usuario, "PAGO".equals(tipo) ? "Cobro de financiera" : "Ajuste de financiera",
                f.ventaNumero() + " · " + f.financiera() + " · " + valor.toPlainString());
    }
}
