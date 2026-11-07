package com.pos.ui;

import com.pos.servicio.ConfiguracionService;
import com.pos.servicio.ImpresoraService;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/**
 * Pestaña Negocio: datos del negocio (salen en el recibo) e impresora de recibos.
 * Teclas: F2 nombre del negocio · F4 elegir impresora · F5 ancho del papel · F7 imprimir prueba · F12 guardar.
 */
public class NegocioPanel extends VBox {

    private static final String PREDETERMINADA = "(Predeterminada de Windows)";

    private final ConfiguracionService config = new ConfiguracionService();
    private final ImpresoraService impresora = new ImpresoraService();

    private final TextField nombre = new TextField();
    private final TextField nit = new TextField();
    private final TextField telefono = new TextField();
    private final ComboBox<String> impresoras = new ComboBox<>();
    private final ToggleGroup ancho = new ToggleGroup();
    private final TextField cajaNombre = new TextField();
    private final TextField ciudad = new TextField();
    private final TextField correo = new TextField();
    private final javafx.scene.control.CheckBox pitido = new javafx.scene.control.CheckBox("Pitar al escanear con la pistola de códigos (agudo = agregado, grave = no existe)");
    private final TextField puntosPorMil = new TextField();
    private final TextField valorPunto = new TextField();
    private final Label mensaje = new Label();

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public NegocioPanel() {
        setSpacing(14);

        // Tarjeta 1: el negocio
        nombre.setPromptText("Ej: Zona Originals");
        nit.setPromptText("Opcional");
        telefono.setPromptText("Opcional");
        VBox negocio = tarjeta("Datos del negocio", "Salen arriba en cada recibo y en el menú.",
                campo("Nombre del negocio", "F2", nombre),
                campo("Nombre de esta caja (sus iniciales forman el número del recibo, ej: C1-00005)", null, cajaNombre),
                new HBox(12, crecer(campo("Ciudad (documentos legales)", null, ciudad)), crecer(campo("Correo para reclamos", null, correo))),
                new HBox(12, crecer(campo("NIT", null, nit)), crecer(campo("Teléfono", null, telefono))));

        // Tarjeta 2: la impresora
        impresoras.getItems().add(PREDETERMINADA);
        impresoras.getItems().addAll(impresora.listarImpresoras());
        impresoras.setMaxWidth(Double.MAX_VALUE);
        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox chipsAncho = new HBox(6, Chips.crear("58 mm", "58", ancho, gris), Chips.crear("80 mm", "80", ancho, gris));
        chipsAncho.setAlignment(Pos.CENTER_LEFT);
        Button prueba = new Button("Imprimir prueba");
        prueba.getStyleClass().add("btn-secundario");
        prueba.setGraphic(Atajos.tecla("F7"));
        prueba.setContentDisplay(ContentDisplay.RIGHT);
        prueba.setGraphicTextGap(8);
        prueba.setFocusTraversable(false);
        prueba.setOnAction(e -> imprimirPrueba());
        VBox tarjetaImpresora = tarjeta("Impresora de recibos", "Una térmica instalada en Windows.",
                campo("Impresora", "F4", impresoras),
                new HBox(12, campo("Ancho del papel", "F5", chipsAncho), prueba));
        ((HBox) tarjetaImpresora.getChildren().get(tarjetaImpresora.getChildren().size() - 1)).setAlignment(Pos.BOTTOM_LEFT);
        tarjetaImpresora.getChildren().add(pitido);

        // Tarjeta 3: puntos de clientes
        puntosPorMil.setPromptText("0.1");
        valorPunto.setPromptText("100");
        VBox tarjetaPuntos = tarjeta("Puntos de clientes", "El cliente gana puntos con cada compra y los usa como descuento (F10 en Ventas). 0 = sin puntos.",
                new HBox(12, crecer(campo("Puntos por cada $1.000 comprados", null, puntosPorMil)),
                        crecer(campo("Cada punto vale (pesos)", null, valorPunto))));

        // Guardar
        Button guardar = new Button("Guardar cambios");
        guardar.getStyleClass().add("btn-primario");
        guardar.setGraphic(Atajos.tecla("F12"));
        guardar.setContentDisplay(ContentDisplay.RIGHT);
        guardar.setGraphicTextGap(8);
        guardar.setFocusTraversable(false);
        guardar.setMinWidth(Region.USE_PREF_SIZE);
        guardar.setOnAction(e -> guardar());
        mensaje.getStyleClass().add("texto-suave");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(14,
                new FlowPane(14, 4, Atajos.etiqueta("F2", "Nombre"), Atajos.etiqueta("F4", "Impresora"),
                        Atajos.etiqueta("F5", "Ancho"), Atajos.etiqueta("F7", "Prueba")),
                espacio, mensaje, guardar);
        pie.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(negocio, tarjetaImpresora, tarjetaPuntos, pie);
        setMaxWidth(820);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        cargar();
        Platform.runLater(nombre::requestFocus);
    }

