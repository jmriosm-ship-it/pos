package com.pos.ui;

import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.util.List;

/** "Chips": botones redondeados de color donde solo uno puede estar elegido (filtros). */
public class Chips {

    private Chips() {
    }

    // Un chip: de color suave cuando no está elegido y de color fuerte cuando sí.
    public static ToggleButton crear(String texto, Object valor, ToggleGroup grupo, ColoresCategoria.Color color) {
        ToggleButton chip = new ToggleButton(texto);
        chip.setUserData(valor);
        chip.setToggleGroup(grupo);
        chip.getStyleClass().add("chip-cat");
        chip.setFocusTraversable(false);
        Runnable pintar = () -> chip.setStyle(chip.isSelected()
                ? "-fx-background-color: " + color.fuerte() + "; -fx-text-fill: white;"
                : "-fx-background-color: " + color.suave() + "; -fx-text-fill: " + color.texto() + ";");
        chip.selectedProperty().addListener((o, antes, ahora) -> pintar.run());
        pintar.run();
        return chip;
    }

    // Hace que siempre haya un chip elegido y avisa cuando cambia la elección.
    public static void alElegir(ToggleGroup grupo, Runnable alCambiar) {
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                antes.setSelected(true);
            } else {
                alCambiar.run();
            }
        });
    }

    // Pasa al siguiente chip (y da la vuelta al llegar al último).
    public static void siguiente(ToggleGroup grupo) {
        List<Toggle> chips = grupo.getToggles();
        if (chips.size() < 2) {
            return;
        }
        int actual = chips.indexOf(grupo.getSelectedToggle());
        grupo.selectToggle(chips.get((actual + 1) % chips.size()));
    }

    // El valor (userData) del chip elegido.
    public static Object valor(ToggleGroup grupo) {
        return grupo.getSelectedToggle() == null ? null : grupo.getSelectedToggle().getUserData();
    }
}
