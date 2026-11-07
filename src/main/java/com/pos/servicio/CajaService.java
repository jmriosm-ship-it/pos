package com.pos.servicio;

import com.pos.dao.CajaDAO;
import com.pos.modelo.Caja;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.sql.SQLException;

public class CajaService {

    private final CajaDAO cajaDAO = new CajaDAO();
    private final com.pos.dao.MovimientoCajaDAO movimientos = new com.pos.dao.MovimientoCajaDAO();

    // Abre una caja para el usuario.
    // Reglas: monto válido y una sola caja abierta por usuario.
    public Caja abrirCaja(Usuario usuario, BigDecimal montoInicial) throws SQLException {
        // signum() devuelve -1 si es negativo, 0 si es cero, 1 si es positivo.
        if (montoInicial == null || montoInicial.signum() < 0) {
            throw new IllegalArgumentException("El monto inicial no puede ser negativo.");
        }
        if (cajaDAO.buscarAbiertaDeUsuario(usuario.getId()) != null) {
            throw new IllegalStateException("Ya tienes una caja abierta. Ciérrala antes de abrir otra.");
        }

        Caja caja = new Caja();
        caja.setIdUsuario(usuario.getId());
        caja.setMontoInicial(montoInicial);
        cajaDAO.abrir(caja);
        caja.setEstado("ABIERTA");
        return caja;
    }

    // Devuelve la caja abierta del usuario, o null si no tiene.
    // Más adelante las ventas usarán esto: sin caja abierta no se puede vender.
    public Caja cajaAbierta(Usuario usuario) throws SQLException {
        return cajaDAO.buscarAbiertaDeUsuario(usuario.getId());
    }

    // Lo que se ha vendido en efectivo en esta caja hasta ahora (para mostrar antes de cerrar).
    public BigDecimal ventasEfectivo(Caja caja) throws SQLException {
        return cajaDAO.totalEfectivoVentas(caja.getId());
    }

    // Ingreso (meter plata a la caja) o egreso (sacarla) manual, siempre con su motivo.
    public void registrarMovimiento(Usuario usuario, Caja caja, String tipo, BigDecimal monto, String motivo) throws SQLException {
        validarMovimiento(caja, monto, motivo);
        movimientos.insertar(caja.getId(), usuario.getId(), tipo, monto, motivo.trim());
        AuditoriaService.registrar(usuario, "INGRESO".equals(tipo) ? "Ingreso de caja" : "Egreso de caja", monto.toPlainString() + " · " + motivo.trim());
    }

    // Devolución de dinero a un cliente sin venta de por medio: sale de la caja y queda como gasto "Devoluciones".
    public void devolucionDeDinero(Usuario usuario, Caja caja, BigDecimal monto, String motivo) throws SQLException {
        validarMovimiento(caja, monto, motivo);
        movimientos.insertar(caja.getId(), usuario.getId(), "EGRESO", monto, "Devolución: " + motivo.trim());
        new GastoService().registrar(usuario, "Devoluciones", "Devolución: " + motivo.trim(), monto);
    }

    public java.util.List<com.pos.modelo.MovimientoCaja> movimientos(Caja caja) throws SQLException {
        return movimientos.listar(caja.getId());
    }

    private void validarMovimiento(Caja caja, BigDecimal monto, String motivo) {
        if (caja == null || !caja.estaAbierta()) {
            throw new IllegalStateException("Debes tener la caja abierta.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El valor debe ser mayor a cero.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe el motivo.");
        }
    }

    // Cierra la caja y calcula el arqueo:
    //   esperado   = monto inicial + ventas en efectivo
    //   diferencia = contado - esperado   (negativo = falta plata, positivo = sobra)
    public Caja cerrarCaja(Caja caja, BigDecimal montoContado) throws SQLException {
        if (!caja.estaAbierta()) {
            throw new IllegalStateException("Esa caja ya está cerrada.");
        }
        if (montoContado == null || montoContado.signum() < 0) {
            throw new IllegalArgumentException("El monto contado no puede ser negativo.");
        }

        BigDecimal ventasEfectivo = cajaDAO.totalEfectivoVentas(caja.getId());
        BigDecimal esperado = caja.getMontoInicial().add(ventasEfectivo);

        caja.setMontoEsperado(esperado);
        caja.setMontoContado(montoContado);
        caja.setDiferencia(montoContado.subtract(esperado));
        cajaDAO.cerrar(caja);
        AuditoriaService.registrar(null, "Caja cerrada", "caja " + caja.getId() + " · contado " + montoContado.toPlainString() + " · dif " + caja.getDiferencia().toPlainString());
        caja.setEstado("CERRADA");
        caja.setCerradaEn(java.time.LocalDateTime.now());   // para el comprobante (la base guardó la hora con NOW())
        return caja;
    }
}