    private HBox crecer(Node n) {
        HBox h = new HBox(n);
        HBox.setHgrow(n, Priority.ALWAYS);
        HBox.setHgrow(h, Priority.ALWAYS);
        if (n instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        return h;
    }

    private VBox tarjeta(String titulo, String ayuda, Node... contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("linea-nombre");
        Label a = new Label(ayuda);
        a.getStyleClass().add("texto-suave");
        VBox v = new VBox(10, new VBox(2, t, a));
        v.getChildren().addAll(contenido);
        v.getStyleClass().add("card");
        return v;
    }

    private VBox campo(String titulo, String tecla, Node contenido) {
        Label l = new Label(titulo);
        l.getStyleClass().add("etiqueta-campo");
        HBox cabecera = tecla == null ? new HBox(l) : new HBox(6, l, Atajos.tecla(tecla));
        cabecera.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(4, cabecera, contenido);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private void cargar() {
        try {
            nombre.setText(config.obtener(ConfiguracionService.NEGOCIO_NOMBRE, ""));
            nit.setText(config.obtener(ConfiguracionService.NEGOCIO_NIT, ""));
            telefono.setText(config.obtener(ConfiguracionService.NEGOCIO_TELEFONO, ""));
            cajaNombre.setText(config.obtener(ConfiguracionService.CAJA_NOMBRE, "Caja 1"));
            ciudad.setText(config.obtener("negocio_ciudad", ""));
            correo.setText(config.obtener("negocio_correo", ""));
            pitido.setSelected(!"false".equals(config.obtener(ConfiguracionService.LECTOR_SONIDO, "true")));
            puntosPorMil.setText(config.obtener(ConfiguracionService.PUNTOS_POR_MIL, "0.1"));
            valorPunto.setText(config.obtener(ConfiguracionService.VALOR_PUNTO, "100"));
            String guardada = config.obtener(ConfiguracionService.IMPRESORA_NOMBRE, "");
            impresoras.getSelectionModel().select(impresoras.getItems().contains(guardada) ? guardada : PREDETERMINADA);
            String mm = config.obtener(ConfiguracionService.IMPRESORA_ANCHO_MM, "80");
            ancho.selectToggle(ancho.getToggles().get("58".equals(mm) ? 0 : 1));
        } catch (SQLException e) {
            Mensajes.error("No se pudo leer la configuración.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F2 -> {
                nombre.requestFocus();
                nombre.selectAll();
            }
            case F4 -> {
                impresoras.requestFocus();
                impresoras.show();
            }
            case F5 -> Chips.siguiente(ancho);
            case F7 -> imprimirPrueba();
            case F12 -> guardar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void guardar() {
        if (nombre.getText().isBlank()) {
            mensaje.setText("El nombre del negocio no puede quedar vacío.");
            nombre.requestFocus();
            return;
        }
        try {
            var u = Sesion.usuario();
            String elegida = impresoras.getValue();
            validarNumero(puntosPorMil.getText(), "Los puntos por cada $1.000");
            validarNumero(valorPunto.getText(), "El valor del punto");
            config.guardar(u, ConfiguracionService.PUNTOS_POR_MIL, puntosPorMil.getText().replace(',', '.'));
            config.guardar(u, ConfiguracionService.VALOR_PUNTO, valorPunto.getText().replace(',', '.'));
            config.guardar(u, ConfiguracionService.NEGOCIO_NOMBRE, nombre.getText());
            config.guardar(u, ConfiguracionService.CAJA_NOMBRE, cajaNombre.getText());
            config.guardar(u, "negocio_ciudad", ciudad.getText());
            config.guardar(u, "negocio_correo", correo.getText());
            config.guardar(u, ConfiguracionService.LECTOR_SONIDO, pitido.isSelected() ? "true" : "false");
            config.guardar(u, ConfiguracionService.NEGOCIO_NIT, nit.getText());
            config.guardar(u, ConfiguracionService.NEGOCIO_TELEFONO, telefono.getText());
            config.guardar(u, ConfiguracionService.IMPRESORA_NOMBRE, PREDETERMINADA.equals(elegida) ? "" : elegida);
            config.guardar(u, ConfiguracionService.IMPRESORA_ANCHO_MM, (String) Chips.valor(ancho));
            mensaje.setText("Guardado.");
        } catch (IllegalStateException e) {
            mensaje.setText(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo guardar.\n" + e.getMessage());
        }
    }

    private void validarNumero(String texto, String nombre) {
        try {
            if (new java.math.BigDecimal(texto.trim().replace(',', '.')).signum() < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException(nombre + " debe ser un número (0 o más).");
        }
    }

    // Guarda lo que hay en pantalla y manda un recibo corto a la impresora elegida.
    private void imprimirPrueba() {
        guardar();
        try {
            impresora.imprimir("*** PRUEBA DE IMPRESION ***\n" + nombre.getText() + "\nSi lees esto, la impresora funciona.");
            mensaje.setText("Prueba enviada a la impresora.");
        } catch (Exception e) {
            Mensajes.error("No se pudo imprimir.\n" + e.getMessage());
        }
    }
}
