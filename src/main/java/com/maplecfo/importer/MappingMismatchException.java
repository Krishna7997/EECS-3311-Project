package com.maplecfo.importer;

/** The chosen profile does not fit the file: the user must create or pick another mapping. */
public class MappingMismatchException extends RuntimeException {

    public MappingMismatchException(String message) {
        super(message);
    }
}
