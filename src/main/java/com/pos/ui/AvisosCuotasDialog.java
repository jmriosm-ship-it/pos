package com.pos.ui;

import com.pos.servicio.CreditoService;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;

/** Los avisos de cuotas por mandar por WhatsApp: cada uno abre el chat con el mensaje ya escrito y se marca como enviado. */
public class AvisosCuotasDialog {

    private AvisosCuotasDialog() {
    }

    // Abre el WhatsApp de un aviso y lo marca como enviado (así no se repite).
    public static void enviar(CreditoService servicio, CreditoService.Aviso a) {
        try {
            if (a.credito().telefono() == null || a.credito().telefono().replaceAll("\\D", "").length() < 7) {
                Mensajes.info("Sin teléfono", a.credito().cliente() + " no tiene un teléfono válido. Agrégalo en Clientes.");
                return;
            }
            java.awt.Desktop.getDesktop().browse(URI.create(a.url()));
            servicio.marcarAviso(a);
        } catch (Exception e) {
            Mensajes.error("No se pudo abrir WhatsApp.\n" + e.getMessage());
        }
    }

    public static void abrir() {
        CreditoService servicio = new CreditoService();
        List<CreditoService.Aviso> avisos;
        try {
            avisos = servicio.avisosPendientes();
        } catch (SQLException e) {
            Mensajes.error("No se pudieron calcular los avisos.\n" + e.getMessage());
            return;
        }
        VBox lista = new VBox(8);
        if (avisos.isEmpty()) {
            Label vacio = new Label("No hay avisos pendientes por hoy.");
            vacio.getStyleClass().add("texto-suave");
            lista.getChildren().add(vacio);
        }
        for (CreditoService.Aviso a : avisos) {
            Label quien = new Label(a.credito().cliente() + "  ·  " + ("mora".equals(a.tipo()) ? "MORA" : "Cuota por vencer"));
            quien.getStyleClass().add("linea-nombre");
            Label texto = new Label(a.texto());
            texto.setWrapText(true);
            texto.getStyleClass().add("texto-suave");
            Button enviar = new Button("Abrir WhatsApp");
            enviar.getStyleClass().add("btn-primario");
            enviar.setFocusTraversable(true);
            HBox fila = new HBox(12, new VBox(2, quien, texto), new Region(), enviar);
            HBox.setHgrow(fila.getChildren().get(0), Priority.ALWAYS);
            HBox.setHgrow(fila.getChildren().get(1), Priority.NEVER);
            fila.getStyleClass().add("card");
            enviar.setOnAction(e -> {
                enviar(servicio, a);
                enviar.setDisable(true);
                enviar.setText("Enviado");
            });
            lista.getChildren().add(fila);
        }
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(680, 380);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(Marca.PRODUCTO);
        dialogo.setHeaderText("Avisos de cuotas por WhatsApp");
        dialogo.getDialogPane().setContent(scroll);
        dialogo.getDialogPane().getStylesheets().add(Mensajes.CSS);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.showAndWait();
    }
}
