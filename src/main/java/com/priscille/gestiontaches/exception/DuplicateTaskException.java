package com.priscille.gestiontaches.exception;

public class DuplicateTaskException extends Exception {

    public DuplicateTaskException(String message) {
        super(message);
    }

    public DuplicateTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}