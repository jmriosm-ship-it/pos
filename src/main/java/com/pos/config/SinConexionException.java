package com.pos.config;

import java.sql.SQLException;

/** No hay conexión con el servidor de la base de datos (sin internet, sin luz, servidor caído...). */
public class SinConexionException extends SQLException {
    public SinConexionException(String mensaje) {
        super(mensaje, "08001");
    }

    public SinConexionException(String mensaje, Throwable causa) {
        super(mensaje, "08001", causa);
    }
}
