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
    public record Resultado(List<Pago> pagos, BigDecimal efectivoRecibido, BigDecimal devolucion,
                            com.pos.modelo.PlanCredito plan, String financieraNombre) {
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
    private final TextField credito = campoMonto();   // lo que se financia en cuotas (solo con cliente que tenga cédula)
    private final TextField financiera = campoMonto();   // lo que paga una financiera más tarde
    private final javafx.scene.control.ComboBox<String> nombreFinanciera = new javafx.scene.control.ComboBox<>();
    private final Label lblFinanciera = new Label();
    private final HBox filaFinanciera = new HBox();
    private final boolean puedeCredito;
    private final String notaCredito;                 // por qué no se puede, si no se puede
    private com.pos.modelo.PlanCredito plan;          // el plan de cuotas armado (F11)
    private final Label lblPlan = new Label();
    private final Label lblCredito = new Label();
    private final HBox filaCredito = new HBox();
    private final com.pos.servicio.CreditoService creditoService = new com.pos.servicio.CreditoService();
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
    // puedeCredito: hay un cliente elegido y tiene cédula; notaCredito explica por qué no, si es el caso.
    public CobroView(BigDecimal total, int articulos, String cliente, boolean puedeFiar, boolean puedeCredito,
                     String notaCredito, Runnable alVolver, Consumer<Resultado> alConfirmar) {
        this.total = total;
        this.puedeFiar = puedeFiar;
        this.puedeCredito = puedeCredito;
        this.notaCredito = notaCredito;
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
            case F10 -> {
                if (puedeCredito) {
                    irA(credito);
                }
            }
            case F11 -> armarPlan();
            case F -> {
                if (e.isControlDown()) {
                    irA(financiera);
                } else {
                    return;
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
        VBox cCredito = tarjetaCredito();
        pagos.add(cCredito, 0, 3, 2, 1);
        // Financiera: lo paga una financiera después (no entra a la caja hasta que pague).
        VBox cFinanciera = tarjetaPago("Financiera  (la paga después)", "Ctrl+F", financiera, false);
        nombreFinanciera.setEditable(true);
        nombreFinanciera.setPromptText("Nombre de la financiera");
        nombreFinanciera.setMaxWidth(Double.MAX_VALUE);
        try {
            nombreFinanciera.getItems().setAll(new com.pos.servicio.FinancieraService().nombres());
        } catch (java.sql.SQLException ex) {
            // sin sugerencias se escribe a mano
        }
        nombreFinanciera.getEditor().textProperty().addListener((o, a, b) -> recalcular());
        cFinanciera.getChildren().add(nombreFinanciera);
        pagos.add(cFinanciera, 0, 4, 2, 1);
        for (int i = 0; i < 2; i++) {
            javafx.scene.layout.ColumnConstraints columna = new javafx.scene.layout.ColumnConstraints();
            columna.setPercentWidth(50);
            pagos.getColumnConstraints().add(columna);
        }
        for (VBox c : List.of(cEfectivo, cTransferencia, cTarjeta, cFiado, cCredito, cFinanciera)) {
            GridPane.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
            c.setPrefWidth(10);   // las dos columnas parten iguales y se reparten el espacio
        }
        // Con cinco tarjetas no cabe todo: las tarjetas de pago se desplazan y el resumen queda siempre a la vista.
        javafx.scene.control.ScrollPane desplazable = new javafx.scene.control.ScrollPane(pagos);
        desplazable.setFitToWidth(true);
        desplazable.setStyle("-fx-background-color: transparent; -fx-background: #f4f6f8;");
        HBox.setHgrow(desplazable, Priority.ALWAYS);

        return new HBox(20, desplazable, crearResumen());
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

    // La tarjeta de Crédito en cuotas: lo que se financia. Hace falta un cliente con cédula.
    private VBox tarjetaCredito() {
        VBox tarjeta = tarjetaPago("Crédito en cuotas  (lo que se financia)", "F10", credito, false);
        if (!puedeCredito) {
            credito.setDisable(true);
            tarjeta.setOpacity(0.6);
            Label nota = new Label(notaCredito);
            nota.getStyleClass().addAll("texto-suave", "texto-pequeno");
            nota.setWrapText(true);
            tarjeta.getChildren().add(nota);
        } else {
            Button plan = new Button("Plan de cuotas");
            plan.getStyleClass().add("btn-secundario");
            plan.setGraphic(Atajos.tecla("F11"));
            plan.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            plan.setGraphicTextGap(8);
            plan.setFocusTraversable(false);
            plan.setOnAction(e -> armarPlan());
            lblPlan.getStyleClass().add("texto-suave");
            lblPlan.setWrapText(true);
            tarjeta.getChildren().addAll(new HBox(12, plan, lblPlan));
        }
        return tarjeta;
    }

    // F11: abre la ventana para elegir frecuencia, número de cuotas, tasa y primera fecha.
    private void armarPlan() {
        BigDecimal cr = puedeCredito ? valor(credito) : BigDecimal.ZERO;
        if (!puedeCredito || cr.signum() <= 0) {
            return;
        }
        com.pos.modelo.PlanCredito elegido = CreditoPlanDialog.abrir(cr, total, plan);
        if (elegido != null) {
            plan = elegido;
            recalcular();
        }
    }

    // Los cinco campos de dinero, en el orden en que aparecen.
    private List<TextField> campos() {
        return List.of(efectivo, transferencia, tarjeta, fiado, credito, financiera);
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

        // "En cuotas $X": lo que se financia. Solo se ve si hay crédito.
        Label etiquetaCredito = new Label("En cuotas");
        etiquetaCredito.getStyleClass().add("cobro-fila-etiqueta");
        lblCredito.getStyleClass().addAll("cobro-fila-valor", "texto-peligro");
        Region e4 = new Region();
        HBox.setHgrow(e4, Priority.ALWAYS);
        filaCredito.getChildren().setAll(etiquetaCredito, e4, lblCredito);
        filaCredito.setAlignment(Pos.CENTER_LEFT);
        filaCredito.managedProperty().bind(filaCredito.visibleProperty());

        Label etiquetaFinanciera = new Label("Por la financiera");
        etiquetaFinanciera.getStyleClass().add("cobro-fila-etiqueta");
        lblFinanciera.getStyleClass().addAll("cobro-fila-valor", "texto-peligro");
        Region e5 = new Region();
        HBox.setHgrow(e5, Priority.ALWAYS);
        filaFinanciera.getChildren().setAll(etiquetaFinanciera, e5, lblFinanciera);
        filaFinanciera.setAlignment(Pos.CENTER_LEFT);
        filaFinanciera.managedProperty().bind(filaFinanciera.visibleProperty());

        VBox resumen = new VBox(14, cajaTotal, filaPagado, filaFiado, filaCredito, filaFinanciera, sep, filaEstado, lblAyuda, relleno, confirmar, teclas);
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
        BigDecimal cr = puedeCredito ? valor(credito) : BigDecimal.ZERO;
        // Con crédito siempre hay un plan: si no existe o quedó de otro valor, se arma uno con las opciones de siempre.
        if (cr.signum() > 0 && (plan == null || plan.financiado().compareTo(cr) != 0)) {
            try {
                plan = plan == null ? CreditoPlanDialog.porDefecto(cr)
                        : creditoService.simular(cr, plan.frecuencia(), plan.plazo(), plan.tasa(), plan.primeraCuota());
            } catch (IllegalArgumentException | java.sql.SQLException ex) {
                plan = null;
            }
        }
        if (cr.signum() == 0) {
            plan = null;
        }
        lblPlan.setText(plan == null ? "" : plan.plazo() + " cuotas " + plan.frecuenciaTexto() + "s de " + Dinero.formato(plan.cuotaValor())
                + "  ·  tasa " + plan.tasa().toPlainString() + " %  ·  primera " + plan.primeraCuota());
        lblCredito.setText(Dinero.formato(cr));
        filaCredito.setVisible(cr.signum() > 0);
        BigDecimal fn = valor(financiera);
        lblFinanciera.setText(Dinero.formato(fn));
        filaFinanciera.setVisible(fn.signum() > 0);
        BigDecimal otros = valor(transferencia).add(valor(tarjeta)).add(fi).add(cr).add(fn);   // lo que no da vuelto
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
            estado(fi.signum() > 0 || cr.signum() > 0 ? "Cubierto, con deuda" : "Pago exacto", "✓", "texto-ok");
            confirmar.setDisable(false);
        }
        if (fn.signum() > 0 && !confirmar.isDisable() && nombreFinanciera.getEditor().getText().isBlank()) {
            lblAyuda.setText("Escribe el nombre de la financiera.");
            confirmar.setDisable(true);
        }
        // El crédito pide una cuota inicial mínima y un plan armado.
        if (cr.signum() > 0 && !confirmar.isDisable()) {
            try {
                creditoService.validarInicial(total, cr);
                if (plan == null) {
                    throw new IllegalArgumentException("Arma el plan de cuotas (F11).");
                }
            } catch (IllegalArgumentException | java.sql.SQLException ex) {
                lblAyuda.setText(ex.getMessage());
                confirmar.setDisable(true);
            }
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
        BigDecimal cr = puedeCredito ? valor(credito) : BigDecimal.ZERO;

        // Del efectivo solo se aplica lo que falta después de la tarjeta, la transferencia, el fiado y el crédito;
        // lo que sobra es la devolución al cliente.
        BigDecimal fn = valor(financiera);
        BigDecimal efectivoAplicado = ef.min(total.subtract(tr).subtract(ta).subtract(fi).subtract(cr).subtract(fn)).max(BigDecimal.ZERO);
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
        if (cr.signum() > 0) {
            pagos.add(new Pago("CREDITO", cr));
        }
        if (fn.signum() > 0) {
            pagos.add(new Pago("FINANCIERA", fn));
        }
        alConfirmar.accept(new Resultado(pagos, ef, devolucion, cr.signum() > 0 ? plan : null,
                fn.signum() > 0 ? nombreFinanciera.getEditor().getText().trim() : null));
    }
}
