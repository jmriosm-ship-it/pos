package com.pos.servicio;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;

/**
 * Envía el texto del recibo a una impresora térmica instalada en Windows.
 * Usa comandos ESC/POS: códigos especiales que entienden casi todas las térmicas.
 * OJO: necesita probarse con la impresora real del cliente (el nombre y la codificación
 * pueden requerir un pequeño ajuste según el modelo).
 */
public class ImpresoraService {

    private final ConfiguracionService config = new ConfiguracionService();

    // Nombres de todas las impresoras instaladas (para elegirla en Configuración).
    public String[] listarImpresoras() {
        PrintService[] servicios = PrintServiceLookup.lookupPrintServices(null, null);
        String[] nombres = new String[servicios.length];
        for (int i = 0; i < servicios.length; i++) {
            nombres[i] = servicios[i].getName();
        }
        return nombres;
    }

    // Imprime un texto (el recibo) en la impresora configurada, o en la predeterminada de Windows.
    public void imprimir(String texto) throws SQLException, PrintException {
        PrintService impresora = buscar(config.obtener(ConfiguracionService.IMPRESORA_NOMBRE, ""));
        if (impresora == null) {
            throw new IllegalStateException("No se encontró la impresora. Revisa la configuración.");
        }

        boolean papel58 = "58".equals(config.obtener(ConfiguracionService.IMPRESORA_ANCHO_MM, "80"));
        DocPrintJob trabajo = impresora.createPrintJob();
        trabajo.print(new SimpleDoc(armarDatos(texto, papel58), DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    // Convierte el recibo (texto con las marcas de ReciboService) en los bytes que entiende la impresora:
    // el logo como imagen, las líneas grandes con su tamaño y el resto como texto normal.
    byte[] armarDatos(String texto, boolean papel58) {
        Charset cp850 = Charset.forName("Cp850");                       // Cp850 trae tildes y la ñ
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        datos.writeBytes(new byte[]{0x1B, 0x40});                       // ESC @  = reiniciar impresora
        String[] lineas = texto.split("\n", -1);
        for (int i = 0; i < lineas.length; i++) {
            String l = lineas[i];
            if (i == lineas.length - 1 && l.isEmpty()) {
                break;                                                  // el texto termina en salto de línea
            }
            char marca = l.isEmpty() ? 0 : l.charAt(0);
            if (marca == ReciboService.LOGO) {
                datos.writeBytes(LogoRecibo.escpos(papel58 ? 160 : 240));
                continue;
            }
            if (marca == ReciboService.GRANDE || marca == ReciboService.MUY_GRANDE) {
                // ESC ! n: 0x08 negrita, 0x10 doble alto, 0x20 doble ancho
                datos.writeBytes(new byte[]{0x1B, 0x21, (byte) (marca == ReciboService.GRANDE ? 0x18 : 0x38)});
                datos.writeBytes(l.substring(1).getBytes(cp850));
                datos.writeBytes(new byte[]{0x1B, 0x21, 0x00, '\n'});
                continue;
            }
            datos.writeBytes(l.getBytes(cp850));
            datos.write('\n');
        }
        datos.writeBytes(new byte[]{'\n', '\n', '\n', '\n'});           // espacio para poder rasgar
        datos.writeBytes(new byte[]{0x1D, 0x56, 0x42, 0x00});           // GS V B 0 = cortar el papel
        return datos.toByteArray();
    }

    // Busca la impresora por nombre; si el nombre está vacío usa la predeterminada.
    private PrintService buscar(String nombre) {
        if (nombre.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (s.getName().equalsIgnoreCase(nombre)) {
                return s;
            }
        }
        return null;
    }
}
