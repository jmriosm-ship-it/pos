package com.pos.ui;

import com.pos.modelo.Caja;
import com.pos.modelo.Modulo;
import com.pos.modelo.Usuario;
import com.pos.servicio.CajaService;
import com.pos.servicio.ConfiguracionService;
import com.pos.servicio.Permisos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La ventana principal: menú lateral oscuro a la izquierda, barra superior arriba
 * y, al centro, la pantalla del módulo elegido.
 * El menú solo muestra los módulos que el rol del usuario puede ver (clase Permisos).
 */
public class MainView extends BorderPane {

    private final Usuario usuario = Sesion.usuario();
    private final CajaService cajaService = new CajaService();
    private final ConfiguracionService config = new ConfiguracionService();

    private final StackPane contenido = new StackPane();
    private final Label titulo = new Label();
    private final Label chipCaja = new Label();
    private final Map<Modulo, Button> botones = new EnumMap<>(Modulo.class);

    // Los módulos en el orden en que salen en el menú: Ctrl+1 es el primero, Ctrl+2 el segundo...
    private final List<Modulo> ordenMenu = new ArrayList<>();
    private final EventHandler<KeyEvent> manejadorAtajos = this::atajo;
    private boolean abrirPestanaCaja = false;   // true cuando se entra a Ventas pidiendo la pestaña de caja
    private javafx.scene.Node menuLateral;      // el menú de la izquierda
    private javafx.scene.Node barraSuperior;    // la barra de arriba (título, caja, fecha)

    public MainView(Runnable alCerrarSesion) {
        getStyleClass().add("app-bg");
        menuLateral = crearMenu(alCerrarSesion);
        barraSuperior = crearBarraSuperior();
        setLeft(menuLateral);
        setTop(barraSuperior);
        contenido.setPadding(new Insets(20, 24, 20, 24));
        setCenter(contenido);
        // Ctrl + número se conecta a la ventana cuando esta pantalla entra en ella y se suelta al salir.
        sceneProperty().addListener((o, antigua, nueva) -> {
            if (antigua != null) {
                antigua.removeEventFilter(KeyEvent.KEY_PRESSED, manejadorAtajos);
            }
            if (nueva != null) {
                nueva.addEventFilter(KeyEvent.KEY_PRESSED, manejadorAtajos);
            }
        });
        ir(Modulo.DASHBOARD);   // todos los roles ven el Inicio
    }

    // Ctrl + 1...9 = ir al módulo que tenga ese número en el menú.
    private void atajo(KeyEvent e) {
        if (e.isControlDown() && e.getCode() == KeyCode.H) {   // Ctrl + H = ayuda y documentos legales
            AyudaDialog.abrir();
            e.consume();
            return;
        }
        if (e.isControlDown() && e.getCode() == KeyCode.B) {   // Ctrl + B = mostrar u ocultar el menú
            alternarMenu();
            e.consume();
            return;
        }
        // Ctrl + U = sacar algo del local a cuenta del cupo (cualquiera) · Ctrl + M = pagar sueldo (solo admin).
        if (e.isControlDown() && e.getCode() == KeyCode.U) {
            SacarCupoDialog.abrir(null);
            e.consume();
            return;
        }
        if (e.isControlDown() && e.getCode() == KeyCode.M && usuario.esAdmin()) {
            PagarSueldoDialog.abrir(null);
            e.consume();
            return;
        }
        // Ctrl + K = cambiar mi propia clave (cualquier usuario).
        if (e.isControlDown() && e.getCode() == KeyCode.K) {
            if (ClaveDialog.cambiarMia()) {
                Mensajes.info("Clave cambiada", "Tu clave quedó cambiada.");
            }
            e.consume();
            return;
        }
        if (e.isControlDown() && (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.COMMA)) {
            int p = e.getCode() == KeyCode.DIGIT0 ? 9 : 10;   // Ctrl+0 = módulo 10, Ctrl+coma = módulo 11
            if (p < ordenMenu.size()) {
                ir(ordenMenu.get(p));
                e.consume();
            }
            return;
        }
        if (!e.isControlDown() || e.getCode().ordinal() < KeyCode.DIGIT1.ordinal()
                || e.getCode().ordinal() > KeyCode.DIGIT9.ordinal()) {
            return;
        }
        int posicion = e.getCode().ordinal() - KeyCode.DIGIT1.ordinal();
        if (posicion < ordenMenu.size()) {
            ir(ordenMenu.get(posicion));
            e.consume();
        }
    }

