package org.example.mail.common;

import java.util.*;

public class Response {
    private String requestId;
    private String status;
    private String message;
    private final Map<String, String> payload = new LinkedHashMap<>();

    public Response() {
    }

    public Response(String requestId, String status, String message) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
    }

    public static Response ok(String requestId, String message) {
        return new Response(requestId, Protocol.STATUS_OK, message);
    }

    public static Response error(String requestId, String message) {
        return new Response(requestId, Protocol.STATUS_ERROR, message);
    }

    public static Response status(String requestId, String status, String message) {
        return new Response(requestId, status, message);
    }

    public boolean isOk() {
        return Protocol.STATUS_OK.equals(status);
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public Response put(String key, String value) {
        if (value != null) {
            this.payload.put(key, value);
        }
        return this;
    }

    public String get(String key) {
        return this.payload.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        return this.payload.getOrDefault(key, defaultValue);
    }

    @Override
    public String toString() {
        return "Response{" +
                "id='" + requestId + '\'' +
                ", status='" + status + '\'' +
                ", msg='" + message + '\'' +
                ", payloadKeys=" + payload.keySet() +
                '}';
    }
}
