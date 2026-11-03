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

        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        datos.writeBytes(new byte[]{0x1B, 0x40});                       // ESC @  = reiniciar impresora
        datos.writeBytes(texto.getBytes(Charset.forName("Cp850")));     // Cp850 trae tildes y la ñ
        datos.writeBytes(new byte[]{'\n', '\n', '\n', '\n'});           // espacio para poder rasgar
        datos.writeBytes(new byte[]{0x1D, 0x56, 0x42, 0x00});           // GS V B 0 = cortar el papel

        DocPrintJob trabajo = impresora.createPrintJob();
        trabajo.print(new SimpleDoc(datos.toByteArray(), DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
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
