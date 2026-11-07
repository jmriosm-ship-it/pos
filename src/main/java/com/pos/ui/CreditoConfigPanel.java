package com.pos.ui;

import com.pos.servicio.ConfiguracionService;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Pestaña Crédito de Configuración: las tasas y plazos que se ofrecen, la cuota inicial mínima, el tope por usura
 * y los mensajes de WhatsApp. F12 guarda.
 */
public class CreditoConfigPanel extends VBox {

    private static final String PLANTILLA = "Hola {nombre}, te escribe {negocio}. Te recordamos que la cuota {n} de tu crédito "
            + "({valor}) vence {cuando}. Saldo pendiente: {saldo}. ¡Gracias!";
    private static final String PLANTILLA_MORA = "Hola {nombre}, te escribe {negocio}. Tienes {cuotas} cuota(s) vencida(s) por {valor} "
            + "(la más vieja venció el {fecha}). Saldo pendiente: {saldo}. Por favor acércate o escríbenos para ponerte al día.";

    private final ConfiguracionService config = new ConfiguracionService();
    private final TextField tasas = new TextField();
    private final TextField plazos = new TextField();
    private final TextField plazosQuincenal = new TextField();
    private final TextField inicialMin = new TextField();
    private final TextField tasaMaxima = new TextField();
    private final TextField diasAntes = new TextField();
    private final TextArea plantilla = new TextArea();
    private final TextArea plantillaMora = new TextArea();
    private final Label mensaje = new Label();
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public CreditoConfigPanel() {
        setSpacing(14);
        setMaxWidth(820);

        VBox opciones = tarjeta("Opciones del crédito", "Lo que se ofrece al armar el plan de cuotas. Varias opciones se separan con comas.",
                new HBox(12, columna("Tasas mensuales ofrecidas (%)", tasas), columna("Tasa máxima permitida (%, vacío = sin tope)", tasaMaxima)),
                new HBox(12, columna("Cuotas mensuales", plazos), columna("Cuotas quincenales", plazosQuincenal)),
                new HBox(12, columna("Cuota inicial mínima (%)", inicialMin), columna("Avisar la cuota (días antes)", diasAntes)));

        plantilla.setPrefRowCount(3);
        plantilla.setWrapText(true);
        plantillaMora.setPrefRowCount(3);
        plantillaMora.setWrapText(true);
        VBox mensajes = tarjeta("Mensajes de WhatsApp", "Puedes usar {nombre} {negocio} {n} {cuotas} {valor} {fecha} {cuando} {saldo}. Vacío = el mensaje de siempre.",
                etiquetado("Aviso de cuota por vencer", plantilla), etiquetado("Aviso de mora", plantillaMora));

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
        HBox pie = new HBox(14, Atajos.etiqueta("F12", "Guardar"), espacio, mensaje, guardar);
        pie.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(opciones, mensajes, pie);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        cargar();
    }

    private VBox tarjeta(String titulo, String ayuda, Node... contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("linea-nombre");
        Label a = new Label(ayuda);
        a.getStyleClass().add("texto-suave");
        a.setWrapText(true);
        VBox v = new VBox(10, new VBox(2, t, a));
        v.getChildren().addAll(contenido);
        v.getStyleClass().add("card");
        return v;
    }

    private VBox columna(String titulo, TextField campo) {
        VBox v = etiquetado(titulo, campo);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }

    private VBox etiquetado(String titulo, Node campo) {
        Label l = new Label(titulo);
        l.getStyleClass().add("etiqueta-campo");
        return new VBox(4, l, campo);
    }

    private void cargar() {
        try {
            tasas.setText(config.obtener(ConfiguracionService.CREDITO_TASAS, "2.3"));
            plazos.setText(config.obtener(ConfiguracionService.CREDITO_PLAZOS, "6,12,18,24,36"));
            plazosQuincenal.setText(config.obtener(ConfiguracionService.CREDITO_PLAZOS_QUINCENAL, "24,36,48"));
            inicialMin.setText(config.obtener(ConfiguracionService.CREDITO_INICIAL_MIN, "30"));
            tasaMaxima.setText(config.obtener(ConfiguracionService.CREDITO_TASA_MAXIMA, ""));
            diasAntes.setText(config.obtener(ConfiguracionService.WA_DIAS_ANTES, "1"));
            plantilla.setText(config.obtener(ConfiguracionService.WA_PLANTILLA, PLANTILLA));
            plantillaMora.setText(config.obtener(ConfiguracionService.WA_PLANTILLA_MORA, PLANTILLA_MORA));
        } catch (SQLException e) {
            Mensajes.error("No se pudo leer la configuración.\n" + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        if (Atajos.visible(this) && e.getCode() == KeyCode.F12) {
            guardar();
            e.consume();
        }
    }

    private void guardar() {
        try {
            lista(tasas.getText(), "Las tasas", true);
            lista(plazos.getText(), "Las cuotas mensuales", false);
            lista(plazosQuincenal.getText(), "Las cuotas quincenales", false);
            numero(inicialMin.getText(), "La cuota inicial mínima", false);
            numero(tasaMaxima.getText(), "La tasa máxima", true);
            numero(diasAntes.getText(), "Los días de aviso", false);
            var u = Sesion.usuario();
            config.guardar(u, ConfiguracionService.CREDITO_TASAS, tasas.getText().replace(';', ','));
            config.guardar(u, ConfiguracionService.CREDITO_PLAZOS, plazos.getText());
            config.guardar(u, ConfiguracionService.CREDITO_PLAZOS_QUINCENAL, plazosQuincenal.getText());
            config.guardar(u, ConfiguracionService.CREDITO_INICIAL_MIN, inicialMin.getText().replace(',', '.'));
            config.guardar(u, ConfiguracionService.CREDITO_TASA_MAXIMA, tasaMaxima.getText().replace(',', '.'));
            config.guardar(u, ConfiguracionService.WA_DIAS_ANTES, diasAntes.getText());
            config.guardar(u, ConfiguracionService.WA_PLANTILLA, plantilla.getText());
            config.guardar(u, ConfiguracionService.WA_PLANTILLA_MORA, plantillaMora.getText());
            mensaje.setText("Guardado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            mensaje.setText(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo guardar.\n" + e.getMessage());
        }
    }

    // Una lista de números separados por comas (con decimales o sin ellos).
    private void lista(String texto, String nombre, boolean decimales) {
        if (texto.isBlank()) {
            throw new IllegalArgumentException(nombre + " no pueden quedar vacías.");
        }
        for (String t : texto.split("[,;\\s]+")) {
            numero(t, nombre, false, decimales);
        }
    }

    private void numero(String texto, String nombre, boolean vacioOk) {
        numero(texto, nombre, vacioOk, true);
    }

    private void numero(String texto, String nombre, boolean vacioOk, boolean decimales) {
        if (texto.isBlank() && vacioOk) {
            return;
        }
        try {
            BigDecimal n = new BigDecimal(texto.trim().replace(',', '.'));
            if (n.signum() < 0 || (!decimales && n.stripTrailingZeros().scale() > 0)) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(nombre + " debe ser un número" + (decimales ? "" : " entero") + " (0 o más).");
        }
    }
}
