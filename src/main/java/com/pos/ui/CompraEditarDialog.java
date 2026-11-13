package com.pos.ui;

import com.pos.modelo.Accion;
import com.pos.modelo.Compra;
import com.pos.modelo.DetalleCompra;
import com.pos.modelo.Modulo;
import com.pos.modelo.Proveedor;
import com.pos.servicio.CompraService;
import com.pos.servicio.Permisos;
import com.pos.servicio.ProveedorService;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ver o editar una compra ya registrada: se cambia el proveedor, la cantidad y el costo de cada línea,
 * o se quita una línea. Al guardar, el inventario se corrige por la diferencia.
 * Si la compra está anulada, o no tienes permiso de editar, solo se puede ver.
 */
public class CompraEditarDialog {

    private CompraEditarDialog() {
    }

    // Devuelve true si se guardaron cambios.
    public static boolean abrir(Compra compra) {
        CompraService servicio = new CompraService();
        List<DetalleCompra> lineas;
        List<Proveedor> proveedores;
        try {
            lineas = new ArrayList<>(servicio.detalles(compra.getId()));
            proveedores = new ProveedorService().listar();
        } catch (SQLException e) {
            Mensajes.error("No se pudo abrir la compra.\n" + e.getMessage());
            return false;
        }
        boolean puedeEditar = !compra.isAnulada() && Permisos.puede(Sesion.usuario(), Modulo.INVENTARIO, Accion.EDITAR);

        ComboBox<Proveedor> comboProveedor = new ComboBox<>();
        comboProveedor.getItems().setAll(proveedores);
        proveedores.stream().filter(p -> p.getId() == compra.getIdProveedor()).findFirst().ifPresent(comboProveedor::setValue);
        comboProveedor.setMaxWidth(Double.MAX_VALUE);
        comboProveedor.setDisable(!puedeEditar);

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        Label total = new Label();
        total.getStyleClass().add("card-titulo");

        GridPane rejilla = new GridPane();
        rejilla.setHgap(10);
        rejilla.setVgap(6);
        String[] cabecera = {"Producto", "Cantidad", "Costo por unidad", "Subtotal", ""};
        for (int i = 0; i < cabecera.length; i++) {
            Label l = new Label(cabecera[i]);
            l.getStyleClass().add("etiqueta-campo");
            rejilla.add(l, i, 0);
        }

        // Los campos de cada línea, para leer lo escrito al guardar.
        List<TextField> cantidades = new ArrayList<>();
        List<TextField> costos = new ArrayList<>();
        List<Label> subtotales = new ArrayList<>();
        List<Boolean> quitadas = new ArrayList<>();
        Runnable recalcular = () -> {
            BigDecimal suma = BigDecimal.ZERO;
            for (int i = 0; i < lineas.size(); i++) {
                BigDecimal sub = BigDecimal.ZERO;
                if (!quitadas.get(i)) {
                    String c = cantidades.get(i).getText();
                    int cant = c.isBlank() ? 0 : Integer.parseInt(c);
                    sub = CamposDinero.valor(costos.get(i)).multiply(BigDecimal.valueOf(cant));
                }
                subtotales.get(i).setText(Dinero.formato(sub));
                suma = suma.add(sub);
            }
            total.setText("Total: " + Dinero.formato(suma));
        };

        for (int i = 0; i < lineas.size(); i++) {
            DetalleCompra d = lineas.get(i);
            int fila = i + 1;
            Label nombre = new Label(d.getNombreProducto());
            nombre.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(nombre, Priority.ALWAYS);

            TextField cant = new TextField(String.valueOf(d.getCantidad()));
            cant.setPrefWidth(80);
            cant.setDisable(!puedeEditar);
            cant.textProperty().addListener((o, antes, ahora) -> {
                String limpio = ahora.replaceAll("[^0-9]", "");
                if (!limpio.equals(ahora)) {
                    cant.setText(limpio);
                }
            });
            TextField costo = CamposDinero.compacto();
            costo.setText(Dinero.plano(d.getCostoUnit()));
            costo.setPrefWidth(140);
            costo.setDisable(!puedeEditar);
            Label sub = new Label();
            sub.setMinWidth(100);
            Button quitar = new Button("×");
            quitar.getStyleClass().add("btn-mini");
            quitar.setFocusTraversable(false);
            quitar.setVisible(puedeEditar);

            cantidades.add(cant);
            costos.add(costo);
            subtotales.add(sub);
            quitadas.add(false);
            int indice = i;
            quitar.setOnAction(e -> {
                boolean ahoraQuitada = !quitadas.get(indice);
                quitadas.set(indice, ahoraQuitada);
                nombre.setStyle(ahoraQuitada ? "-fx-strikethrough: true; -fx-opacity: 0.5;" : "");
                cant.setDisable(ahoraQuitada || !puedeEditar);
                costo.setDisable(ahoraQuitada || !puedeEditar);
                recalcular.run();
            });
            cant.textProperty().addListener((o, a, b) -> recalcular.run());
            costo.textProperty().addListener((o, a, b) -> recalcular.run());

            rejilla.add(nombre, 0, fila);
            rejilla.add(cant, 1, fila);
            rejilla.add(costo, 2, fila);
            rejilla.add(sub, 3, fila);
            rejilla.add(quitar, 4, fila);
        }
        recalcular.run();

        ScrollPane scroll = new ScrollPane(rejilla);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(260, 60 + lineas.size() * 42));
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");

