# Funciones del POS "Zona Originals" (React) que hay que tener en el POS Java

Fuente: `Desktop\poos zona originals`. Los colores/tema del POS Java se quedan como están.
Lo que ya tiene el POS Java y el viejo no (caja con arqueo/comprobante, permisos por rol editables, copias, reportes) se conserva.

## Estado (marcar al terminar cada fase)
- [x] F1 (reducida) El negocio es de COLONIAS/PERFUMES: producto con marca y tamaño. IMEI/garantía/condición quedaron como base apagada (por_imei=false), no se construye su pantalla.
- [x] F2 Crédito en cuotas HECHO: cobro con tarjeta Crédito (F10, plan F11), pestaña Clientes>Créditos (Alt+2), contrato (F7), WhatsApp (F8/F9), config en Configuración>Crédito (Alt+3).
- [x] F3 Financiera HECHO (Cobro Ctrl+F, Clientes>Financiera Alt+3, ingresos al cobrar).
- [x] F4 Trabajadores HECHO: pestaña Usuarios>Trabajadores (Alt+3): cupo, consumos (Ctrl+U), pagar sueldo (Ctrl+M/F4), detalle, permisos por trabajador (anular/devolver, editar productos, pantallas extra) en el diálogo de usuario.
- [x] F5 Promociones y puntos HECHO (descuento con PIN: el usuario pidió NO hacerlo)
- [x] F6 HECHO. Ventas ahora en pantalla completa (sin menú lateral; Ctrl+B lo muestra): barra de caja arriba (F7 movimientos, F8 cobrar deuda, F9 cerrar caja), buscador + cantidad, ticket, panel de cliente/descuento/puntos, total abajo con Cancelar F6 y Cobrar F4; cobro como recuadro con precios editables (Ctrl+P) y límite de rebaja por usuario.
- [x] F7 Movimientos, Ganancias (devoluciones, intereses, categorías, vendedores, calendario), Facturas (recibos, fotos, papelera) y Registro de acciones HECHO (pestañas de Reportes Alt+4/5/6 y Configuración>Registro Alt+4).
- [x] F8 Inventario inteligente (Inventario>Por reponer Alt+5), pistola (Lector), Inicio mejorado, Ayuda y documentos legales (Ctrl+H) HECHO.

## Reglas exactas

**IMEI / serial**: si el producto es `porImei`, stock = número de IMEI. Serial válido `^[A-Z0-9]{8,17}$` (mayúsculas, sin espacios). Si son 15 dígitos exactos, validar dígito Luhn. No repetir IMEI en carrito. Al vender/devolver/anular se mueven los IMEI individuales. Buscar por IMEI o código de barras exacto.
**Producto**: nombre, categoría, marca, condición (Nuevo / Open box / Usado), garantía en días, costo (solo admin), precio, porImei, variantes (capacidad, color, código de barras, stock, mínimo=1).
**Categorías base**: iPhone, MacBook, AppleWatch, Monastery, Perfumes, Chevignon, AirPods, Accesorios.

