package com.pos.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.function.Function;

/** Ayudas para armar tablas: una columna de texto y una columna cuyo color depende de la fila. */
public class Tablas {

    private Tablas() {
    }

    // Una columna de texto: "valor" saca de cada fila lo que se debe mostrar.
    public static <T> TableColumn<T, String> columna(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        return c;
    }

    // Pinta el texto de la columna según la fila. "estilo" devuelve una clase de color
    // ("texto-ok", "texto-aviso", "texto-peligro") o null para dejarlo normal.
    public static <T> void colorear(TableColumn<T, String> columna, Function<T, String> estilo) {
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio ? null : item);
                getStyleClass().removeAll("texto-ok", "texto-aviso", "texto-peligro", "texto-suave");
                T fila = (vacio || getTableRow() == null) ? null : getTableRow().getItem();
                String clase = fila == null ? null : estilo.apply(fila);
                if (clase != null) {
                    getStyleClass().add(clase);
                }
            }
        });
    }
}
