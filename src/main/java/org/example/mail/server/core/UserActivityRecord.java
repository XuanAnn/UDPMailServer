package org.example.mail.server.core;

public class UserActivityRecord {

    private String username;
    private boolean connected;
    private boolean registered;
    private String registeredTime;
    private boolean loggedIn;
    private String loginTime;
    private String logoutTime;
    private String clientIp;
    private int clientPort;
    private String currentActivity;
    private String lastActiveTime;

    public UserActivityRecord(String username) {
        this.username = username != null ? username : "Unknown";
        this.connected = false;
        this.registered = false;
        this.registeredTime = "-";
        this.loggedIn = false;
        this.loginTime = "-";
        this.logoutTime = "-";
        this.clientIp = "-";
        this.clientPort = 0;
        this.currentActivity = "Chờ";
        this.lastActiveTime = "-";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public String getRegisteredTime() {
        return registeredTime;
    }

    public void setRegisteredTime(String registeredTime) {
        this.registeredTime = registeredTime;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    public String getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(String logoutTime) {
        this.logoutTime = logoutTime;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public String getCurrentActivity() {
        return currentActivity;
    }

    public void setCurrentActivity(String currentActivity) {
        this.currentActivity = currentActivity;
    }

    public String getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(String lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
}
