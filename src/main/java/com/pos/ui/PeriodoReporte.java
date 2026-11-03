package com.pos.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * El periodo que se está mirando en Reportes (desde / hasta). Lo comparten las tres pestañas:
 * cuando cambia, cada una vuelve a cargar sus datos.
 */
public class PeriodoReporte {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LocalDate desde = LocalDate.now();
    private LocalDate hasta = LocalDate.now();
    private String nombre = "Hoy";
    private final List<Runnable> oyentes = new ArrayList<>();

    public LocalDate desde() { return desde; }
    public LocalDate hasta() { return hasta; }

    // Una frase para mostrar: "Hoy", "Últimos 7 días", "01/10/2026 al 31/10/2026".
    public String texto() {
        return nombre;
    }

    public void hoy() {
        poner("Hoy", LocalDate.now(), LocalDate.now());
    }

    public void ultimosSieteDias() {
        poner("Últimos 7 días", LocalDate.now().minusDays(6), LocalDate.now());
    }

    public void esteMes() {
        poner("Este mes", LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    public void rango(LocalDate desde, LocalDate hasta) {
        poner(desde.format(FECHA) + " al " + hasta.format(FECHA), desde, hasta);
    }

    // Se llama a "oyente" cada vez que cambia el periodo.
    public void alCambiar(Runnable oyente) {
        oyentes.add(oyente);
    }

    private void poner(String nombre, LocalDate desde, LocalDate hasta) {
        this.nombre = nombre;
        this.desde = desde;
        this.hasta = hasta;
        oyentes.forEach(Runnable::run);
    }
}
