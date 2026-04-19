package com.nium.virtualcard.dto;

import java.time.Instant;

/**
 * Response DTO for error responses.
 * Returned when API requests fail.
 */
public class ErrorResponse {
    /**
     * Timestamp when the error occurred.
     */
    private Instant timestamp;

    /**
     * HTTP status code of the error.
     */
    private int status;

    /**
     * Error message summarizing the problem.
     */
    private String message;

    /**
     * Additional details about the error.
     */
    private String details;

    // Constructors

    /**
     * No-argument constructor for JSON serialization.
     */
    public ErrorResponse() {
    }

    /**
     * Constructor with basic fields.
     */
    public ErrorResponse(Instant timestamp, int status, String message) {
        this.timestamp = timestamp;
        this.status = status;
        this.message = message;
        this.details = null;
    }

    /**
     * Constructor with all fields.
     */
    public ErrorResponse(Instant timestamp, int status, String message, String details) {
        this.timestamp = timestamp;
        this.status = status;
        this.message = message;
        this.details = details;
    }

    // Getters and Setters

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    // Object methods

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "timestamp=" + timestamp +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", details='" + details + '\'' +
                '}';
    }
}


