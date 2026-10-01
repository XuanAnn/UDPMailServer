package org.example.mail.client.model;

import java.time.LocalDateTime;

public class UserSession {
    private String username;
    private String token;
    private LocalDateTime loginTime;

    public UserSession() {}

    public UserSession(String username, String token) {
        this.username = username;
        this.token = token;
        this.loginTime = LocalDateTime.now();
    }

    private java.util.List<String> accountFiles = new java.util.ArrayList<>();

    public boolean isValid() {
        return username != null && !username.isEmpty() && token != null && !token.isEmpty();
    }

    public void clear() {
        this.username = null;
        this.token = null;
        this.loginTime = null;
        this.accountFiles.clear();
    }

    public java.util.List<String> getAccountFiles() {
        return accountFiles;
    }

    public void setAccountFiles(java.util.List<String> accountFiles) {
        this.accountFiles = accountFiles != null ? accountFiles : new java.util.ArrayList<>();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}
