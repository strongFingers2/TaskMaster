package com.example.demo.exceptionClasses;

public class UnauthorizedAccess extends RuntimeException {
    long id;
    public UnauthorizedAccess(long id, String message) {
        super(message);
        this.id = id;
    }
}
