package com.pos.ui;

import com.pos.modelo.Modulo;
import com.pos.modelo.Accion;
import com.pos.modelo.Caja;
import com.pos.modelo.Usuario;
import com.pos.servicio.CajaService;
import com.pos.servicio.ComprobanteCajaService;
import com.pos.util.Dinero;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/**
 * Módulo Caja: abrir el turno, cerrar con el arqueo y sacar el comprobante del cierre.
 * Al escribir lo que se contó se ve EN VIVO si la caja cuadra, falta o sobra.
 * Teclas: F2 contar · F12 cerrar la caja · F7 ver comprobante · F8 imprimirlo.
 */
public class CajaView extends VBox {

    private final CajaService cajaService = new CajaService();
    private final ComprobanteCajaService comprobanteService = new ComprobanteCajaService();
    private final Usuario usuario = Sesion.usuario();
    private final Runnable alCambiarCaja;

    private Caja ultimaCerrada;          // para ver o imprimir su comprobante (F7 / F8)
    private Caja cajaActual;             // la caja abierta que se está mostrando (para ingresos y egresos)
    private TextField campoContado;      // solo existe mientras hay una caja abierta en pantalla
    private Button botonCerrar;
    private Runnable alTecla12;          // qué hace F12 en el estado actual (cerrar caja, abrir caja...)

    // Las teclas se escuchan en toda la ventana mientras esta pantalla está a la vista.
    private final EventHandler<KeyEvent> manejadorTeclas = this::tecla;

