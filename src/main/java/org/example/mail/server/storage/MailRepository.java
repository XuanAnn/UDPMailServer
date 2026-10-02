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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MailRepository {

    private static final Pattern EMAIL_NUM_PATTERN = Pattern.compile("^email_(\\d+)\\.txt$");

    private final Path rootPath;
    private int lastAssignedMailNum = -1;

    public MailRepository(String dataPath) {
        // Mailboxes are stored directly inside each user's account path: accounts/<username>/
        if (dataPath == null || dataPath.trim().isEmpty() || ".".equals(dataPath.trim()) || "data".equalsIgnoreCase(dataPath.trim())) {
            this.rootPath = Paths.get("accounts");
        } else {
            Path p = Paths.get(dataPath);
            this.rootPath = p.endsWith("accounts") ? p : p.resolve("accounts");
        }
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize accounts/mailboxes directory: " + rootPath, e);
        }
    }

    public synchronized String getNextMailId() {
        int diskMax = scanMaxMailNumFromDisk();
        int candidate = Math.max(lastAssignedMailNum, diskMax) + 1;
        lastAssignedMailNum = candidate;
        return String.format("email_%03d", candidate);
    }

    private int scanMaxMailNumFromDisk() {
        if (!Files.exists(rootPath)) return 0;
        try (var stream = Files.walk(rootPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .map(EMAIL_NUM_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(m -> {
                        try {
                            return Integer.parseInt(m.group(1));
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    public Path getUserMailboxDir(String username) {
        if (!Validator.isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid username format: " + username);
        }
        return rootPath.resolve(username);
    }

    public synchronized void saveMailToAccountRoot(String username, String mailId, String body) throws IOException {
        Path userDir = getUserMailboxDir(username);
        String baseId = mailId.endsWith(".txt") ? mailId.substring(0, mailId.length() - 4) : mailId;
        Path file = userDir.resolve(baseId + ".txt");
        Files.writeString(file, body != null ? body : "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Path getFolderDir(String username, String folder) {
        return getUserMailboxDir(username);
    }

    public synchronized void initUserMailboxes(String username) throws IOException {
        Path userDir = getUserMailboxDir(username);
        Files.createDirectories(userDir);

        String welcomeMsg = "Thank you for using this service. we hope that you will feel comfortabl........";

        // Put welcome email directly into user's account directory
        Path welcomeFile = userDir.resolve("new_email.txt");
        if (!Files.exists(welcomeFile)) {
            String welcomeSenderIp = org.example.mail.common.NetworkUtils.getLocalIPv4Address();
            if (welcomeSenderIp == null || welcomeSenderIp.isEmpty()) {
                welcomeSenderIp = "127.0.0.1";
            }
            String formatted = "Id: new_email\n" +
                               "From: System\n" +
                               "Sender-IP: " + welcomeSenderIp + "\n" +
                               "Sender-Port: 5000\n" +
                               "To: " + username + "\n" +
                               "Subject: Thank you for using this service. we hope that you will feel comfortabl........\n" +
                               "Date: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                               "Read: false\n" +
                               "Folder: INBOX\n" +
                               "\n---BODY---\n" +
                               welcomeMsg;
            Files.writeString(welcomeFile, formatted, StandardCharsets.UTF_8);
        }
    }

    public synchronized void saveMail(String username, String folder, MailItem mail) throws IOException {
        initUserMailboxes(username);
        Path userDir = getUserMailboxDir(username);

        if (mail.getMailId() == null || mail.getMailId().trim().isEmpty()) {
            mail.setMailId(getNextMailId());
        }

        String rawId = mail.getMailId().trim();
        String baseId = rawId.endsWith(".txt") ? rawId.substring(0, rawId.length() - 4) : rawId;
        mail.setMailId(baseId);

        if (!Validator.isValidMailId(mail.getMailId())) {
            throw new IllegalArgumentException("Invalid mail ID format: " + mail.getMailId());
        }

        Path targetFile = userDir.resolve(baseId + ".txt");
        Path tmpFile = userDir.resolve(baseId + ".tmp");

        String sIp = mail.getSenderIp();
        if (sIp == null || sIp.isEmpty() || sIp.equals("127.0.0.1") || sIp.startsWith("127.")) {
            String lan = org.example.mail.common.NetworkUtils.getLocalIPv4Address();
            if (lan != null && !lan.isEmpty()) sIp = lan;
            else sIp = "127.0.0.1";
            mail.setSenderIp(sIp);
        }

        String targetFolderStr = (folder != null && !folder.trim().isEmpty() && !"ALL".equalsIgnoreCase(folder) && !"ROOT".equalsIgnoreCase(folder))
                ? folder.toUpperCase()
                : (mail.getFolder() != null ? mail.getFolder().name() : "INBOX");

        StringBuilder sb = new StringBuilder();
        sb.append("Id: ").append(mail.getMailId()).append("\n");
        sb.append("From: ").append(mail.getSender() != null ? mail.getSender() : "").append("\n");
        sb.append("Sender-IP: ").append(sIp).append("\n");
        sb.append("Sender-Port: ").append(mail.getSenderPort()).append("\n");
        sb.append("To: ").append(mail.getRecipient() != null ? mail.getRecipient() : "").append("\n");
        sb.append("Subject: ").append(mail.getSubject() != null ? mail.getSubject() : "").append("\n");
        sb.append("Date: ").append(mail.getCreatedAt() != null ? mail.getCreatedAt() : "").append("\n");
        sb.append("Read: ").append(mail.isReadState()).append("\n");
        sb.append("Folder: ").append(targetFolderStr).append("\n");
        sb.append("\n---BODY---\n");
        sb.append(mail.getBody() != null ? mail.getBody() : "");

        Files.writeString(tmpFile, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized List<MailItem> listMails(String username, String folder) {
        List<MailItem> mails = new ArrayList<>();
        try {
            initUserMailboxes(username);
            Path userDir = getUserMailboxDir(username);
            if (!Files.exists(userDir)) return mails;

            boolean returnAll = (folder == null || folder.trim().isEmpty() || "ALL".equalsIgnoreCase(folder) || "ROOT".equalsIgnoreCase(folder));
            MailFolder targetFolder = returnAll ? null : MailFolder.fromString(folder);

            try (var stream = Files.list(userDir)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return (name.endsWith(".txt") || name.endsWith(".mail"))
                                    && !name.equals("account.txt")
                                    && !name.endsWith(".tmp");
                        })
                        .collect(Collectors.toList());

                for (Path p : files) {
                    try {
                        MailItem item = parseMailFile(p);
                        if (item != null) {
                            if (targetFolder == null || item.getFolder() == targetFolder) {
                                mails.add(item);
                            }
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
            if (mailId == null) return null;
            String baseId = mailId.endsWith(".txt") ? mailId.substring(0, mailId.length() - 4) : mailId;
            Path userDir = getUserMailboxDir(username);
            Path mailFile = userDir.resolve(baseId + ".txt");
            if (!Files.exists(mailFile)) {
                mailFile = userDir.resolve(baseId + ".mail");
                if (!Files.exists(mailFile)) {
                    if (folder != null) {
                        mailFile = userDir.resolve(folder.toLowerCase()).resolve(baseId + ".txt");
                    }
                    if (!Files.exists(mailFile)) {
                        return null;
                    }
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
            String f = mail.getFolder() != null ? mail.getFolder().name() : (folder != null ? folder : "INBOX");
            saveMail(username, f, mail);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean moveMail(String username, String srcFolder, String dstFolder, String mailId) {
        try {
            if (mailId == null) return false;
            String baseId = mailId.endsWith(".txt") ? mailId.substring(0, mailId.length() - 4) : mailId;
            Path userDir = getUserMailboxDir(username);
            Path srcFile = userDir.resolve(baseId + ".txt");
            if (!Files.exists(srcFile)) {
                srcFile = userDir.resolve(baseId + ".mail");
                if (!Files.exists(srcFile)) {
                    if (srcFolder != null) {
                        srcFile = userDir.resolve(srcFolder.toLowerCase()).resolve(baseId + ".txt");
                    }
                    if (!Files.exists(srcFile)) return false;
                }
            }

            MailItem mail = parseMailFile(srcFile);
            if (mail == null) return false;

            mail.setFolder(MailFolder.fromString(dstFolder));
            saveMail(username, dstFolder, mail);
            if (!srcFile.equals(userDir.resolve(baseId + ".txt"))) {
                Files.deleteIfExists(srcFile);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error moving mail " + mailId + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean deletePermanently(String username, String folder, String mailId) {
        try {
            if (mailId == null) return false;
            String baseId = mailId.endsWith(".txt") ? mailId.substring(0, mailId.length() - 4) : mailId;
            Path userDir = getUserMailboxDir(username);
            Path file = userDir.resolve(baseId + ".txt");
            boolean deleted = Files.deleteIfExists(file);
            if (!deleted) {
                Path alt = userDir.resolve(baseId + ".mail");
                deleted = Files.deleteIfExists(alt);
            }
            if (!deleted && folder != null) {
                Path sub = userDir.resolve(folder.toLowerCase()).resolve(baseId + ".txt");
                deleted = Files.deleteIfExists(sub);
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
                try (var mailStream = Files.list(userDir)) {
                    total += mailStream.filter(p -> {
                        if (!Files.isRegularFile(p)) return false;
                        String n = p.getFileName().toString();
                        return (n.endsWith(".txt") || n.endsWith(".mail"))
                                && !n.equals("account.txt")
                                && !n.endsWith(".tmp");
                    }).count();
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return total;
    }

    public synchronized List<MailItem> listAccountRootMails(String username) {
        return listMails(username, "ALL");
    }

    public synchronized boolean deleteAccountRootMail(String username, String mailId) {
        return deletePermanently(username, null, mailId);
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
            String defLan = org.example.mail.common.NetworkUtils.getLocalIPv4Address();
            mail.setSenderIp(defLan != null && !defLan.isEmpty() ? defLan : "127.0.0.1");
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
                    case "sender-ip" -> {
                        String parsedIp = val;
                        if (parsedIp.equals("127.0.0.1") || parsedIp.startsWith("127.")) {
                            String lan = org.example.mail.common.NetworkUtils.getLocalIPv4Address();
                            if (lan != null && !lan.isEmpty() && !lan.equals("127.0.0.1")) {
                                parsedIp = lan;
                            }
                        }
                        mail.setSenderIp(parsedIp);
                    }
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
