package com.maplecfo.persistence.sqlite;

/** A database problem, wrapped so callers do not have to handle SQLException. */
public class PersistenceException extends RuntimeException {
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