        Label estado = new Label(compra.isAnulada() ? "Esta compra está anulada: solo se puede ver."
                : puedeEditar ? "Cambia cantidades o costos; el inventario se corrige solo. La × quita una línea."
                : "No tienes permiso para editar compras: solo puedes verla.");
        estado.getStyleClass().add("texto-suave");
        estado.setWrapText(true);

        Label etProveedor = new Label("Proveedor");
        etProveedor.getStyleClass().add("etiqueta-campo");
        VBox contenido = new VBox(10, new VBox(4, etProveedor, comboProveedor), scroll, total, estado, error);
        contenido.setPrefWidth(640);
        contenido.setPadding(new Insets(4, 0, 0, 0));

        ButtonType guardar = new ButtonType("Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText((compra.isAnulada() ? "Compra anulada #" : puedeEditar ? "Editar compra #" : "Compra #") + compra.getId());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().add(puedeEditar ? ButtonType.CANCEL : ButtonType.CLOSE);
        if (puedeEditar) {
            dialogo.getDialogPane().getButtonTypes().add(guardar);
        }

        boolean[] guardado = {false};
        if (puedeEditar) {
            dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
                try {
                    Compra nueva = new Compra();
                    nueva.setId(compra.getId());
                    Proveedor p = comboProveedor.getValue();
                    if (p == null) {
                        throw new IllegalArgumentException("Elige el proveedor.");
                    }
                    nueva.setIdProveedor(p.getId());
                    for (int i = 0; i < lineas.size(); i++) {
                        if (quitadas.get(i)) {
                            continue;
                        }
                        DetalleCompra original = lineas.get(i);
                        DetalleCompra d = new DetalleCompra();
                        d.setIdProducto(original.getIdProducto());
                        d.setNombreProducto(original.getNombreProducto());
                        String c = cantidades.get(i).getText();
                        d.setCantidad(c.isBlank() ? 0 : Integer.parseInt(c));
                        d.setCostoUnit(CamposDinero.valor(costos.get(i)));
                        nueva.getDetalles().add(d);
                    }
                    servicio.editar(Sesion.usuario(), nueva);
                    guardado[0] = true;
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    error.setText(ex.getMessage());
                    e.consume();
                } catch (SQLException ex) {
                    error.setText("Error de base de datos: " + ex.getMessage());
                    e.consume();
                }
            });
        }
        Platform.runLater(() -> { if (!cantidades.isEmpty() && puedeEditar) { cantidades.get(0).requestFocus(); } });
        dialogo.showAndWait();
        return guardado[0];
    }
}
