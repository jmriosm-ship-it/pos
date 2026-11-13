package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.servicio.AutenticacionService;
import com.pos.servicio.ConfiguracionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * La primera vez que se abre el sistema (todavía no hay ningún usuario): primero se leen y aceptan los términos y la
 * política de privacidad y después el dueño crea su usuario y su clave. No hay usuario ni clave de fábrica.
 */
public class PrimerUsoView extends VBox {

    private static final String VERSION_LEGAL = "1";

    private final Consumer<Usuario> alTerminar;

    public PrimerUsoView(Consumer<Usuario> alTerminar) {
        this.alTerminar = alTerminar;
        getStyleClass().add("app-bg");
        setAlignment(Pos.CENTER);
        setPadding(new Insets(24));
        pasoLegal();
    }

    private VBox tarjeta(double ancho, javafx.scene.Node... contenido) {
        VBox t = new VBox(12, contenido);
        t.getStyleClass().add("card");
        t.setMaxWidth(ancho);
        t.setPrefWidth(ancho);
        t.setMaxHeight(Region.USE_PREF_SIZE);
        return t;
    }

    private Label titulo(String texto) {
        Label l = new Label(texto);
        l.setStyle("-fx-font-family: 'Georgia'; -fx-font-size: 24px;");
        return l;
    }

    // ---------------------------------------------------------------- paso 1: términos y privacidad

    private void pasoLegal() {
        String nombreNegocio;
        TextArea terminos = new TextArea();
        TextArea privacidad = new TextArea();
        try {
            nombreNegocio = new ConfiguracionService().nombreNegocio();
            terminos.setText(AyudaTextos.completar(AyudaTextos.terminos()));
            privacidad.setText(AyudaTextos.completar(AyudaTextos.privacidad()));
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los documentos.\n" + e.getMessage());
            return;
        }
        for (TextArea a : new TextArea[]{terminos, privacidad}) {
            a.setEditable(false);
            a.setWrapText(true);
            a.setStyle("-fx-font-size: 13px;");
        }
        TabPane documentos = new TabPane(new Tab("Términos y condiciones", terminos), new Tab("Política de privacidad", privacidad));
        documentos.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        documentos.setPrefHeight(340);

        CheckBox acepto = new CheckBox("He leído y acepto los términos y condiciones y la política de privacidad");
        Button seguir = new Button("Continuar");
        seguir.getStyleClass().addAll("btn-primario", "btn-grande");
        seguir.setMaxWidth(Double.MAX_VALUE);
        seguir.setDefaultButton(true);
        seguir.disableProperty().bind(acepto.selectedProperty().not());
        seguir.setOnAction(e -> pasoUsuario());

        Label ayuda = new Label("Marca la casilla con Espacio y pulsa Enter para continuar.");
        ayuda.getStyleClass().add("texto-suave");
        getChildren().setAll(tarjeta(760, titulo("Bienvenido a " + Marca.PRODUCTO),
                new Label(nombreNegocio + " · Antes de empezar, lee y acepta estos documentos."),
                documentos, acepto, seguir, ayuda));
        Platform.runLater(acepto::requestFocus);
    }

    // ---------------------------------------------------------------- paso 2: el usuario del dueño

    private void pasoUsuario() {
        TextField nombre = new TextField();
        nombre.setPromptText("Tu nombre completo");
        TextField usuario = new TextField();
        usuario.setPromptText("Con este usuario vas a entrar (ej: julian)");
        PasswordField clave = new PasswordField();
        clave.setPromptText("Mínimo 6 caracteres");
        PasswordField repetir = new PasswordField();
        repetir.setPromptText("Repite la clave");
        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        Button crear = new Button("Crear mi usuario y entrar");
        crear.getStyleClass().addAll("btn-primario", "btn-grande");
        crear.setMaxWidth(Double.MAX_VALUE);
        crear.setDefaultButton(true);
        crear.setOnAction(e -> {
            error.setText("");
            if (!clave.getText().equals(repetir.getText())) {
                error.setText("Las dos claves no coinciden.");
                return;
            }
            try {
                Usuario u = new AutenticacionService().crearPrimerAdmin(nombre.getText(), usuario.getText(), clave.getText());
                // Queda constancia de que el dueño aceptó los documentos.
                ConfiguracionService cfg = new ConfiguracionService();
                cfg.guardar(u, "legal_version", VERSION_LEGAL);
                cfg.guardar(u, "legal_aceptado_por", u.getNombre());
                cfg.guardar(u, "legal_aceptado_fecha", LocalDateTime.now().toString());
                alTerminar.accept(u);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
            } catch (SQLException ex) {
                error.setText("No se pudo crear el usuario: " + ex.getMessage());
            }
        });

        Label aviso = new Label("Este usuario será el administrador: puede crear los de sus trabajadores, ver los reportes y cambiar la configuración. "
                + "Anota bien tu clave: nadie más puede ver cuál es.");
        aviso.getStyleClass().add("texto-suave");
        aviso.setWrapText(true);
        getChildren().setAll(tarjeta(480, titulo("Crea tu usuario"), aviso,
                etiqueta("Nombre"), nombre, etiqueta("Usuario"), usuario, etiqueta("Clave"), clave,
                etiqueta("Repetir clave"), repetir, error, crear));
        Platform.runLater(nombre::requestFocus);
    }

    private Label etiqueta(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
