package com.pos.ui;

import com.pos.dao.FinancieraDAO;
import com.pos.servicio.FinancieraService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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
 * Pestaña Financiera (Clientes): lo que las financieras todavía deben pagar de las ventas.
 * F5 registra un pago de la financiera o un ajuste (cierra la cuenta cuando pagó de menos, ej: su comisión).
 * Teclas: F4 pendientes / todas · F5 registrar cobro.
 */
public class FinancieraPanel extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FinancieraService servicio = new FinancieraService();
    private final TableView<FinancieraDAO.Fila> tabla = new TableView<>();
    private final ToggleGroup filtro = new ToggleGroup();
    private final Label resumen = new Label();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public FinancieraPanel() {
        setSpacing(12);

        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chips = new HBox(6, Chips.crear("Pendientes", "PENDIENTES", filtro, gris), Chips.crear("Todas", "TODAS", filtro, gris));
        chips.getChildren().forEach(c -> ((Region) c).setMinWidth(Region.USE_PREF_SIZE));
        filtro.selectToggle(filtro.getToggles().get(0));
        Chips.alElegir(filtro, this::recargar);
        resumen.getStyleClass().add("texto-suave");

        Button registrar = new Button("Registrar cobro");
        registrar.getStyleClass().add("btn-primario");
        registrar.setGraphic(Atajos.tecla("F5"));
        registrar.setContentDisplay(ContentDisplay.RIGHT);
        registrar.setGraphicTextGap(8);
        registrar.setFocusTraversable(false);
        registrar.setMinWidth(Region.USE_PREF_SIZE);
        registrar.setOnAction(e -> registrar());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, chips, resumen, espacio, registrar);
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay ventas con financiera. Se crean al cobrar con \"Financiera\"."));
        tabla.getColumns().add(Tablas.columna("Venta", 100, (FinancieraDAO.Fila f) -> f.ventaNumero()));
        tabla.getColumns().add(Tablas.columna("Fecha", 100, (FinancieraDAO.Fila f) -> f.fecha().format(FECHA)));
        tabla.getColumns().add(Tablas.columna("Cliente", 170, (FinancieraDAO.Fila f) -> f.cliente()));
        tabla.getColumns().add(Tablas.columna("Financiera", 150, (FinancieraDAO.Fila f) -> f.financiera() == null ? "" : f.financiera()));
        tabla.getColumns().add(Tablas.columna("Esperado", 110, (FinancieraDAO.Fila f) -> Dinero.formato(f.esperado())));
        tabla.getColumns().add(Tablas.columna("Recibido", 110, (FinancieraDAO.Fila f) -> Dinero.formato(f.recibido())));
        var falta = Tablas.columna("Falta", 110, (FinancieraDAO.Fila f) -> f.anulada() ? "Anulada" : Dinero.formato(f.falta()));
        Tablas.colorear(falta, f -> f.anulada() ? "texto-suave" : f.falta().signum() > 0 ? "texto-peligro" : "texto-ok");
        tabla.getColumns().add(falta);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                registrar();
            }
        });

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F4", "Pendientes / todas"), Atajos.etiqueta("F5", "Registrar cobro"),
                Atajos.etiqueta("↑ ↓", "Moverse"));
        getChildren().addAll(barra, tabla, teclas);

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

    public void recargar() {
        FinancieraDAO.Fila previa = tabla.getSelectionModel().getSelectedItem();
        try {
            List<FinancieraDAO.Fila> todas = servicio.listar();
            boolean soloPendientes = "PENDIENTES".equals(Chips.valor(filtro));
            List<FinancieraDAO.Fila> lista = todas.stream().filter(f -> !soloPendientes || f.falta().signum() > 0).toList();
            tabla.getItems().setAll(lista);
            if (previa != null) {
                lista.stream().filter(f -> f.idVenta() == previa.idVenta()).findFirst().ifPresent(f -> tabla.getSelectionModel().select(f));
            } else if (!lista.isEmpty()) {
                tabla.getSelectionModel().select(0);
            }
            BigDecimal porCobrar = todas.stream().map(FinancieraDAO.Fila::falta).reduce(BigDecimal.ZERO, BigDecimal::add);
            resumen.setText("Por cobrar a financieras: " + Dinero.formato(porCobrar));
        } catch (SQLException e) {
            Mensajes.error("No se pudo cargar la financiera.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F4 -> Chips.siguiente(filtro);
            case F5 -> registrar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    // Un pago de la financiera, o un ajuste para cerrar la cuenta con diferencia.
    private void registrar() {
        FinancieraDAO.Fila f = tabla.getSelectionModel().getSelectedItem();
        if (f == null) {
            Mensajes.info("Elige una venta", "Muévete a la tabla con la flecha ↓ y elige una venta.");
            return;
        }
        if (f.falta().signum() == 0) {
            Mensajes.info("Nada por cobrar", "Esta venta ya está al día con la financiera.");
            return;
        }
        ToggleGroup tipo = new ToggleGroup();
        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chips = new HBox(6, Chips.crear("La financiera pagó", "PAGO", tipo, gris),
                Chips.crear("Ajuste (cerrar con diferencia)", "AJUSTE", tipo, gris));
        tipo.selectToggle(tipo.getToggles().get(0));
        TextField valor = CamposDinero.compacto();
        valor.setText(f.falta().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        TextField nota = new TextField();
        nota.setPromptText("Nota (opcional), ej: comisión");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        Label info = new Label(f.ventaNumero() + " · " + f.financiera() + " · falta " + Dinero.formato(f.falta()));
        info.getStyleClass().add("linea-nombre");
        VBox contenido = new VBox(8, info, chips, etiqueta("Valor"), valor, etiqueta("Nota"), nota, error);
        contenido.setPrefWidth(460);

        ButtonType guardar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Cobro de la financiera");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(() -> {
            valor.requestFocus();
            valor.selectAll();
        });
        boolean[] hecho = {false};
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                servicio.registrar(Sesion.usuario(), f, (String) Chips.valor(tipo), CamposDinero.valor(valor), nota.getText());
                hecho[0] = true;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                ev.consume();
            }
        });
        dialogo.showAndWait();
        if (hecho[0]) {
            recargar();
        }
    }

    private static Label etiqueta(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
