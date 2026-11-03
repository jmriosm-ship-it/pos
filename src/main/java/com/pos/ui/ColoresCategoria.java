package com.pos.ui;

/**
 * Cada categoría tiene su color, para reconocer los productos de un vistazo en Ventas.
 * No se guarda en la base de datos: sale del id de la categoría, así que siempre es el mismo
 * (la categoría 1 siempre es azul, la 2 verde azulado, etc.) y no hay nada que configurar.
 */
public class ColoresCategoria {

    // fuerte = para franjas y chips seleccionados, suave = fondos claros, texto = letra sobre el fondo suave.
    public record Color(String fuerte, String suave, String texto) {
    }

    private static final Color[] PALETA = {
            new Color("#2563eb", "#dbeafe", "#1e40af"),   // azul
            new Color("#0f766e", "#ccfbf1", "#115e59"),   // verde azulado
            new Color("#7c3aed", "#ede9fe", "#5b21b6"),   // violeta
            new Color("#c2410c", "#ffedd5", "#9a3412"),   // naranja
            new Color("#be185d", "#fce7f3", "#9d174d"),   // rosa
            new Color("#15803d", "#dcfce7", "#166534"),   // verde
    };

    private static final Color GRIS = new Color("#6b7280", "#f3f4f6", "#374151");

    private ColoresCategoria() {
    }

    // El color de una categoría. Sin categoría (null o 0) es gris.
    public static Color de(Integer idCategoria) {
        if (idCategoria == null || idCategoria <= 0) {
            return GRIS;
        }
        return PALETA[(idCategoria - 1) % PALETA.length];
    }
}
