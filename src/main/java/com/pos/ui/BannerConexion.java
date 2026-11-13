package com.pos.ui;

import com.pos.config.Conectividad;
import com.pos.offline.ColaVentas;
import com.pos.offline.Sincronizador;
import javafx.application.Platform;
import javafx.scene.control.Label;

/**
 * El aviso de conexión que se ve arriba: "SIN CONEXIÓN" (en rojo) cuando no hay internet, y cuántas ventas están
 * guardadas en este PC esperando subir. Se actualiza solo y no ocupa lugar cuando todo está normal.
 */
public final class BannerConexion {

    private BannerConexion() {
    }

    public static Label crear() {
        Label l = new Label();
        l.getStyleClass().add("chip");
        l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Runnable pintar = () -> Platform.runLater(() -> pintar(l));
        Conectividad.alCambiar(en -> pintar.run());
        Sincronizador.alCambiar(pintar);
        pintar(l);
        return l;
    }

    private static void pintar(Label l) {
        int pendientes = ColaVentas.cantidad();
        int errores = ColaVentas.cantidadConError();
        l.getStyleClass().removeAll("chip-off", "chip-aviso", "chip-ok");
        String texto;
        if (!Conectividad.enLinea()) {
            texto = "● Sin conexión" + (pendientes == 0 ? "" : "  ·  " + pendientes + " sin subir");
            l.getStyleClass().add("chip-off");
        } else if (Sincronizador.subiendo() && pendientes > 0) {
            texto = "● Sincronizando " + pendientes + "…";
            l.getStyleClass().add("chip-aviso");
        } else if (pendientes > 0) {
            texto = "● " + pendientes + " por subir a la nube";
            l.getStyleClass().add("chip-aviso");
        } else if (errores > 0) {
            texto = errores + (errores == 1 ? " venta con error al subir: revísala" : " ventas con error al subir: revísalas");
            l.getStyleClass().add("chip-off");
        } else {
            texto = "● Conectado a la nube";
            l.getStyleClass().add("chip-ok");
        }
        l.setText(texto);
        boolean ver = !texto.isEmpty();
        l.setVisible(ver);
        l.setManaged(ver);
    }
}
