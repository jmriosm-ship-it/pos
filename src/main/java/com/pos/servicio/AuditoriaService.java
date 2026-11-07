package com.pos.servicio;

import com.pos.dao.AuditoriaDAO;
import com.pos.modelo.Usuario;

import java.sql.SQLException;
import java.util.List;

/**
 * El registro de acciones importantes (inicios de sesión, anulaciones, devoluciones, gastos, sueldos...).
 * Anotar nunca debe romper lo que se está haciendo: si falla, se ignora.
 */
public class AuditoriaService {

    private static final AuditoriaDAO DAO = new AuditoriaDAO();

    // Anota una acción. "usuario" puede ser null (por ejemplo un intento de entrada fallido).
    public static void registrar(Usuario usuario, String accion, String detalle) {
        try {
            DAO.insertar(usuario == null ? null : usuario.getId(), accion, detalle == null ? "" : detalle);
        } catch (SQLException e) {
            // el registro es secundario: no se interrumpe la operación
        }
    }

    public List<AuditoriaDAO.Fila> listar(Usuario quien, String texto) throws SQLException {
        if (!quien.esAdmin()) {
            throw new IllegalStateException("Solo un administrador puede ver el registro.");
        }
        return DAO.listar(texto, 400);
    }
}
