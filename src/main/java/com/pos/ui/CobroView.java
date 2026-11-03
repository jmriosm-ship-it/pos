package com.pos.ui;

import com.pos.modelo.Pago;
import com.pos.util.Dinero;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pantalla de cobro. Se pueden repartir el total entre varios medios (efectivo, transferencia,
 * tarjeta); el sistema muestra cuánto falta o, si el cliente pagó de más en efectivo, la DEVOLUCIÓN.
 * El botón "Confirmar venta" solo se activa cuando el pago cubre el total.
 *
 * Se maneja entera con el teclado:
 *   F1 / F2 / F3  = ir a Efectivo / Transferencia / Tarjeta
 *   F5            = completar lo que falta en el campo donde estás
 *   F6 a F9       = el cliente entregó $10.000 / $20.000 / $50.000 / $100.000 en efectivo
 *   F12 o Enter   = confirmar la venta (si el pago ya cubre el total)
 *   Esc           = volver a la venta
 */
public class CobroView extends VBox {

    // Lo que devuelve esta pantalla al confirmar: los pagos aplicados, lo que el cliente entregó
    // en efectivo y el vuelto que hay que devolverle.
    public record Resultado(List<Pago> pagos, BigDecimal efectivoRecibido, BigDecimal devolucion) {
    }

    private static final int[] BILLETES = {10000, 20000, 50000, 100000};

    private final BigDecimal total;
    private final Consumer<Resultado> alConfirmar;
    private final Runnable alVolver;

    private final TextField efectivo = campoMonto();
    private final TextField transferencia = campoMonto();
    private final TextField tarjeta = campoMonto();
    private final TextField fiado = campoMonto();   // lo que queda a deber (solo si hay un cliente elegido)
    private final boolean puedeFiar;
    private TextField campoActivo = efectivo;   // el último campo donde estuvo el cursor (lo usa F5)
    private final Label lblFiado = new Label();
    private final HBox filaFiado = new HBox();   // "A fiado $X": solo se ve cuando hay algo fiado

    private final Label lblPagado = new Label();
    private final Label lblEstadoTitulo = new Label();
    private final Label lblEstadoValor = new Label();
    private final Label lblAyuda = new Label();   // explica el error cuando los montos no son válidos
    private final Button confirmar = new Button("Confirmar venta");

