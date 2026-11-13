package com.pos.offline;

import com.pos.dao.CajaDAO;
import com.pos.dao.ConfiguracionDAO;
import com.pos.dao.PermisoDAO;
import com.pos.dao.ProductoDAO;
import com.pos.dao.PromocionDAO;
import com.pos.dao.UsuarioDAO;
import com.pos.modelo.Caja;
import com.pos.modelo.DetalleVenta;
import com.pos.modelo.Modulo;
import com.pos.modelo.Producto;
import com.pos.modelo.Promocion;
import com.pos.modelo.Usuario;
import com.pos.modelo.Venta;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Una copia en este PC de lo mínimo que hace falta para VENDER sin internet: usuarios (para poder entrar),
 * productos con su stock y precio, la caja abierta de cada usuario, la configuración, los permisos y las
 * promociones. Se actualiza sola cada minuto mientras hay conexión, y se guarda en un archivo para que
 * también sirva si se apaga el PC y se vuelve a encender sin internet.
 */
public final class CacheLocal {

    private static final Path ARCHIVO = Path.of(System.getProperty("user.home"), ".sistema-sas", "cache", "datos.bin");

    private static class Datos implements Serializable {
        private static final long serialVersionUID = 1L;
        ArrayList<Usuario> usuarios = new ArrayList<>();
        ArrayList<Producto> productos = new ArrayList<>();
        HashMap<Integer, Caja> cajas = new HashMap<>();          // caja abierta de cada usuario (por id de usuario)
        HashMap<String, String> config = new HashMap<>();
        HashMap<String, Set<Modulo>> permisos = new HashMap<>();
        HashMap<String, Set<String>> acciones = new HashMap<>();
        ArrayList<Promocion> promociones = new ArrayList<>();
        long actualizado;
    }

    private static Datos datos = leerDeDisco();

    private CacheLocal() {
    }

    private static Datos leerDeDisco() {
        if (Files.isRegularFile(ARCHIVO)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(ARCHIVO))) {
                return (Datos) in.readObject();
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                // archivo dañado o de otra versión: se empieza vacío y se vuelve a llenar con la próxima conexión
            }
        }
        return new Datos();
    }

    private static void escribirEnDisco() {
        try {
            Files.createDirectories(ARCHIVO.getParent());
            Path temporal = ARCHIVO.resolveSibling("datos.tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temporal))) {
                out.writeObject(datos);
            }
            Files.move(temporal, ARCHIVO, StandardCopyOption.REPLACE_EXISTING);   // reemplazo completo: nunca queda a medias
        } catch (IOException e) {
            // si no se puede guardar, sigue funcionando en memoria
        }
    }

    /** ¿La copia local se renovó hace menos de estos milisegundos? */
    public static synchronized boolean fresco(long milisegundos) {
        return datos.actualizado > 0 && System.currentTimeMillis() - datos.actualizado < milisegundos;
    }

    /** ¿Ya se descargó alguna vez la información necesaria? Sin eso no se puede trabajar sin conexión. */
    public static synchronized boolean hayDatos() {
        return datos.actualizado > 0 && !datos.usuarios.isEmpty();
    }

    /** Vuelve a leer todo de la base de datos (solo con conexión) y lo guarda en el archivo local. */
    public static synchronized void refrescar() throws SQLException {
        Datos nuevo = new Datos();
        UsuarioDAO usuarioDAO = new UsuarioDAO();
        nuevo.usuarios.addAll(usuarioDAO.listar());
        nuevo.productos.addAll(new ProductoDAO().listar());
        CajaDAO cajaDAO = new CajaDAO();
        for (Usuario u : nuevo.usuarios) {
            Caja c = cajaDAO.buscarAbiertaDeUsuario(u.getId());
            if (c != null) {
                nuevo.cajas.put(u.getId(), c);
            }
        }
        nuevo.config.putAll(new ConfiguracionDAO().todos());
        PermisoDAO permisoDAO = new PermisoDAO();
        nuevo.permisos.putAll(permisoDAO.cargarTodos());
        nuevo.acciones.putAll(permisoDAO.cargarAcciones());
        nuevo.promociones.addAll(new PromocionDAO().listar());
        nuevo.actualizado = System.currentTimeMillis();
        datos = nuevo;
        // Lo que se vendió sin conexión y aún no subió ya salió del inventario en este PC.
        for (Venta v : ColaVentas.leerTodas()) {
            descontar(v.getDetalles());
        }
        escribirEnDisco();
    }

    // ---------------------------------------------------------------- lectura (lo que usan los DAO sin conexión)

    public static synchronized Usuario usuario(String login) {
        for (Usuario u : datos.usuarios) {
            if (u.getUsuario().equalsIgnoreCase(login)) {
                return u;
            }
        }
        return null;
    }

    public static synchronized List<Usuario> usuarios() {
        return new ArrayList<>(datos.usuarios);
    }

    public static synchronized List<Producto> productos() {
        List<Producto> lista = new ArrayList<>();
        for (Producto p : datos.productos) {
            if (p.isActivo()) {
                lista.add(p);
            }
        }
        return lista;
    }

    public static synchronized Producto productoPorCodigo(String codigo) {
        for (Producto p : datos.productos) {
            if (p.isActivo() && p.getCodigo().equalsIgnoreCase(codigo)) {
                return p;
            }
        }
        return null;
    }

    public static synchronized Producto productoPorId(int id) {
        for (Producto p : datos.productos) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    public static synchronized List<Producto> buscarProductos(String texto, int limite) {
        String t = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
        List<Producto> lista = new ArrayList<>();
        for (Producto p : datos.productos) {
            if (!p.isActivo()) {
                continue;
            }
            if (t.isEmpty() || p.getNombre().toLowerCase(Locale.ROOT).contains(t) || p.getCodigo().toLowerCase(Locale.ROOT).contains(t)
                    || (p.getMarca() != null && p.getMarca().toLowerCase(Locale.ROOT).contains(t))) {
                lista.add(p);
                if (lista.size() >= limite) {
                    break;
                }
            }
        }
        return lista;
    }

    public static synchronized Caja cajaAbierta(int idUsuario) {
        return datos.cajas.get(idUsuario);
    }

    public static synchronized Map<String, String> config() {
        return new HashMap<>(datos.config);
    }

    public static synchronized Map<String, Set<Modulo>> permisos() {
        return new HashMap<>(datos.permisos);
    }

    public static synchronized Map<String, Set<String>> acciones() {
        Map<String, Set<String>> copia = new HashMap<>();
        datos.acciones.forEach((k, v) -> copia.put(k, new HashSet<>(v)));
        return copia;
    }

    public static synchronized List<Promocion> promociones() {
        return new ArrayList<>(datos.promociones);
    }

    // ---------------------------------------------------------------- cambios hechos sin conexión

    /** Una venta hecha sin conexión: baja el stock de la copia local para que no se venda lo que ya no hay. */
    public static synchronized void descontarStock(Venta v) {
        descontar(v.getDetalles());
        escribirEnDisco();
    }

    private static void descontar(List<DetalleVenta> lineas) {
        for (DetalleVenta d : lineas) {
            for (Producto p : datos.productos) {
                if (p.getId() == d.getIdProducto()) {
                    p.setStock(p.getStock() - d.getCantidad());
                }
            }
        }
    }
}
