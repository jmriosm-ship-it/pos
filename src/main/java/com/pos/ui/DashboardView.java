package com.pos.ui;

import com.pos.modelo.Modulo;
import com.pos.modelo.Producto;
import com.pos.modelo.ProductoVendido;
import com.pos.modelo.ResumenFinanciero;
import com.pos.modelo.Usuario;
import com.pos.modelo.VentaDia;
import com.pos.servicio.InventarioService;
import com.pos.servicio.Permisos;
import com.pos.servicio.ReporteService;
import com.pos.util.Dinero;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Pantalla de Inicio: cómo va el día (tarjetas), ventas de la semana, más vendidos y alertas. */
public class DashboardView extends ScrollPane {

    private final Usuario usuario = Sesion.usuario();
    private final ReporteService reportes = new ReporteService();
    private final InventarioService inventario = new InventarioService();

    public DashboardView(Consumer<Modulo> ir) {
        setFitToWidth(true);
        VBox raiz = new VBox(16);
        raiz.setPadding(new Insets(0, 6, 8, 0));
        try {
            cargar(raiz, ir);
        } catch (SQLException e) {
            raiz.getChildren().add(new Label("No se pudieron cargar los datos: " + e.getMessage()));
        }
        setContent(raiz);
    }

    private void cargar(VBox raiz, Consumer<Modulo> ir) throws SQLException {
        LocalDate hoy = LocalDate.now();
        ResumenFinanciero r = reportes.resumenHoy();
        List<Producto> stockBajo = inventario.productosConStockBajo();

        // Fila 1: tarjetas de números
        HBox metricas = new HBox(14);
        metricas.getChildren().add(metrica("Vendido hoy", Dinero.formato(r.ingresos()), r.numeroVentas() + " ventas", false));
        metricas.getChildren().add(metrica("Ticket promedio", Dinero.formato(r.ticketPromedio()), "por venta", false));
        if (usuario.esAdmin()) {   // la ganancia solo la ve el administrador
            metricas.getChildren().add(metrica("Ganancia bruta hoy", Dinero.formato(r.gananciaBruta()),
                    "Gastos: " + Dinero.formato(r.gastos()), false));
        }
        metricas.getChildren().add(metrica("Stock bajo", String.valueOf(stockBajo.size()),
                "productos por reponer", !stockBajo.isEmpty()));

        // Fila 1b: lo que se debe, lo que hay en bodega y la ganancia del mes (según lo que cada usuario puede ver)
        HBox metricas2 = new HBox(14);
        if (Permisos.puedeVer(usuario, Modulo.CLIENTES)) {
            java.math.BigDecimal fiado = java.math.BigDecimal.ZERO;
            for (com.pos.modelo.Cliente c : new com.pos.dao.ClienteDAO().listarConResumen()) {
                fiado = fiado.add(c.getSaldoPendiente() == null ? java.math.BigDecimal.ZERO : c.getSaldoPendiente());
            }
            com.pos.servicio.CreditoService creditos = new com.pos.servicio.CreditoService();
            java.math.BigDecimal enCreditos = java.math.BigDecimal.ZERO;
            int enMora = 0;
            for (com.pos.dao.CreditoDAO.Fila f : creditos.listar(true)) {
                com.pos.servicio.CreditoService.Estado e = creditos.estado(f);
                enCreditos = enCreditos.add(e.saldo());
                if (e.mora().signum() > 0) {
                    enMora++;
                }
            }
            java.math.BigDecimal financieras = new com.pos.servicio.FinancieraService().totalPorCobrar();
            java.math.BigDecimal porCobrar = fiado.add(enCreditos).add(financieras);
            metricas2.getChildren().add(metrica("Por cobrar", Dinero.formato(porCobrar),
                    "fiado, créditos y financiera" + (enMora > 0 ? " · " + enMora + " en mora" : ""), enMora > 0));
        }
        if (Permisos.puedeVer(usuario, Modulo.INVENTARIO) || usuario.esAdmin()) {
            List<Producto> todos = new com.pos.dao.ProductoDAO().listar();
            int unidades = todos.stream().mapToInt(Producto::getStock).sum();
            metricas2.getChildren().add(metrica("En bodega", unidades + " unidades", todos.size() + " productos", false));
        }
        if (usuario.esAdmin()) {
            ResumenFinanciero mes = reportes.resumen(hoy.withDayOfMonth(1), hoy);
            metricas2.getChildren().add(metrica("Ganancia neta del mes", Dinero.formato(mes.gananciaNeta()),
                    mes.numeroVentas() + " ventas · ingresos " + Dinero.formato(mes.ingresos()), mes.gananciaNeta().signum() < 0));
        }

        // Avisos (solo administrador): cuotas por avisar y productos por reponer según lo que se vende.
        VBox avisos = new VBox(8);
        if (usuario.esAdmin()) {
            int cuotas = new com.pos.servicio.CreditoService().avisosPendientes().size();
            if (cuotas > 0) {
                avisos.getChildren().add(aviso("Hay " + cuotas + (cuotas == 1 ? " cuota" : " cuotas")
                        + " por avisar a los clientes por WhatsApp.", "Ir a Créditos", Modulo.CLIENTES, ir));
            }
            int porReponer = new com.pos.servicio.InteligenteService().porReponer().size();
            if (porReponer > 0) {
                avisos.getChildren().add(aviso(porReponer + (porReponer == 1 ? " producto se está acabando" : " productos se están acabando")
                        + " según lo que se vende.", "Ver qué reponer", Modulo.INVENTARIO, ir));
            }
        }

        // Fila 2: gráfica de la semana y más vendidos
        VBox graficaTarjeta = tarjeta("Ventas de los últimos 7 días", crearGrafica(reportes.ventasUltimosDias(7), hoy));
        HBox.setHgrow(graficaTarjeta, Priority.ALWAYS);
        VBox masVendidos = tarjeta("Más vendidos hoy", listaMasVendidos(reportes.masVendidos(hoy, hoy, 5)));
        masVendidos.setPrefWidth(340);
        masVendidos.setMinWidth(340);
        HBox fila2 = new HBox(14, graficaTarjeta, masVendidos);

        // Fila 3: alertas de stock y accesos rápidos
        VBox alertas = tarjeta("Productos por reponer", listaStockBajo(stockBajo));
        HBox.setHgrow(alertas, Priority.ALWAYS);
        VBox accesos = tarjeta("Accesos rápidos", accesosRapidos(ir));
        accesos.setPrefWidth(340);
        accesos.setMinWidth(340);
        HBox fila3 = new HBox(14, alertas, accesos);

        raiz.getChildren().add(metricas);
        if (!metricas2.getChildren().isEmpty()) {
            raiz.getChildren().add(metricas2);
        }
        if (!avisos.getChildren().isEmpty()) {
            raiz.getChildren().add(avisos);
        }
        raiz.getChildren().addAll(fila2, fila3);
    }