**Crédito en cuotas** (Configuración → Crédito): tasas mensuales ofrecidas (def. 2.3 %), plazos mensuales 6,12,18,24,36, quincenales 24,36,48, inicial mínima 30 % (botones 10..50 %, los menores al mínimo deshabilitados), tope de tasa por usura (vacío = sin tope; si se supera bloquea), avisos WhatsApp `waDiasAntes`=1 + plantillas.
- Cédula del cliente obligatoria. Frecuencia mensual o quincenal (cada 15 días). Primera cuota por defecto +1 mes / +15 días.
- `inicial = total − crédito − fiado − financiera`; `inicialPct = inicial/total*100 >= creditoInicialMin`. La inicial se paga en efectivo/transferencia/tarjeta.
- Cuota (sistema francés): `i = tasa/100` (mensual) o `(tasa/2)/100` (quincenal); `cuota = round(financiado*i/(1-(1+i)^-plazo))`; si i=0, `financiado/plazo`. `totalCredito = cuota*plazo`; `intereses = totalCredito − financiado`. **Última cuota ajustada**: `total − cuota*(plazo−1)`. Fechas mensuales respetan fin de mes.
- Pagos se aplican a la cuota más vieja primero. Estado cuota: Pagada/Vencida/Parcial/Pendiente. Crédito: cancelado/pagado/mora/al día. `mora = Σ pendiente de cuotas vencidas`; `diasMora` desde la cuota vencida más antigua. Sin interés de mora calculado (solo texto del contrato).
- Anular la venta cancela el crédito. Admin puede "Cancelar crédito". No se devuelven ítems de una venta a crédito.
- Interés ganado entra en ganancias proporcional a lo cobrado: `Σ pagos del periodo * (intereses/totalCredito)`.
- Contrato imprimible: acreedor, deudor (C.C., tel.), producto con IMEI, precio, inicial, financiado, plazo, tasa, cuota, total, intereses, plan de pagos; cláusulas: pago anticipado a cuotas viejas, mora a tasa máxima legal sin superar usura, incumplir 2+ cuotas permite exigir el saldo, autorización de datos (Ley 1581/2012), casilla SÍ/NO centrales de riesgo (Ley 1266/2008), aviso de revisión por abogado.
- Avisos WhatsApp (`wa.me/57XXXXXXXXXX`, 10 dígitos → prefijo 57): "aviso" = cuota que vence entre hoy y waDiasAntes (una vez por cuota); "mora" = cuota vencida más vieja de cada crédito, repetir cada 3 días. Variables: {nombre} {negocio} {n} {cuotas} {valor} {fecha} {cuando} {saldo}. Lista de créditos: primero mora (desc.), luego saldo.

**Financiera**: medio de pago con nombre de financiera (ej. iPadLock). Por cobrar: no entra a caja ni ganancias hasta que paga. `saldo = max(0, valorFinanciera − cobrado)` (0 si anulada). Pantalla Cobros: Venta, Financiera, Esperado, Recibido, Falta; registrar pago o "ajuste" (cierra con diferencia/comisión, no suma ingresos). Ganancia: parte local cuenta el día de la venta; parte financiera cuenta cada día que paga, costo proporcional.

**Trabajadores / nómina**: cupo por usuario; consumos (concepto, valor); `pendiente = max(0, Σ consumos − Σ descuentos de pagos)`; `disponible = cupo − pendiente`. "Sacar del cupo": trabajador solo para sí y sin pasar el disponible; admin elige a quién y puede pasarse confirmando; anular consumo. **Pagar sueldo** (admin): `sueldo = round(días*valorDía)`, descuento por defecto = pendiente (0..pendiente), `neto = sueldo − descuento`; registra pago y, si neto>0, un gasto "Nómina" ("Sueldo <nombre> · <periodo>"). Ayuda: días del mes en que vendió. Sin descuentos de ley. Pantalla: ventas por usuario (hoy/mes, netas de devoluciones), ventas sin vendedor, cajas que abrió/cerró, calendario por trabajador.
**Permisos por trabajador**: pantallas extra, editar productos (sin ver costos), anular/devolver, descuento máximo sin PIN (%; admin 100).

**Promociones**: nombre, alcance (tienda/categoría/producto), tipo `porcentaje` o `nxm` (lleva N paga M), desde/hasta opcionales, activa. Mejor promo por línea. `porcentaje = round(precio*cant*valor/100)`; `nxm = floor(cant/lleva)*(lleva−paga)*precio` (lleva>paga).
**Descuentos**: promo automática + descuento manual (% o $, tope = base) + puntos. `total = subtotal − promos − descManual − puntos`. Si `descManual + rebaja > subtotalNormal*descuentoMax/100` → pedir PIN admin. Rebaja de precio por producto al cobrar (nunca sobre el precio normal).
**Puntos**: ganados `floor(total/1000*puntosPorMil)` (def. 0.1 por $1.000); cada punto vale `valorPunto` ($100). Usables: `floor((base−descManual)/valorPunto)` limitado a los del cliente. Puntos cliente = Σ(ganados−usados) de ventas vigentes.

