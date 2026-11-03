package com.pos.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Cambios a la estructura de la base de datos hechos DESPUÉS de crearla con schema.sql.
 * Se ejecutan solos cada vez que arranca el programa y son seguros de repetir: si el cambio
 * ya está hecho, no pasa nada. Así quien ya tenga la base creada se actualiza sin tocar nada.
 */
public class Migraciones {

    private Migraciones() {
    }

    public static void aplicar() throws SQLException {
        try (Connection con = Conexion.obtener(); Statement st = con.createStatement()) {

            // 1) Pagos de cada venta: permite pagar con varios medios a la vez.
            st.execute("CREATE TABLE IF NOT EXISTS pagos_venta ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "id_venta INT NOT NULL, "
                    + "metodo ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO') NOT NULL, "
                    + "monto DECIMAL(12,2) NOT NULL, "
                    + "FOREIGN KEY (id_venta) REFERENCES ventas(id))");

            // 2) Los métodos de pago ahora incluyen MIXTO (varios medios) y FIADO (queda a deber).
            //    Solo se modifican las columnas que todavía no los tengan.
            if (!columnaContiene(st, "pagos_venta", "metodo", "FIADO")) {
                st.execute("ALTER TABLE pagos_venta MODIFY metodo "
                        + "ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO') NOT NULL");
            }
            if (!columnaContiene(st, "ventas", "metodo_pago", "FIADO")) {
                st.execute("ALTER TABLE ventas MODIFY metodo_pago "
                        + "ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','MIXTO','FIADO') NOT NULL");
            }

            // 3) Las ventas que no tengan pagos registrados (las hechas antes de este cambio)
            //    reciben uno por el total, con su método de siempre.
            st.execute("INSERT INTO pagos_venta (id_venta, metodo, monto) "
                    + "SELECT v.id, v.metodo_pago, v.total FROM ventas v "
                    + "WHERE v.metodo_pago NOT IN ('MIXTO', 'FIADO') "
                    + "AND NOT EXISTS (SELECT 1 FROM pagos_venta p WHERE p.id_venta = v.id)");

            // 4) Fiados: lo que un cliente quedó debiendo de una venta. "saldo" baja con cada abono.
            st.execute("CREATE TABLE IF NOT EXISTS fiados ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "id_venta INT NOT NULL, "
                    + "id_cliente INT NOT NULL, "
                    + "monto DECIMAL(12,2) NOT NULL, "
                    + "saldo DECIMAL(12,2) NOT NULL, "
                    + "estado ENUM('PENDIENTE','PAGADO','ANULADO') NOT NULL DEFAULT 'PENDIENTE', "
                    + "creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_venta) REFERENCES ventas(id), "
                    + "FOREIGN KEY (id_cliente) REFERENCES clientes(id))");

            // 5) Abonos: pagos que el cliente va haciendo sobre sus fiados. Quedan ligados a la caja
            //    donde se recibieron, para que el efectivo cuadre en el arqueo.
            st.execute("CREATE TABLE IF NOT EXISTS abonos_fiado ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "id_fiado INT NOT NULL, "
                    + "id_usuario INT NOT NULL, "
                    + "id_caja INT NOT NULL, "
                    + "metodo ENUM('EFECTIVO','TARJETA','TRANSFERENCIA') NOT NULL, "
                    + "monto DECIMAL(12,2) NOT NULL, "
                    + "fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_fiado) REFERENCES fiados(id), "
                    + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id), "
                    + "FOREIGN KEY (id_caja) REFERENCES cajas(id))");

            // 6) Permisos de cada rol: qué módulos ve cada uno. Se pueden cambiar desde "Usuarios y permisos".
            //    El administrador no tiene filas: siempre ve todo.
            st.execute("CREATE TABLE IF NOT EXISTS permisos_rol ("
                    + "id_rol INT NOT NULL, "
                    + "modulo VARCHAR(40) NOT NULL, "
                    + "PRIMARY KEY (id_rol, modulo), "
                    + "FOREIGN KEY (id_rol) REFERENCES roles(id))");
            int filas;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM permisos_rol")) {
                rs.next();
                filas = rs.getInt(1);
            }
            if (filas == 0) {   // primera vez: se cargan los permisos de siempre
                sembrar(st, "CAJERO", "DASHBOARD", "VENTAS", "CAJA", "CLIENTES");
                sembrar(st, "BODEGUERO", "DASHBOARD", "PRODUCTOS", "CATEGORIAS_PROVEEDORES", "INVENTARIO");
            }
        }
    }

    // Da a un rol acceso a esos módulos (los valores son fijos del programa, no vienen del usuario).
    private static void sembrar(Statement st, String rol, String... modulos) throws SQLException {
        for (String modulo : modulos) {
            st.execute("INSERT INTO permisos_rol (id_rol, modulo) SELECT id, '" + modulo + "' FROM roles WHERE nombre = '" + rol + "'");
        }
    }

    // ¿La columna ya acepta este valor? Se mira el tipo (ENUM) que tiene guardado MySQL.
    private static boolean columnaContiene(Statement st, String tabla, String columna, String valor) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tabla + "' AND COLUMN_NAME = '" + columna + "'")) {
            return rs.next() && rs.getString(1).contains(valor);
        }
    }
}