    // Una franja de aviso con un botón que lleva a donde se resuelve.
    private HBox aviso(String texto, String boton, Modulo destino, Consumer<Modulo> ir) {
        Label t = new Label("● " + texto);
        t.getStyleClass().add("texto-aviso");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Button b = new Button(boton);
        b.getStyleClass().add("btn-secundario");
        b.setOnAction(e -> ir.accept(destino));
        HBox fila = new HBox(12, t, espacio, b);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("card");
        return fila;
    }

    private VBox metrica(String etiqueta, String valor, String nota, boolean alerta) {
        Label e = new Label(etiqueta);
        e.getStyleClass().add("metrica-etiqueta");
        Label v = new Label(valor);
        v.getStyleClass().add("metrica-valor");
        if (alerta) {
            v.getStyleClass().add("texto-peligro");
        }
        Label n = new Label(nota);
        n.getStyleClass().add("metrica-nota");
        VBox caja = new VBox(4, e, v, n);
        caja.getStyleClass().add("card");
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setPrefWidth(10);   // todas las tarjetas parten del mismo ancho y se reparten el espacio igual
        return caja;
    }

    private VBox tarjeta(String titulo, javafx.scene.Node contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("card-titulo");
        VBox caja = new VBox(10, t, contenido);
        caja.getStyleClass().add("card");
        return caja;
    }

