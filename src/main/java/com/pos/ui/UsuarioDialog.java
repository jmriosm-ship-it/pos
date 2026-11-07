package com.pos.ui;

import com.pos.modelo.Modulo;
import com.pos.modelo.Rol;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;
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

        // Lo propio de cada trabajador: cupo, pago por día, permisos y pantallas extra.
        TextField cupo = CamposDinero.compacto();
        TextField pagoDia = CamposDinero.compacto();
        if (!nuevo) {
            cupo.setText(original.getCupo().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
            pagoDia.setText(original.getPagoDia().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        }
        TextField descMax = new TextField(nuevo ? "100" : String.valueOf(original.getDescuentoMax()));
        descMax.setPromptText("100");
        CheckBox puedeAnular = new CheckBox("Puede anular ventas y hacer devoluciones");
        puedeAnular.setSelected(!nuevo && original.isPuedeAnular());
        CheckBox puedeEditar = new CheckBox("Puede crear y editar productos");
        puedeEditar.setSelected(nuevo || original.isPuedeEditarProductos());
        FlowPane extras = new FlowPane(14, 6);
        java.util.Map<Modulo, CheckBox> cajas = new java.util.LinkedHashMap<>();
        for (Modulo m : Modulo.values()) {
            if (m == Modulo.DASHBOARD || m == Modulo.CAJA) {
                continue;
            }
            CheckBox c = new CheckBox(m.getTitulo());
            c.setSelected(!nuevo && original.getModulosExtra().contains(m));
            cajas.put(m, c);
            extras.getChildren().add(c);
        }
        Label etExtras = new Label("Pantallas extra (además de las de su rol)");
        etExtras.getStyleClass().add("etiqueta-campo");

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);

        VBox contenido = new VBox(10, conEtiqueta("Nombre", nombre),
                new HBox(12, conEtiqueta("Usuario", usuario), conEtiqueta("Rol", rol)));
        if (nuevo) {
            contenido.getChildren().add(new HBox(12, conEtiqueta("Clave", clave), conEtiqueta("Repetir clave", repetir)));
        }
        contenido.getChildren().addAll(new HBox(12, conEtiqueta("Cupo (lo que puede sacar del local)", cupo),
                conEtiqueta("Pago por día", pagoDia),
                conEtiqueta("Rebaja máxima al cobrar (%)", descMax)), puedeAnular, puedeEditar, etExtras, extras, error);
        contenido.setPrefWidth(560);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(nuevo ? "Nuevo usuario" : "Editar usuario");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        Platform.runLater(nombre::requestFocus);

        Usuario[] resultado = {null};
        // Si hay un error se muestra aquí mismo y la ventana NO se cierra.
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                Usuario creado = null;
                if (nuevo) {
                    if (!clave.getText().equals(repetir.getText())) {
                        throw new IllegalArgumentException("Las dos claves no coinciden.");
                    }
                    creado = servicio.crear(Sesion.usuario(), nombre.getText(), usuario.getText(),
                            clave.getText(), rol.getValue().getId());
                }
                Usuario cambios = new Usuario();
                cambios.setId(nuevo ? creado.getId() : original.getId());
                cambios.setUsuario(nuevo ? creado.getUsuario() : original.getUsuario());
                cambios.setNombre(nombre.getText().trim());
                cambios.setIdRol(rol.getValue().getId());
                cambios.setCupo(CamposDinero.valor(cupo));
                cambios.setPagoDia(CamposDinero.valor(pagoDia));
                cambios.setPuedeAnular(puedeAnular.isSelected());
                try {
                    cambios.setDescuentoMax(Integer.parseInt(descMax.getText().trim()));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("La rebaja máxima debe ser un número entre 0 y 100.");
                }
                cambios.setPuedeEditarProductos(puedeEditar.isSelected());
                java.util.Set<Modulo> elegidos = java.util.EnumSet.noneOf(Modulo.class);
                cajas.forEach((m, c) -> {
                    if (c.isSelected()) {
                        elegidos.add(m);
                    }
                });
                cambios.setModulosExtra(elegidos);
                if (cambios.getNombre().isEmpty()) {
                    throw new IllegalArgumentException("El nombre es obligatorio.");
                }
                servicio.actualizar(Sesion.usuario(), cambios);
                resultado[0] = nuevo ? creado : cambios;
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
