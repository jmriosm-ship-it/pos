package com.pos.ui;

import com.pos.modelo.Accion;
import com.pos.modelo.Modulo;
import com.pos.modelo.Rol;
import com.pos.modelo.Usuario;
import com.pos.servicio.Permisos;
import com.pos.servicio.UsuarioService;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;

/**
 * Pestaña "Usuarios" (diseño A): lista a la izquierda y ficha a la derecha, como Clientes y Proveedores.
 * La ficha muestra el rol, si está activo y qué módulos puede ver, con botones para editar, cambiar su
 * clave y activar o desactivar. Un usuario NUNCA se borra: se desactiva (sus ventas y cajas dependen de él).
 * Teclas: F2 buscar · F3 nuevo · F4 editar · F5 cambiar clave · F6 activar / desactivar.
 */
public class UsuariosPanel extends HBox {

    private final UsuarioService servicio = new UsuarioService();

    private final ListView<Usuario> lista = new ListView<>();
    private final TextField buscador = new TextField();
    private List<Usuario> todos = List.of();

    private final StackPane avatar = new StackPane();
    private final Label titulo = new Label();
    private final Label subtitulo = new Label();
    private final HBox pastillas = new HBox(8);
    private final FlowPane modulos = new FlowPane(6, 6);
    private final Label nota = new Label();
    private final Button editar = new Button("Editar");
    private final Button clave = new Button("Cambiar clave");
    private final Button estado = new Button("Desactivar");

    private Usuario actual;

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public UsuariosPanel() {
        setSpacing(16);
        VBox izquierda = crearColumnaIzquierda();
        VBox derecha = crearFicha();
        HBox.setHgrow(derecha, Priority.ALWAYS);
        getChildren().addAll(izquierda, derecha);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        cargar(Sesion.usuario().getId());
    }

    // ---------------------------------------------------------------- diseño

