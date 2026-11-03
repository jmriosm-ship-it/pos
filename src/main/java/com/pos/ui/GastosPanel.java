package com.pos.ui;

import com.pos.modelo.Gasto;
import com.pos.modelo.Usuario;
import com.pos.servicio.GastoService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Pestaña "Gastos": plata que sale del negocio (arriendo, servicios, bolsas...). Se anota arriba con el
 * teclado y queda en la lista del periodo. Los gastos restan de la ganancia neta del Resumen.
 * Teclas: F2 concepto · Enter pasa al monto y agrega · Supr elimina el elegido (solo administrador).
 */
public class GastosPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GastoService servicio = new GastoService();
    private final Usuario usuario = Sesion.usuario();
    private final PeriodoReporte periodo;

    private final TextField concepto = new TextField();
    private final TextField monto = CamposDinero.crear();
    private final Label error = new Label();
    private final Label total = new Label();
    private final TableView<Gasto> tabla = new TableView<>();

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public GastosPanel(PeriodoReporte periodo) {
        this.periodo = periodo;
        setSpacing(12);

        // Fila para anotar un gasto
        concepto.setPromptText("Ej: Arriendo del local");
        concepto.setOnAction(e -> {
            monto.requestFocus();
            monto.selectAll();
        });
        monto.setStyle("-fx-font-size: 13px; -fx-font-weight: normal; -fx-padding: 8 10;");
        monto.setPrefWidth(180);
        monto.setOnAction(e -> agregar());
        Button agregar = new Button("Agregar");
        agregar.getStyleClass().add("btn-primario");
        agregar.setGraphic(Atajos.tecla("Enter"));
        agregar.setContentDisplay(ContentDisplay.RIGHT);
        agregar.setGraphicTextGap(8);
        agregar.setFocusTraversable(false);
        agregar.setOnAction(e -> agregar());

        VBox colConcepto = campo("Concepto", Atajos.tecla("F2"), concepto);
        HBox.setHgrow(colConcepto, Priority.ALWAYS);
        HBox entrada = new HBox(10, colConcepto, campo("Monto", null, monto), agregar);
        entrada.setAlignment(Pos.BOTTOM_LEFT);
        error.getStyleClass().add("error-texto");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        VBox tarjeta = new VBox(8, entrada, error);
        tarjeta.getStyleClass().add("card");

        // Lista del periodo
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay gastos en este periodo."));
        tabla.getColumns().add(Tablas.columna("Fecha", 150, (Gasto g) -> g.getFecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Concepto", 320, (Gasto g) -> g.getConcepto()));
        tabla.getColumns().add(Tablas.columna("Monto", 130, (Gasto g) -> Dinero.formato(g.getMonto())));
        tabla.getColumns().add(Tablas.columna("Registró", 160, (Gasto g) -> g.getNombreUsuario()));
        tabla.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                eliminar();
                e.consume();
            }
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        total.getStyleClass().add("linea-nombre");
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Nuevo gasto"), Atajos.etiqueta("↑ ↓", "Moverse"));
        if (usuario.esAdmin()) {
            teclas.getChildren().add(Atajos.etiqueta("Supr", "Eliminar el elegido"));
        }
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(14, teclas, espacio, total);
        pie.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(tarjeta, tabla, pie);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        periodo.alCambiar(this::recargar);
        recargar();
    }

    private VBox campo(String titulo, Node tecla, Node contenido) {
        Label l = new Label(titulo);
        l.getStyleClass().add("etiqueta-campo");
        HBox cabecera = tecla == null ? new HBox(l) : new HBox(6, l, tecla);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, cabecera, contenido);
    }

    // ---------------------------------------------------------------- datos

    public void recargar() {
        try {
            List<Gasto> lista = servicio.listar(periodo.desde(), periodo.hasta());
            tabla.getItems().setAll(lista);
            BigDecimal suma = lista.stream().map(Gasto::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
            total.setText("Total del periodo (" + periodo.texto() + "): " + Dinero.formato(suma));
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los gastos.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- acciones

    private void tecla(KeyEvent e) {
        if (e.getCode() == KeyCode.F2) {
            concepto.requestFocus();
            concepto.selectAll();
            e.consume();
        }
    }

    private void agregar() {
        try {
            servicio.registrar(usuario, concepto.getText(), CamposDinero.valor(monto));
            concepto.clear();
            monto.clear();
            error.setText("");
            recargar();
            Platform.runLater(concepto::requestFocus);   // listo para anotar el siguiente
        } catch (IllegalArgumentException | IllegalStateException e) {
            error.setText(e.getMessage());
        } catch (SQLException e) {
            error.setText("No se pudo guardar el gasto: " + e.getMessage());
        }
    }

    private void eliminar() {
        Gasto g = tabla.getSelectionModel().getSelectedItem();
        if (g == null || !usuario.esAdmin()) {
            return;
        }
        if (!Mensajes.confirmar("Eliminar gasto", "¿Eliminar \"" + g.getConcepto() + "\" por "
                + Dinero.formato(g.getMonto()) + "?\nLa ganancia neta del periodo vuelve a subir.")) {
            return;
        }
        try {
            servicio.eliminar(usuario, g);
            recargar();
        } catch (IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo eliminar el gasto.\n" + e.getMessage());
        }
    }
}