    public CajaView(Runnable alCambiarCaja) {
        this.alCambiarCaja = alCambiarCaja;
        setSpacing(14);
        setAlignment(Pos.TOP_CENTER);
        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorTeclas);
            }
        });
        mostrar(null);
    }

    private void tecla(KeyEvent e) {
        if (!Atajos.visible(this)) {
            return;
        }
        switch (e.getCode()) {
            case F2 -> {
                if (campoContado != null) {
                    campoContado.requestFocus();
                    campoContado.selectAll();
                }
            }
            case F3 -> movimiento("INGRESO");
            case F4 -> movimiento("EGRESO");
            case F5 -> movimiento("DEVOLUCION");
            case F12 -> {
                if (alTecla12 != null) {
                    alTecla12.run();
                }
            }
            case F7 -> {
                if (ultimaCerrada != null) {
                    verComprobante(ultimaCerrada);
                }
            }
            case F8 -> {
                if (ultimaCerrada != null) {
                    imprimirComprobante(ultimaCerrada);
                }
            }
            default -> {
                return;
            }
        }
        e.consume();
    }

    // F3 ingreso · F4 egreso · F5 devolución de dinero: solo con la caja abierta.
    private void movimiento(String tipo) {
        if ("DEVOLUCION".equals(tipo) ? !Seguridad.permitido(Modulo.CAJA, Accion.BORRAR, "devolver dinero")
                : !Seguridad.permitido(Modulo.CAJA, Accion.CREAR, "registrar movimientos de caja")) {
            return;
        }
        if (cajaActual != null && MovimientoCajaDialog.abrir(tipo, cajaActual)) {
            mostrar(null);
        }
    }

    // Los últimos ingresos y egresos manuales de esta caja, con tres botones para anotar uno nuevo.
    private VBox listaMovimientos(Caja caja) {
        VBox caja1 = new VBox(6);
        Button ingreso = botonMov("Ingreso", "F3", "INGRESO");
        Button egreso = botonMov("Egreso", "F4", "EGRESO");
        Button devolucion = botonMov("Devolver dinero", "F5", "DEVOLUCION");
        HBox botones = new HBox(8, ingreso, egreso, devolucion);
        caja1.getChildren().add(botones);
        try {
            var lista = cajaService.movimientos(caja);
            for (int i = 0; i < Math.min(4, lista.size()); i++) {
                var m = lista.get(i);
                Label l = new Label((m.tipo().equals("INGRESO") ? "+ " : "- ") + Dinero.formato(m.monto()) + "   " + m.motivo()
                        + "   (" + m.fecha().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                l.getStyleClass().add(m.tipo().equals("INGRESO") ? "texto-ok" : "texto-suave");
                caja1.getChildren().add(l);
            }
        } catch (SQLException e) {
            // sin la lista la caja funciona igual
        }
        return caja1;
    }

    private Button botonMov(String texto, String tecla, String tipo) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn-secundario");
        b.setGraphic(Atajos.tecla(tecla));
        b.setContentDisplay(ContentDisplay.RIGHT);
        b.setGraphicTextGap(8);
        b.setFocusTraversable(false);
        b.setOnAction(e -> movimiento(tipo));
        return b;
    }
    // Dibuja la pantalla según el estado de la caja. "recienCerrada" trae el resultado del último arqueo.
    private void mostrar(Caja recienCerrada) {
        getChildren().clear();
        campoContado = null;
        botonCerrar = null;
        alTecla12 = null;
        cajaActual = null;
        if (recienCerrada != null) {
            ultimaCerrada = recienCerrada;
            getChildren().add(tarjetaResultado(recienCerrada));
        }
        try {
            Caja abierta = cajaService.cajaAbierta(usuario);
            getChildren().add(abierta != null ? tarjetaAbierta(abierta) : tarjetaCerrada());
        } catch (SQLException e) {
            Mensajes.error("No se pudo consultar la caja.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- caja cerrada

    private Node tarjetaCerrada() {
        TextField monto = CamposDinero.crear();
        monto.setPromptText("Monto inicial, ej: 50.000");
        Button abrir = new Button("Abrir caja");
        abrir.getStyleClass().addAll("btn-primario", "btn-grande");
        abrir.setMaxWidth(Double.MAX_VALUE);
        abrir.setDefaultButton(true);
        abrir.setOnAction(e -> {
            if (!Seguridad.permitido(Modulo.CAJA, Accion.CREAR, "abrir la caja")) {
                return;
            }
            try {
                cajaService.abrirCaja(usuario, CamposDinero.valor(monto));
                alCambiarCaja.run();
                mostrar(null);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Mensajes.error(ex.getMessage());
            } catch (SQLException ex) {
                Mensajes.error("No se pudo abrir la caja.\n" + ex.getMessage());
            }
        });
        campoContado = monto;   // F2 lleva el cursor aquí también
        javafx.application.Platform.runLater(monto::requestFocus);
        return tarjeta("La caja está cerrada",
                suave("Abre un turno con el efectivo con el que empiezas para poder vender."), monto, abrir);
    }

    // ---------------------------------------------------------------- caja abierta

    private Node tarjetaAbierta(Caja caja) {
        BigDecimal efectivoRecibido;
        try {
            efectivoRecibido = cajaService.ventasEfectivo(caja);
        } catch (SQLException e) {
            Mensajes.error("No se pudo calcular la caja.\n" + e.getMessage());
            return new Label();
        }
        cajaActual = caja;
        BigDecimal esperado = caja.getMontoInicial().add(efectivoRecibido);

        // Lo que se contó, y debajo si cuadra, falta o sobra (se actualiza al escribir).
        TextField contado = CamposDinero.crear();
        contado.setPromptText("Efectivo contado");
        campoContado = contado;

        Label estadoTitulo = new Label();
        estadoTitulo.getStyleClass().add("cobro-estado-titulo");
        Label estadoValor = new Label();
        estadoValor.getStyleClass().add("cobro-estado-valor");
        Region espacioEstado = new Region();
        HBox.setHgrow(espacioEstado, Priority.ALWAYS);
        HBox filaEstado = new HBox(estadoTitulo, espacioEstado, estadoValor);
        filaEstado.setAlignment(Pos.CENTER_LEFT);

        Button cerrar = new Button("Cerrar caja y hacer arqueo");
        cerrar.getStyleClass().addAll("btn-primario", "btn-grande");
        cerrar.setGraphic(Atajos.tecla("F12"));
        cerrar.setContentDisplay(ContentDisplay.RIGHT);
        cerrar.setGraphicTextGap(12);
        cerrar.setMaxWidth(Double.MAX_VALUE);
        botonCerrar = cerrar;

        Runnable actualizar = () -> {
            estadoTitulo.getStyleClass().removeAll("texto-peligro", "texto-ok", "texto-aviso", "texto-suave");
            estadoValor.getStyleClass().removeAll("texto-peligro", "texto-ok", "texto-aviso", "texto-suave");
            if (contado.getText().isBlank()) {
                estado(estadoTitulo, estadoValor, "Cuenta el efectivo", "", "texto-suave");
                cerrar.setDisable(true);
                return;
            }
            BigDecimal dif = CamposDinero.valor(contado).subtract(esperado);
            if (dif.signum() == 0) {
                estado(estadoTitulo, estadoValor, "Cuadra", "✓", "texto-ok");
            } else if (dif.signum() < 0) {
                estado(estadoTitulo, estadoValor, "Falta", Dinero.formato(dif.negate()), "texto-peligro");
            } else {
                estado(estadoTitulo, estadoValor, "Sobra", Dinero.formato(dif), "texto-aviso");
            }
            cerrar.setDisable(false);
        };
        contado.textProperty().addListener((o, antes, ahora) -> actualizar.run());
        actualizar.run();

        Runnable cerrarCaja = () -> cerrarCaja(caja, contado, esperado);
        cerrar.setOnAction(e -> cerrarCaja.run());
        contado.setOnAction(e -> {   // Enter en el campo también cierra
            if (!cerrar.isDisable()) {
                cerrarCaja.run();
            }
        });
        alTecla12 = () -> {
            if (!cerrar.isDisable()) {
                cerrarCaja.run();
            }
        };
        javafx.application.Platform.runLater(contado::requestFocus);

        Label sep = new Label();
        sep.getStyleClass().add("separador");
        sep.setMaxWidth(Double.MAX_VALUE);
        FlowPane teclas = new FlowPane(14, 4, Atajos.etiqueta("F2", "Contar"), Atajos.etiqueta("F3", "Ingreso"),
                Atajos.etiqueta("F4", "Egreso"), Atajos.etiqueta("F5", "Devolver dinero"), Atajos.etiqueta("F12", "Cerrar caja"));
        VBox movimientos = listaMovimientos(caja);
        return tarjeta("Caja abierta",
                fila("Abierta desde", caja.getAbiertoEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
                fila("Monto inicial", Dinero.formato(caja.getMontoInicial())),
                fila("Efectivo neto (ventas, abonos, ingresos y egresos)", Dinero.formato(efectivoRecibido)),
                fila("Esperado en caja", Dinero.formato(esperado)),
                movimientos, sep, suave("Cuenta el efectivo que hay en la caja y escríbelo:"), contado, filaEstado, cerrar, teclas);
    }

    private void estado(Label titulo, Label valor, String textoTitulo, String textoValor, String estilo) {
        titulo.setText(textoTitulo);
        valor.setText(textoValor);
        titulo.getStyleClass().add(estilo);
        valor.getStyleClass().add(estilo);
    }

    // Pregunta con un mensaje claro (cuadra / faltan / sobran) y, si se acepta, cierra y muestra el comprobante.
    private void cerrarCaja(Caja caja, TextField campo, BigDecimal esperado) {
        if (!Seguridad.permitido(Modulo.CAJA, Accion.CREAR, "cerrar la caja")) {
            return;
        }
        try {
            BigDecimal contado = CamposDinero.valor(campo);
            BigDecimal dif = contado.subtract(esperado);
            String resumen;
            if (dif.signum() == 0) {
                resumen = "La caja cuadra: contaste " + Dinero.formato(contado) + ", justo lo que espera el sistema.";
            } else if (dif.signum() < 0) {
                resumen = "Faltan " + Dinero.formato(dif.negate()) + ": contaste " + Dinero.formato(contado)
                        + " y el sistema espera " + Dinero.formato(esperado) + ".";
            } else {
                resumen = "Sobran " + Dinero.formato(dif) + ": contaste " + Dinero.formato(contado)
                        + " y el sistema espera " + Dinero.formato(esperado) + ".";
            }
            if (!Mensajes.confirmar("Cerrar caja", resumen + "\n\n¿Cerrar la caja?")) {
                return;
            }
            cajaService.cerrarCaja(caja, contado);
            alCambiarCaja.run();
            mostrar(caja);
            verComprobante(caja);   // al cerrar sale el comprobante, listo para imprimir
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Mensajes.error(ex.getMessage());
        } catch (SQLException ex) {
            Mensajes.error("No se pudo cerrar la caja.\n" + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- resultado y comprobante

    // El resultado del arqueo: cuadra, faltan o sobran, con acceso al comprobante.
    private Node tarjetaResultado(Caja caja) {
        BigDecimal dif = caja.getDiferencia();
        Label veredicto = new Label(dif.signum() == 0 ? "La caja cuadra perfecto"
                : dif.signum() < 0 ? "Faltan " + Dinero.formato(dif.negate()) : "Sobran " + Dinero.formato(dif));
        veredicto.getStyleClass().addAll("card-titulo",
                dif.signum() == 0 ? "texto-ok" : dif.signum() < 0 ? "texto-peligro" : "texto-aviso");

        Button ver = new Button("Ver comprobante");
        ver.getStyleClass().add("btn-secundario");
        ver.setGraphic(Atajos.tecla("F7"));
        ver.setContentDisplay(ContentDisplay.RIGHT);
        ver.setGraphicTextGap(8);
        ver.setFocusTraversable(false);
        ver.setOnAction(e -> verComprobante(caja));
        Button imprimir = new Button("Imprimir");
        imprimir.getStyleClass().add("btn-secundario");
        imprimir.setGraphic(Atajos.tecla("F8"));
        imprimir.setContentDisplay(ContentDisplay.RIGHT);
        imprimir.setGraphicTextGap(8);
        imprimir.setFocusTraversable(false);
        imprimir.setOnAction(e -> imprimirComprobante(caja));

        return tarjeta("Arqueo de la caja #" + caja.getId(),
                fila("Esperado", Dinero.formato(caja.getMontoEsperado())),
                fila("Contado", Dinero.formato(caja.getMontoContado())),
                veredicto, new HBox(8, ver, imprimir));
    }

    private void verComprobante(Caja caja) {
        try {
            TicketDialog.mostrar("Comprobante del cierre de caja #" + caja.getId(),
                    comprobanteService.generar(caja, usuario.getNombre()));
        } catch (SQLException e) {
            Mensajes.error("No se pudo generar el comprobante.\n" + e.getMessage());
        }
    }

    private void imprimirComprobante(Caja caja) {
        try {
            TicketDialog.enviar(comprobanteService.generar(caja, usuario.getNombre()));
        } catch (SQLException e) {
            Mensajes.error("No se pudo generar el comprobante.\n" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- ayudas

    // Una tarjeta de ancho fijo con título y contenido.
    private VBox tarjeta(String titulo, Node... contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("card-titulo");
        VBox caja = new VBox(10, t);
        caja.getChildren().addAll(contenido);
        caja.getStyleClass().add("card");
        caja.setMaxWidth(560);
        return caja;
    }

    private HBox fila(String etiqueta, String valor) {
        Label e = new Label(etiqueta);
        e.getStyleClass().add("texto-suave");
        Label v = new Label(valor);
        v.getStyleClass().add("linea-nombre");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        return new HBox(8, e, espacio, v);
    }

    private Label suave(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-suave");
        l.setWrapText(true);
        return l;
    }
}