    // Cambia la pantalla del centro y marca el botón activo del menú.
    public void ir(Modulo pedido) {
        // La caja es una pestaña de Ventas: pedirla abre Ventas con esa pestaña elegida.
        Modulo modulo = pedido == Modulo.CAJA ? Modulo.VENTAS : pedido;
        abrirPestanaCaja = pedido == Modulo.CAJA;
        if (!Permisos.puedeVer(usuario, pedido) || botones.get(modulo) == null) {
            return;
        }
        botones.values().forEach(b -> b.getStyleClass().remove("activo"));
        botones.get(modulo).getStyleClass().add("activo");
        titulo.setText(modulo.getTitulo());
        // En Ventas se trabaja en pantalla completa: sin menú ni barra de arriba (el botón Menú de la caja los trae de vuelta).
        boolean pantallaCompleta = modulo == Modulo.VENTAS;
        setLeft(pantallaCompleta ? null : menuLateral);
        setTop(pantallaCompleta ? null : barraSuperior);
        contenido.getChildren().setAll(crearVista(modulo));
        actualizarCaja();
    }

    // Muestra u oculta el menú lateral estando en Ventas (botón Menú o Ctrl + B).
    public void alternarMenu() {
        setLeft(getLeft() == null ? menuLateral : null);
    }

    // Qué pantalla corresponde a cada módulo. Los que faltan muestran "próximamente".
    private Node crearVista(Modulo modulo) {
        return switch (modulo) {
            case DASHBOARD -> new DashboardView(this::ir);
            case VENTAS -> new VentasCajaView(this::actualizarCaja, abrirPestanaCaja, this::alternarMenu, this::ir);
            case PRODUCTOS -> new ProductosView();
            case CATEGORIAS_PROVEEDORES -> new CategoriasProveedoresView();
            case CLIENTES -> new ClientesView();
            case INVENTARIO -> new InventarioView();
            case USUARIOS -> new UsuariosView();
            case REPORTES -> new ReportesView();
            case HISTORIAL -> new HistorialVentasView();
            case COPIAS -> new CopiasView();
            case CONFIGURACION -> new ConfiguracionView();
            case CAJA -> new VentasCajaView(this::actualizarCaja, true, this::alternarMenu, this::ir);
            default -> new ProximamenteView(modulo);
        };
    }

    // Pone en la barra superior si la caja del usuario está abierta o cerrada.
    public void actualizarCaja() {
        boolean abierta = false;
        try {
            Caja caja = cajaService.cajaAbierta(usuario);
            abierta = caja != null;
        } catch (SQLException e) {
            // si falla la consulta se muestra como cerrada
        }
        chipCaja.setText(abierta ? "Caja abierta" : "Caja cerrada");
        chipCaja.getStyleClass().removeAll("chip-ok", "chip-off");
        chipCaja.getStyleClass().add(abierta ? "chip-ok" : "chip-off");
    }