**Cobro**: medios Efectivo, Transferencia, Tarjeta, Fiado, Crédito, Financiera combinables; botones Completar y billetes 10/20/50/100 mil; cambio solo en efectivo; fiado/crédito exigen cliente (nuevo: nombre, teléfono, autorización de datos). Número de ticket por caja con prefijo `C1-00005`. Garantía "hasta" fecha en el recibo + texto de garantía + mensaje del recibo.
**Caja**: nombre de caja por computador; una caja abierta por turno; ingreso/egreso manual (valor+motivo); esperado = `inicial + efectivoVentas + abonosEfectivo(+pagos crédito efectivo) − devEfectivo − pagoProveedores + ingresos − egresos`. Resumen por medio y por vendedor; historial de cierres y resumen de todas las cajas (admin). **Devolución de dinero** (motivo+valor → gasto "Devoluciones" + egreso; sin permiso pide PIN).
**Devoluciones de venta**: elegir unidades/IMEI y medio de reembolso; `reembolso = round(Σ precio*cant*(total/subtotal))`; sube stock/IMEI; si medio Fiado baja la deuda. Anular: reintegra lo no devuelto, estado anulada, cancela crédito. `netoVenta = total − Σ devoluciones`.
**Compras**: proveedor, producto/variante, cantidad o lista de IMEI, costo unidad, pago inicial y medio; actualiza costo del producto; abonos posteriores; saldo; foto de factura.
**Clientes**: nombre, cédula, teléfono, WhatsApp, correo, nota, autorización; lista con compras, total, puntos, debe; ficha con historial; borrar.
**Deudas**: pestañas por cobrar (fiado + créditos), por pagar (proveedores), financiera; abonos Efectivo/Transferencia/Tarjeta sin pasar el saldo; recordatorio WhatsApp.
**Ganancias**: periodos Hoy/Este mes/Mes pasado/Todo/Fechas/Calendario (ventas por día, filtro vendedor). `bruta = ingresos − costos`; `neta = bruta + intereses − gastos`; `margen = neta/ingresos*100`. Gráficas ingresos/costos por día, categorías, vendedores, top 5. Gastos: Arriendo, Servicios, Nómina, Publicidad, Bolsas y empaques, Transporte, Otros.
**Resumen/Inicio**: KPIs del mes, últimas ventas, atención al stock, tarjetas por módulo con dato en vivo (por cobrar = fiado + saldo créditos + saldo financiera), avisos admin (IMEI vendidos 2 veces, cuotas por avisar, productos por reponer).
**Movimientos**: historial unificado (ventas, compras, abonos, créditos, gastos, caja, turnos, financiera) con filtros fecha/grupo/texto, paginado de 150.
**Facturas**: recibos de venta (buscables por ticket, cliente, vendedor, caja, producto, IMEI), fotos de facturas, papelera restaurable.
**Inventario inteligente**: ritmo = vendidas 30 d /30; días = stock/ritmo; nivel agotado (stock<=0), bajo (stock<=mínimo), pronto (días<7); sugerido pedir `max(ceil(ritmo*30)−stock, mínimo*2−stock, 1)`.
**Configuración**: negocio (nombre, NIT, teléfono, dirección, mensaje recibo, texto garantía, datos del titular legal), puntos, crédito, categorías, promociones, usuarios (PIN 4–6 dígitos, permisos), registro/auditoría (inicio sesión, devoluciones, anulaciones, gastos, usuarios, PIN, sueldos...), impresora/pistola (sonido), ayuda y documentos legales (privacidad, términos, garantía, crédito, autorización), copias.
**Pistola**: ráfaga de teclas <60 ms y largo >=4 se toma como código aunque no esté el cursor en el buscador; pitido bueno/malo.
