package org.example.mail.client.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MailItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String senderIp = "127.0.0.1";
    private int senderPort = 0;
    private String mailId;
    private String sender;
    private String recipient;
    private String subject;
    private String body;
    private String createdAt;
    private boolean readState;
    private MailFolder folder = MailFolder.INBOX;

    public String getSenderIp() {
        return senderIp;
    }

    public void setSenderIp(String senderIp) {
        this.senderIp = senderIp;
    }

    public int getSenderPort() {
        return senderPort;
    }

    public void setSenderPort(int senderPort) {
        this.senderPort = senderPort;
    }

    public MailItem() {
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public MailItem(String mailId, String sender, String recipient, String subject, String body) {
        this.mailId = mailId;
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.readState = false;
        this.folder = MailFolder.INBOX;
    }

    public String getMailId() {
        return mailId;
    }

    public void setMailId(String mailId) {
        this.mailId = mailId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isReadState() {
        return readState;
    }

    public void setReadState(boolean readState) {
        this.readState = readState;
    }

    public MailFolder getFolder() {
        return folder;
    }

    public void setFolder(MailFolder folder) {
        this.folder = folder;
    }

    public String getPreview(int maxChars) {
        if (body == null || body.trim().isEmpty()) {
            return "(No content)";
        }
        String singleLine = body.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= maxChars) {
            return singleLine;
        }
        return singleLine.substring(0, maxChars) + "...";
    }

    @Override
    public String toString() {
        return (readState ? "" : "● ") + (subject != null && !subject.isEmpty() ? subject : "(No subject)") +
                " - " + (sender != null ? sender : "Unknown");
    }
}
