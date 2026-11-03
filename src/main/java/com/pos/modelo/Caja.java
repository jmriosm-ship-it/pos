package com.pos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Caja {
    private int id;
    private int idUsuario;

    // usuario que abrio la caja o quien la abre
    private LocalDateTime abiertoEn;
    private LocalDateTime cerradaEn; // sera null mientras la caja siga abierta

    private BigDecimal montoInicial; //efectivo con el que arranca el turno
    private BigDecimal montoEsperado; //lo que el sistema calculo( sale null hata cerrar)
    private BigDecimal montoContado; //lo que el cajero cuenta a mano (null hasta cerrar)
    private BigDecimal diferencia; // contado - esperado (positivo = sobra plata, negativo = falta)

    private String estado; //esta opcion es ABIERTA CERRADA.

    public Caja() {
    }

    // Metodo que nos ayuda a que no pueda el usuario copiar ABIERTO en todos lados
    // o aparezca
    public boolean estaAbierta() {
        return "ABIERTA".equals(estado);
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public LocalDateTime getAbiertoEn() {
        return abiertoEn;
    }

    public void setAbiertoEn(LocalDateTime abiertoEn) {
        this.abiertoEn = abiertoEn;
    }

    public LocalDateTime getCerradaEn() {
        return cerradaEn;
    }

    public void setCerradaEn(LocalDateTime cerradaEn) {
        this.cerradaEn = cerradaEn;
    }

    public BigDecimal getMontoInicial() {
        return montoInicial;
    }

    public void setMontoInicial(BigDecimal montoInicial) {
        this.montoInicial = montoInicial;
    }

    public BigDecimal getMontoEsperado() {
        return montoEsperado;
    }

    public void setMontoEsperado(BigDecimal montoEsperado) {
        this.montoEsperado = montoEsperado;
    }

    public BigDecimal getMontoContado() {
        return montoContado;
    }

    public void setMontoContado(BigDecimal montoContado) {
        this.montoContado = montoContado;
    }

    public BigDecimal getDiferencia() {
        return diferencia;
    }

    public void setDiferencia(BigDecimal diferencia) {
        this.diferencia = diferencia;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    @Override
    public String toString(){
        return "Caja #" + id + "("+estado+")";
    }
}

