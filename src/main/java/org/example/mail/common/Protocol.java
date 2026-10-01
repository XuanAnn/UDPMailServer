package org.example.mail.common;

public final class Protocol {

    public static final String VERSION = "1.0";
    public static final int DEFAULT_PORT = 5000;
    public static final int MAX_PACKET_SIZE = 65507;
    public static final int MAX_PAYLOAD_BYTES = 60000;
    public static final int DEFAULT_TIMEOUT_MS = 4000;
    public static final int MAX_RETRIES = 3;

    // Commands
    public static final String CMD_REGISTER = "REGISTER";
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_LOGOUT = "LOGOUT";
    public static final String CMD_LIST = "LIST";
    public static final String CMD_READ = "READ";
    public static final String CMD_SEND = "SEND";
    public static final String CMD_SAVE_DRAFT = "SAVE_DRAFT";
    public static final String CMD_MARK_READ = "MARK_READ";
    public static final String CMD_MOVE = "MOVE";
    public static final String CMD_DELETE = "DELETE";
    public static final String CMD_PING = "PING";
    public static final String CMD_STATS = "STATS";

    // Status Responses
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_AUTH_FAILED = "AUTH_FAILED";
    public static final String STATUS_USER_EXISTS = "USER_EXISTS";
    public static final String STATUS_INVALID = "INVALID";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String STATUS_PONG = "PONG";

    // Folders
    public static final String FOLDER_INBOX = "INBOX";
    public static final String FOLDER_SENT = "SENT";
    public static final String FOLDER_DRAFTS = "DRAFTS";
    public static final String FOLDER_TRASH = "TRASH";

    private Protocol() {}
}
