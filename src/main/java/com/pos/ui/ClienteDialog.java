package com.pos.ui;

import com.pos.modelo.Cliente;
import com.pos.servicio.ClienteService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/** Formulario para crear o editar un cliente. Devuelve el cliente guardado, o null si se canceló. */
public class ClienteDialog {

    private ClienteDialog() {
    }

    public static Cliente abrir(Cliente original) {
        boolean nuevo = original == null;
        ClienteService servicio = new ClienteService();

        TextField nombre = new TextField(nuevo ? "" : texto(original.getNombre()));
        TextField documento = new TextField(nuevo ? "" : texto(original.getDocumento()));
        TextField telefono = new TextField(nuevo ? "" : texto(original.getTelefono()));
        TextField correo = new TextField(nuevo ? "" : texto(original.getCorreo()));
        TextField direccion = new TextField(nuevo ? "" : texto(original.getDireccion()));
        nombre.setPromptText("Nombre completo");
        documento.setPromptText("Cédula o NIT");
        telefono.setPromptText("300 123 4567");
        correo.setPromptText("correo@ejemplo.com");
        direccion.setPromptText("Calle, barrio, ciudad");

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        HBox fila1 = new HBox(12, conEtiqueta("Documento", documento), conEtiqueta("Teléfono", telefono));
        HBox fila2 = new HBox(12, conEtiqueta("Correo", correo), conEtiqueta("Dirección", direccion));
        VBox contenido = new VBox(10, conEtiqueta("Nombre", nombre), fila1, fila2, error);
        contenido.setPrefWidth(560);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(nuevo ? "Nuevo cliente" : "Editar cliente");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(nombre::requestFocus);

        Cliente[] resultado = {null};
        // Si hay un error se muestra aquí mismo y la ventana NO se cierra.
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                Cliente c = new Cliente();
                if (!nuevo) {
                    c.setId(original.getId());
                }
                c.setNombre(nombre.getText());
                c.setDocumento(documento.getText());
                c.setTelefono(telefono.getText());
                c.setCorreo(correo.getText());
                c.setDireccion(direccion.getText());
                servicio.guardar(c);
                resultado[0] = c;
            } catch (IllegalArgumentException ex) {
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

    private static String texto(String valor) {
        return valor == null ? "" : valor;
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
