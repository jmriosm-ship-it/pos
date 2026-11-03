package com.pos;

import com.pos.modelo.Usuario;
import com.pos.servicio.AutenticacionService;

public class PruebaConexion {
    public static void main(String[] args) throws Exception {
        AutenticacionService auth = new AutenticacionService();
        auth.crearAdminSiNoExiste();

        Usuario ok = auth.login("admin", "admin123");
        System.out.println("Clave correcta: " + (ok != null ? "ENTRÓ como " + ok : "RECHAZADO"));

        Usuario mal = auth.login("admin", "otraclave");
        System.out.println("Clave incorrecta: " + (mal != null ? "ENTRÓ" : "RECHAZADO"));
    }
}