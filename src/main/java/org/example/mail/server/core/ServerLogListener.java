package org.example.mail.server.core;

@FunctionalInterface
public interface ServerLogListener {
    void onLog(String level, String message);
}
