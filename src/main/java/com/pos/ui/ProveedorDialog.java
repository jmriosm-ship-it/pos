package com.pos.ui;

import com.pos.modelo.Proveedor;
import com.pos.servicio.ProveedorService;
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

/** Formulario para crear o editar un proveedor. Devuelve el proveedor guardado, o null si se canceló. */
public class ProveedorDialog {

    private ProveedorDialog() {
    }

    public static Proveedor abrir(Proveedor original) {
        boolean nuevo = original == null;
        ProveedorService servicio = new ProveedorService();

        TextField nombre = new TextField(nuevo ? "" : original.getNombre());
        TextField telefono = new TextField(nuevo || original.getTelefono() == null ? "" : original.getTelefono());
        TextField correo = new TextField(nuevo || original.getCorreo() == null ? "" : original.getCorreo());
        TextField direccion = new TextField(nuevo || original.getDireccion() == null ? "" : original.getDireccion());
        nombre.setPromptText("Ej: Distribuidora Tech");
        telefono.setPromptText("300 123 4567");
        correo.setPromptText("ventas@empresa.com");
        direccion.setPromptText("Calle, barrio, ciudad");

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        HBox contacto = new HBox(12, conEtiqueta("Teléfono", telefono), conEtiqueta("Correo", correo));
        VBox contenido = new VBox(10, conEtiqueta("Nombre", nombre), contacto, conEtiqueta("Dirección", direccion), error);
        contenido.setPrefWidth(520);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(nuevo ? "Nuevo proveedor" : "Editar proveedor");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(nombre::requestFocus);

        Proveedor[] resultado = {null};
        // Si hay un error se muestra aquí mismo y la ventana NO se cierra.
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                Proveedor p = new Proveedor();
                if (!nuevo) {
                    p.setId(original.getId());
                }
                p.setNombre(nombre.getText());
                p.setTelefono(telefono.getText());
                p.setCorreo(correo.getText());
                p.setDireccion(direccion.getText());
                p.setActivo(true);
                servicio.guardar(p);
                resultado[0] = p;
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

    // Un campo con su etiqueta encima. Se estira para repartirse el ancho con su vecino.
    private static VBox conEtiqueta(String texto, Node campo) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        VBox caja = new VBox(4, l, campo);
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        return caja;
    }
}
