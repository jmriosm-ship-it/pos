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

            // 7) Productos de celulares y equipos: marca, condición, garantía, capacidad, color y si se
            //    controla por IMEI / serial (cada unidad tiene su propio número).
            agregarColumna(st, "productos", "marca", "VARCHAR(60) NULL");
            agregarColumna(st, "productos", "condicion", "VARCHAR(20) NOT NULL DEFAULT 'Nuevo'");
            agregarColumna(st, "productos", "garantia_dias", "INT NOT NULL DEFAULT 0");
            agregarColumna(st, "productos", "por_imei", "BOOLEAN NOT NULL DEFAULT FALSE");
            agregarColumna(st, "productos", "capacidad", "VARCHAR(30) NULL");
            agregarColumna(st, "productos", "color", "VARCHAR(30) NULL");

            // 8) Un IMEI/serial por fila. Si el producto es "por IMEI", su stock = IMEI DISPONIBLES.
            st.execute("CREATE TABLE IF NOT EXISTS producto_imeis ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "id_producto INT NOT NULL, "
                    + "imei VARCHAR(20) NOT NULL UNIQUE, "
                    + "estado ENUM('DISPONIBLE','VENDIDO') NOT NULL DEFAULT 'DISPONIBLE', "
                    + "id_venta INT NULL, "
                    + "creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_producto) REFERENCES productos(id))");

            // 10) Promociones (2x1, % de descuento) y puntos de los clientes en cada venta.
            st.execute("CREATE TABLE IF NOT EXISTS promociones ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "nombre VARCHAR(80) NOT NULL, "
                    + "alcance ENUM('TIENDA','CATEGORIA','PRODUCTO') NOT NULL DEFAULT 'TIENDA', "
                    + "id_objetivo INT NULL, "
                    + "tipo ENUM('PORCENTAJE','NXM') NOT NULL, "
                    + "valor DECIMAL(5,2) NOT NULL DEFAULT 0, "
                    + "lleva INT NOT NULL DEFAULT 0, "
                    + "paga INT NOT NULL DEFAULT 0, "
                    + "desde DATE NULL, hasta DATE NULL, "
                    + "activa BOOLEAN NOT NULL DEFAULT TRUE)");
            agregarColumna(st, "ventas", "descuento_promo", "DECIMAL(12,2) NOT NULL DEFAULT 0");
            agregarColumna(st, "ventas", "descuento_puntos", "DECIMAL(12,2) NOT NULL DEFAULT 0");
            agregarColumna(st, "ventas", "puntos_usados", "INT NOT NULL DEFAULT 0");
            agregarColumna(st, "ventas", "puntos_ganados", "INT NOT NULL DEFAULT 0");

            // 11) Caja: ingresos y egresos manuales, devoluciones de ventas, categoría de los gastos y número de recibo.
            st.execute("CREATE TABLE IF NOT EXISTS movimientos_caja ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_caja INT NOT NULL, id_usuario INT NOT NULL, "
                    + "tipo ENUM('INGRESO','EGRESO') NOT NULL, monto DECIMAL(12,2) NOT NULL, motivo VARCHAR(160) NOT NULL, "
                    + "fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_caja) REFERENCES cajas(id), FOREIGN KEY (id_usuario) REFERENCES usuarios(id))");
            st.execute("CREATE TABLE IF NOT EXISTS devoluciones ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_venta INT NOT NULL, id_usuario INT NOT NULL, id_caja INT NULL, "
                    + "metodo ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO') NOT NULL, monto DECIMAL(12,2) NOT NULL, "
                    + "fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_venta) REFERENCES ventas(id), FOREIGN KEY (id_usuario) REFERENCES usuarios(id))");
            st.execute("CREATE TABLE IF NOT EXISTS devolucion_items ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_devolucion INT NOT NULL, id_producto INT NOT NULL, cantidad INT NOT NULL, "
                    + "FOREIGN KEY (id_devolucion) REFERENCES devoluciones(id), FOREIGN KEY (id_producto) REFERENCES productos(id))");
            agregarColumna(st, "gastos", "categoria", "VARCHAR(40) NOT NULL DEFAULT 'Otros'");
            agregarColumna(st, "ventas", "etiqueta", "VARCHAR(20) NULL");

            // 12) Trabajadores: cupo, pago por día, permisos propios, pantallas extra y la nómina (consumos y sueldos).
            agregarColumna(st, "usuarios", "cupo", "DECIMAL(12,2) NOT NULL DEFAULT 0");
            agregarColumna(st, "usuarios", "pago_dia", "DECIMAL(12,2) NOT NULL DEFAULT 0");
            agregarColumna(st, "usuarios", "puede_anular", "BOOLEAN NOT NULL DEFAULT FALSE");
            agregarColumna(st, "usuarios", "puede_editar_productos", "BOOLEAN NOT NULL DEFAULT TRUE");
            // Permisos por acción (crear, editar, borrar) de cada rol. Al crearse, cada rol conserva lo que ya
            // tenía: puede crear en los módulos que ya veía; editar y borrar quedan apagados.
            boolean accionesNuevas;
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'permisos_accion'")) {
                accionesNuevas = !rs.next();
            }
            if (accionesNuevas) {
                st.execute("CREATE TABLE permisos_accion ("
                        + "id_rol INT NOT NULL, modulo VARCHAR(40) NOT NULL, accion VARCHAR(10) NOT NULL, "
                        + "PRIMARY KEY (id_rol, modulo, accion), FOREIGN KEY (id_rol) REFERENCES roles(id))");
                st.execute("INSERT INTO permisos_accion (id_rol, modulo, accion) SELECT p.id_rol, p.modulo, 'CREAR' "
                        + "FROM permisos_rol p JOIN roles r ON p.id_rol = r.id WHERE r.nombre <> 'ADMIN' "
                        + "AND p.modulo IN ('PRODUCTOS','CATEGORIAS_PROVEEDORES','CLIENTES','VENTAS','INVENTARIO','CAJA')");
            }
            st.execute("CREATE TABLE IF NOT EXISTS usuario_modulos ("
                    + "id_usuario INT NOT NULL, modulo VARCHAR(40) NOT NULL, PRIMARY KEY (id_usuario, modulo), "
                    + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id))");
            st.execute("CREATE TABLE IF NOT EXISTS nomina ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NOT NULL, "
                    + "tipo ENUM('CONSUMO','PAGO') NOT NULL, concepto VARCHAR(160) NOT NULL, "
                    + "valor DECIMAL(12,2) NOT NULL, sueldo DECIMAL(12,2) NULL, descuento DECIMAL(12,2) NULL, "
                    + "dias INT NULL, valor_dia DECIMAL(12,2) NULL, periodo VARCHAR(60) NULL, "
                    + "anulado BOOLEAN NOT NULL DEFAULT FALSE, id_registro INT NOT NULL, "
                    + "fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id), FOREIGN KEY (id_registro) REFERENCES usuarios(id))");

            // 13) Crédito en cuotas: un método de pago más, el crédito con sus cuotas y los pagos que se le hacen.
            if (!columnaContiene(st, "pagos_venta", "metodo", "CREDITO")) {
                st.execute("ALTER TABLE pagos_venta MODIFY metodo "
                        + "ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO','CREDITO') NOT NULL");
            }
            if (!columnaContiene(st, "ventas", "metodo_pago", "CREDITO")) {
                st.execute("ALTER TABLE ventas MODIFY metodo_pago "
                        + "ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','MIXTO','FIADO','CREDITO') NOT NULL");
            }
            st.execute("CREATE TABLE IF NOT EXISTS creditos ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_venta INT NOT NULL, id_cliente INT NOT NULL, "
                    + "descripcion VARCHAR(200) NOT NULL, precio DECIMAL(12,2) NOT NULL, financiado DECIMAL(12,2) NOT NULL, "
                    + "cuota_inicial DECIMAL(12,2) NOT NULL, plazo INT NOT NULL, tasa DECIMAL(5,2) NOT NULL, "
                    + "frecuencia ENUM('MENSUAL','QUINCENAL') NOT NULL, cuota_valor DECIMAL(12,2) NOT NULL, "
                    + "total_credito DECIMAL(12,2) NOT NULL, intereses DECIMAL(12,2) NOT NULL, "
                    + "estado ENUM('ACTIVO','CANCELADO') NOT NULL DEFAULT 'ACTIVO', "
                    + "creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_venta) REFERENCES ventas(id), FOREIGN KEY (id_cliente) REFERENCES clientes(id))");
            st.execute("CREATE TABLE IF NOT EXISTS credito_cuotas ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_credito INT NOT NULL, n INT NOT NULL, vence DATE NOT NULL, "
                    + "valor DECIMAL(12,2) NOT NULL, FOREIGN KEY (id_credito) REFERENCES creditos(id))");
            st.execute("CREATE TABLE IF NOT EXISTS credito_pagos ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_credito INT NOT NULL, id_usuario INT NOT NULL, id_caja INT NULL, "
                    + "metodo ENUM('EFECTIVO','TARJETA','TRANSFERENCIA') NOT NULL, valor DECIMAL(12,2) NOT NULL, "
                    + "fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_credito) REFERENCES creditos(id), FOREIGN KEY (id_usuario) REFERENCES usuarios(id))");
            st.execute("CREATE TABLE IF NOT EXISTS credito_avisos ("
                    + "id_credito INT NOT NULL, clave VARCHAR(30) NOT NULL, fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id_credito, clave), FOREIGN KEY (id_credito) REFERENCES creditos(id))");

            // 14) Registro de acciones (auditoría) y fotos de facturas.
            st.execute("CREATE TABLE IF NOT EXISTS auditoria ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NULL, accion VARCHAR(60) NOT NULL, "
                    + "detalle VARCHAR(300) NOT NULL DEFAULT '', fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            st.execute("CREATE TABLE IF NOT EXISTS facturas ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_venta INT NULL, cliente VARCHAR(100) NULL, nota VARCHAR(200) NULL, "
                    + "archivo VARCHAR(500) NOT NULL, id_usuario INT NOT NULL, fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "eliminada BOOLEAN NOT NULL DEFAULT FALSE, fecha_eliminada DATETIME NULL, "
                    + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id))");

            // 15) Financiera: se vende pero la plata la paga después una financiera (no entra a la caja ni a las ganancias hasta que paga).
            if (!columnaContiene(st, "pagos_venta", "metodo", "FINANCIERA")) {
                st.execute("ALTER TABLE pagos_venta MODIFY metodo "
                        + "ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO','CREDITO','FINANCIERA') NOT NULL");
            }
            if (!columnaContiene(st, "ventas", "metodo_pago", "FINANCIERA")) {
                st.execute("ALTER TABLE ventas MODIFY metodo_pago "
                        + "ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','MIXTO','FIADO','CREDITO','FINANCIERA') NOT NULL");
            }
            agregarColumna(st, "ventas", "financiera_nombre", "VARCHAR(80) NULL");
            st.execute("CREATE TABLE IF NOT EXISTS cobros_financiera ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, id_venta INT NOT NULL, tipo ENUM('PAGO','AJUSTE') NOT NULL, "
                    + "valor DECIMAL(12,2) NOT NULL, nota VARCHAR(160) NULL, id_usuario INT NOT NULL, "
                    + "fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (id_venta) REFERENCES ventas(id), FOREIGN KEY (id_usuario) REFERENCES usuarios(id))");

            // 16) Rebaja de precio por producto al cobrar y límite de rebaja por usuario (sin PIN: si se pasa, se bloquea).
            agregarColumna(st, "detalle_venta", "precio_original", "DECIMAL(12,2) NULL");
            agregarColumna(st, "usuarios", "descuento_max", "INT NOT NULL DEFAULT 100");

            // 9) Los IMEI vendidos en cada línea de venta (texto separado por comas) para el recibo.
            agregarColumna(st, "detalle_venta", "imeis", "VARCHAR(600) NULL");
        }
    }

    // Agrega una columna solo si todavía no existe.
    private static void agregarColumna(Statement st, String tabla, String columna, String definicion) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = '" + tabla + "' AND COLUMN_NAME = '" + columna + "'")) {
            if (rs.next()) {
                return;
            }
        }
        st.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
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
