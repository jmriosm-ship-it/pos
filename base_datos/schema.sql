-- =====================================================================
-- POS 2.0 - Esquema de base de datos (MySQL 8)
-- Se ejecuta UNA vez para crear todas las tablas.
-- Cada bloque corresponde a un módulo de tu lista de funcionalidades.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS pos2
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pos2;

-- ---------------------------------------------------------------------
-- MÓDULO: Usuarios y permisos
-- Un rol agrupa permisos (ADMIN, CAJERO...). Cada usuario tiene un rol.
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(30) NOT NULL UNIQUE      -- ADMIN, CAJERO, BODEGUERO
);

CREATE TABLE usuarios (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(80)  NOT NULL,
    usuario     VARCHAR(30)  NOT NULL UNIQUE, -- lo que escribe para entrar
    clave_hash  VARCHAR(100) NOT NULL,        -- clave cifrada con BCrypt
    id_rol      INT NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_rol) REFERENCES roles(id)
);

-- Permisos de cada rol: qué módulos ve cada uno (se cambian desde "Usuarios y permisos").
-- El administrador no tiene filas: siempre ve todo. (También lo crea Migraciones.java.)
CREATE TABLE permisos_rol (
    id_rol  INT NOT NULL,
    modulo  VARCHAR(40) NOT NULL,
    PRIMARY KEY (id_rol, modulo),
    FOREIGN KEY (id_rol) REFERENCES roles(id)
);

-- ---------------------------------------------------------------------
-- MÓDULO: Categorías y proveedores
-- ---------------------------------------------------------------------
CREATE TABLE categorias (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE proveedores (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    telefono  VARCHAR(20),
    correo    VARCHAR(100),
    direccion VARCHAR(150),
    activo    BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- MÓDULO: Registro y gestión de productos
-- ---------------------------------------------------------------------
CREATE TABLE productos (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    codigo        VARCHAR(40)  NOT NULL UNIQUE, -- código de barras o interno
    nombre        VARCHAR(120) NOT NULL,
    id_categoria  INT,
    id_proveedor  INT,
    precio_compra DECIMAL(12,2) NOT NULL DEFAULT 0, -- lo que nos cuesta
    precio_venta  DECIMAL(12,2) NOT NULL,           -- lo que cobramos
    stock         INT NOT NULL DEFAULT 0,           -- unidades actuales
    stock_minimo  INT NOT NULL DEFAULT 0,           -- para alertas
    activo        BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (id_categoria) REFERENCES categorias(id),
    FOREIGN KEY (id_proveedor) REFERENCES proveedores(id)
);

-- ---------------------------------------------------------------------
-- MÓDULO: Clientes e historial de compras
-- (el historial sale de la tabla ventas, filtrando por id_cliente)
-- ---------------------------------------------------------------------
CREATE TABLE clientes (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    nombre     VARCHAR(100) NOT NULL,
    documento  VARCHAR(30) UNIQUE,
    telefono   VARCHAR(20),
    correo     VARCHAR(100),
    direccion  VARCHAR(150),
    creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- MÓDULO: Control de caja y arqueo
-- Un "turno" de caja: se abre con un monto inicial y se cierra contando
-- el efectivo. La diferencia entre lo esperado y lo contado es el arqueo.
-- ---------------------------------------------------------------------
CREATE TABLE cajas (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario       INT NOT NULL,
    abierta_en       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrada_en       DATETIME,
    monto_inicial    DECIMAL(12,2) NOT NULL,
    monto_esperado   DECIMAL(12,2),  -- calculado por el sistema al cerrar
    monto_contado    DECIMAL(12,2),  -- lo que el cajero cuenta con la mano
    diferencia       DECIMAL(12,2),  -- contado - esperado (sobra / falta)
    estado           ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
);

-- ---------------------------------------------------------------------
-- MÓDULO: Ventas y carrito de compras
-- ventas = la factura (encabezado) / detalle_venta = cada producto vendido
-- ---------------------------------------------------------------------
CREATE TABLE ventas (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    id_caja      INT NOT NULL,
    id_usuario   INT NOT NULL,
    id_cliente   INT,                         -- NULL = cliente general
    fecha        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal     DECIMAL(12,2) NOT NULL,
    descuento    DECIMAL(12,2) NOT NULL DEFAULT 0,
    total        DECIMAL(12,2) NOT NULL,
    metodo_pago  ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','MIXTO','FIADO') NOT NULL,   -- MIXTO = varios medios; FIADO = todo a deber
    estado       ENUM('COMPLETADA','ANULADA') NOT NULL DEFAULT 'COMPLETADA',
    FOREIGN KEY (id_caja)    REFERENCES cajas(id),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id),
    FOREIGN KEY (id_cliente) REFERENCES clientes(id)
);

CREATE TABLE detalle_venta (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    id_venta      INT NOT NULL,
    id_producto   INT NOT NULL,
    cantidad      INT NOT NULL,
    precio_unit   DECIMAL(12,2) NOT NULL,  -- precio al momento de vender
    costo_unit    DECIMAL(12,2) NOT NULL,  -- costo al momento (para ganancias)
    subtotal      DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (id_venta)    REFERENCES ventas(id),
    FOREIGN KEY (id_producto) REFERENCES productos(id)
);

-- Pagos de cada venta: una venta puede pagarse con varios medios a la vez
-- (ej: $20.000 en efectivo y el resto con tarjeta). Cada fila es un pago.
-- (El programa también crea esta tabla solo si falta: ver Migraciones.java)
CREATE TABLE pagos_venta (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    id_venta  INT NOT NULL,
    metodo    ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO') NOT NULL,   -- FIADO = queda a deber
    monto     DECIMAL(12,2) NOT NULL,         -- lo aplicado a la venta (sin el vuelto)
    FOREIGN KEY (id_venta) REFERENCES ventas(id)
);

-- Fiados: lo que un cliente quedó debiendo de una venta. "saldo" baja con cada abono.
CREATE TABLE fiados (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    id_venta   INT NOT NULL,
    id_cliente INT NOT NULL,
    monto      DECIMAL(12,2) NOT NULL,
    saldo      DECIMAL(12,2) NOT NULL,
    estado     ENUM('PENDIENTE','PAGADO','ANULADO') NOT NULL DEFAULT 'PENDIENTE',
    creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_venta)   REFERENCES ventas(id),
    FOREIGN KEY (id_cliente) REFERENCES clientes(id)
);

-- Abonos: pagos del cliente sobre sus fiados, ligados a la caja donde se recibieron.
CREATE TABLE abonos_fiado (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    id_fiado   INT NOT NULL,
    id_usuario INT NOT NULL,
    id_caja    INT NOT NULL,
    metodo     ENUM('EFECTIVO','TARJETA','TRANSFERENCIA') NOT NULL,
    monto      DECIMAL(12,2) NOT NULL,
    fecha      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_fiado)   REFERENCES fiados(id),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id),
    FOREIGN KEY (id_caja)    REFERENCES cajas(id)
);

