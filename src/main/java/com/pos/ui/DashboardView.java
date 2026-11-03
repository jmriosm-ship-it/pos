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

        raiz.getChildren().addAll(metricas, fila2, fila3);
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
            caja.getChildren().add(acceso("Caja y arqueo", Modulo.CAJA, false, ir));
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
