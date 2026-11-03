package com.pos.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexion: único lugar del programa donde se abre la conexión a MySQL.
 * Todos los DAO (clases que leen/escriben datos) piden la conexión aquí,
 * así si cambia la contraseña o el servidor solo se edita este archivo.
 */
public class Conexion {

    // Dirección de la base de datos: servidor:puerto/nombre_base
    private static final String URL =
            "jdbc:mysql://localhost:3306/pos2?useSSL=false&serverTimezone=America/Bogota&allowPublicKeyRetrieval=true";
    private static final String USUARIO = "root";
    private static final String CLAVE = "root";   // <-- pon aquí la clave de tu MySQL

    // Constructor privado: esta clase solo tiene métodos estáticos, no se instancia.
    private Conexion() {
    }

    /** Abre y devuelve una conexión nueva. Quien la pida debe cerrarla (try-with-resources). */
    public static Connection obtener() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }

    // Datos para los programas de MySQL que se ejecutan aparte (mysqldump / mysql),
    // que usan las copias de seguridad. Así la clave sigue viviendo en UN solo lugar.
    public static String usuario() {
        return USUARIO;
    }

    public static String clave() {
        return CLAVE;
    }

    public static String baseDatos() {
        return "pos2";
    }
}
