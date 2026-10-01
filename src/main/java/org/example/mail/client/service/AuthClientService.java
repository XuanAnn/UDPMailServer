package org.example.mail.client.service;

import org.example.mail.client.model.UserSession;
import org.example.mail.client.network.MailClient;
import org.example.mail.common.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AuthClientService {

    public interface AuthListener {
        void onLoginSuccess(UserSession session);
        void onLogout();
    }

    private final MailClient client;
    private final UserSession currentSession = new UserSession();
    private final List<AuthListener> listeners = new ArrayList<>();

    public AuthClientService(MailClient client) {
        this.client = client;
    }

    public void addAuthListener(AuthListener listener) {
        listeners.add(listener);
    }

    public Response register(String username, String password) throws IOException {
        return client.register(username, password);
    }

    public Response login(String username, String password) throws IOException {
        Response response = client.login(username, password);
        if (response.isOk()) {
            String token = response.get("token");
            currentSession.setUsername(username);
            currentSession.setToken(token);
            currentSession.setLoginTime(java.time.LocalDateTime.now());

            String fileCountStr = response.get("fileCount");
            int fileCount = fileCountStr != null ? Integer.parseInt(fileCountStr) : 0;
            List<String> files = new ArrayList<>();
            for (int i = 0; i < fileCount; i++) {
                String f = response.get("file_" + i);
                if (f != null) files.add(f);
            }
            currentSession.setAccountFiles(files);

            for (AuthListener l : listeners) {
                l.onLoginSuccess(currentSession);
            }
        }
        return response;
    }

    public Response logout() {
        if (!currentSession.isValid()) return Response.ok("LOCAL", "Already logged out");
        try {
            Response response = client.logout(currentSession.getToken());
            currentSession.clear();
            for (AuthListener l : listeners) {
                l.onLogout();
            }
            return response;
        } catch (Exception e) {
            currentSession.clear();
            for (AuthListener l : listeners) {
                l.onLogout();
            }
            return Response.ok("LOCAL", "Logged out locally");
        }
    }

    public UserSession getCurrentSession() {
        return currentSession;
    }

    public boolean isLoggedIn() {
        return currentSession.isValid();
    }

    public MailClient getClient() {
        return client;
    }
}