    // Las teclas se escuchan en toda la ventana mientras esta pantalla está a la vista.
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    // puedeFiar: true solo si la venta tiene un cliente elegido (el cliente general no puede quedar debiendo).
    public CobroView(BigDecimal total, int articulos, String cliente, boolean puedeFiar,
                     Runnable alVolver, Consumer<Resultado> alConfirmar) {
        this.total = total;
        this.puedeFiar = puedeFiar;
        this.alConfirmar = alConfirmar;
        this.alVolver = alVolver;
        setSpacing(18);

        getChildren().addAll(crearCabecera(articulos, cliente), crearCuerpo());
        VBox.setVgrow(getChildren().get(1), Priority.ALWAYS);

        // Los atajos se conectan a la ventana cuando esta pantalla entra y se sueltan cuando sale.
        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });

        confirmar.setText("Confirmar venta");
        confirmar.setGraphic(Atajos.tecla("F12"));
        confirmar.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        confirmar.setGraphicTextGap(12);
        confirmar.setOnAction(e -> confirmar());
        for (TextField campo : campos()) {
            // Cada vez que cambia un monto se recalcula lo pagado, lo que falta o la devolución.
            campo.textProperty().addListener((o, antes, ahora) -> recalcular());
            campo.focusedProperty().addListener((o, antes, ahora) -> {
                if (ahora) {
                    campoActivo = campo;
                }
            });
            // Enter dentro de un campo = confirmar, si ya se puede.
            campo.setOnAction(e -> {
                if (!confirmar.isDisable()) {
                    confirmar();
                }
            });
        }

        recalcular();
        Platform.runLater(efectivo::requestFocus);
    }

    // ---------------------------------------------------------------- teclado

    private void tecla(KeyEvent e) {
        switch (e.getCode()) {
            case F1 -> irA(efectivo);
            case F2 -> irA(transferencia);
            case F3 -> irA(tarjeta);
            case F4 -> {
                if (puedeFiar) {
                    irA(fiado);
                }
            }
            case F5 -> completar(campoActivo);
            case F6, F7, F8, F9 -> {
                // F6 = $10.000 ... F9 = $100.000. Es lo que el cliente entregó en efectivo.
                int billete = BILLETES[e.getCode().ordinal() - KeyCode.F6.ordinal()];
                efectivo.setText(String.valueOf(billete));
                irA(efectivo);
            }
            case F12 -> {
                if (!confirmar.isDisable()) {
                    confirmar();
                }
            }
            case ESCAPE -> alVolver.run();
            default -> {
                return;   // cualquier otra tecla se deja pasar normalmente
            }
        }
        e.consume();
    }

    private void irA(TextField campo) {
        campo.requestFocus();
        campo.selectAll();
    }

    // ---------------------------------------------------------------- diseño

    private HBox crearCabecera(int articulos, String cliente) {
        Label titulo = new Label("Cobrar");
        titulo.getStyleClass().add("cobro-titulo");
        String detalle = articulos + (articulos == 1 ? " artículo" : " artículos") + "  ·  " + cliente;
        Label sub = new Label(detalle);
        sub.getStyleClass().add("texto-suave");

        Button volver = new Button("←  Volver a la venta");
        volver.getStyleClass().add("btn-secundario");
        volver.setGraphic(Atajos.tecla("Esc"));
        volver.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        volver.setGraphicTextGap(10);
        volver.setFocusTraversable(false);
        volver.setOnAction(e -> alVolver.run());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cabecera = new HBox(new VBox(2, titulo, sub), espacio, volver);
        cabecera.setAlignment(Pos.TOP_LEFT);
        return cabecera;
    }

    private HBox crearCuerpo() {
        // Tarjetas de pago: efectivo y transferencia arriba, tarjeta debajo.
        GridPane pagos = new GridPane();
        pagos.setHgap(16);
        pagos.setVgap(16);
        VBox cEfectivo = tarjetaPago("Efectivo", "F1", efectivo, true);
        VBox cTransferencia = tarjetaPago("Transferencia", "F2", transferencia, false);
        VBox cTarjeta = tarjetaPago("Tarjeta", "F3", tarjeta, false);
        // El efectivo ocupa todo el ancho (tiene muchos botones); debajo van transferencia y tarjeta.
        pagos.add(cEfectivo, 0, 0, 2, 1);
        pagos.add(cTransferencia, 0, 1);
        pagos.add(cTarjeta, 1, 1);
        VBox cFiado = tarjetaFiado();
        pagos.add(cFiado, 0, 2, 2, 1);
        for (int i = 0; i < 2; i++) {
            javafx.scene.layout.ColumnConstraints columna = new javafx.scene.layout.ColumnConstraints();
            columna.setPercentWidth(50);
            pagos.getColumnConstraints().add(columna);
        }
        for (VBox c : List.of(cEfectivo, cTransferencia, cTarjeta, cFiado)) {
            GridPane.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
            c.setPrefWidth(10);   // las dos columnas parten iguales y se reparten el espacio
        }
        HBox.setHgrow(pagos, Priority.ALWAYS);

        return new HBox(20, pagos, crearResumen());
    }

    // Una tarjeta con el campo de monto y sus botones rápidos. Cada botón muestra su tecla.
    private VBox tarjetaPago(String nombre, String teclaCampo, TextField campo, boolean conBilletes) {
        Label etiqueta = new Label(nombre);
        etiqueta.getStyleClass().add("texto-suave");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox titulo = new HBox(etiqueta, espacio, Atajos.tecla(teclaCampo));
        titulo.setAlignment(Pos.CENTER_LEFT);

        FlowPane rapidos = new FlowPane(8, 8);
        rapidos.getChildren().add(botonRapido("Completar", "F5", () -> completar(campo)));
        if (conBilletes) {
            // Los billetes fijan lo que el cliente entregó en efectivo (F6 a F9).
            for (int i = 0; i < BILLETES.length; i++) {
                int billete = BILLETES[i];
                rapidos.getChildren().add(botonRapido(Dinero.formato(BigDecimal.valueOf(billete)), "F" + (6 + i),
                        () -> campo.setText(String.valueOf(billete))));
            }
        }

        VBox tarjeta = new VBox(10, titulo, campo, rapidos);
        tarjeta.getStyleClass().add("card");
        tarjeta.setAlignment(Pos.TOP_LEFT);
        return tarjeta;
    }

    // La tarjeta de Fiado: lo que no se paga ahora y queda a deber. Sin cliente elegido se bloquea.
    private VBox tarjetaFiado() {
        VBox tarjeta = tarjetaPago("Fiado  (queda a deber)", "F4", fiado, false);
        if (!puedeFiar) {
            fiado.setDisable(true);
            tarjeta.setOpacity(0.6);
            Label nota = new Label("Para fiar, vuelve a la venta (Esc) y elige un cliente con F3.");
            nota.getStyleClass().addAll("texto-suave", "texto-pequeno");
            tarjeta.getChildren().add(nota);
        }
        return tarjeta;
    }

    // Los cuatro campos de dinero, en el orden en que aparecen.
    private List<TextField> campos() {
        return List.of(efectivo, transferencia, tarjeta, fiado);
    }

    private Button botonRapido(String texto, String tecla, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn-rapido");
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setOnAction(e -> accion.run());
        return b;
    }

    // El panel de la derecha: total, cuánto lleva pagado y cuánto falta / cuánto devolver.
    private VBox crearResumen() {
        Label etiquetaTotal = new Label("TOTAL A PAGAR");
        etiquetaTotal.getStyleClass().add("total-oscuro-etiqueta");
        Label valorTotal = new Label(Dinero.formato(total));
        valorTotal.getStyleClass().add("total-oscuro-valor");
        VBox cajaTotal = new VBox(0, etiquetaTotal, valorTotal);
        cajaTotal.setAlignment(Pos.CENTER_RIGHT);
        cajaTotal.getStyleClass().add("total-oscuro");

        Label etiquetaPagado = new Label("Pagado");
        etiquetaPagado.getStyleClass().add("cobro-fila-etiqueta");
        lblPagado.getStyleClass().add("cobro-fila-valor");
        Region e1 = new Region();
        HBox.setHgrow(e1, Priority.ALWAYS);
        HBox filaPagado = new HBox(etiquetaPagado, e1, lblPagado);
        filaPagado.setAlignment(Pos.CENTER_LEFT);

        Label sep = new Label();
        sep.getStyleClass().add("separador");
        sep.setMaxWidth(Double.MAX_VALUE);

        lblEstadoTitulo.getStyleClass().add("cobro-estado-titulo");
        lblEstadoValor.getStyleClass().add("cobro-estado-valor");
        Region e2 = new Region();
        HBox.setHgrow(e2, Priority.ALWAYS);
        HBox filaEstado = new HBox(lblEstadoTitulo, e2, lblEstadoValor);
        filaEstado.setAlignment(Pos.CENTER_LEFT);

        lblAyuda.getStyleClass().add("error-texto");
        lblAyuda.setWrapText(true);
        lblAyuda.managedProperty().bind(lblAyuda.textProperty().isNotEmpty());   // no ocupa espacio si está vacío

        Region relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        confirmar.getStyleClass().addAll("btn-primario", "btn-grande");
        confirmar.setMaxWidth(Double.MAX_VALUE);

        // Recordatorio de las teclas principales, debajo del botón.
        HBox teclas = new HBox(16, Atajos.etiqueta("Enter", "Confirmar"), Atajos.etiqueta("Esc", "Volver"));
        teclas.setAlignment(Pos.CENTER);

        // "A fiado $X": lo que el cliente se lleva sin pagar. Solo se ve si hay algo fiado.
        Label etiquetaFiado = new Label("A fiado");
        etiquetaFiado.getStyleClass().add("cobro-fila-etiqueta");
        lblFiado.getStyleClass().addAll("cobro-fila-valor", "texto-peligro");
        Region e3 = new Region();
        HBox.setHgrow(e3, Priority.ALWAYS);
        filaFiado.getChildren().setAll(etiquetaFiado, e3, lblFiado);
        filaFiado.setAlignment(Pos.CENTER_LEFT);
        filaFiado.managedProperty().bind(filaFiado.visibleProperty());

        VBox resumen = new VBox(14, cajaTotal, filaPagado, filaFiado, sep, filaEstado, lblAyuda, relleno, confirmar, teclas);
        resumen.getStyleClass().add("card");
        resumen.setPrefWidth(420);
        resumen.setMinWidth(420);
        resumen.setMaxWidth(420);
        return resumen;
    }

    // ---------------------------------------------------------------- cálculo

    // Un campo donde solo se pueden escribir números, y se muestran con puntos de miles (50.000).
    private static TextField campoMonto() {
        TextField campo = new TextField();
        campo.setPromptText("0");
        campo.getStyleClass().add("campo-monto");
        campo.textProperty().addListener((o, antes, ahora) -> {
            String digitos = ahora.replaceAll("[^0-9]", "");
            if (digitos.length() > 9) {
                digitos = digitos.substring(0, 9);
            }
            String formateado = digitos.isEmpty() ? "" : Dinero.formato(new BigDecimal(digitos)).substring(1);
            if (!formateado.equals(ahora)) {
                campo.setText(formateado);
                campo.positionCaret(formateado.length());
            }
        });
        return campo;
    }

    private BigDecimal valor(TextField campo) {
        String digitos = campo.getText() == null ? "" : campo.getText().replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digitos);
    }

    // "Completar": pone en este campo lo que falta para llegar al total, descontando lo de los otros campos.
    private void completar(TextField campo) {
        BigDecimal otros = BigDecimal.ZERO;
        for (TextField c : campos()) {
            if (c != campo) {
                otros = otros.add(valor(c));
            }
        }
        BigDecimal falta = total.subtract(otros).max(BigDecimal.ZERO);
        // setScale(0): el total viene con decimales (25000.00) y esos ceros no deben contarse como dígitos.
        campo.setText(falta.signum() == 0 ? "" : falta.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        irA(campo);
    }

    // Se llama cada vez que cambia cualquier monto: actualiza Pagado, Falta/Devolución y el botón.
    private void recalcular() {
        BigDecimal ef = valor(efectivo);
        BigDecimal fi = puedeFiar ? valor(fiado) : BigDecimal.ZERO;
        BigDecimal otros = valor(transferencia).add(valor(tarjeta)).add(fi);   // lo que no da vuelto
        BigDecimal entregado = ef.add(otros);
        BigDecimal diferencia = entregado.subtract(total);   // negativo = falta, positivo = sobra

        // "Pagado" es plata que sí entró (sin contar lo fiado, que todavía se debe).
        BigDecimal pagadoReal = ef.add(valor(transferencia)).add(valor(tarjeta));
        lblPagado.setText(Dinero.formato(pagadoReal.min(total)));
        lblFiado.setText(Dinero.formato(fi));
        filaFiado.setVisible(fi.signum() > 0);
        lblEstadoTitulo.getStyleClass().removeAll("texto-peligro", "texto-ok");
        lblEstadoValor.getStyleClass().removeAll("texto-peligro", "texto-ok");

        lblAyuda.setText("");
        if (otros.compareTo(total) > 0) {
            // La tarjeta y la transferencia no admiten vuelto: no pueden pasar del total.
            estado("Sobra", Dinero.formato(otros.subtract(total)), "texto-peligro");
            lblAyuda.setText("La tarjeta, la transferencia y el fiado no pueden pasar del total (no dan vuelto). Baja alguno de los montos.");
            confirmar.setDisable(true);
        } else if (diferencia.signum() < 0) {
            estado("Falta", Dinero.formato(diferencia.negate()), "texto-peligro");
            confirmar.setDisable(true);
        } else if (diferencia.signum() > 0) {
            estado("Devolución", Dinero.formato(diferencia), "texto-ok");
            confirmar.setDisable(false);
        } else {
            // Con fiado el total queda cubierto, pero una parte todavía se debe: no se llama "pago exacto".
            estado(fi.signum() > 0 ? "Cubierto, con fiado" : "Pago exacto", "✓", "texto-ok");
            confirmar.setDisable(false);
        }
    }

    private void estado(String titulo, String valor, String estilo) {
        lblEstadoTitulo.setText(titulo);
        lblEstadoValor.setText(valor);
        lblEstadoTitulo.getStyleClass().add(estilo);
        lblEstadoValor.getStyleClass().add(estilo);
    }

    private void confirmar() {
        BigDecimal ef = valor(efectivo);
        BigDecimal tr = valor(transferencia);
        BigDecimal ta = valor(tarjeta);
        BigDecimal fi = puedeFiar ? valor(fiado) : BigDecimal.ZERO;

        // Del efectivo solo se aplica lo que falta después de la tarjeta, la transferencia y el fiado;
        // lo que sobra es la devolución al cliente.
        BigDecimal efectivoAplicado = ef.min(total.subtract(tr).subtract(ta).subtract(fi)).max(BigDecimal.ZERO);
        BigDecimal devolucion = ef.subtract(efectivoAplicado);

        List<Pago> pagos = new ArrayList<>();
        if (efectivoAplicado.signum() > 0) {
            pagos.add(new Pago("EFECTIVO", efectivoAplicado));
        }
        if (tr.signum() > 0) {
            pagos.add(new Pago("TRANSFERENCIA", tr));
        }
        if (ta.signum() > 0) {
            pagos.add(new Pago("TARJETA", ta));
        }
        if (fi.signum() > 0) {
            pagos.add(new Pago("FIADO", fi));
        }
        alConfirmar.accept(new Resultado(pagos, ef, devolucion));
    }
}
