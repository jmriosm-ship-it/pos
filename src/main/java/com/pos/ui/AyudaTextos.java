package com.pos.ui;

import com.pos.servicio.ConfiguracionService;

import java.sql.SQLException;

/** Los textos de la ayuda: la guía de uso y los documentos legales (borradores que debe revisar un abogado). */
public class AyudaTextos {

    private AyudaTextos() {
    }

    public static String guia() {
        return """
                GUÍA DE USO — Sistema SAS

                MOVERSE SIN MOUSE
                  Ctrl + 1 al 9 y Ctrl + 0 ......... ir a cada módulo del menú (el número sale a la derecha de cada uno)
                  Alt + 1, 2, 3... ................. cambiar de pestaña dentro de un módulo
                  Ctrl + K ......................... cambiar mi clave
                  Ctrl + U ......................... sacar algo del local a cuenta de mi cupo
                  Ctrl + M ......................... pagar un sueldo (administrador)
                  Ctrl + H ......................... esta ayuda
                  Cada botón muestra su tecla; si no la ves, es que no hace falta.

                PRIMEROS PASOS
                  1. Abre tu caja: al entrar a Ventas se pide primero, con el efectivo con el que empiezas.
                  2. Vende: F2 busca o escanea, F3 elige cliente, F4 cobra.
                  3. Al terminar el turno, cierra la caja: cuenta el efectivo y el sistema dice si cuadra, falta o sobra.

                VENDER
                  F2 buscar o escanear · F3 cliente · F4 cobrar · F5 descuento · F6 cancelar la venta · F10 usar puntos · + y - cantidad
                  F7 movimientos de caja · F8 cobrar deuda · F9 cerrar caja · F11 historial de ventas y facturas · Ctrl+B muestra u oculta el menú
                  Al cobrar se abre un recuadro donde se puede bajar el precio de cada producto (Ctrl+P); el límite de rebaja lo fija el administrador.
                  Con la pistola de códigos solo escanea: se agrega solo, aunque el cursor no esté en el buscador.
                  Las promociones se aplican solas (2x1, porcentajes) y la mejor gana en cada producto.

                COBRAR
                  Se puede pagar con varios medios a la vez: efectivo, transferencia, tarjeta, fiado, crédito en cuotas
                  y financiera. F5 completa lo que falta. F6 a F9 son billetes. F12 o Enter confirma.
                  Fiado: el cliente se lleva todo y queda debiendo (se cobra con abonos en Clientes).
                  Crédito: en cuotas con interés; hace falta cliente con cédula y una cuota inicial mínima.
                  Financiera: la paga una financiera después; no entra a la caja hasta que pague.

                CAJA
                  F3 ingreso · F4 egreso · F5 devolver dinero a un cliente · F12 cerrar caja.
                  Todo lo que mueve efectivo queda en el arqueo y en el comprobante del cierre.

                INVENTARIO
                  Existencias, compras, movimientos y "Por reponer", que avisa qué se está acabando según lo que se vende.

                CLIENTES
                  Clientes (fiados y abonos), Créditos en cuotas (pagos, contrato, avisos por WhatsApp) y Financiera.

                REPORTES
                  Resumen, ventas, gastos, movimientos, calendario y facturas. F10 cambia el periodo y F11 exporta.
                  Para devolver productos de una venta: Reportes > Ventas > F8.

                USUARIOS Y TRABAJADORES
                  Cada persona tiene su rol y se le pueden dar permisos propios y pantallas extra.
                  Trabajadores: cupo para sacar mercancía, pago de sueldos y ventas de cada uno.

                COPIAS DE SEGURIDAD
                  Copias de seguridad > F5 hace una copia. Guárdala en una carpeta de OneDrive o Google Drive para tenerla también en la nube.
                """;
    }

    public static String privacidad() throws SQLException {
        return encabezado("POLÍTICA DE PRIVACIDAD Y TRATAMIENTO DE DATOS") + """
                1. Responsable. {negocio} (NIT {nit}), {ciudad}. Contacto para consultas y reclamos: {correo} · {telefono}.

                2. Datos que recogemos. Nombre, cédula, teléfono, correo y dirección de los clientes, y el historial de sus compras,
                fiados, créditos y pagos.

                3. Para qué los usamos. Registrar ventas y garantías, manejar fiados y créditos, avisar de cuotas por WhatsApp,
                atender reclamos y cumplir obligaciones legales y tributarias.

                4. Derechos. El titular puede conocer, actualizar, rectificar y pedir la supresión de sus datos, y revocar su
                autorización, escribiendo a {correo} (Ley 1581 de 2012).

                5. Seguridad. Los datos se guardan en el sistema del negocio con acceso por usuario y clave, y se hacen copias de seguridad.

                6. Vigencia. Los datos se conservan mientras exista la relación comercial y el tiempo que exija la ley.
                """ + pie();
    }

