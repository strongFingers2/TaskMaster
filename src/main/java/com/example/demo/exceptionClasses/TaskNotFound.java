package com.example.demo.exceptionClasses;


public class TaskNotFound extends RuntimeException {
    long taskId;
    public TaskNotFound(long id, String message) {
        super(message);
        this.taskId = id;
    }
}
