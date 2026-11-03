package com.pos.ui;

import com.pos.modelo.Rol;
import com.pos.modelo.Usuario;
import com.pos.servicio.UsuarioService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/**
 * Formulario para crear o editar un usuario. Devuelve el usuario guardado, o null si se canceló.
 * Al editar solo se cambian el nombre y el rol (el usuario y la clave tienen su propio camino).
 */
public class UsuarioDialog {

    private UsuarioDialog() {
    }

    public static Usuario abrir(Usuario original) {
        boolean nuevo = original == null;
        UsuarioService servicio = new UsuarioService();

        TextField nombre = new TextField(nuevo ? "" : original.getNombre());
        nombre.setPromptText("Nombre completo");
        TextField usuario = new TextField(nuevo ? "" : original.getUsuario());
        usuario.setPromptText("Con este entra al sistema");
        usuario.setDisable(!nuevo);   // el nombre de usuario no se cambia después
        ComboBox<Rol> rol = new ComboBox<>();
        rol.getItems().setAll(Rol.values());
        rol.setMaxWidth(Double.MAX_VALUE);
        rol.setValue(nuevo ? Rol.CAJERO : Rol.porNombre(original.getRol()));
        PasswordField clave = new PasswordField();
        clave.setPromptText("Mínimo 6 caracteres");
        PasswordField repetir = new PasswordField();
        repetir.setPromptText("Repite la clave");

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        VBox contenido = new VBox(10, conEtiqueta("Nombre", nombre),
                new HBox(12, conEtiqueta("Usuario", usuario), conEtiqueta("Rol", rol)));
        if (nuevo) {
            contenido.getChildren().add(new HBox(12, conEtiqueta("Clave", clave), conEtiqueta("Repetir clave", repetir)));
        }
        contenido.getChildren().add(error);
        contenido.setPrefWidth(520);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("POS 2.0");
        dialogo.setHeaderText(nuevo ? "Nuevo usuario" : "Editar usuario");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(nombre::requestFocus);

        Usuario[] resultado = {null};
        // Si hay un error se muestra aquí mismo y la ventana NO se cierra.
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                if (nuevo) {
                    if (!clave.getText().equals(repetir.getText())) {
                        throw new IllegalArgumentException("Las dos claves no coinciden.");
                    }
                    resultado[0] = servicio.crear(Sesion.usuario(), nombre.getText(), usuario.getText(),
                            clave.getText(), rol.getValue().getId());
                } else {
                    Usuario cambios = new Usuario();
                    cambios.setId(original.getId());
                    cambios.setUsuario(original.getUsuario());
                    cambios.setNombre(nombre.getText().trim());
                    cambios.setIdRol(rol.getValue().getId());
                    if (cambios.getNombre().isEmpty()) {
                        throw new IllegalArgumentException("El nombre es obligatorio.");
                    }
                    servicio.actualizar(Sesion.usuario(), cambios);
                    resultado[0] = cambios;
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return resultado[0];
    }

    private static VBox conEtiqueta(String texto, Node campo) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        VBox caja = new VBox(4, l, campo);
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        return caja;
    }
}
