package com.pos.ui;

import com.pos.modelo.ProductoVendido;
import com.pos.modelo.ResumenFinanciero;
import com.pos.modelo.VentaDia;
import com.pos.servicio.GastoService;
import com.pos.servicio.ReporteService;
import com.pos.util.Dinero;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pestaña "Resumen" (diseño A): cuánto se vendió y ganó en el periodo elegido. Cuatro tarjetas (ingresos,
 * ganancia bruta, gastos, ganancia neta), ventas por día, cómo pagaron y lo más vendido.
 * F8 exporta el resumen a un archivo que se abre en Excel.
 */
public class ResumenPanel extends ScrollPane {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] METODOS = {"EFECTIVO", "TARJETA", "TRANSFERENCIA", "FIADO", "CREDITO", "FINANCIERA"};
    private static final String[] NOMBRES = {"Efectivo", "Tarjeta", "Transferencia", "Fiado (por cobrar)", "Crédito (por cobrar)", "Financiera (por cobrar)"};
    private static final String[] COLORES = {"#2563eb", "#14b8a6", "#7c3aed", "#f59e0b", "#ef4444", "#64748b"};

    private final ReporteService reportes = new ReporteService();
    private final GastoService gastos = new GastoService();
    private final PeriodoReporte periodo;
    private final VBox raiz = new VBox(14);

    public ResumenPanel(PeriodoReporte periodo) {
        this.periodo = periodo;
        setFitToWidth(true);
        raiz.setPadding(new Insets(0, 6, 8, 0));
        setContent(raiz);
        periodo.alCambiar(this::recargar);
        recargar();
    }

    // Vuelve a calcular todo para el periodo actual.
    public void recargar() {
        raiz.getChildren().clear();
        try {
            LocalDate desde = periodo.desde();
            LocalDate hasta = periodo.hasta();
            ResumenFinanciero r = reportes.resumen(desde, hasta);
            int numeroGastos = gastos.listar(desde, hasta).size();

            // Fila 1: las cuatro tarjetas
            BigDecimal margen = r.ingresos().signum() == 0 ? BigDecimal.ZERO
                    : r.gananciaBruta().multiply(BigDecimal.valueOf(100)).divide(r.ingresos(), 0, RoundingMode.HALF_UP);
            HBox tarjetas = new HBox(10,
                    tarjeta("Ingresos", Dinero.formato(r.ingresos()), r.numeroVentas() + (r.numeroVentas() == 1 ? " venta" : " ventas"), null),
                    tarjeta("Ganancia bruta", Dinero.formato(r.gananciaBruta()), "margen " + margen + "%", null),
                    tarjeta("Gastos", Dinero.formato(r.gastos()), numeroGastos + (numeroGastos == 1 ? " gasto" : " gastos"),
                            r.gastos().signum() > 0 ? "texto-peligro" : null),
                    tarjeta("Ganancia neta", Dinero.formato(r.gananciaNeta()), "después de gastos",
                            r.gananciaNeta().signum() >= 0 ? "texto-ok" : "texto-peligro"));

            // Fila 2: ventas por día y cómo pagaron
            VBox grafica = caja("Ventas por día", crearGrafica(reportes.ventasPorDia(desde, hasta), desde, hasta));
            HBox.setHgrow(grafica, Priority.ALWAYS);
            grafica.setMinWidth(0);   // la gráfica se encoge antes de empujar la tarjeta de pagos fuera de la pantalla
            grafica.setPrefWidth(10);
            VBox pagos = caja("Cómo pagaron", crearPagos(reportes.ventasPorMetodoPago(desde, hasta)));
            pagos.setPrefWidth(340);
            pagos.setMinWidth(340);
            HBox fila2 = new HBox(10, grafica, pagos);

            // Fila 3: más vendidos
            VBox top = caja("Más vendidos", crearTop(reportes.masVendidos(desde, hasta, 5)));

            // Fila 4: ganancia por categoría y ventas por vendedor
            VBox porCategoria = caja("Ganancia por categoría", listaCategorias(reportes.gananciaPorCategoria(desde, hasta)));
            VBox porVendedor = caja("Ventas por vendedor (menos devoluciones)", listaVendedores(desde, hasta));
            HBox.setHgrow(porCategoria, Priority.ALWAYS);
            HBox.setHgrow(porVendedor, Priority.ALWAYS);
            porCategoria.setPrefWidth(10);
            porVendedor.setPrefWidth(10);
            HBox fila4 = new HBox(10, porCategoria, porVendedor);

            Label periodoTexto = new Label("Periodo: " + periodo.texto());
            periodoTexto.getStyleClass().add("texto-suave");
            raiz.getChildren().addAll(periodoTexto, tarjetas);
            if (r.intereses().signum() > 0) {   // solo si hubo créditos cobrados
                Label interes = new Label("Incluye " + Dinero.formato(r.intereses()) + " de intereses de créditos cobrados.");
                interes.getStyleClass().add("texto-suave");
                raiz.getChildren().add(interes);
            }
            raiz.getChildren().addAll(fila2, top, fila4);
        } catch (SQLException | IllegalArgumentException e) {
            raiz.getChildren().add(new Label("No se pudo calcular el resumen: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------- piezas

    private VBox tarjeta(String etiqueta, String valor, String nota, String estiloValor) {
        Label e = new Label(etiqueta);
        e.getStyleClass().add("metrica-etiqueta");
        Label v = new Label(valor);
        v.getStyleClass().add("metrica-valor");
        if (estiloValor != null) {
            v.getStyleClass().add(estiloValor);
        }
        Label n = new Label(nota);
        n.getStyleClass().add("metrica-nota");
        VBox caja = new VBox(4, e, v, n);
        caja.getStyleClass().add("card");
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setPrefWidth(10);   // todas parten del mismo ancho y se reparten el espacio igual
        return caja;
    }

    private VBox caja(String titulo, Node contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("card-titulo");
        VBox caja = new VBox(10, t, contenido);
        caja.getStyleClass().add("card");
        return caja;
    }

    // Una barra por día del periodo; los días sin ventas salen en cero.
    private BarChart<String, Number> crearGrafica(List<VentaDia> datos, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Number> porDia = new HashMap<>();
        for (VentaDia d : datos) {
            porDia.put(d.dia(), d.total());
        }
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            serie.getData().add(new XYChart.Data<>(dia.format(DIA), porDia.getOrDefault(dia, 0)));
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
        grafica.setCategoryGap(serie.getData().size() > 14 ? 4 : 24);
        grafica.setPrefHeight(230);
        grafica.setMinHeight(230);
        return grafica;
    }

    // Una barra repartida por métodos de pago y, debajo, cuánto fue de cada uno.
    private Node crearPagos(Map<String, BigDecimal> mapa) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : mapa.values()) {
            total = total.add(v);
        }
        if (total.signum() == 0) {
            Label vacio = new Label("Sin ventas en este periodo.");
            vacio.getStyleClass().add("texto-suave");
            return vacio;
        }
        HBox barra = new HBox();
        barra.setMinHeight(14);
        barra.setMaxHeight(14);
        barra.setPrefWidth(10);   // el ancho lo da la tarjeta; si no, los tramos (que dependen de él) la ensanchan
        VBox leyenda = new VBox(6);
        for (int i = 0; i < METODOS.length; i++) {
            BigDecimal monto = mapa.getOrDefault(METODOS[i], BigDecimal.ZERO);
            if (monto.signum() == 0) {
                continue;
            }
            double fraccion = monto.doubleValue() / total.doubleValue();
            Region tramo = new Region();
            tramo.setMinHeight(14);
            tramo.setStyle("-fx-background-color: " + COLORES[i] + ";");
            tramo.prefWidthProperty().bind(barra.widthProperty().multiply(fraccion));
            tramo.setMinWidth(0);   // el mínimo NO sigue al ancho: si no, la barra nunca podría encogerse
            tramo.maxWidthProperty().bind(tramo.prefWidthProperty());
            barra.getChildren().add(tramo);

            Region punto = new Region();
            punto.setMinSize(9, 9);
            punto.setMaxSize(9, 9);
            punto.setStyle("-fx-background-color: " + COLORES[i] + "; -fx-background-radius: 5;");
            Label nombre = new Label(NOMBRES[i]);
            Label valor = new Label(Dinero.formato(monto) + "  (" + Math.round(fraccion * 100) + "%)");
            valor.getStyleClass().add("linea-nombre");
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox fila = new HBox(8, punto, nombre, espacio, valor);
            fila.setAlignment(Pos.CENTER_LEFT);
            leyenda.getChildren().add(fila);
        }
        // Se recorta la barra con esquinas redondeadas.
        barra.setStyle("-fx-background-radius: 7;");
        javafx.scene.shape.Rectangle recorte = new javafx.scene.shape.Rectangle();
        recorte.setArcWidth(14);
        recorte.setArcHeight(14);
        recorte.widthProperty().bind(barra.widthProperty());
        recorte.setHeight(14);
        barra.setClip(recorte);
        return new VBox(12, barra, leyenda);
    }

    // Cada categoría con lo vendido y lo que dejó de ganancia.
    private Node listaCategorias(List<com.pos.dao.ReporteDAO.FilaCategoria> lista) {
        VBox caja = new VBox(2);
        if (lista.isEmpty()) {
            Label vacio = new Label("Sin ventas en este periodo.");
            vacio.getStyleClass().add("texto-suave");
            caja.getChildren().add(vacio);
        }
        for (var c : lista) {
            Label nombre = new Label(c.nombre());
            Label vendido = new Label(Dinero.formato(c.ingresos()));
            vendido.getStyleClass().add("texto-suave");
            Label ganancia = new Label(Dinero.formato(c.ganancia()));
            ganancia.getStyleClass().add("linea-nombre");
            ganancia.setMinWidth(100);
            ganancia.setAlignment(Pos.CENTER_RIGHT);
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox fila = new HBox(12, nombre, espacio, vendido, ganancia);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("linea");
            caja.getChildren().add(fila);
        }
        return caja;
    }

    // Cuánto vendió cada trabajador activo en el periodo (netas, sin lo devuelto).
    private Node listaVendedores(LocalDate desde, LocalDate hasta) throws SQLException {
        VBox caja = new VBox(2);
        com.pos.servicio.NominaService nomina = new com.pos.servicio.NominaService();
        boolean alguno = false;
        for (com.pos.modelo.Usuario u : new com.pos.servicio.UsuarioService().listar()) {
            BigDecimal[] v = nomina.ventas(u, desde, hasta);
            if (v[0].signum() == 0) {
                continue;
            }
            alguno = true;
            Label nombre = new Label(u.getNombre());
            Label cantidad = new Label(v[0].toBigInteger() + (v[0].intValue() == 1 ? " venta" : " ventas"));
            cantidad.getStyleClass().add("texto-suave");
            Label total = new Label(Dinero.formato(v[1]));
            total.getStyleClass().add("linea-nombre");
            total.setMinWidth(100);
            total.setAlignment(Pos.CENTER_RIGHT);
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox fila = new HBox(12, nombre, espacio, cantidad, total);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("linea");
            caja.getChildren().add(fila);
        }
        if (!alguno) {
            Label vacio = new Label("Sin ventas en este periodo.");
            vacio.getStyleClass().add("texto-suave");
            caja.getChildren().add(vacio);
        }
        return caja;
    }

    private Node crearTop(List<ProductoVendido> lista) {
        VBox caja = new VBox(2);
        if (lista.isEmpty()) {
            Label vacio = new Label("Sin ventas en este periodo.");
            vacio.getStyleClass().add("texto-suave");
            caja.getChildren().add(vacio);
            return caja;
        }
        int puesto = 1;
        for (ProductoVendido p : lista) {
            Label nombre = new Label(puesto++ + ".  " + p.nombre());
            Label unidades = new Label(p.unidades() + (p.unidades() == 1 ? " unidad" : " unidades"));
            unidades.getStyleClass().add("texto-suave");
            Label total = new Label(Dinero.formato(p.total()));
            total.getStyleClass().add("linea-nombre");
            total.setMinWidth(110);
            total.setAlignment(Pos.CENTER_RIGHT);
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox fila = new HBox(14, nombre, espacio, unidades, total);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("linea");
            caja.getChildren().add(fila);
        }
        return caja;
    }

    // ---------------------------------------------------------------- exportar

    // Escribe el resumen en un archivo CSV (se abre con Excel: separado por ";" y con tildes bien).
    public void exportar(Path destino) throws IOException, SQLException {
        LocalDate desde = periodo.desde();
        LocalDate hasta = periodo.hasta();
        ResumenFinanciero r = reportes.resumen(desde, hasta);
        List<String> lineas = new ArrayList<>();
        lineas.add("Resumen de ventas;" + periodo.texto());
        lineas.add("Desde;" + desde + ";Hasta;" + hasta);
        lineas.add("");
        lineas.add("Indicador;Valor");
        lineas.add("Ventas realizadas;" + r.numeroVentas());
        lineas.add("Ingresos;" + entero(r.ingresos()));
        lineas.add("Costo de lo vendido;" + entero(r.costoVendido()));
        lineas.add("Ganancia bruta;" + entero(r.gananciaBruta()));
        lineas.add("Gastos;" + entero(r.gastos()));
        lineas.add("Ganancia neta;" + entero(r.gananciaNeta()));
        lineas.add("Ticket promedio;" + entero(r.ticketPromedio()));
        lineas.add("");
        lineas.add("Método de pago;Monto");
        for (Map.Entry<String, BigDecimal> e : reportes.ventasPorMetodoPago(desde, hasta).entrySet()) {
            lineas.add(e.getKey() + ";" + entero(e.getValue()));
        }
        lineas.add("");
        lineas.add("Día;Vendido");
        for (VentaDia d : reportes.ventasPorDia(desde, hasta)) {
            lineas.add(d.dia() + ";" + entero(d.total()));
        }
        lineas.add("");
        lineas.add("Producto;Unidades;Total");
        for (ProductoVendido p : reportes.masVendidos(desde, hasta, 20)) {
            lineas.add(p.nombre() + ";" + p.unidades() + ";" + entero(p.total()));
        }
        // "﻿" al inicio le dice a Excel que el archivo está en UTF-8 (para que las tildes se vean bien).
        Files.writeString(destino, "﻿" + String.join("\r\n", lineas) + "\r\n", StandardCharsets.UTF_8);
    }

    private String entero(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
