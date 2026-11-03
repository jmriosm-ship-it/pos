package com.pos.ui;

import com.pos.modelo.Modulo;
import com.pos.modelo.Rol;
import com.pos.servicio.PermisoService;
import com.pos.servicio.Permisos;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pestaña "Permisos" (diseño B): una matriz de interruptores, módulos en las filas y roles en las columnas.
 * Cada cambio se guarda al instante. El administrador siempre ve todo y el Inicio lo ven todos.
 * Se maneja con teclado: Tab pasa de un interruptor al siguiente y Espacio lo enciende o apaga.
 */
public class PermisosPanel extends VBox {

    private final PermisoService servicio = new PermisoService();
    // Los interruptores por rol y módulo, para poder refrescarlos.
    private final Map<Rol, Map<Modulo, Interruptor>> interruptores = new EnumMap<>(Rol.class);
    private boolean refrescando = false;

    public PermisosPanel() {
        setSpacing(12);

        GridPane matriz = new GridPane();
        matriz.setHgap(10);
        matriz.setVgap(2);
        ColumnConstraints modulosCol = new ColumnConstraints();
        modulosCol.setHgrow(Priority.ALWAYS);
        matriz.getColumnConstraints().add(modulosCol);
        for (int i = 0; i < Rol.values().length; i++) {
            ColumnConstraints c = new ColumnConstraints(150);
            c.setHalignment(HPos.CENTER);
            matriz.getColumnConstraints().add(c);
        }

        // Encabezado
        Label tituloModulo = new Label("Módulo");
        tituloModulo.getStyleClass().add("texto-suave");
        matriz.add(tituloModulo, 0, 0);
        Rol[] roles = Rol.values();
        for (int c = 0; c < roles.length; c++) {
            matriz.add(UsuariosPanel.pastillaRol(roles[c].name()), c + 1, 0);
            interruptores.put(roles[c], new EnumMap<>(Modulo.class));
        }

        // Una fila por módulo
        int fila = 1;
        for (Modulo m : Modulo.values()) {
            Label nombre = new Label(m.getTitulo());
            nombre.setPadding(new Insets(8, 0, 8, 0));
            matriz.add(nombre, 0, fila);
            for (int c = 0; c < roles.length; c++) {
                Rol rol = roles[c];
                Interruptor i = new Interruptor(Permisos.modulosDeRol(rol.name()).contains(m));
                // Ni el administrador ni el Inicio se pueden apagar: se ven apagados de color y bloqueados.
                i.setDisable(rol == Rol.ADMIN || m == Modulo.DASHBOARD);
                i.activoProperty().addListener((o, antes, ahora) -> {
                    if (!refrescando) {
                        guardar(rol, m, ahora, i);
                    }
                });
                interruptores.get(rol).put(m, i);
                matriz.add(i, c + 1, fila);
                GridPane.setHalignment(i, HPos.CENTER);
            }
            fila++;
        }

        VBox tarjeta = new VBox(matriz);
        tarjeta.getStyleClass().add("card");

        Label nota = new Label("El administrador siempre ve todo, para que nadie se pueda dejar sin acceso. "
                + "Los cambios se guardan al instante y se aplican la próxima vez que cada persona entre.");
        nota.getStyleClass().add("texto-suave");
        nota.setWrapText(true);
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("Tab", "Siguiente interruptor"),
                Atajos.etiqueta("Espacio", "Encender o apagar"));

        getChildren().addAll(tarjeta, nota, teclas);
    }

    // Guarda el cambio; si falla, deja el interruptor como estaba y avisa.
    private void guardar(Rol rol, Modulo modulo, boolean permitido, Interruptor interruptor) {
        try {
            servicio.cambiar(Sesion.usuario(), rol, modulo, permitido);
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
        refrescando = true;
        for (Rol rol : Rol.values()) {
            for (Modulo m : Modulo.values()) {
                interruptores.get(rol).get(m).setActivo(Permisos.modulosDeRol(rol.name()).contains(m));
            }
        }
        refrescando = false;
    }
}
