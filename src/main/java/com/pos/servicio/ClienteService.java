package com.pos.servicio;

import com.pos.dao.ClienteDAO;
import com.pos.dao.VentaDAO;
import com.pos.modelo.Cliente;
import com.pos.modelo.Venta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class ClienteService {

    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final VentaDAO ventaDAO = new VentaDAO();

    public List<Cliente> listar() throws SQLException {
        return clienteDAO.listar();
    }

    // Texto vacío = todos los clientes; si no, filtra por nombre/documento/teléfono.
    public List<Cliente> buscar(String texto) throws SQLException {
        if (texto == null || texto.isBlank()) {
            return clienteDAO.listar();
        }
        return clienteDAO.buscar(texto.trim());
    }

    public void guardar(Cliente c) throws SQLException {
        if (c.getNombre() == null || c.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio.");
        }
        c.setNombre(c.getNombre().trim());

        try {
            // id == 0 significa que es nuevo (todavía no tiene id de la base de datos).
            if (c.getId() == 0) {
                clienteDAO.insertar(c);
            } else {
                clienteDAO.actualizar(c);
            }
        } catch (SQLException e) {
            // El documento es UNIQUE en la base de datos: si ya existe, MySQL avisa con "Duplicate entry".
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw new IllegalArgumentException("Ya existe un cliente con ese documento.");
            }
            throw e;
        }
    }

    // Todos los clientes con total comprado y saldo que deben (para la pantalla de Clientes).
    public List<Cliente> listarConResumen() throws SQLException {
        return clienteDAO.listarConResumen();
    }

    // Historial de compras del cliente (las más recientes primero).
    public List<Venta> historial(Cliente c) throws SQLException {
        return ventaDAO.listarPorCliente(c.getId());
    }

    public BigDecimal totalComprado(Cliente c) throws SQLException {
        return clienteDAO.totalComprado(c.getId());
    }

    public void eliminar(Cliente c) throws SQLException {
        clienteDAO.eliminar(c.getId());
    }
}