    private VBox crearColumnaIzquierda() {
        buscador.setPromptText("Buscar usuario (F2)");
        buscador.textProperty().addListener((o, antes, ahora) -> filtrar(null));
        HBox.setHgrow(buscador, Priority.ALWAYS);

        Button nuevo = new Button("", Iconos.crear(Iconos.MAS, 14));
        nuevo.getStyleClass().add("btn-primario");
        nuevo.setFocusTraversable(false);
        nuevo.setOnAction(e -> nuevoUsuario());
        HBox fila = new HBox(6, buscador, nuevo);

        FlowPane teclas = new FlowPane(12, 4, Atajos.etiqueta("F3", "Nuevo"), Atajos.etiqueta("F4", "Editar"),
                Atajos.etiqueta("F5", "Clave"), Atajos.etiqueta("F6", "Activar"));

        lista.getStyleClass().add("lista-categorias");
        lista.setPlaceholder(new Label("No hay usuarios."));
        lista.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean vacio) {
                super.updateItem(u, vacio);
                if (vacio || u == null) {
                    setGraphic(null);
                    setText(null);
                    setOpacity(1);
                    return;
                }
                Label nombre = new Label(u.getNombre());
                Label sub = new Label(u.getUsuario());
                sub.getStyleClass().addAll("texto-suave", "texto-pequeno");
                Region espacio = new Region();
                HBox.setHgrow(espacio, Priority.ALWAYS);
                HBox contenido = new HBox(8, new VBox(1, nombre, sub), espacio, pastillaRol(u.getRol()));
                contenido.setAlignment(Pos.CENTER_LEFT);
                setGraphic(contenido);
                setText(null);
                setOpacity(u.isActivo() ? 1 : 0.5);   // los desactivados se ven apagados
            }
        });
        lista.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) -> {
            if (ahora != null) {
                mostrarFicha(ahora);
            }
        });
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox columna = new VBox(8, fila, teclas, lista);
        columna.getStyleClass().add("card");
        columna.setPrefWidth(340);
        columna.setMinWidth(340);
        columna.setMaxWidth(340);
        return columna;
    }

    private VBox crearFicha() {
        avatar.setMinSize(46, 46);
        avatar.setMaxSize(46, 46);
        titulo.getStyleClass().add("titulo-detalle");
        subtitulo.getStyleClass().add("texto-suave");

        conTecla(editar, "F4", "btn-secundario", this::editar);
        conTecla(clave, "F5", "btn-secundario", this::cambiarClave);
        conTecla(estado, "F6", "btn-peligro", this::cambiarEstado);

        HBox cabecera = new HBox(12, avatar, new VBox(1, titulo, subtitulo));
        cabecera.setAlignment(Pos.CENTER_LEFT);
        HBox acciones = new HBox(8, editar, clave, estado);   // los botones van en su propia fila para que quepan

        Label puede = new Label("Qué puede ver");
        puede.getStyleClass().add("card-titulo");
        Label ayuda = new Label("Los cambia el administrador en la pestaña Permisos.");
        ayuda.getStyleClass().addAll("texto-suave", "texto-pequeno");
        nota.getStyleClass().addAll("texto-suave");

        VBox ficha = new VBox(12, cabecera, pastillas, acciones, puede, modulos, ayuda, nota);
        ficha.getStyleClass().add("card");
        return ficha;
    }

    private void conTecla(Button b, String tecla, String estilo, Runnable accion) {
        b.getStyleClass().add(estilo);
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
    }

    // Colores de cada rol (también los usa la pestaña de permisos).
    static ColoresCategoria.Color colorRol(String rol) {
        return switch (rol) {
            case "ADMIN" -> new ColoresCategoria.Color("#2563eb", "#dbeafe", "#1e40af");
            case "CAJERO" -> new ColoresCategoria.Color("#0f766e", "#ccfbf1", "#115e59");
            default -> new ColoresCategoria.Color("#7c3aed", "#ede9fe", "#5b21b6");
        };
    }

    static Label pastilla(String texto, String fondo, String letra) {
        Label l = new Label(texto);
        l.getStyleClass().add("pill");
        l.setStyle("-fx-background-color: " + fondo + "; -fx-text-fill: " + letra + ";");
        return l;
    }

    static Label pastillaRol(String rol) {
        Rol r = Rol.porNombre(rol);
        ColoresCategoria.Color c = colorRol(rol);
        return pastilla(r == null ? rol : r.getEtiqueta(), c.suave(), c.texto());
    }

    // ---------------------------------------------------------------- datos

    // Vuelve a leer los usuarios y deja elegido el de ese id (o el que ya estaba, o el primero).
    private void cargar(Integer idASeleccionar) {
        try {
            todos = servicio.listar();
            filtrar(idASeleccionar);
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar los usuarios.\n" + e.getMessage());
        }
    }

    private void filtrar(Integer idASeleccionar) {
        String texto = buscador.getText() == null ? "" : buscador.getText().trim().toLowerCase();
        List<Usuario> visibles = todos.stream()
                .filter(u -> u.getNombre().toLowerCase().contains(texto) || u.getUsuario().toLowerCase().contains(texto))
                .toList();
        lista.getItems().setAll(visibles);

        Integer idActual = idASeleccionar != null ? idASeleccionar : (actual == null ? null : actual.getId());
        Usuario elegido = visibles.stream().filter(u -> idActual != null && u.getId() == idActual).findFirst()
                .orElse(visibles.isEmpty() ? null : visibles.get(0));
        if (elegido == null) {
            mostrarFicha(null);
        } else {
            lista.getSelectionModel().select(elegido);
            mostrarFicha(elegido);
        }
    }

    private void mostrarFicha(Usuario u) {
        actual = u;
        boolean hay = u != null;
        editar.setDisable(!hay);
        clave.setDisable(!hay);
        estado.setDisable(!hay);
        modulos.getChildren().clear();
        pastillas.getChildren().clear();
        avatar.getChildren().clear();
        if (!hay) {
            titulo.setText("Sin usuarios");
            subtitulo.setText("");
            nota.setText("");
            return;
        }
        ColoresCategoria.Color c = colorRol(u.getRol());
        Label iniciales = new Label(iniciales(u.getNombre()));
        iniciales.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");
        avatar.setStyle("-fx-background-color: " + c.fuerte() + "; -fx-background-radius: 23;");
        avatar.getChildren().add(iniciales);

        titulo.setText(u.getNombre());
        subtitulo.setText(u.getUsuario());
        pastillas.getChildren().add(pastillaRol(u.getRol()));
        pastillas.getChildren().add(u.isActivo() ? pastilla("Activo", "#dcfce7", "#166534") : pastilla("Desactivado", "#f3f4f6", "#6b7280"));

        for (Modulo m : Modulo.values()) {
            if (Permisos.puedeVer(u, m)) {
                modulos.getChildren().add(pastilla(m.getTitulo(), "#e8efff", "#1d4ed8"));
            }
        }

        boolean soyYo = u.getId() == Sesion.usuario().getId();
        estado.setText(u.isActivo() ? "Desactivar" : "Activar");
        estado.setDisable(soyYo);   // nadie puede desactivarse a sí mismo
        nota.setText(soyYo ? "Este eres tú: no puedes desactivarte ni quitarte el rol de administrador." : "");
    }

    private String iniciales(String nombre) {
        String[] partes = nombre.trim().split("\\s+");
        String a = partes[0].substring(0, 1);
        String b = partes.length > 1 ? partes[1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    // ---------------------------------------------------------------- acciones

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F2 -> {
                buscador.requestFocus();
                buscador.selectAll();
            }
            case F3 -> nuevoUsuario();
            case F4 -> editar();
            case F5 -> cambiarClave();
            case F6 -> cambiarEstado();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void nuevoUsuario() {
        if (!Seguridad.permitido(Modulo.USUARIOS, Accion.CREAR, "crear usuarios")) {
            return;
        }
        Usuario creado = UsuarioDialog.abrir(null);
        if (creado != null) {
            buscador.clear();
            cargar(creado.getId());
        }
    }

    private void editar() {
        if (!Seguridad.permitido(Modulo.USUARIOS, Accion.EDITAR, "editar usuarios")) {
            return;
        }
        if (actual == null) {
            return;
        }
        Usuario editado = UsuarioDialog.abrir(actual);
        if (editado != null) {
            cargar(editado.getId());
        }
    }

    private void cambiarClave() {
        if (actual == null) {
            return;
        }
        boolean soyYo = actual.getId() == Sesion.usuario().getId();
        boolean cambiada = soyYo ? ClaveDialog.cambiarMia() : ClaveDialog.restablecer(actual);
        if (cambiada) {
            Mensajes.info("Clave cambiada", soyYo ? "Tu clave quedó cambiada."
                    : "La clave de " + actual.getNombre() + " quedó cambiada. Díselo para que entre con la nueva.");
        }
    }

    private void cambiarEstado() {
        if (actual == null || actual.getId() == Sesion.usuario().getId()) {
            return;
        }
        boolean desactivar = actual.isActivo();
        if (desactivar && !Mensajes.confirmar("Desactivar usuario",
                actual.getNombre() + " ya no podrá entrar al sistema.\nSus ventas y cajas se conservan.")) {
            return;
        }
        try {
            servicio.cambiarActivo(Sesion.usuario(), actual, !desactivar);
            cargar(actual.getId());
        } catch (IllegalStateException e) {
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            Mensajes.error("No se pudo cambiar el estado.\n" + e.getMessage());
        }
    }
}
