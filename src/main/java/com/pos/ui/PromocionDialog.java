package com.pos.ui;

import com.pos.dao.CategoriaDAO;
import com.pos.dao.ProductoDAO;
import com.pos.modelo.Categoria;
import com.pos.modelo.Producto;
import com.pos.modelo.Promocion;
import com.pos.servicio.PromocionService;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/** Formulario para crear o editar una promoción. Devuelve true si se guardó. */
public class PromocionDialog {

    private static final String[] ALCANCES = {"Toda la tienda", "Una categoría", "Un producto"};
    private static final String[] TIPOS = {"Porcentaje de descuento", "Lleva N, paga M (2x1, 3x2...)"};

    private PromocionDialog() {
    }

    public static boolean abrir(Promocion original) {
        boolean nueva = original == null;
        PromocionService servicio = new PromocionService();

        TextField nombre = new TextField();
        nombre.setPromptText("Ej: 2x1 en colonias de 100 ml");
        ComboBox<String> alcance = new ComboBox<>();
        alcance.getItems().addAll(ALCANCES);
        ComboBox<Object> objetivo = new ComboBox<>();
        objetivo.setMaxWidth(Double.MAX_VALUE);
        alcance.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> tipo = new ComboBox<>();
        tipo.getItems().addAll(TIPOS);
        tipo.setMaxWidth(Double.MAX_VALUE);
        TextField porcentaje = new TextField();
        porcentaje.setPromptText("Ej: 10");
        TextField lleva = new TextField();
        lleva.setPromptText("Ej: 2");
        TextField paga = new TextField();
        paga.setPromptText("Ej: 1");
        DatePicker desde = new DatePicker();
        DatePicker hasta = new DatePicker();
        desde.setPromptText("Sin fecha");
        hasta.setPromptText("Sin fecha");
        CheckBox activa = new CheckBox("Promoción activa");
        activa.setSelected(true);

        // Categorías y productos para elegir el objetivo.
        List<Categoria> categorias = List.of();
        List<Producto> productos = List.of();
        try {
            categorias = new CategoriaDAO().listar();
            productos = new ProductoDAO().listar();
        } catch (SQLException e) {
            Mensajes.error("No se pudieron cargar categorías y productos.\n" + e.getMessage());
        }
        final List<Categoria> cats = categorias;
        final List<Producto> prods = productos;

        Runnable ajustar = () -> {
            int a = alcance.getSelectionModel().getSelectedIndex();
            objetivo.getItems().clear();
            if (a == 1) {
                objetivo.getItems().addAll(cats);
            } else if (a == 2) {
                objetivo.getItems().addAll(prods);
            }
            objetivo.setDisable(a == 0);
            boolean porcentajeTipo = tipo.getSelectionModel().getSelectedIndex() == 0;
            porcentaje.setDisable(!porcentajeTipo);
            lleva.setDisable(porcentajeTipo);
            paga.setDisable(porcentajeTipo);
        };
        alcance.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> ajustar.run());
        tipo.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> ajustar.run());

        if (nueva) {
            alcance.getSelectionModel().select(0);
            tipo.getSelectionModel().select(0);
        } else {
            nombre.setText(original.getNombre());
            alcance.getSelectionModel().select(switch (original.getAlcance()) {
                case "CATEGORIA" -> 1;
                case "PRODUCTO" -> 2;
                default -> 0;
            });
            tipo.getSelectionModel().select("PORCENTAJE".equals(original.getTipo()) ? 0 : 1);
            ajustar.run();
            if (original.getIdObjetivo() != null) {
                for (Object o : objetivo.getItems()) {
                    int id = o instanceof Categoria c ? c.getId() : ((Producto) o).getId();
                    if (id == original.getIdObjetivo()) {
                        objetivo.setValue(o);
                    }
                }
            }
            porcentaje.setText(original.getValor().stripTrailingZeros().toPlainString());
            lleva.setText(original.getLleva() == 0 ? "" : String.valueOf(original.getLleva()));
            paga.setText(original.getPaga() == 0 ? "" : String.valueOf(original.getPaga()));
            desde.setValue(original.getDesde());
            hasta.setValue(original.getHasta());
            activa.setSelected(original.isActiva());
        }
        ajustar.run();

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(6, 0, 0, 0));
        form.add(conEtiqueta("Nombre", nombre), 0, 0, 2, 1);
        form.add(conEtiqueta("¿Dónde aplica?", alcance), 0, 1);
        form.add(conEtiqueta("Cuál", objetivo), 1, 1);
        form.add(conEtiqueta("Tipo", tipo), 0, 2, 2, 1);
        form.add(conEtiqueta("Porcentaje (%)", porcentaje), 0, 3);
        form.add(conEtiqueta("Lleva (unidades)", lleva), 0, 4);
        form.add(conEtiqueta("Paga (unidades)", paga), 1, 4);
        form.add(conEtiqueta("Desde (opcional)", desde), 0, 5);
        form.add(conEtiqueta("Hasta (opcional)", hasta), 1, 5);
        form.add(activa, 0, 6, 2, 1);
        for (javafx.scene.Node n : form.getChildren()) {
            GridPane.setHgrow(n, Priority.ALWAYS);
        }

        Label error = new Label();
        error.getStyleClass().add("error-texto");
        error.setWrapText(true);
        VBox contenido = new VBox(10, form, error);
        contenido.setPrefWidth(500);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText(nueva ? "Nueva promoción" : "Editar promoción");
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);

        boolean[] guardado = {false};
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                Promocion p = nueva ? new Promocion() : original;
                p.setNombre(nombre.getText());
                int a = alcance.getSelectionModel().getSelectedIndex();
                p.setAlcance(a == 1 ? "CATEGORIA" : a == 2 ? "PRODUCTO" : "TIENDA");
                Object o = objetivo.getValue();
                p.setIdObjetivo(a == 0 || o == null ? null : o instanceof Categoria c ? c.getId() : ((Producto) o).getId());
                boolean porcentajeTipo = tipo.getSelectionModel().getSelectedIndex() == 0;
                p.setTipo(porcentajeTipo ? "PORCENTAJE" : "NXM");
                p.setValor(porcentajeTipo ? numero(porcentaje.getText(), "El porcentaje") : BigDecimal.ZERO);
                p.setLleva(porcentajeTipo ? 0 : entero(lleva.getText(), "\"Lleva\""));
                p.setPaga(porcentajeTipo ? 0 : entero(paga.getText(), "\"Paga\""));
                p.setDesde(desde.getValue());
                p.setHasta(hasta.getValue());
                p.setActiva(activa.isSelected());
                servicio.guardar(Sesion.usuario(), p);
                guardado[0] = true;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error.setText(ex.getMessage());
                e.consume();
            } catch (SQLException ex) {
                error.setText("Error de base de datos: " + ex.getMessage());
                e.consume();
            }
        });

        dialogo.showAndWait();
        return guardado[0];
    }

    private static VBox conEtiqueta(String texto, javafx.scene.Node campo) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        VBox caja = new VBox(4, l, campo);
        caja.setPrefWidth(240);
        return caja;
    }

    private static BigDecimal numero(String texto, String nombre) {
        try {
            return new BigDecimal(texto.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " debe ser un número.");
        }
    }

    private static int entero(String texto, String nombre) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " debe ser un número entero.");
        }
    }
}
