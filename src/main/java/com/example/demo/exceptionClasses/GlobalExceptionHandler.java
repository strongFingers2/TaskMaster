package com.example.demo.exceptionClasses;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFound.class)
    public ResponseEntity<String> handleTaskNotFound(TaskNotFound ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }
    @ExceptionHandler(UnauthorizedAccess.class)
    public ResponseEntity<String> handleUnauthorizedAccess(UnauthorizedAccess ex) {
        return ResponseEntity.status(403).body(ex.getMessage());
    }
}
