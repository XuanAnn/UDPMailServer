package org.example.mail.common;

import java.util.regex.Pattern;

public final class Validator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final Pattern MAIL_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,64}$");

    private Validator() {}

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && !password.trim().isEmpty() && password.length() <= 100;
    }

    public static boolean isValidMailId(String mailId) {
        return mailId != null && MAIL_ID_PATTERN.matcher(mailId.trim()).matches();
    }

    public static boolean isValidSubject(String subject) {
        return subject != null && !subject.trim().isEmpty() && subject.length() <= 200;
    }

    public static boolean isValidBody(String body) {
        return body != null && body.length() <= 50000;
    }

    public static boolean isValidPort(int port) {
        return port >= 1024 && port <= 65535;
    }

    public static boolean isSafeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return false;
        }
        return true;
    }

    public static boolean isValidFolder(String folder) {
        if (folder == null) return false;
        String f = folder.trim().toUpperCase();
        return Protocol.FOLDER_INBOX.equals(f)
                || Protocol.FOLDER_SENT.equals(f)
                || Protocol.FOLDER_DRAFTS.equals(f)
                || Protocol.FOLDER_TRASH.equals(f)
                || "ALL".equals(f)
                || "ROOT".equals(f);
    }
}
