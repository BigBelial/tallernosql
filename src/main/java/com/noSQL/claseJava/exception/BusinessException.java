package com.noSQL.claseJava.exception;

// Se lanza cuando se rompe una regla del negocio (libro ya prestado, reseña repetida, límite de préstamos...)
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
