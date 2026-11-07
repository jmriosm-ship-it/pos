package com.pos.servicio;

import com.pos.dao.NominaDAO;
import com.pos.dao.UsuarioDAO;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Cupo y sueldo de los trabajadores.
 *  - Cupo: lo máximo que cada uno puede sacar del local (consumos) a cuenta de su sueldo.
 *  - pendiente = consumos − descuentos ya hechos en sueldos;  disponible = cupo − pendiente.
 *  - Pagar sueldo: sueldo = días × valor por día;  neto = sueldo − descuento (lo que se le entrega).
 */
public class NominaService {

    public record Estado(BigDecimal cupo, BigDecimal pendiente, BigDecimal disponible) {
    }

    private final NominaDAO dao = new NominaDAO();
    private final UsuarioDAO usuarios = new UsuarioDAO();

    public Estado estado(Usuario u) throws SQLException {
        BigDecimal pendiente = dao.pendiente(u.getId());
        return new Estado(u.getCupo(), pendiente, u.getCupo().subtract(pendiente));
    }

    public List<NominaDAO.Mov> movimientos(Usuario u) throws SQLException {
        return dao.listar(u.getId());
    }

    // Sacar algo del local a cuenta del cupo. Un trabajador solo para sí mismo y sin pasarse del disponible;
    // el administrador elige a quién y puede pasarse (la pantalla le pide confirmar).
    public void sacarDelCupo(Usuario quienRegistra, Usuario paraQuien, String concepto, BigDecimal valor,
                             boolean permitirPasarse) throws SQLException {
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("Escribe qué se saca.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El valor debe ser mayor a cero.");
        }
        if (!quienRegistra.esAdmin() && quienRegistra.getId() != paraQuien.getId()) {
            throw new IllegalStateException("Solo puedes sacar del cupo para ti.");
        }
        Estado e = estado(paraQuien);
        if (valor.compareTo(e.disponible()) > 0 && !(quienRegistra.esAdmin() && permitirPasarse)) {
            throw new IllegalArgumentException("Te pasas del cupo: disponible " + e.disponible().max(BigDecimal.ZERO).toPlainString() + ".");
        }
        dao.insertarConsumo(paraQuien.getId(), concepto.trim(), valor, quienRegistra.getId());
        AuditoriaService.registrar(quienRegistra, "Consumo de cupo", paraQuien.getNombre() + " · " + concepto.trim() + " · " + valor.toPlainString());
    }

    public void anularConsumo(Usuario quien, int idMovimiento) throws SQLException {
        if (!quien.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede anular consumos.");
        }
        dao.anular(idMovimiento);
        AuditoriaService.registrar(quien, "Consumo anulado", "movimiento " + idMovimiento);
    }

    // Guarda el cupo y el pago por día de un trabajador (solo admin).
    public void configurar(Usuario admin, Usuario u, BigDecimal cupo, BigDecimal pagoDia) throws SQLException {
        exigirAdmin(admin);
        if (cupo == null || cupo.signum() < 0 || pagoDia == null || pagoDia.signum() < 0) {
            throw new IllegalArgumentException("El cupo y el pago por día no pueden ser negativos.");
        }
        u.setCupo(cupo);
        u.setPagoDia(pagoDia);
        usuarios.actualizar(u);
    }

    // Días del mes en que el trabajador vendió: una ayuda para decidir cuántos días pagarle.
    public int diasVendidos(Usuario u, YearMonth mes) throws SQLException {
        return dao.diasVendidos(u.getId(), mes.atDay(1), mes.atEndOfMonth());
    }

    // Registra el pago del sueldo y, si se entrega plata, la anota como gasto "Nómina".
    public BigDecimal pagarSueldo(Usuario admin, Usuario trabajador, int dias, BigDecimal valorDia, BigDecimal descuento,
                                  String periodo) throws SQLException {
        exigirAdmin(admin);
        if (dias <= 0) {
            throw new IllegalArgumentException("Los días trabajados deben ser más de cero.");
        }
        if (valorDia == null || valorDia.signum() <= 0) {
            throw new IllegalArgumentException("El valor del día debe ser mayor a cero.");
        }
        BigDecimal pendiente = dao.pendiente(trabajador.getId());
        if (descuento == null || descuento.signum() < 0 || descuento.compareTo(pendiente) > 0) {
            throw new IllegalArgumentException("El descuento debe estar entre 0 y lo pendiente (" + pendiente.toPlainString() + ").");
        }
        BigDecimal sueldo = valorDia.multiply(BigDecimal.valueOf(dias)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal neto = sueldo.subtract(descuento);
        String texto = "Sueldo " + trabajador.getNombre() + " · " + (periodo == null || periodo.isBlank() ? "sin periodo" : periodo.trim());
        dao.insertarPago(trabajador.getId(), texto, sueldo, descuento, neto, dias, valorDia, periodo, admin.getId());
        AuditoriaService.registrar(admin, "Sueldo pagado", texto + " · neto " + neto.toPlainString());
        if (!valorDia.equals(trabajador.getPagoDia())) {   // recuerda el valor del día para la próxima vez
            trabajador.setPagoDia(valorDia);
            usuarios.actualizar(trabajador);
        }
        if (neto.signum() > 0) {
            new GastoService().registrar(admin, "Nómina", texto, neto);
        }
        return neto;
    }

    // Ventas netas (vendido − devuelto) de un trabajador en un rango: {cantidad, valor}.
    public BigDecimal[] ventas(Usuario u, LocalDate desde, LocalDate hasta) throws SQLException {
        return dao.ventasNetas(u.getId(), desde, hasta);
    }

    public List<NominaDAO.CajaTurno> cajas(Usuario u, LocalDate desde, LocalDate hasta) throws SQLException {
        return dao.cajas(u.getId(), desde, hasta);
    }

    private void exigirAdmin(Usuario u) {
        if (!u.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede hacer esto.");
        }
    }
}
