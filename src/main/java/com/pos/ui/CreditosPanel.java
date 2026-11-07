package com.pos.ui;

import com.pos.dao.CreditoDAO;
import com.pos.modelo.Usuario;
import com.pos.servicio.CreditoService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Pestaña Créditos en cuotas: quién debe, cuánto y si está en mora (los que más atrasan van primero).
 * Teclas: F3 detalle · F4 filtro · F5 recibir pago · F7 contrato · F8 recordar por WhatsApp · F9 avisos de hoy · F10 cancelar.
 */
public class CreditosPanel extends VBox {

    private final CreditoService servicio = new CreditoService();
    private final Usuario usuario = Sesion.usuario();
    private final TableView<CreditoDAO.Fila> tabla = new TableView<>();
    private final ToggleGroup filtro = new ToggleGroup();
    private final Label resumen = new Label();
    private final Label avisosLbl = new Label();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public CreditosPanel() {
        setSpacing(12);

        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chips = new HBox(6, Chips.crear("Activos", "ACTIVOS", filtro, gris), Chips.crear("Todos", "TODOS", filtro, gris));
        chips.setAlignment(Pos.CENTER_LEFT);
        filtro.selectToggle(filtro.getToggles().get(0));
        Chips.alElegir(filtro, this::recargar);
        resumen.getStyleClass().add("texto-suave");
        avisosLbl.getStyleClass().add("texto-aviso");

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, chips, espacio, boton("Detalle", "F3", "btn-secundario", this::detalle),
                boton("Recibir pago", "F5", "btn-primario", this::pagar), boton("Contrato", "F7", "btn-secundario", this::contrato),
                boton("WhatsApp", "F8", "btn-secundario", this::recordar));
        barra.setAlignment(Pos.CENTER_LEFT);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay créditos. Se crean al cobrar una venta con \"Crédito en cuotas\"."));
        tabla.getColumns().add(Tablas.columna("Venta", 90, (CreditoDAO.Fila c) -> c.ventaNumero()));
        tabla.getColumns().add(Tablas.columna("Cliente", 170, (CreditoDAO.Fila c) -> c.cliente()));
        tabla.getColumns().add(Tablas.columna("Producto", 190, (CreditoDAO.Fila c) -> c.descripcion()));
        tabla.getColumns().add(Tablas.columna("Cuotas", 130, (CreditoDAO.Fila c) -> c.plazo() + " x " + Dinero.formato(c.cuotaValor())));
        tabla.getColumns().add(Tablas.columna("Total", 100, (CreditoDAO.Fila c) -> Dinero.formato(c.totalCredito())));
        tabla.getColumns().add(Tablas.columna("Pagado", 100, (CreditoDAO.Fila c) -> texto(c, true)));
        tabla.getColumns().add(Tablas.columna("Saldo", 100, (CreditoDAO.Fila c) -> texto(c, false)));
        var estado = Tablas.columna("Estado", 100, (CreditoDAO.Fila c) -> estadoTexto(c));
        Tablas.colorear(estado, c -> switch (estadoTexto(c)) {
            case "En mora" -> "texto-peligro";
            case "Al día", "Pagado" -> "texto-ok";
            default -> "texto-suave";
        });
        tabla.getColumns().add(estado);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                detalle();
            }
        });

        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F3", "Detalle"), Atajos.etiqueta("F4", "Activos / todos"),
                Atajos.etiqueta("F5", "Recibir pago"), Atajos.etiqueta("F7", "Contrato"), Atajos.etiqueta("F8", "Recordar por WhatsApp"),
                Atajos.etiqueta("F9", "Avisos de hoy"));
        if (usuario.esAdmin()) {
            teclas.getChildren().add(Atajos.etiqueta("F10", "Cancelar crédito"));
        }
        chips.getChildren().forEach(c -> ((javafx.scene.layout.Region) c).setMinWidth(Region.USE_PREF_SIZE));
        getChildren().addAll(barra, resumen, avisosLbl, tabla, teclas);

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

    private Button boton(String texto, String tecla, String estilo, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add(estilo);
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private String texto(CreditoDAO.Fila c, boolean pagado) {
        try {
            CreditoService.Estado e = servicio.estado(c);
            return Dinero.formato(pagado ? e.pagado() : e.saldo());
        } catch (SQLException ex) {
            return "?";
        }
    }

    private String estadoTexto(CreditoDAO.Fila c) {
        try {
            return servicio.estado(c).texto();
        } catch (SQLException e) {
            return "?";
        }
    }

    public void recargar() {
        CreditoDAO.Fila previa = tabla.getSelectionModel().getSelectedItem();
        try {
            List<CreditoDAO.Fila> lista = servicio.listar("ACTIVOS".equals(Chips.valor(filtro)));
            tabla.getItems().setAll(lista);
            if (previa != null) {
                lista.stream().filter(c -> c.id() == previa.id()).findFirst().ifPresent(c -> tabla.getSelectionModel().select(c));
            } else if (!lista.isEmpty()) {
                tabla.getSelectionModel().select(0);
            }
            BigDecimal porCobrar = BigDecimal.ZERO;
            int enMora = 0;
            for (CreditoDAO.Fila c : lista) {
                if ("ACTIVO".equals(c.estado())) {
                    CreditoService.Estado e = servicio.estado(c);
                    porCobrar = porCobrar.add(e.saldo());
                    if (e.mora().signum() > 0) {
                        enMora++;
                    }
                }
            }
            resumen.setText(lista.size() + (lista.size() == 1 ? " crédito" : " créditos") + "  ·  por cobrar " + Dinero.formato(porCobrar)
                    + (enMora > 0 ? "  ·  " + enMora + " en mora" : ""));
            int avisos = servicio.avisosPendientes().size();
            avisosLbl.setText(avisos > 0 ? "● Hay " + avisos + (avisos == 1 ? " aviso" : " avisos") + " de cuotas por mandar por WhatsApp (F9)." : "");
            avisosLbl.setManaged(avisos > 0);
            avisosLbl.setVisible(avisos > 0);
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los créditos.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F3 -> detalle();
            case F4 -> Chips.siguiente(filtro);
            case F5 -> pagar();
            case F7 -> contrato();
            case F8 -> recordar();
            case F9 -> {
                AvisosCuotasDialog.abrir();
                recargar();
            }
            case F10 -> cancelar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private CreditoDAO.Fila elegido() {
        CreditoDAO.Fila c = tabla.getSelectionModel().getSelectedItem();
        if (c == null) {
            Mensajes.info("Elige un crédito", "Muévete a la tabla con la flecha ↓ y elige uno.");
        }
        return c;
    }

    private void detalle() {
        CreditoDAO.Fila c = elegido();
        if (c != null) {
            CreditoDetalleDialog.abrir(c);
        }
    }

    private void pagar() {
        CreditoDAO.Fila c = elegido();
        if (c != null && PagoCreditoDialog.abrir(c)) {
            recargar();
        }
    }

    private void contrato() {
        CreditoDAO.Fila c = elegido();
        if (c == null) {
            return;
        }
        try {
            TicketDialog.mostrar("Contrato del crédito " + c.ventaNumero(), servicio.contrato(c));
        } catch (SQLException e) {
            Mensajes.error("No se pudo armar el contrato.\n" + e.getMessage());
        }
    }

    private void recordar() {
        CreditoDAO.Fila c = elegido();
        if (c == null) {
            return;
        }
        try {
            AvisosCuotasDialog.enviar(servicio, servicio.avisoDe(c));
            recargar();
        } catch (IllegalStateException | SQLException e) {
            Mensajes.info("Sin cuotas pendientes", e.getMessage());
        }
    }

    private void cancelar() {
        CreditoDAO.Fila c = tabla.getSelectionModel().getSelectedItem();
        if (c == null || !usuario.esAdmin() || !"ACTIVO".equals(c.estado())) {
            return;
        }
        if (!Mensajes.confirmar("Cancelar crédito", "¿Cancelar el crédito de " + c.cliente() + "?\nLo que ya pagó se devuelve aparte."
                + " La venta sigue registrada.")) {
            return;
        }
        try {
            servicio.cancelar(usuario, c);
            recargar();
        } catch (IllegalStateException | SQLException e) {
            Mensajes.error(e.getMessage());
        }
    }
}
