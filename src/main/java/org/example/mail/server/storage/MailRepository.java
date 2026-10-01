package org.example.mail.server.storage;

import org.example.mail.client.model.MailFolder;
import org.example.mail.client.model.MailItem;
import org.example.mail.common.Protocol;
import org.example.mail.common.Validator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MailRepository {

    private final Path rootPath;

    public MailRepository(String dataPath) {
        // Mailboxes are stored directly inside each user's account path: data/accounts/<username>/
        this.rootPath = Paths.get(dataPath, "accounts");
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize accounts/mailboxes directory: " + rootPath, e);
        }
    }

    private Path getUserMailboxDir(String username) {
        if (!Validator.isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid username format: " + username);
        }
        return rootPath.resolve(username);
    }

    private Path getFolderDir(String username, String folder) {
        String cleanFolder = folder.toLowerCase().trim();
        if (!Validator.isValidFolder(folder)) {
            throw new IllegalArgumentException("Invalid mailbox folder: " + folder);
        }
        return getUserMailboxDir(username).resolve(cleanFolder);
    }

    public synchronized void initUserMailboxes(String username) throws IOException {
        Path userDir = getUserMailboxDir(username);
        Path inbox = userDir.resolve("inbox");
        Files.createDirectories(inbox);
        Files.createDirectories(userDir.resolve("sent"));
        Files.createDirectories(userDir.resolve("drafts"));
        Files.createDirectories(userDir.resolve("trash"));

        String welcomeMsg = "Thank you for using this service. we hope that you will feel comfortabl........";

        // Create new_email.txt directly in account folder
        Path rootNewEmail = userDir.resolve("new_email.txt");
        if (!Files.exists(rootNewEmail)) {
            Files.writeString(rootNewEmail, welcomeMsg, StandardCharsets.UTF_8);
        }

        // Also put in inbox as welcome mail
        Path inboxNewEmail = inbox.resolve("new_email.txt");
        if (!Files.exists(inboxNewEmail)) {
            String formatted = "Id: new_email\n" +
                               "From: System\n" +
                               "Sender-IP: 127.0.0.1\n" +
                               "Sender-Port: 5000\n" +
                               "To: " + username + "\n" +
                               "Subject: Thank you for using this service. we hope that you will feel comfortabl........\n" +
                               "Date: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                               "Read: false\n" +
                               "Folder: INBOX\n" +
                               "\n---BODY---\n" +
                               welcomeMsg;
            Files.writeString(inboxNewEmail, formatted, StandardCharsets.UTF_8);
        }
    }

    public synchronized void saveMail(String username, String folder, MailItem mail) throws IOException {
        initUserMailboxes(username);
        Path folderDir = getFolderDir(username, folder);

        if (mail.getMailId() == null || mail.getMailId().isEmpty()) {
            mail.setMailId(UUID.randomUUID().toString());
        }

        if (!Validator.isValidMailId(mail.getMailId())) {
            throw new IllegalArgumentException("Invalid mail ID format: " + mail.getMailId());
        }

        Path targetFile = folderDir.resolve(mail.getMailId() + ".txt");
        Path tmpFile = folderDir.resolve(mail.getMailId() + ".tmp");

        StringBuilder sb = new StringBuilder();
        sb.append("Id: ").append(mail.getMailId()).append("\n");
        sb.append("From: ").append(mail.getSender() != null ? mail.getSender() : "").append("\n");
        sb.append("Sender-IP: ").append(mail.getSenderIp() != null ? mail.getSenderIp() : "127.0.0.1").append("\n");
        sb.append("Sender-Port: ").append(mail.getSenderPort()).append("\n");
        sb.append("To: ").append(mail.getRecipient() != null ? mail.getRecipient() : "").append("\n");
        sb.append("Subject: ").append(mail.getSubject() != null ? mail.getSubject() : "").append("\n");
        sb.append("Date: ").append(mail.getCreatedAt() != null ? mail.getCreatedAt() : "").append("\n");
        sb.append("Read: ").append(mail.isReadState()).append("\n");
        sb.append("Folder: ").append(folder.toUpperCase()).append("\n");
        sb.append("\n---BODY---\n");
        sb.append(mail.getBody() != null ? mail.getBody() : "");

        Files.writeString(tmpFile, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized List<MailItem> listMails(String username, String folder) {
        List<MailItem> mails = new ArrayList<>();
        try {
            initUserMailboxes(username);
            Path folderDir = getFolderDir(username, folder);
            if (!Files.exists(folderDir)) return mails;

            try (var stream = Files.list(folderDir)) {
                List<Path> files = stream
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return (name.endsWith(".txt") || name.endsWith(".mail")) && !name.equals("account.txt");
                        })
                        .collect(Collectors.toList());

                for (Path p : files) {
                    try {
                        MailItem item = parseMailFile(p);
                        if (item != null) {
                            mails.add(item);
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to parse mail file " + p + ": " + ex.getMessage());
                    }
                }
            }

            // Sort newest first
            mails.sort((a, b) -> {
                String d1 = a.getCreatedAt() != null ? a.getCreatedAt() : "";
                String d2 = b.getCreatedAt() != null ? b.getCreatedAt() : "";
                return d2.compareTo(d1);
            });

        } catch (IOException e) {
            System.err.println("Error listing mails: " + e.getMessage());
        }
        return mails;
    }

    public synchronized MailItem getMail(String username, String folder, String mailId) {
        try {
            Path folderDir = getFolderDir(username, folder);
            Path mailFile = folderDir.resolve(mailId + ".txt");
            if (!Files.exists(mailFile)) {
                // Check fallback .mail
                mailFile = folderDir.resolve(mailId + ".mail");
                if (!Files.exists(mailFile)) {
                    return null;
                }
            }
            return parseMailFile(mailFile);
        } catch (Exception e) {
            System.err.println("Error getting mail " + mailId + ": " + e.getMessage());
            return null;
        }
    }

    public synchronized boolean markAsRead(String username, String folder, String mailId, boolean read) {
        MailItem mail = getMail(username, folder, mailId);
        if (mail == null) return false;
        mail.setReadState(read);
        try {
            saveMail(username, folder, mail);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean moveMail(String username, String srcFolder, String dstFolder, String mailId) {
        try {
            Path srcFile = getFolderDir(username, srcFolder).resolve(mailId + ".txt");
            if (!Files.exists(srcFile)) {
                srcFile = getFolderDir(username, srcFolder).resolve(mailId + ".mail");
                if (!Files.exists(srcFile)) return false;
            }

            MailItem mail = parseMailFile(srcFile);
            if (mail == null) return false;

            mail.setFolder(MailFolder.fromString(dstFolder));
            saveMail(username, dstFolder, mail);
            Files.deleteIfExists(srcFile);
            return true;
        } catch (IOException e) {
            System.err.println("Error moving mail " + mailId + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean deletePermanently(String username, String folder, String mailId) {
        try {
            Path file = getFolderDir(username, folder).resolve(mailId + ".txt");
            boolean deleted = Files.deleteIfExists(file);
            if (!deleted) {
                Path alt = getFolderDir(username, folder).resolve(mailId + ".mail");
                deleted = Files.deleteIfExists(alt);
            }
            return deleted;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized int countUnreadMails(String username, String folder) {
        return (int) listMails(username, folder).stream().filter(m -> !m.isReadState()).count();
    }

    public synchronized int countAllMails() {
        int total = 0;
        if (!Files.exists(rootPath)) return 0;
        try (var userStream = Files.list(rootPath)) {
            for (Path userDir : userStream.filter(Files::isDirectory).toList()) {
                for (String folder : List.of("inbox", "sent", "drafts", "trash")) {
                    Path f = userDir.resolve(folder);
                    if (Files.exists(f)) {
                        try (var mailStream = Files.list(f)) {
                            total += mailStream.filter(p -> {
                                String n = p.getFileName().toString();
                                return (n.endsWith(".txt") || n.endsWith(".mail")) && !n.equals("account.txt");
                            }).count();
                        }
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return total;
    }

    private MailItem parseMailFile(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        MailItem mail = new MailItem();

        int bodyIdx = content.indexOf("\n---BODY---\n");
        if (bodyIdx < 0) {
            String fileName = path.getFileName().toString();
            String mailId = fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - 4) : fileName;
            mail.setMailId(mailId);
            mail.setSender("System");
            mail.setSenderIp("127.0.0.1");
            mail.setSenderPort(5000);
            mail.setRecipient("Me");
            mail.setSubject("Thank you for using this service. we hope that you will feel comfortabl........");
            mail.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            mail.setReadState(false);
            mail.setFolder(MailFolder.INBOX);
            mail.setBody(content.trim());
            return mail;
        }

        String headerPart = content.substring(0, bodyIdx);
        String bodyPart = content.substring(bodyIdx + "\n---BODY---\n".length());

        String[] lines = headerPart.split("\r?\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String val = line.substring(colon + 1).trim();
                switch (key) {
                    case "id" -> mail.setMailId(val);
                    case "from" -> mail.setSender(val);
                    case "sender-ip" -> mail.setSenderIp(val);
                    case "sender-port" -> {
                        try {
                            mail.setSenderPort(Integer.parseInt(val));
                        } catch (NumberFormatException ignored) {}
                    }
                    case "to" -> mail.setRecipient(val);
                    case "subject" -> mail.setSubject(val);
                    case "date" -> mail.setCreatedAt(val);
                    case "read" -> mail.setReadState(Boolean.parseBoolean(val));
                    case "folder" -> mail.setFolder(MailFolder.fromString(val));
                }
            }
        }
        mail.setBody(bodyPart);
        return mail;
    }
}
