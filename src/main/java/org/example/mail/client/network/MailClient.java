package org.example.mail.client.network;

import org.example.mail.client.model.MailItem;
import org.example.mail.common.Protocol;
import org.example.mail.common.Request;
import org.example.mail.common.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MailClient {

    private String host;
    private int port;
    private final UdpTransport transport;

    public MailClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.transport = new UdpTransport();
    }

    public Response ping() throws IOException {
        Request req = new Request(Protocol.CMD_PING);
        return transport.send(req, host, port);
    }

    public Response register(String username, String password) throws IOException {
        Request req = new Request(Protocol.CMD_REGISTER)
                .put("username", username)
                .put("password", password);
        return transport.send(req, host, port);
    }

    public Response login(String username, String password) throws IOException {
        Request req = new Request(Protocol.CMD_LOGIN)
                .put("username", username)
                .put("password", password);
        return transport.send(req, host, port);
    }

    public Response logout(String token) throws IOException {
        Request req = new Request(Protocol.CMD_LOGOUT, token);
        return transport.send(req, host, port);
    }

    public Response list(String token, String folder) throws IOException {
        Request req = new Request(Protocol.CMD_LIST, token)
                .put("folder", folder);
        return transport.send(req, host, port);
    }

    public Response read(String token, String folder, String mailId) throws IOException {
        Request req = new Request(Protocol.CMD_READ, token)
                .put("folder", folder)
                .put("mailId", mailId);
        return transport.send(req, host, port);
    }

    public Response sendEmail(String token, String to, String subject, String body) throws IOException {
        Request req = new Request(Protocol.CMD_SEND, token)
                .put("to", to)
                .put("subject", subject)
                .put("body", body);
        return transport.send(req, host, port);
    }

    public Response saveDraft(String token, String to, String subject, String body, String existingMailId) throws IOException {
        Request req = new Request(Protocol.CMD_SAVE_DRAFT, token)
                .put("to", to)
                .put("subject", subject)
                .put("body", body)
                .put("mailId", existingMailId);
        return transport.send(req, host, port);
    }

    public Response markRead(String token, String folder, String mailId, boolean read) throws IOException {
        Request req = new Request(Protocol.CMD_MARK_READ, token)
                .put("folder", folder)
                .put("mailId", mailId)
                .put("read", String.valueOf(read));
        return transport.send(req, host, port);
    }

    public Response moveMail(String token, String srcFolder, String dstFolder, String mailId) throws IOException {
        Request req = new Request(Protocol.CMD_MOVE, token)
                .put("srcFolder", srcFolder)
                .put("dstFolder", dstFolder)
                .put("mailId", mailId);
        return transport.send(req, host, port);
    }

    public Response deleteMail(String token, String folder, String mailId) throws IOException {
        Request req = new Request(Protocol.CMD_DELETE, token)
                .put("folder", folder)
                .put("mailId", mailId);
        return transport.send(req, host, port);
    }

    public Response getStats() throws IOException {
        Request req = new Request(Protocol.CMD_STATS);
        return transport.send(req, host, port);
    }

    public static List<MailItem> parseMailList(Response response) {
        List<MailItem> list = new ArrayList<>();
        if (!response.isOk()) return list;

        String countStr = response.get("count");
        int count = countStr != null ? Integer.parseInt(countStr) : 0;

        for (int i = 0; i < count; i++) {
            String id = response.get("mail_" + i + "_id");
            String from = response.get("mail_" + i + "_from");
            String senderIp = response.getOrDefault("mail_" + i + "_senderIp", "127.0.0.1");
            int senderPort = 0;
            try {
                senderPort = Integer.parseInt(response.getOrDefault("mail_" + i + "_senderPort", "0"));
            } catch (Exception ignored) {}
            String to = response.get("mail_" + i + "_to");
            String subject = response.get("mail_" + i + "_subject");
            String date = response.get("mail_" + i + "_date");
            boolean read = Boolean.parseBoolean(response.get("mail_" + i + "_read"));
            String preview = response.get("mail_" + i + "_preview");

            MailItem item = new MailItem(id, from, to, subject, preview);
            item.setSenderIp(senderIp);
            item.setSenderPort(senderPort);
            item.setCreatedAt(date);
            item.setReadState(read);
            list.add(item);
        }
        return list;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public UdpTransport getTransport() {
        return transport;
    }
}