-- ---------------------------------------------------------------------
-- MÓDULO: Inventario y movimientos
-- Cada vez que el stock cambia (venta, compra, ajuste) se anota aquí.
-- Así siempre se sabe por qué cambió una cantidad.
-- ---------------------------------------------------------------------
CREATE TABLE movimientos_inventario (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    id_producto  INT NOT NULL,
    id_usuario   INT NOT NULL,
    tipo         ENUM('ENTRADA','SALIDA','AJUSTE') NOT NULL,
    cantidad     INT NOT NULL,
    motivo       VARCHAR(150),           -- "Venta #12", "Compra", "Merma"...
    fecha        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_producto) REFERENCES productos(id),
    FOREIGN KEY (id_usuario)  REFERENCES usuarios(id)
);

-- Compras a proveedores (hacen subir el stock)
CREATE TABLE compras (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    id_proveedor  INT NOT NULL,
    id_usuario    INT NOT NULL,
    fecha         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total         DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (id_proveedor) REFERENCES proveedores(id),
    FOREIGN KEY (id_usuario)   REFERENCES usuarios(id)
);

CREATE TABLE detalle_compra (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    id_compra     INT NOT NULL,
    id_producto   INT NOT NULL,
    cantidad      INT NOT NULL,
    costo_unit    DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (id_compra)   REFERENCES compras(id),
    FOREIGN KEY (id_producto) REFERENCES productos(id)
);

-- ---------------------------------------------------------------------
-- MÓDULO: Reportes de ingresos y gastos
-- Ingresos = ventas. Gastos = esta tabla (arriendo, servicios, etc.)
-- ---------------------------------------------------------------------
CREATE TABLE gastos (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario   INT NOT NULL,
    concepto     VARCHAR(150) NOT NULL,
    monto        DECIMAL(12,2) NOT NULL,
    fecha        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
);

-- ---------------------------------------------------------------------
-- MÓDULO: Configuración (impresora térmica, datos del negocio)
-- Tabla clave/valor: guarda cualquier ajuste sin crear columnas nuevas.
-- ---------------------------------------------------------------------
CREATE TABLE configuracion (
    clave  VARCHAR(50) PRIMARY KEY,   -- ej: 'negocio_nombre', 'impresora'
    valor  VARCHAR(255)
);

-- ---------------------------------------------------------------------
-- DATOS INICIALES
-- ---------------------------------------------------------------------
INSERT INTO roles (nombre) VALUES ('ADMIN'), ('CAJERO'), ('BODEGUERO');

INSERT INTO configuracion (clave, valor) VALUES
    ('negocio_nombre', 'Mi Negocio'),
    ('negocio_nit', ''),
    ('negocio_telefono', ''),
    ('impresora_nombre', ''),
    ('impresora_ancho_mm', '80');

-- El usuario ADMIN lo crea el programa la primera vez que arranca
-- (así la clave se cifra con BCrypt desde Java).
