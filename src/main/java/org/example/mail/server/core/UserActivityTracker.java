package org.example.mail.server.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserActivityTracker {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<String, UserActivityRecord> records = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public synchronized void addChangeListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }

    public synchronized void initUsers(List<Map<String, String>> accounts) {
        if (accounts == null) return;
        for (Map<String, String> acc : accounts) {
            String username = acc.get("username");
            if (username != null && !username.trim().isEmpty()) {
                String key = username.trim().toLowerCase();
                UserActivityRecord r = records.computeIfAbsent(key, k -> new UserActivityRecord(username.trim()));
                r.setRegistered(true);
                String createdAt = acc.get("createdat");
                r.setRegisteredTime(createdAt != null ? createdAt : "Đã đăng ký");
            }
        }
        notifyListeners();
    }

    public void recordConnection(String username, String ip, int port, String activity) {
        if (username == null || username.trim().isEmpty()) {
            return; // Never create a dummy user for unauthenticated connections
        }
        String user = username.trim();
        UserActivityRecord r = records.computeIfAbsent(user.toLowerCase(), k -> new UserActivityRecord(user));
        r.setConnected(true);
        r.setClientIp(ip);
        r.setClientPort(port);
        r.setCurrentActivity("[" + ip + ":" + port + "] " + activity);
        r.setLastActiveTime(LocalDateTime.now().format(SHORT_TIME_FMT));
        notifyListeners();
    }

    public void recordRegister(String username, String ip, int port) {
        if (username == null || username.trim().isEmpty()) return;
        String user = username.trim();
        String nowStr = LocalDateTime.now().format(TIME_FMT);
        UserActivityRecord r = records.computeIfAbsent(user.toLowerCase(), k -> new UserActivityRecord(user));
        r.setUsername(user);
        r.setRegistered(true);
        r.setRegisteredTime(nowStr);
        r.setConnected(true);
        r.setClientIp(ip);
        r.setClientPort(port);
        r.setCurrentActivity("[" + ip + ":" + port + "] Đăng ký");
        r.setLastActiveTime(LocalDateTime.now().format(SHORT_TIME_FMT));
        notifyListeners();
    }

    public void recordLogin(String username, String ip, int port) {
        if (username == null || username.trim().isEmpty()) return;
        String user = username.trim();
        String nowStr = LocalDateTime.now().format(TIME_FMT);
        UserActivityRecord r = records.computeIfAbsent(user.toLowerCase(), k -> new UserActivityRecord(user));
        r.setUsername(user);
        r.setRegistered(true);
        r.setConnected(true);
        r.setLoggedIn(true);
        r.setLoginTime(nowStr);
        r.setLogoutTime("Đang hoạt động (Online)");
        r.setClientIp(ip);
        r.setClientPort(port);
        r.setCurrentActivity("[" + ip + ":" + port + "] Đăng nhập");
        r.setLastActiveTime(LocalDateTime.now().format(SHORT_TIME_FMT));
        notifyListeners();
    }

    public void recordLogout(String username, String ip, int port) {
        if (username == null || username.trim().isEmpty()) return;
        String user = username.trim();
        String nowStr = LocalDateTime.now().format(TIME_FMT);
        UserActivityRecord r = records.computeIfAbsent(user.toLowerCase(), k -> new UserActivityRecord(user));
        r.setConnected(false);
        r.setLoggedIn(false);
        r.setLogoutTime(nowStr);
        r.setClientIp(ip);
        r.setClientPort(port);
        r.setCurrentActivity("[" + ip + ":" + port + "] Đăng xuất");
        r.setLastActiveTime(LocalDateTime.now().format(SHORT_TIME_FMT));
        notifyListeners();
    }

    public void recordActivity(String username, String ip, int port, String activity) {
        if (username == null || username.trim().isEmpty()) {
            return; // Ignore anonymous/unauthenticated probes
        }
        String user = username.trim();
        UserActivityRecord r = records.computeIfAbsent(user.toLowerCase(), k -> new UserActivityRecord(user));
        r.setConnected(true);
        r.setClientIp(ip);
        r.setClientPort(port);
        r.setCurrentActivity("[" + ip + ":" + port + "] " + activity);
        r.setLastActiveTime(LocalDateTime.now().format(SHORT_TIME_FMT));
        notifyListeners();
    }

    public UserActivityRecord getRecord(String username) {
        if (username == null) return null;
        return records.get(username.trim().toLowerCase());
    }

    public void recordDisconnect(String username) {
        if (username == null) return;
        UserActivityRecord r = records.get(username.trim().toLowerCase());
        if (r != null) {
            r.setConnected(false);
            r.setLoggedIn(false);
            notifyListeners();
        }
    }

    public List<UserActivityRecord> getAllRecords() {
        List<UserActivityRecord> list = new ArrayList<>();
        for (UserActivityRecord r : records.values()) {
            // Only include real registered user accounts, never raw IP keys
            if (r.getUsername() != null && !r.getUsername().contains(":") && r.isRegistered()) {
                list.add(r);
            }
        }
        list.sort((a, b) -> {
            if (a.isLoggedIn() != b.isLoggedIn()) {
                return a.isLoggedIn() ? -1 : 1;
            }
            if (a.isConnected() != b.isConnected()) {
                return a.isConnected() ? -1 : 1;
            }
            return a.getUsername().compareToIgnoreCase(b.getUsername());
        });
        return list;
    }
}
