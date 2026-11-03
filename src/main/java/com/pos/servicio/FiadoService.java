package com.pos.servicio;

import com.pos.dao.FiadoDAO;
import com.pos.modelo.Abono;
import com.pos.modelo.Caja;
import com.pos.modelo.Cliente;
import com.pos.modelo.Fiado;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/** Las deudas de los clientes (fiados) y los abonos que van haciendo. */
public class FiadoService {

    private final FiadoDAO dao = new FiadoDAO();
    private final CajaService cajaService = new CajaService();

    public List<Fiado> fiadosDe(Cliente c) throws SQLException {
        return dao.listarPorCliente(c.getId());
    }

    public List<Abono> abonosDe(Cliente c) throws SQLException {
        return dao.listarAbonosPorCliente(c.getId());
    }

    public BigDecimal saldoDe(Cliente c) throws SQLException {
        return dao.saldoDeCliente(c.getId());
    }

    // Registra un abono del cliente (se aplica a la deuda más vieja primero).
    // Hace falta una caja abierta: el abono queda en ella y, si es en efectivo, cuenta en el arqueo.
    public void registrarAbono(Usuario usuario, Cliente c, String metodo, BigDecimal monto) throws SQLException {
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA").contains(metodo)) {
            throw new IllegalArgumentException("Elige cómo pagó el cliente: efectivo, transferencia o tarjeta.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El abono debe ser mayor a cero.");
        }
        Caja caja = cajaService.cajaAbierta(usuario);
        if (caja == null) {
            throw new IllegalStateException("Abre tu caja antes de recibir abonos.");
        }

        BigDecimal deuda = dao.saldoDeCliente(c.getId());
        if (deuda.signum() == 0) {
            throw new IllegalStateException(c.getNombre() + " no tiene deudas pendientes.");
        }
        if (monto.compareTo(deuda) > 0) {
            throw new IllegalArgumentException("El abono (" + monto.toPlainString() + ") es mayor que lo que debe ("
                    + deuda.toPlainString() + ").");
        }
        dao.registrarAbono(c.getId(), usuario.getId(), caja.getId(), metodo, monto);
    }
}