    public static String terminos() throws SQLException {
        return encabezado("TÉRMINOS Y CONDICIONES DE VENTA") + """
                1. Precios. Los precios están en pesos colombianos e incluyen los impuestos que apliquen.

                2. Medios de pago. Efectivo, transferencia, tarjeta, fiado autorizado, crédito en cuotas y financiera.

                3. Devoluciones y cambios. Se aceptan dentro de los plazos que {negocio} indique, con el recibo y el producto en buen estado
                y sellado. Los perfumes y colonias abiertos o usados no tienen cambio, salvo defecto de fábrica.

                4. Descuentos y promociones. Se aplican según su vigencia y no son acumulables salvo que se indique.

                5. Puntos. Los puntos de cliente no se pueden cambiar por dinero y pueden perder vigencia según las reglas del negocio.

                6. Reclamos. {correo} · {telefono}.
                """ + pie();
    }

    public static String garantia() throws SQLException {
        return encabezado("POLÍTICA DE GARANTÍA") + """
                1. {negocio} garantiza que los productos son originales y están en las condiciones descritas al momento de la venta.

                2. La garantía cubre defectos de fábrica reportados con el recibo de compra. No cubre daños por mal uso, derrames,
                manipulación, ni productos abiertos o usados.

                3. Para reclamar: presenta el recibo y el producto. Se revisa y se responde con cambio, reparación o devolución del dinero,
                según el caso.

                4. Contacto: {correo} · {telefono}.
                """ + pie();
    }

    public static String credito() throws SQLException {
        return encabezado("CONDICIONES DEL CRÉDITO EN CUOTAS") + """
                1. El cliente paga una cuota inicial y financia el resto en cuotas mensuales o quincenales, con la tasa de interés
                informada al momento de la venta.

                2. Cada pago se aplica primero a las cuotas más antiguas. Se puede pagar por anticipado.

                3. En caso de mora se cobrarán intereses a la tasa máxima legal vigente, sin superar el límite de usura.

                4. Si el cliente incumple dos o más cuotas, {negocio} puede exigir el saldo total.

                5. Los recordatorios de pago se pueden enviar por WhatsApp al número registrado.

                6. El contrato firmado y el pagaré son documentos aparte. Este texto es un modelo y debe revisarlo un abogado.
                """ + pie();
    }

    public static String autorizacion() throws SQLException {
        return encabezado("AUTORIZACIÓN DE TRATAMIENTO DE DATOS PERSONALES") + """
                Yo, el titular de los datos, autorizo a {negocio} (NIT {nit}) para recolectar, almacenar, usar y consultar mis datos
                personales con las finalidades descritas en su política de privacidad, incluido el envío de recordatorios de pago por
                WhatsApp o llamada.

                Autorizo la consulta y reporte de mi comportamiento de pago a centrales de riesgo (Ley 1266 de 2008):   [  ] SÍ    [  ] NO

                Conozco mis derechos de conocer, actualizar, rectificar y suprimir mis datos y de revocar esta autorización escribiendo a {correo}.

                Nombre: ______________________   C.C.: ______________   Firma: ______________________
                """ + pie();
    }

    // ---- ayudas

    private static String encabezado(String titulo) throws SQLException {
        return reemplazar(titulo + "\n{negocio}\n\n");
    }

    private static String pie() {
        return "\n\nNota: este texto es un modelo. Pídele a un abogado que lo revise antes de usarlo con clientes.\n";
    }

    // Cambia {negocio}, {nit}, {ciudad}, {correo} y {telefono} por los datos de Configuración.
    public static String reemplazar(String texto) throws SQLException {
        ConfiguracionService c = new ConfiguracionService();
        return texto.replace("{negocio}", c.nombreNegocio())
                .replace("{nit}", c.obtener(ConfiguracionService.NEGOCIO_NIT, "________"))
                .replace("{ciudad}", c.obtener("negocio_ciudad", "________"))
                .replace("{correo}", c.obtener("negocio_correo", "________"))
                .replace("{telefono}", c.obtener(ConfiguracionService.NEGOCIO_TELEFONO, "________"));
    }

    public static String completar(String texto) throws SQLException {
        return reemplazar(texto);
    }
}
