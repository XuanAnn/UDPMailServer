package org.example.mail.client.service;

import org.example.mail.client.model.MailFolder;
import org.example.mail.client.model.MailItem;
import org.example.mail.client.network.MailClient;
import org.example.mail.common.Response;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MailClientService {

    private final MailClient client;
    private final AuthClientService authService;
    private final ExecutorService asyncPool = Executors.newCachedThreadPool();

    public MailClientService(MailClient client, AuthClientService authService) {
        this.client = client;
        this.authService = authService;
    }

    public void addNotificationListener(org.example.mail.client.network.UdpTransport.PushNotificationListener listener) {
        this.client.getTransport().addNotificationListener(listener);
    }

    private <T> void runAsync(AsyncSupplier<T> supplier, AsyncCallback<T> callback) {
        asyncPool.submit(() -> {
            try {
                T result = supplier.get();
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onComplete(result, null));
                }
            } catch (Exception ex) {
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.onComplete(null, ex));
                }
            }
        });
    }

    @FunctionalInterface
    private interface AsyncSupplier<T> {
        T get() throws Exception;
    }

    public void checkConnection(AsyncCallback<Boolean> callback) {
        runAsync(() -> {
            Response resp = client.ping();
            return resp != null && "PONG".equalsIgnoreCase(resp.getStatus());
        }, callback);
    }

    public void fetchFolder(String folder, AsyncCallback<List<MailItem>> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.list(token, folder);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            return MailClient.parseMailList(resp);
        }, callback);
    }

    public void fetchMail(String folder, String mailId, AsyncCallback<MailItem> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.read(token, folder, mailId);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            MailItem mail = new MailItem(
                    resp.get("mailId"),
                    resp.get("from"),
                    resp.get("to"),
                    resp.get("subject"),
                    resp.get("body")
            );
            mail.setSenderIp(resp.getOrDefault("senderIp", "127.0.0.1"));
            try {
                mail.setSenderPort(Integer.parseInt(resp.getOrDefault("senderPort", "0")));
            } catch (Exception ignored) {}
            mail.setCreatedAt(resp.get("date"));
            mail.setReadState(Boolean.parseBoolean(resp.get("read")));
            mail.setFolder(MailFolder.fromString(resp.get("folder")));
            return mail;
        }, callback);
    }

    public void sendMail(String to, String subject, String body, AsyncCallback<String> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.sendEmail(token, to, subject, body);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            return resp.get("mailId");
        }, callback);
    }

    public void saveDraft(String to, String subject, String body, String existingMailId, AsyncCallback<String> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.saveDraft(token, to, subject, body, existingMailId);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            return resp.get("mailId");
        }, callback);
    }

    public void markRead(String folder, String mailId, boolean read, AsyncCallback<Boolean> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.markRead(token, folder, mailId, read);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            return true;
        }, callback);
    }

    public void moveMail(String srcFolder, String dstFolder, String mailId, AsyncCallback<Boolean> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.moveMail(token, srcFolder, dstFolder, mailId);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            return true;
        }, callback);
    }

    public void deleteMail(String folder, String mailId, AsyncCallback<Boolean> callback) {
        runAsync(() -> {
            String token = authService.getCurrentSession().getToken();
            Response resp = client.deleteMail(token, folder, mailId);
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            return true;
        }, callback);
    }

    public MailClient getClient() {
        return client;
    }
}
