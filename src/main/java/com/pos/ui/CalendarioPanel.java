package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.modelo.VentaDia;
import com.pos.servicio.ReporteService;
import com.pos.servicio.UsuarioService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pestaña Calendario (Reportes): lo vendido cada día de un mes, de todos o de un vendedor.
 * Teclas: F5 mes anterior · F6 mes siguiente · F4 elegir vendedor.
 */
public class CalendarioPanel extends VBox {

    private final ReporteService reportes = new ReporteService();
    private final GridPane rejilla = new GridPane();
    private final Label mesTexto = new Label();
    private final Label totalMes = new Label();
    private final ComboBox<Object> vendedor = new ComboBox<>();   // "Todos" o un Usuario
    private YearMonth mes = YearMonth.now();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public CalendarioPanel() {
        setSpacing(12);

        vendedor.getItems().add("Todos los vendedores");
        try {
            vendedor.getItems().addAll(new UsuarioService().listar().stream().filter(Usuario::isActivo).toList());
        } catch (SQLException e) {
            // sin la lista, solo "Todos"
        }
        vendedor.getSelectionModel().select(0);
        vendedor.valueProperty().addListener((o, a, b) -> recargar());

        mesTexto.getStyleClass().add("linea-nombre");
        totalMes.getStyleClass().add("texto-suave");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, boton("◀", "F5", () -> cambiarMes(-1)), mesTexto, boton("▶", "F6", () -> cambiarMes(1)),
                totalMes, espacio, new Label("Vendedor"), vendedor, Atajos.tecla("F4"));
        barra.setAlignment(Pos.CENTER_LEFT);

        rejilla.setHgap(6);
        rejilla.setVgap(6);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / 7);
            rejilla.getColumnConstraints().add(c);
        }
        VBox.setVgrow(rejilla, Priority.ALWAYS);

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F5", "Mes anterior"), Atajos.etiqueta("F6", "Mes siguiente"),
                Atajos.etiqueta("F4", "Elegir vendedor"));
        getChildren().addAll(barra, rejilla, teclas);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        recargar();
    }

    private Button boton(String texto, String tecla, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn-secundario");
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(6);
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void cambiarMes(int delta) {
        mes = mes.plusMonths(delta);
        recargar();
    }

    public void recargar() {
        rejilla.getChildren().clear();
        String nombreMes = mes.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "CO"));
        mesTexto.setText(Character.toUpperCase(nombreMes.charAt(0)) + nombreMes.substring(1) + " " + mes.getYear());
        String[] dias = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        for (int i = 0; i < 7; i++) {
            Label l = new Label(dias[i]);
            l.getStyleClass().add("etiqueta-campo");
            l.setMaxWidth(Double.MAX_VALUE);
            l.setAlignment(Pos.CENTER);
            rejilla.add(l, i, 0);
        }
        Map<LocalDate, BigDecimal> ventas = new HashMap<>();
        BigDecimal suma = BigDecimal.ZERO;
        try {
            Object elegido = vendedor.getValue();
            Integer id = elegido instanceof Usuario u ? u.getId() : null;
            List<VentaDia> lista = reportes.ventasPorDiaDe(mes.atDay(1), mes.atEndOfMonth(), id);
            for (VentaDia d : lista) {
                ventas.put(d.dia(), d.total());
                suma = suma.add(d.total());
            }
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar el calendario.\n" + e.getMessage());
        }
        totalMes.setText("Vendido en el mes: " + Dinero.formato(suma));

        int columna = mes.atDay(1).getDayOfWeek().getValue() - 1;   // lunes = 0
        int fila = 1;
        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            LocalDate fecha = mes.atDay(dia);
            BigDecimal total = ventas.get(fecha);
            Label numero = new Label(String.valueOf(dia));
            numero.getStyleClass().add("texto-suave");
            Label valor = new Label(total == null ? "" : Dinero.formato(total));
            valor.getStyleClass().add("linea-nombre");
            VBox celda = new VBox(2, numero, valor);
            celda.getStyleClass().add("card");
            celda.setMinHeight(64);
            celda.setMaxWidth(Double.MAX_VALUE);
            if (total != null) {
                valor.getStyleClass().add("texto-ok");
            }
            if (fecha.equals(LocalDate.now())) {
                celda.setStyle("-fx-border-color: #2563eb; -fx-border-radius: 10; -fx-border-width: 2;");
            }
            rejilla.add(celda, columna, fila);
            columna++;
            if (columna == 7) {
                columna = 0;
                fila++;
            }
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F5 -> cambiarMes(-1);
            case F6 -> cambiarMes(1);
            case F4 -> {
                vendedor.requestFocus();
                vendedor.show();
            }
            default -> {
                return;
            }
        }
        e.consume();
    }
}
