package org.example.mail.common;

import java.util.*;

public class Request {
    private String version = Protocol.VERSION;
    private String requestId;
    private String command;
    private String token;
    private final Map<String, String> payload = new LinkedHashMap<>();

    public Request() {
        this.requestId = UUID.randomUUID().toString();
    }

    public Request(String command) {
        this.requestId = UUID.randomUUID().toString();
        this.command = command;
    }

    public Request(String command, String token) {
        this.requestId = UUID.randomUUID().toString();
        this.command = command;
        this.token = token;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public Request put(String key, String value) {
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
        return "Request{" +
                "id='" + requestId + '\'' +
                ", cmd='" + command + '\'' +
                ", token='" + (token != null ? "[SET]" : "null") + '\'' +
                ", payloadKeys=" + payload.keySet() +
                '}';
    }
}
