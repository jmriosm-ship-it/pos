package com.pos.ui;

import com.pos.servicio.ConfiguracionService;
import com.pos.servicio.CopiaSeguridadService;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Módulo Copias de seguridad: guarda toda la base de datos en un archivo .sql y permite volver a una copia.
 * Tip: si la carpeta es de OneDrive o Google Drive, la copia también queda en la nube.
 * Teclas: F2 elegir carpeta · F5 hacer copia ahora · F9 restaurar la elegida · ↑ ↓ moverse.
 */
public class CopiasView extends VBox {

    static final String CLAVE_CARPETA = "copias_carpeta";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CopiaSeguridadService servicio = new CopiaSeguridadService();
    private final ConfiguracionService config = new ConfiguracionService();

    private final Label carpetaTexto = new Label();
    private final Label estado = new Label();
    private final TableView<Path> tabla = new TableView<>();
    private final Button botonCopia = new Button();
    private Path carpeta;
    private boolean ocupado = false;

    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public CopiasView() {
        setSpacing(12);

        // Tarjeta: dónde se guardan las copias y el botón principal
        Label titulo = new Label("Carpeta de las copias");
        titulo.getStyleClass().add("etiqueta-campo");
        carpetaTexto.getStyleClass().add("linea-nombre");
        carpetaTexto.setWrapText(true);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        botonCopia.setText("Hacer copia ahora");
        botonCopia.getStyleClass().add("btn-primario");
        botonCopia.setGraphic(Atajos.tecla("F5"));
        botonCopia.setContentDisplay(ContentDisplay.RIGHT);
        botonCopia.setGraphicTextGap(8);
        botonCopia.setFocusTraversable(false);
        botonCopia.setOnAction(e -> hacerCopia());
        HBox fila = new HBox(12, new VBox(4, titulo, carpetaTexto), espacio,
                boton("Cambiar carpeta", "F2", "btn-secundario", this::elegirCarpeta), botonCopia);
        fila.setAlignment(Pos.CENTER_LEFT);
        estado.getStyleClass().add("texto-suave");
        estado.setWrapText(true);
        VBox tarjeta = new VBox(8, fila, estado);
        tarjeta.getStyleClass().add("card");

        // Lista de copias
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Todavía no hay copias en esta carpeta."));
        tabla.getColumns().add(Tablas.columna("Archivo", 280, (Path p) -> p.getFileName().toString()));
        tabla.getColumns().add(Tablas.columna("Fecha", 160, (Path p) -> fecha(p)));
        tabla.getColumns().add(Tablas.columna("Tamaño", 120, (Path p) -> tamano(p)));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox pie = new HBox(14,
                new FlowPane(14, 4, Atajos.etiqueta("F2", "Cambiar carpeta"), Atajos.etiqueta("F5", "Copia ahora"),
                        Atajos.etiqueta("↑ ↓", "Moverse"), Atajos.etiqueta("F9", "Restaurar la elegida")));
        pie.getChildren().add(hueco);
        pie.setAlignment(Pos.CENTER_LEFT);

        HBox barraLista = new HBox(new Label("Copias guardadas"), new Region(),
                boton("Restaurar", "F9", "btn-peligro", this::restaurar));
        ((Label) barraLista.getChildren().get(0)).getStyleClass().add("linea-nombre");
        HBox.setHgrow(barraLista.getChildren().get(1), Priority.ALWAYS);
        barraLista.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(tarjeta, barraLista, tabla, pie);

        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });

        try {
            carpeta = Path.of(config.obtener(CLAVE_CARPETA,
                    Path.of(System.getProperty("user.home"), "Documents", "POS-copias").toString()));
        } catch (SQLException e) {
            carpeta = Path.of(System.getProperty("user.home"), "Documents", "POS-copias");
        }
        refrescar();
    }

    private Button boton(String texto, String tecla, String estilo, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add(estilo);
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private String fecha(Path p) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault()).format(FECHA);
        } catch (IOException e) {
            return "";
        }
    }

    private String tamano(Path p) {
        try {
            long kb = Math.max(1, Files.size(p) / 1024);
            return kb >= 1024 ? String.format("%.1f MB", kb / 1024.0) : kb + " KB";
        } catch (IOException e) {
            return "";
        }
    }

    // Vuelve a leer la carpeta y deja el cursor en la copia más reciente.
    private void refrescar() {
        carpetaTexto.setText(carpeta.toString());
        try {
            List<Path> copias = servicio.listarCopias(carpeta);
            tabla.getItems().setAll(copias);
            if (copias.isEmpty()) {
                estado.setText("Aún no hay copias. Pulsa F5 para hacer la primera.");
            } else {
                estado.setText("Última copia: " + fecha(copias.get(0)) + "   ·   " + copias.size()
                        + (copias.size() == 1 ? " copia guardada" : " copias guardadas"));
                tabla.getSelectionModel().select(0);
            }
        } catch (IOException e) {
            estado.setText("No se pudo leer la carpeta: " + e.getMessage());
        }
    }

    private void tecla(KeyEvent e) {
        switch (e.getCode()) {
            case F2 -> elegirCarpeta();
            case F5 -> hacerCopia();
            case F9 -> restaurar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void elegirCarpeta() {
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle("Carpeta para las copias");
        if (Files.isDirectory(carpeta)) {
            selector.setInitialDirectory(carpeta.toFile());
        }
        File elegida = selector.showDialog(getScene().getWindow());
        if (elegida == null) {
            return;
        }
        try {
            config.guardar(Sesion.usuario(), CLAVE_CARPETA, elegida.getAbsolutePath());
            carpeta = elegida.toPath();
            refrescar();
        } catch (IllegalStateException | SQLException ex) {
            Mensajes.error(ex.getMessage());
        }
    }

    // La copia puede tardar unos segundos: se hace en otro hilo para que la ventana no se congele.
    private void hacerCopia() {
        if (ocupado) {
            return;
        }
        ocupado = true;
        estado.setText("Haciendo la copia, un momento...");
        Path destino = carpeta;
        new Thread(() -> {
            String mensaje;
            try {
                Path archivo = servicio.crearCopia(Sesion.usuario(), destino);
                mensaje = "Copia lista: " + archivo.getFileName();
            } catch (Exception ex) {
                mensaje = "ERROR: " + ex.getMessage();
            }
            String resultado = mensaje;
            Platform.runLater(() -> {
                ocupado = false;
                refrescar();
                if (resultado.startsWith("ERROR")) {
                    Mensajes.error(resultado.substring(7));
                } else {
                    estado.setText(resultado);
                }
            });
        }, "copia-seguridad").start();
    }

    // Antes de volver a una copia vieja se guarda una copia de seguridad de lo de ahora, por si fue un error.
    private void restaurar() {
        Path elegida = tabla.getSelectionModel().getSelectedItem();
        if (ocupado) {
            return;
        }
        if (elegida == null) {
            Mensajes.info("Elige una copia", "Muévete a la lista con la flecha ↓ y elige la copia a restaurar.");
            return;
        }
        if (!Mensajes.confirmar("Restaurar copia", "Se borrarán los datos de ahora y quedarán los de\n"
                + elegida.getFileName() + "\n\nAntes se guarda una copia de lo actual. ¿Continuar?")) {
            return;
        }
        ocupado = true;
        estado.setText("Restaurando, un momento...");
        Path destino = carpeta;
        new Thread(() -> {
            String mensaje;
            try {
                servicio.crearCopia(Sesion.usuario(), destino);
                servicio.restaurar(Sesion.usuario(), elegida);
                mensaje = "OK";
            } catch (Exception ex) {
                mensaje = "ERROR: " + ex.getMessage();
            }
            String resultado = mensaje;
            Platform.runLater(() -> {
                ocupado = false;
                refrescar();
                if (resultado.startsWith("ERROR")) {
                    Mensajes.error(resultado.substring(7));
                } else {
                    Mensajes.info("Copia restaurada", "Listo. Cierra y vuelve a abrir el POS para ver los datos.");
                }
            });
        }, "restaurar-copia").start();
    }
}
