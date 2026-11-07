package com.pos.ui;

import com.pos.modelo.Caja;
import com.pos.modelo.Modulo;
import com.pos.modelo.Usuario;
import com.pos.servicio.CajaService;
import com.pos.servicio.ConfiguracionService;
import com.pos.servicio.Permisos;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Módulo Ventas en pantalla completa (sin menú lateral): arriba la barra de la caja, y debajo la venta.
 * Movimientos y Cerrar caja abren la pantalla de la caja; con Esc se vuelve a la venta.
 * Si la caja está cerrada, se muestra primero la caja para abrirla y luego se pasa sola a vender.
 * Teclas: F7 movimientos de caja · F8 cobrar deuda (Clientes) · F9 cerrar caja · Ctrl+B mostrar el menú.
 */
public class VentasCajaView extends VBox {

    private final Usuario usuario = Sesion.usuario();
    private final CajaService cajaService = new CajaService();
    private final Runnable alCambiarCajaExterno;
    private final Runnable alternarMenu;
    private final Consumer<Modulo> ir;

    private final HBox barra = new HBox(10);
    private final StackPane contenido = new StackPane();
    private boolean enCaja = false;
    private boolean enHistorial = false;
    private final boolean hayCaja;
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public VentasCajaView(Runnable alCambiarCaja, boolean abrirCaja, Runnable alternarMenu, Consumer<Modulo> ir) {
        this.alCambiarCajaExterno = alCambiarCaja;
        this.alternarMenu = alternarMenu;
        this.ir = ir;
        this.hayCaja = Permisos.puedeVer(usuario, Modulo.CAJA);
        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));

        barra.getStyleClass().add("card");
        barra.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(contenido, Priority.ALWAYS);
        getChildren().addAll(barra, contenido);

        // Sin caja abierta (o si se pidió) se muestra primero la caja.
        if (hayCaja && (abrirCaja || cajaAbierta() == null)) {
            mostrarCaja();
        } else {
            mostrarVenta();
        }

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
    }

    private Caja cajaAbierta() {
        try {
            return cajaService.cajaAbierta(usuario);
        } catch (SQLException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- pantallas

    private void mostrarHistorial() {
        enCaja = false;
        enHistorial = true;
        Button volver = new Button("←  Volver a la venta");
        volver.getStyleClass().add("btn-secundario");
        volver.setGraphic(Atajos.tecla("Esc"));
        volver.setContentDisplay(ContentDisplay.RIGHT);
        volver.setGraphicTextGap(10);
        volver.setFocusTraversable(false);
        volver.setOnAction(e -> mostrarVenta());
        HistorialVentasView historial = new HistorialVentasView();
        VBox pantalla = new VBox(10, volver, historial);
        VBox.setVgrow(historial, Priority.ALWAYS);
        contenido.getChildren().setAll(pantalla);
        pintarBarra();
    }

    private void mostrarVenta() {
        enCaja = false;
        enHistorial = false;
        contenido.getChildren().setAll(new VentasView(this::cajaCambio));
        pintarBarra();
    }

    private void mostrarCaja() {
        enCaja = true;
        enHistorial = false;
        CajaView caja = new CajaView(this::cajaCambio);
        VBox pantalla = new VBox(10);
        if (cajaAbierta() != null) {
            Button volver = new Button("←  Volver a la venta");
            volver.getStyleClass().add("btn-secundario");
            volver.setGraphic(Atajos.tecla("Esc"));
            volver.setContentDisplay(ContentDisplay.RIGHT);
            volver.setGraphicTextGap(10);
            volver.setFocusTraversable(false);
            volver.setOnAction(e -> mostrarVenta());
            pantalla.getChildren().add(volver);
        }
        pantalla.getChildren().add(caja);
        VBox.setVgrow(caja, Priority.ALWAYS);
        contenido.getChildren().setAll(pantalla);
        pintarBarra();
    }

    // La caja se abrió o se cerró: se avisa al resto y, si quedó abierta, se pasa a vender.
    private void cajaCambio() {
        alCambiarCajaExterno.run();
        if (cajaAbierta() != null) {
            mostrarVenta();
        } else {
            pintarBarra();
        }
    }

    // ---------------------------------------------------------------- barra de la caja

    private void pintarBarra() {
        barra.getChildren().clear();
        Caja caja = cajaAbierta();
        Button menu = boton("☰  Menú", "Ctrl+B", "btn-secundario", alternarMenu);
        Label estado = new Label(caja == null ? "CAJA CERRADA" : "CAJA ABIERTA");
        estado.getStyleClass().addAll("chip", caja == null ? "chip-off" : "chip-ok");
        String nombre = "Caja 1";
        try {
            nombre = new ConfiguracionService().obtener(ConfiguracionService.CAJA_NOMBRE, "Caja 1");
        } catch (SQLException e) {
            // se queda el nombre por defecto
        }
        Label lblNombre = new Label(nombre);
        lblNombre.getStyleClass().add("linea-nombre");
        barra.getChildren().addAll(menu, estado, lblNombre);
        if (caja != null) {
            Label desde = new Label("desde " + caja.getAbiertoEn().format(DateTimeFormatter.ofPattern("HH:mm"))
                    + "  ·  inicio " + Dinero.formato(caja.getMontoInicial()));
            desde.getStyleClass().add("texto-suave");
            barra.getChildren().add(desde);
        }
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        barra.getChildren().add(espacio);
        if (caja != null && hayCaja) {
            barra.getChildren().add(boton("Historial", "F11", "btn-secundario", this::mostrarHistorial));
            barra.getChildren().add(boton("Movimientos", "F7", "btn-secundario", this::mostrarCaja));
            if (Permisos.puedeVer(usuario, Modulo.CLIENTES)) {
                barra.getChildren().add(boton("Cobrar deuda", "F8", "btn-secundario", () -> ir.accept(Modulo.CLIENTES)));
            }
            barra.getChildren().add(boton("Cerrar caja", "F9", "btn-peligro", this::mostrarCaja));
        }
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

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F11 -> {
                if (cajaAbierta() != null) {
                    mostrarHistorial();
                } else {
                    return;
                }
            }
            case F7, F9 -> {
                if (enHistorial) {
                    return;   // en el historial, F7 a F9 son del historial (recibo, devolver, anular)
                }
                if (hayCaja && cajaAbierta() != null) {
                    mostrarCaja();
                } else {
                    return;
                }
            }
            case F8 -> {
                if (enHistorial) {
                    return;
                }
                if (Permisos.puedeVer(usuario, Modulo.CLIENTES) && cajaAbierta() != null) {
                    ir.accept(Modulo.CLIENTES);
                } else {
                    return;
                }
            }
            case ESCAPE -> {
                if ((enCaja || enHistorial) && cajaAbierta() != null) {
                    mostrarVenta();
                } else {
                    return;
                }
            }
            default -> {
                return;
            }
        }
        e.consume();
    }
}
