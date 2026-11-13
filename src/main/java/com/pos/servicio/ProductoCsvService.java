package com.pos.servicio;

import com.pos.dao.ProductoDAO;
import com.pos.modelo.Categoria;
import com.pos.modelo.Producto;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importar y exportar el inventario en un archivo CSV (Excel lo abre y lo guarda). Sirve para cargar muchos perfumes
 * de una vez: se descarga el archivo, se llena en Excel y se vuelve a subir.
 *
 * Columnas (separadas por ; ): codigo ; nombre ; categoria ; marca ; tamano ; costo ; precio ; stock ; minimo
 * Al importar, el código identifica el producto: si ya existe se actualizan sus datos (menos el stock, que solo
 * cambia con compras, ventas y ajustes); si no existe se crea con el stock del archivo. Sin código, se inventa uno.
 */
public class ProductoCsvService {

    public static final List<String> COLUMNAS = List.of("codigo", "nombre", "categoria", "marca", "tamano", "costo", "precio", "stock", "minimo");

    /** Lo que pasó al importar. */
    public record Resultado(int creados, int actualizados, List<String> errores) {
    }

    private final ProductoService productos = new ProductoService();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CategoriaService categorias = new CategoriaService();

    // ---------------------------------------------------------------- exportar

    public int exportar(Path archivo) throws IOException, SQLException {
        List<Producto> lista = productoDAO.listar();
        Map<Integer, String> nombresCategoria = new HashMap<>();
        for (Categoria c : categorias.listar()) {
            nombresCategoria.put(c.getId(), c.getNombre());
        }
        StringBuilder sb = new StringBuilder("﻿").append(String.join(";", COLUMNAS)).append("\r\n");
        for (Producto p : lista) {
            sb.append(String.join(";",
                    celda(p.getCodigo()), celda(p.getNombre()),
                    celda(p.getIdCategoria() == null ? "" : nombresCategoria.getOrDefault(p.getIdCategoria(), "")),
                    celda(p.getMarca()), celda(p.getCapacidad()),
                    numero(p.getPrecioCompra()), numero(p.getPrecioVenta()),
                    String.valueOf(p.getStock()), String.valueOf(p.getStockMinimo()))).append("\r\n");
        }
        if (lista.isEmpty()) {   // sin productos todavía: un ejemplo, para que se vea cómo llenarlo
            sb.append("P001;Bleu de Chanel;Hombre;Chanel;100 ml;260000;350000;5;2\r\n");
        }
        Files.writeString(archivo, sb.toString(), StandardCharsets.UTF_8);
        return lista.size();
    }

