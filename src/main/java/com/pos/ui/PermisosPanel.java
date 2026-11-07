package com.pos.ui;

import com.pos.modelo.Accion;
import com.pos.modelo.Modulo;
import com.pos.modelo.Rol;
import com.pos.servicio.PermisoService;
import com.pos.servicio.Permisos;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pestaña "Permisos": se elige un rol y se ve una matriz con los módulos en las filas y, en las columnas,
 * Ver · Crear · Editar · Borrar. Cada cambio se guarda al instante.
 *  - Ver: el módulo aparece en el menú. Crear/Editar/Borrar: qué puede hacer dentro (cobrar, anular, etc.).
 *  - El administrador siempre puede todo y el Inicio lo ven todos.
 * Teclado: F2 cambia de rol · Tab pasa al siguiente interruptor · Espacio lo enciende o apaga.
 */
public class PermisosPanel extends VBox {

    private static final Accion[] ACCIONES = Accion.values();

    private final PermisoService servicio = new PermisoService();
    private final ToggleGroup chipsRol = new ToggleGroup();
    private final GridPane matriz = new GridPane();
    private final Map<Modulo, Interruptor> ver = new EnumMap<>(Modulo.class);
    private final Map<Modulo, Map<Accion, Interruptor>> acciones = new EnumMap<>(Modulo.class);
    private boolean refrescando = false;
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public PermisosPanel() {
        setSpacing(12);

        // Selector de rol
        ColoresCategoria.Color gris = new ColoresCategoria.Color("#111827", "#e5e7eb", "#111827");
        HBox roles = new HBox(6);
        roles.setAlignment(Pos.CENTER_LEFT);
        for (Rol r : Rol.values()) {
            roles.getChildren().add(Chips.crear(r.getEtiqueta(), r, chipsRol, gris));
        }
        chipsRol.selectToggle(chipsRol.getToggles().get(1));   // empieza en Cajero
        chipsRol.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                antes.setSelected(true);
            } else {
                mostrar();
            }
        });
        HBox barra = new HBox(14, new Label("Rol:"), roles, Atajos.etiqueta("F2", "Cambiar de rol"));
        barra.setAlignment(Pos.CENTER_LEFT);

        // Matriz
        matriz.setHgap(10);
        matriz.setVgap(2);
        ColumnConstraints modulosCol = new ColumnConstraints();
        modulosCol.setHgrow(Priority.ALWAYS);
        matriz.getColumnConstraints().add(modulosCol);
        for (int i = 0; i < 1 + ACCIONES.length; i++) {
            ColumnConstraints c = new ColumnConstraints(110);
            c.setHalignment(HPos.CENTER);
            matriz.getColumnConstraints().add(c);
        }
        Label tituloModulo = new Label("Módulo");
        tituloModulo.getStyleClass().add("texto-suave");
        matriz.add(tituloModulo, 0, 0);
        matriz.add(encabezado("Ver"), 1, 0);
        for (int a = 0; a < ACCIONES.length; a++) {
            matriz.add(encabezado(ACCIONES[a].getTitulo()), a + 2, 0);
        }

        int fila = 1;
        for (Modulo m : Modulo.values()) {
            VBox nombre = new VBox(0);
            Label titulo = new Label(m.getTitulo());
            nombre.getChildren().add(titulo);
            nombre.setPadding(new Insets(6, 0, 6, 0));
            matriz.add(nombre, 0, fila);

            Interruptor iVer = new Interruptor(false);
            iVer.activoProperty().addListener((o, antes, ahora) -> {
                if (!refrescando) {
                    guardarVer(m, ahora, iVer);
                }
            });
            ver.put(m, iVer);
            matriz.add(iVer, 1, fila);
            GridPane.setHalignment(iVer, HPos.CENTER);

            Map<Accion, Interruptor> delModulo = new EnumMap<>(Accion.class);
            acciones.put(m, delModulo);
            StringBuilder detalle = new StringBuilder();
            for (int a = 0; a < ACCIONES.length; a++) {
                Accion accion = ACCIONES[a];
                if (!Accion.deModulo(m).contains(accion)) {
                    Label guion = new Label("–");
                    guion.getStyleClass().add("texto-suave");
                    matriz.add(guion, a + 2, fila);
                    GridPane.setHalignment(guion, HPos.CENTER);
                    continue;
                }
                Interruptor i = new Interruptor(false);
                i.activoProperty().addListener((o, antes, ahora) -> {
                    if (!refrescando) {
                        guardarAccion(m, accion, ahora, i);
                    }
                });
                delModulo.put(accion, i);
                matriz.add(i, a + 2, fila);
                GridPane.setHalignment(i, HPos.CENTER);
                String explica = Accion.explicacion(m, accion);
                if (!explica.equals(accion.getTitulo())) {
                    detalle.append(detalle.length() > 0 ? " · " : "").append(accion.getTitulo()).append(": ").append(explica);
                }
            }
            if (detalle.length() > 0) {
                Label l = new Label(detalle.toString());
                l.getStyleClass().add("texto-suave");
                l.setStyle("-fx-font-size: 11px;");
                nombre.getChildren().add(l);
            }
            fila++;
        }

        VBox tarjeta = new VBox(matriz);
        tarjeta.getStyleClass().add("card");

        Label nota = new Label("Ver muestra el módulo en el menú; Crear, Editar y Borrar controlan lo que se puede hacer dentro. "
                + "El administrador siempre puede todo y el Inicio lo ven todos. "
                + "Los cambios se guardan al instante y se aplican la próxima vez que cada persona entre.");
        nota.getStyleClass().add("texto-suave");
        nota.setWrapText(true);
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Cambiar de rol"),
                Atajos.etiqueta("Tab", "Siguiente interruptor"), Atajos.etiqueta("Espacio", "Encender o apagar"));

        getChildren().addAll(barra, tarjeta, nota, teclas);
        mostrar();

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
    }

    private Label encabezado(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-suave");
        return l;
    }

    private Rol rolElegido() {
        return (Rol) Chips.valor(chipsRol);
    }

    // F2: pasa al siguiente rol.
    private void tecla(KeyEvent e) {
        if (e.getCode() == KeyCode.F2 && Atajos.visible(this)) {
            int n = chipsRol.getToggles().size();
            Toggle actual = chipsRol.getSelectedToggle();
            int sig = (chipsRol.getToggles().indexOf(actual) + 1) % n;
            chipsRol.selectToggle(chipsRol.getToggles().get(sig));
            e.consume();
        }
    }

    // Pone cada interruptor según lo guardado para el rol elegido.
    private void mostrar() {
        Rol rol = rolElegido();
        boolean admin = rol == Rol.ADMIN;
        refrescando = true;
        for (Modulo m : Modulo.values()) {
            boolean ve = Permisos.modulosDeRol(rol.name()).contains(m);
            Interruptor iVer = ver.get(m);
            iVer.setActivo(ve);
            iVer.setDisable(admin || m == Modulo.DASHBOARD);   // ni el admin ni el Inicio se pueden apagar
            for (Map.Entry<Accion, Interruptor> e : acciones.get(m).entrySet()) {
                Interruptor i = e.getValue();
                boolean tiene = admin || Permisos.accionesDeRol(rol.name()).contains(m.name() + ":" + e.getKey().name());
                i.setActivo(tiene);
                i.setDisable(admin || !ve);   // sin Ver no hay nada que hacer
            }
        }
        refrescando = false;
    }

    private void guardarVer(Modulo modulo, boolean permitido, Interruptor interruptor) {
        try {
            servicio.cambiar(Sesion.usuario(), rolElegido(), modulo, permitido);
            mostrar();   // activa o bloquea los interruptores de acciones de esa fila
        } catch (IllegalArgumentException | IllegalStateException e) {
            revertir(interruptor, !permitido);
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            revertir(interruptor, !permitido);
            Mensajes.error("No se pudo guardar el permiso.\n" + e.getMessage());
        }
    }

    private void guardarAccion(Modulo modulo, Accion accion, boolean permitido, Interruptor interruptor) {
        try {
            servicio.cambiarAccion(Sesion.usuario(), rolElegido(), modulo, accion, permitido);
        } catch (IllegalArgumentException | IllegalStateException e) {
            revertir(interruptor, !permitido);
            Mensajes.error(e.getMessage());
        } catch (SQLException e) {
            revertir(interruptor, !permitido);
            Mensajes.error("No se pudo guardar el permiso.\n" + e.getMessage());
        }
    }

    private void revertir(Interruptor interruptor, boolean valor) {
        refrescando = true;
        interruptor.setActivo(valor);
        refrescando = false;
    }

    // Se llama al abrir la pestaña: vuelve a leer los permisos guardados.
    public void recargar() {
        Permisos.recargar();
        mostrar();
    }
}
