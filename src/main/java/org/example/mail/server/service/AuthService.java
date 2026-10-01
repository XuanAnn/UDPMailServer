package org.example.mail.server.service;

import org.example.mail.server.storage.AccountRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private final AccountRepository accountRepository;
    private final Map<String, SessionRecord> activeSessions = new ConcurrentHashMap<>();
    private final long sessionDurationMillis = 24 * 60 * 60 * 1000L; // 24 hours

    public static class ClientEndpoint {
        public final java.net.InetAddress address;
        public final int port;

        public ClientEndpoint(java.net.InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    public static class SessionRecord {
        private final String username;
        private final long expiresAt;
        private volatile java.net.InetAddress address;
        private volatile int port;

        public SessionRecord(String username, long expiresAt, java.net.InetAddress address, int port) {
            this.username = username;
            this.expiresAt = expiresAt;
            this.address = address;
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public java.net.InetAddress getAddress() {
            return address;
        }

        public void setAddress(java.net.InetAddress address) {
            this.address = address;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public AuthService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String login(String username, String password) {
        return login(username, password, null, 0);
    }

    public String login(String username, String password, java.net.InetAddress clientAddress, int clientPort) {
        cleanExpiredSessions();
        if (username == null || password == null) return null;

        boolean valid = accountRepository.verifyPassword(username.trim(), password);
        if (!valid) {
            return null;
        }

        String token = "sess-" + UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + sessionDurationMillis;
        activeSessions.put(token, new SessionRecord(username.trim(), expiry, clientAddress, clientPort));
        return token;
    }

    public void updateEndpoint(String token, java.net.InetAddress address, int port) {
        if (token == null) return;
        SessionRecord s = activeSessions.get(token);
        if (s != null && !s.isExpired() && address != null && port > 0) {
            s.setAddress(address);
            s.setPort(port);
        }
    }

    public ClientEndpoint getEndpointForUser(String username) {
        if (username == null) return null;
        cleanExpiredSessions();
        for (SessionRecord s : activeSessions.values()) {
            if (!s.isExpired() && username.equalsIgnoreCase(s.getUsername()) && s.getAddress() != null && s.getPort() > 0) {
                return new ClientEndpoint(s.getAddress(), s.getPort());
            }
        }
        return null;
    }

    public boolean logout(String token) {
        if (token == null) return false;
        return activeSessions.remove(token) != null;
    }

    public String authenticate(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        SessionRecord session = activeSessions.get(token.trim());
        if (session == null) return null;
        if (session.isExpired()) {
            activeSessions.remove(token.trim());
            return null;
        }
        return session.getUsername();
    }

    public int getActiveSessionCount() {
        cleanExpiredSessions();
        return activeSessions.size();
    }

    public void cleanExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
