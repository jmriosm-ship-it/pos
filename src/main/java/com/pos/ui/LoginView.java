package com.pos.ui;

import com.pos.modelo.Usuario;
import com.pos.servicio.AutenticacionService;
import com.pos.servicio.ConfiguracionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.prefs.Preferences;

/**
 * Pantalla de entrada (diseño B): a la izquierda la marca del negocio con tres ventajas,
 * a la derecha el formulario con usuario y clave. Si son correctos, avisa con "alEntrar".
 */
public class LoginView extends HBox {

    private static final String PREF_USUARIO = "ultimo_usuario";

    private final AutenticacionService auth = new AutenticacionService();
    private final Preferences preferencias = Preferences.userNodeForPackage(LoginView.class);

    private final TextField campoUsuario = new TextField();
    private final PasswordField campoClave = new PasswordField();
    private final TextField campoClaveVisible = new TextField();   // el mismo texto, pero sin puntos
    private final CheckBox recordar = new CheckBox("Recordar usuario");
    private final Label error = new Label();

    public LoginView(Runnable alEntrar) {
        getStyleClass().add("login");

        VBox izquierda = crearPanelMarca();
        VBox derecha = crearPanelFormulario(alEntrar);
        // La izquierda ocupa 42 % del ancho; la derecha, el resto.
        izquierda.prefWidthProperty().bind(widthProperty().multiply(0.42));
        HBox.setHgrow(derecha, Priority.ALWAYS);
        getChildren().addAll(izquierda, derecha);

        // Si había un usuario recordado, se rellena y el cursor va directo a la clave.
        String ultimo = preferencias.get(PREF_USUARIO, "");
        if (!ultimo.isEmpty()) {
            campoUsuario.setText(ultimo);
            recordar.setSelected(true);
            javafx.application.Platform.runLater(campoClave::requestFocus);
        } else {
            javafx.application.Platform.runLater(campoUsuario::requestFocus);
        }
    }

    // ---------------------------------------------------------------- izquierda

    private VBox crearPanelMarca() {
        String negocio = nombreNegocio();

        Label titulo = new Label("POS 2.0");
        titulo.getStyleClass().add("login-marca-titulo");
        Label sub = new Label(negocio);
        sub.getStyleClass().add("login-marca-sub");

        VBox panel = new VBox(18, Logo.crear(54, negocio), new VBox(2, titulo, sub),
                ventaja("Ventas rápidas"), ventaja("Inventario al día"), ventaja("Caja sin errores"));
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.getStyleClass().add("login-izq");
        // Un poco más de aire entre el nombre y la lista de ventajas.
        VBox.setMargin(panel.getChildren().get(1), new Insets(0, 0, 10, 0));
        return panel;
    }

    private HBox ventaja(String texto) {
        HBox fila = new HBox(10, Iconos.crear(Iconos.CHECK, 16), new Label(texto));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("login-ventaja");
        return fila;
    }

    private String nombreNegocio() {
        try {
            return new ConfiguracionService().nombreNegocio();
        } catch (SQLException e) {
            return "Mi Negocio";
        }
    }

    // ---------------------------------------------------------------- derecha

    private VBox crearPanelFormulario(Runnable alEntrar) {
        Label titulo = new Label("Iniciar sesión");
        titulo.getStyleClass().add("login-titulo");

        campoUsuario.setPromptText("Tu usuario");
        campoClave.setPromptText("Tu clave");
        campoClaveVisible.setPromptText("Tu clave");

        Button entrar = new Button("Entrar");
        entrar.getStyleClass().addAll("btn-primario", "btn-grande");
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setDefaultButton(true);   // se activa con Enter
        entrar.setOnAction(e -> intentar(alEntrar));

        Button olvide = new Button("¿Olvidaste tu clave?");
        olvide.getStyleClass().add("enlace");
        olvide.setOnAction(e -> Mensajes.info("Recuperar tu clave",
                "Pídele al administrador que la restablezca desde el módulo \"Usuarios y permisos\"."));
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox opciones = new HBox(recordar, espacio, olvide);
        opciones.setAlignment(Pos.CENTER_LEFT);
        recordar.getStyleClass().add("texto-suave");

        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        VBox formulario = new VBox(8, titulo, new Label(), etiqueta("Usuario"), campoUsuario,
                etiqueta("Clave"), campoClaveConOjo(), opciones, error, entrar);
        formulario.setMaxWidth(360);
        formulario.setMaxHeight(Region.USE_PREF_SIZE);

        VBox derecha = new VBox(formulario);
        derecha.setAlignment(Pos.CENTER);
        derecha.getStyleClass().add("login-der");
        return derecha;
    }

    // La clave con un ojito a la derecha para verla u ocultarla.
    private StackPane campoClaveConOjo() {
        campoClave.getStyleClass().add("con-ojo");
        campoClaveVisible.getStyleClass().add("con-ojo");
        // Los dos campos comparten el texto: lo que escribas en uno aparece en el otro.
        campoClaveVisible.textProperty().bindBidirectional(campoClave.textProperty());
        campoClaveVisible.setVisible(false);
        campoClaveVisible.setManaged(false);

        Button ojo = new Button();
        ojo.setGraphic(Iconos.crear(Iconos.OJO, 18));
        ojo.getStyleClass().add("ojo-btn");
        ojo.setFocusTraversable(false);
        ojo.setOnAction(e -> {
            boolean mostrar = !campoClaveVisible.isVisible();
            campoClaveVisible.setVisible(mostrar);
            campoClaveVisible.setManaged(mostrar);
            campoClave.setVisible(!mostrar);
            campoClave.setManaged(!mostrar);
            ojo.setGraphic(Iconos.crear(mostrar ? Iconos.OJO_TACHADO : Iconos.OJO, 18));
        });
        StackPane.setAlignment(ojo, Pos.CENTER_RIGHT);
        StackPane.setMargin(ojo, new Insets(0, 6, 0, 0));
        return new StackPane(campoClave, campoClaveVisible, ojo);
    }

    private void intentar(Runnable alEntrar) {
        error.setText("");
        try {
            Usuario u = auth.login(campoUsuario.getText().trim(), campoClave.getText());
            if (u == null) {
                error.setText("Usuario o clave incorrectos.");
                campoClave.clear();
                return;
            }
            if (recordar.isSelected()) {
                preferencias.put(PREF_USUARIO, u.getUsuario());
            } else {
                preferencias.remove(PREF_USUARIO);
            }
            Sesion.iniciar(u);
            alEntrar.run();
        } catch (SQLException e) {
            error.setText("No se pudo conectar con la base de datos.");
        }
    }

    private Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        return l;
    }
}