    // Barras: una por día. Los días sin ventas se muestran en cero para que la gráfica no tenga huecos.
    private BarChart<String, Number> crearGrafica(List<VentaDia> datos, LocalDate hoy) {
        Map<LocalDate, Number> porDia = new HashMap<>();
        for (VentaDia d : datos) {
            porDia.put(d.dia(), d.total());
        }
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            serie.getData().add(new XYChart.Data<>(dia.format(formato), porDia.getOrDefault(dia, 0)));
        }

        NumberAxis ejeY = new NumberAxis();
        ejeY.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number n) {
                double v = n.doubleValue();
                return v >= 1000 ? "$" + Math.round(v / 1000) + "k" : "$" + Math.round(v);
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });
        BarChart<String, Number> grafica = new BarChart<>(new CategoryAxis(), ejeY);
        grafica.getData().add(serie);
        grafica.setLegendVisible(false);
        grafica.setAnimated(false);
        grafica.setCategoryGap(28);
        grafica.setPrefHeight(250);
        grafica.setMinHeight(250);
        return grafica;
    }

    private VBox listaMasVendidos(List<ProductoVendido> lista) {
        VBox caja = new VBox(8);
        if (lista.isEmpty()) {
            caja.getChildren().add(suave("Aún no hay ventas hoy."));
        }
        int puesto = 1;
        for (ProductoVendido p : lista) {
            Label n = new Label(puesto++ + ". " + p.nombre());
            Label u = new Label(p.unidades() + " uds");
            u.getStyleClass().add("texto-suave");
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox fila = new HBox(8, n, espacio, u);
            fila.setAlignment(Pos.CENTER_LEFT);
            caja.getChildren().add(fila);
        }
        return caja;
    }

    private VBox listaStockBajo(List<Producto> lista) {
        VBox caja = new VBox(8);
        if (lista.isEmpty()) {
            caja.getChildren().add(suave("Todo el inventario está por encima del mínimo."));
        }
        for (Producto p : lista.stream().limit(6).toList()) {
            Label n = new Label(p.getNombre());
            Label s = new Label(p.getStock() + " en stock (mín. " + p.getStockMinimo() + ")");
            s.getStyleClass().add("texto-peligro");
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox fila = new HBox(8, n, espacio, s);
            fila.setAlignment(Pos.CENTER_LEFT);
            caja.getChildren().add(fila);
        }
        return caja;
    }

    // Botones de acceso directo, solo a los módulos que el rol puede ver.
    private VBox accesosRapidos(Consumer<Modulo> ir) {
        VBox caja = new VBox(8);
        if (Permisos.puedeVer(usuario, Modulo.VENTAS)) {
            caja.getChildren().add(acceso("Nueva venta", Modulo.VENTAS, true, ir));
        }
        if (Permisos.puedeVer(usuario, Modulo.PRODUCTOS)) {
            caja.getChildren().add(acceso("Ver productos", Modulo.PRODUCTOS, false, ir));
        }
        if (Permisos.puedeVer(usuario, Modulo.CAJA)) {
            caja.getChildren().add(acceso("Caja del día", Modulo.CAJA, false, ir));
        }
        if (Permisos.puedeVer(usuario, Modulo.CLIENTES)) {
            caja.getChildren().add(acceso("Clientes y créditos", Modulo.CLIENTES, false, ir));
        }
        if (Permisos.puedeVer(usuario, Modulo.REPORTES)) {
            caja.getChildren().add(acceso("Reportes", Modulo.REPORTES, false, ir));
        }
        return caja;
    }

    private Button acceso(String texto, Modulo destino, boolean principal, Consumer<Modulo> ir) {
        Button b = new Button(texto);
        b.getStyleClass().add(principal ? "btn-primario" : "btn-secundario");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> ir.accept(destino));
        return b;
    }

    private Label suave(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-suave");
        return l;
    }
}