    private VBox crearMenu(Runnable alCerrarSesion) {
        String negocio = "";
        try {
            negocio = config.nombreNegocio();
        } catch (SQLException e) {
            negocio = "Mi Negocio";
        }
        Label marca = new Label(negocio);
        marca.getStyleClass().add("marca");
        Label sub = new Label(Marca.PRODUCTO.toUpperCase());
        sub.getStyleClass().add("marca-sub");
        VBox cabecera = new VBox(2, marca, sub);
        cabecera.getStyleClass().add("marca-caja");

        VBox menu = new VBox(3);
        menu.setPadding(new Insets(14, 12, 0, 12));
        for (Modulo m : Modulo.values()) {
            if (!Permisos.puedeVer(usuario, m) || m == Modulo.CAJA) {   // Caja es una pestaña de Ventas
                continue;
            }
            ordenMenu.add(m);
            int numero = ordenMenu.size();   // 1, 2, 3...

            // Icono + nombre a la izquierda y, a la derecha, el número del atajo (Ctrl + número).
            Label nombre = new Label(m.getTitulo());
            Label teclaNumero = new Label(numero <= 9 ? String.valueOf(numero) : numero == 10 ? "0" : ",");
            teclaNumero.getStyleClass().add("nav-tecla");
            teclaNumero.setVisible(numero <= 11);
            Region hueco = new Region();
            HBox.setHgrow(hueco, Priority.ALWAYS);
            HBox contenido = new HBox(11, Iconos.crear(iconoDe(m), 18), nombre, hueco, teclaNumero);
            contenido.setAlignment(Pos.CENTER_LEFT);

            Button b = new Button();
            b.setGraphic(contenido);
            contenido.prefWidthProperty().bind(b.widthProperty().subtract(30));   // el contenido ocupa todo el botón
            b.getStyleClass().add("nav-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setTooltip(new Tooltip(m.getTitulo() + (numero <= 11 ? "   (Ctrl + " + (numero <= 9 ? numero : numero == 10 ? "0" : ",") + ")" : "")));
            b.setOnAction(e -> ir(m));
            botones.put(m, b);
            menu.getChildren().add(b);
        }

        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);

        Label nombre = new Label(usuario.getNombre());
        nombre.getStyleClass().add("usuario-nombre");
        Label rol = new Label(usuario.getRol());
        rol.getStyleClass().add("usuario-rol");
        Region separador = new Region();
        HBox.setHgrow(separador, Priority.ALWAYS);
        Button salir = new Button();
        salir.setGraphic(Iconos.crear(Iconos.SALIR, 18));
        salir.getStyleClass().add("salir-btn");
        salir.setOnAction(e -> alCerrarSesion.run());
        HBox pie = new HBox(8, new VBox(1, nombre, rol), separador, salir);
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.getStyleClass().add("usuario-caja");
        // Un clic en el nombre abre "Cambiar mi clave" (también con Ctrl + K).
        Tooltip.install(pie, new Tooltip("Clic: cambiar mi clave (Ctrl + K)"));
        pie.setOnMouseClicked(e -> {
            if (ClaveDialog.cambiarMia()) {
                Mensajes.info("Clave cambiada", "Tu clave quedó cambiada.");
            }
        });

        Label ayudaMenu = new Label("Ctrl + número: módulos  ·  Ctrl + H: ayuda");
        ayudaMenu.getStyleClass().add("menu-ayuda");
        ayudaMenu.setPadding(new Insets(10, 4, 10, 20));

        VBox lateral = new VBox(cabecera, menu, ayudaMenu, espacio, pie);
        VBox.setMargin(pie, new Insets(0, 12, 0, 12));
        lateral.getStyleClass().add("sidebar");
        lateral.setPrefWidth(284);
        lateral.setMinWidth(284);
        return lateral;
    }

    private HBox crearBarraSuperior() {
        titulo.getStyleClass().add("titulo-pagina");
        chipCaja.getStyleClass().add("chip");

        String hoy = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.of("es", "CO")));
        Label fecha = new Label(Character.toUpperCase(hoy.charAt(0)) + hoy.substring(1));
        fecha.getStyleClass().add("fecha");

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(14, titulo, espacio, chipCaja, fecha);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("topbar");
        return barra;
    }

    // Icono de cada módulo del menú.
    private String iconoDe(Modulo m) {
        return switch (m) {
            case DASHBOARD -> Iconos.INICIO;
            case PRODUCTOS -> Iconos.PRODUCTOS;
            case CATEGORIAS_PROVEEDORES -> Iconos.CATEGORIAS;
            case VENTAS -> Iconos.VENTAS;
            case CAJA -> Iconos.CAJA;
            case INVENTARIO -> Iconos.INVENTARIO;
            case CLIENTES -> Iconos.CLIENTES;
            case USUARIOS -> Iconos.USUARIOS;
            case REPORTES -> Iconos.REPORTES;
            case HISTORIAL -> Iconos.INVENTARIO;
            case COPIAS -> Iconos.COPIAS;
            case CONFIGURACION -> Iconos.CONFIGURACION;
        };
    }
}