    private static String numero(BigDecimal v) {
        return v == null ? "0" : v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String celda(String t) {
        String s = t == null ? "" : t;
        return s.matches(".*[;\"\\n\\r].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    // ---------------------------------------------------------------- importar

    public Resultado importar(Path archivo) throws IOException, SQLException {
        List<List<String>> filas = leer(archivo);
        List<String> errores = new ArrayList<>();
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
        // La primera fila dice qué trae cada columna (en cualquier orden y con o sin tildes).
        Map<String, Integer> pos = new HashMap<>();
        List<String> cabecera = filas.get(0);
        for (int i = 0; i < cabecera.size(); i++) {
            pos.put(sinTildes(cabecera.get(i)), i);
        }
        if (!pos.containsKey("nombre") || !pos.containsKey("precio")) {
            throw new IllegalArgumentException("La primera fila debe tener al menos las columnas \"nombre\" y \"precio\". "
                    + "Descarga el archivo con \"Exportar CSV\" para ver el formato.");
        }
        Map<String, Integer> idsCategoria = new HashMap<>();
        for (Categoria c : categorias.listar()) {
            idsCategoria.put(sinTildes(c.getNombre()), c.getId());
        }

        // Para las filas sin código: si ya hay un producto con ese mismo nombre se actualiza ese (así no se duplica al repetir la importación).
        Map<String, String> codigoPorNombre = new HashMap<>();
        for (Producto existente : productoDAO.listar()) {
            codigoPorNombre.putIfAbsent(sinTildes(existente.getNombre()), existente.getCodigo());
        }
        int creados = 0;
        int actualizados = 0;
        int sinCodigo = 0;
        for (int n = 1; n < filas.size(); n++) {
            List<String> f = filas.get(n);
            int numeroFila = n + 1;   // como se ve en Excel (la fila 1 es la cabecera)
            String nombre = dato(f, pos, "nombre");
            if (nombre.isEmpty() && f.stream().allMatch(String::isBlank)) {
                continue;   // fila vacía
            }
            try {
                String codigo = dato(f, pos, "codigo");
                if (codigo.isEmpty()) {
                    codigo = codigoPorNombre.getOrDefault(sinTildes(nombre), "");
                }
                if (codigo.isEmpty()) {
                    codigo = "AUTO" + System.currentTimeMillis() % 1_000_000 + String.format("%02d", ++sinCodigo);
                }
                Producto existente = productoDAO.buscarPorCodigo(codigo);
                Producto p = existente != null ? existente : new Producto();
                p.setCodigo(codigo);
                p.setNombre(nombre);
                p.setMarca(dato(f, pos, "marca"));
                p.setCapacidad(dato(f, pos, "tamano"));
                if (pos.containsKey("costo")) {
                    p.setPrecioCompra(plata(dato(f, pos, "costo")));
                } else if (existente == null) {
                    p.setPrecioCompra(BigDecimal.ZERO);
                }
                p.setPrecioVenta(plata(dato(f, pos, "precio")));
                if (pos.containsKey("minimo") || existente == null) {
                    p.setStockMinimo(entero(dato(f, pos, "minimo")));
                }
                if (existente == null) {
                    p.setStock(entero(dato(f, pos, "stock")));
                    p.setActivo(true);
                }
                String cat = dato(f, pos, "categoria");
                if (!cat.isEmpty()) {
                    Integer id = idsCategoria.get(sinTildes(cat));
                    if (id == null) {
                        Categoria nueva = new Categoria(cat);
                        categorias.guardar(nueva);
                        id = nueva.getId();
                        idsCategoria.put(sinTildes(cat), id);
                    }
                    p.setIdCategoria(id);
                }
                productos.guardar(p);
                if (existente == null) {
                    creados++;
                } else {
                    actualizados++;
                }
            } catch (IllegalArgumentException e) {
                errores.add("Fila " + numeroFila + " (" + (nombre.isEmpty() ? "sin nombre" : nombre) + "): " + e.getMessage());
            }
        }
        return new Resultado(creados, actualizados, errores);
    }

    private static String dato(List<String> fila, Map<String, Integer> pos, String columna) {
        Integer i = pos.get(columna);
        return i == null || i >= fila.size() ? "" : fila.get(i).trim();
    }

    // "260.000", "$260000" o "260,000" -> 260000 (los precios son en pesos, sin decimales).
    private static BigDecimal plata(String texto) {
        String digitos = texto.replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digitos);
    }

    private static int entero(String texto) {
        String digitos = texto.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            return 0;
        }
        if (digitos.length() > 9) {
            throw new IllegalArgumentException("El número \"" + texto + "\" es demasiado grande.");
        }
        return Integer.parseInt(digitos);
    }

    private static String sinTildes(String t) {
        return Normalizer.normalize(t == null ? "" : t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase();
    }

    // ---------------------------------------------------------------- leer el archivo

    // Lee el CSV: separador ; o , (se detecta), comillas dobles para textos con separador, UTF-8 o, si viene de Excel
    // en español sin UTF-8, Windows-1252.
    static List<List<String>> leer(Path archivo) throws IOException {
        byte[] bytes = Files.readAllBytes(archivo);
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            texto = new String(bytes, Charset.forName("windows-1252"));
        }
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        String primera = texto.lines().findFirst().orElse("");
        char sep = primera.chars().filter(c -> c == ';').count() >= primera.chars().filter(c -> c == ',').count() ? ';' : ',';

        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        celda.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    celda.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == sep) {
                fila.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                fila.add(celda.toString());
                celda.setLength(0);
                filas.add(fila);
                fila = new ArrayList<>();
            } else {
                celda.append(c);
            }
        }
        if (celda.length() > 0 || !fila.isEmpty()) {
            fila.add(celda.toString());
            filas.add(fila);
        }
        return filas;
    }
}
