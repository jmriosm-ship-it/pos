package com.pos.servicio;

import com.pos.dao.GastoDAO;
import com.pos.modelo.Gasto;
import com.pos.modelo.Usuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class GastoService {

    // Categorías de gastos del local.
    public static final java.util.List<String> CATEGORIAS = java.util.List.of("Arriendo", "Servicios", "Nómina",
            "Publicidad", "Bolsas y empaques", "Transporte", "Devoluciones", "Otros");

    private final GastoDAO gastoDAO = new GastoDAO();

    // Registra un gasto (arriendo, servicios, etc.).
    public Gasto registrar(Usuario usuario, String concepto, BigDecimal monto) throws SQLException {
        return registrar(usuario, "Otros", concepto, monto);
    }

    public Gasto registrar(Usuario usuario, String categoria, String concepto, BigDecimal monto) throws SQLException {
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("Debes escribir el concepto del gasto.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }

        Gasto g = new Gasto();
        g.setIdUsuario(usuario.getId());
        g.setConcepto(concepto.trim());
        g.setCategoria(categoria == null || categoria.isBlank() ? "Otros" : categoria);
        g.setMonto(monto);
        gastoDAO.insertar(g);
        AuditoriaService.registrar(usuario, "Gasto registrado", g.getCategoria() + " · " + g.getConcepto() + " · " + monto.toPlainString());
        return g;
    }

    public List<Gasto> listar(LocalDate desde, LocalDate hasta) throws SQLException {
        return gastoDAO.listar(desde, hasta);
    }

    // Borrar un gasto es delicado (cambia los reportes), por eso solo el ADMIN.
    public void eliminar(Usuario usuario, Gasto g) throws SQLException {
        if (!usuario.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede eliminar gastos.");
        }
        gastoDAO.eliminar(g.getId());
        AuditoriaService.registrar(usuario, "Gasto eliminado", g.getConcepto() + " · " + g.getMonto().toPlainString());
    }
}
