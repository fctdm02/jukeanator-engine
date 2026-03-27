package com.djt.jukeanator_engine.domain.common.controller;
public class ApiError {

    private String message;
    private String error;
    private int status;
    private long timestamp;

    public ApiError(String message, String error, int status) {
        this.message = message;
        this.error = error;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() { return message; }
    public String getError() { return error; }
    public int getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
}