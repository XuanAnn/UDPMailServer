package org.example.mail.server.core;

import org.example.mail.client.model.MailItem;
import org.example.mail.common.Protocol;
import org.example.mail.common.Request;
import org.example.mail.common.Response;
import org.example.mail.common.Validator;
import org.example.mail.server.service.AccountService;
import org.example.mail.server.service.AuthService;
import org.example.mail.server.service.MailService;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RequestDispatcher {

    private final AuthService authService;
    private final AccountService accountService;
    private final MailService mailService;
    private final ServerLogListener logListener;

    // Idempotency cache: requestId -> CachedResponse
    private static class CachedResponse {
        final Response response;
        final long createdAt;

        CachedResponse(Response response) {
            this.response = response;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    private final long cacheTtlMillis = 5 * 60 * 1000L; // 5 minutes

    // Statistics counters
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalSuccess = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong duplicateRequestsBlocked = new AtomicLong(0);

    @FunctionalInterface
    public interface RealtimeNotificationSender {
        void sendNotification(InetAddress addr, int port, String message);
    }

    private RealtimeNotificationSender notificationSender;

    public void setNotificationSender(RealtimeNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    private final UserActivityTracker activityTracker = new UserActivityTracker();

    public UserActivityTracker getActivityTracker() {
        return activityTracker;
    }

    public RequestDispatcher(AuthService authService,
                             AccountService accountService,
                             MailService mailService,
                             ServerLogListener logListener) {
        this.authService = authService;
        this.accountService = accountService;
        this.mailService = mailService;
        this.logListener = logListener != null ? logListener : (level, msg) -> {};
    }

    public Response dispatch(Request request, InetAddress clientAddress, int clientPort) {
        totalRequests.incrementAndGet();
        cleanOldCache();

        String reqId = request.getRequestId();
        String cmd = request.getCommand();

        // 1. Check idempotency cache for duplicate request ID
        if (reqId != null && !reqId.isEmpty()) {
            CachedResponse cached = responseCache.get(reqId);
            if (cached != null) {
                duplicateRequestsBlocked.incrementAndGet();
                log("INFO", String.format("[IDEMPOTENT] Duplicate requestId '%s' (%s) from %s:%d -> Returning cached response",
                        reqId, cmd, clientAddress.getHostAddress(), clientPort));
                return cached.response;
            }
        }

        Response response;
        try {
            response = executeCommand(request, clientAddress, clientPort);
        } catch (Exception ex) {
            totalErrors.incrementAndGet();
            log("ERROR", String.format("[DISPATCH_ERROR] %s from %s:%d failed: %s",
                    cmd, clientAddress.getHostAddress(), clientPort, ex.getMessage()));
            response = Response.error(reqId, ex.getMessage());
        }

        if (response.isOk()) {
            totalSuccess.incrementAndGet();
        } else {
            totalErrors.incrementAndGet();
        }

        // Cache response for idempotent commands (especially SEND, REGISTER, MARK_READ, MOVE, DELETE)
        if (reqId != null && !reqId.isEmpty()) {
            responseCache.put(reqId, new CachedResponse(response));
        }

        return response;
    }

    private Response executeCommand(Request request, InetAddress clientAddress, int clientPort) throws Exception {
        String cmd = request.getCommand();
        String reqId = request.getRequestId();

        if (cmd == null || cmd.trim().isEmpty()) {
            return Response.status(reqId, Protocol.STATUS_INVALID, "Missing command");
        }

        String ip = clientAddress != null ? clientAddress.getHostAddress() : "127.0.0.1";

        switch (cmd) {
            case Protocol.CMD_PING -> {
                String token = request.getToken();
                String username = (token != null && !token.isEmpty()) ? authService.authenticate(token) : null;
                activityTracker.recordActivity(username, ip, clientPort, "Ping UDP");
                log("INFO", String.format("[PING] From %s:%d -> PONG", ip, clientPort));
                return Response.status(reqId, Protocol.STATUS_PONG, "Server is alive")
                        .put("serverTime", String.valueOf(System.currentTimeMillis()));
            }

            case Protocol.CMD_REGISTER -> {
                String username = request.get("username");
                String password = request.get("password");

                log("INFO", String.format("[REGISTER] Attempt from %s:%d | User: '%s' | Password: '%s'",
                        ip, clientPort, username, password));

                if (!Validator.isValidUsername(username)) {
                    activityTracker.recordActivity(username, ip, clientPort, "Lỗi ĐK: Tên không hợp lệ");
                    return Response.status(reqId, Protocol.STATUS_INVALID, "Invalid username format (3-30 alphanumeric or _)");
                }
                if (!Validator.isValidPassword(password)) {
                    activityTracker.recordActivity(username, ip, clientPort, "Lỗi ĐK: Mật khẩu không hợp lệ");
                    return Response.status(reqId, Protocol.STATUS_INVALID, "Invalid password format");
                }

                boolean registered = accountService.register(username, password);
                if (registered) {
                    activityTracker.recordRegister(username, ip, clientPort);
                    log("INFO", String.format("[REGISTER_SUCCESS] User '%s' created successfully!", username));
                    return Response.ok(reqId, "Account registered successfully")
                            .put("username", username);
                } else {
                    activityTracker.recordActivity(username, ip, clientPort, "Lỗi ĐK: Trùng tên");
                    log("WARN", String.format("[REGISTER_FAILED] Username '%s' already exists", username));
                    return Response.status(reqId, Protocol.STATUS_USER_EXISTS, "Username already exists");
                }
            }

            case Protocol.CMD_LOGIN -> {
                String username = request.get("username");
                String password = request.get("password");

                log("INFO", String.format("[LOGIN] Attempt from %s:%d | User: '%s' | Password: '%s'",
                        ip, clientPort, username, password));

                String token = authService.login(username, password, clientAddress, clientPort);
                if (token != null) {
                    activityTracker.recordLogin(username, ip, clientPort);
                    List<String> files = accountService.getAccountFiles(username);
                    Response loginResp = Response.ok(reqId, "Login successful")
                            .put("token", token)
                            .put("username", username)
                            .put("fileCount", String.valueOf(files.size()));

                    for (int i = 0; i < files.size(); i++) {
                        loginResp.put("file_" + i, files.get(i));
                    }

                    log("INFO", String.format("[LOGIN_SUCCESS] User '%s' logged in -> Token: %s | Directory Files: %s",
                            username, token, files));
                    return loginResp;
                } else {
                    activityTracker.recordActivity(username, ip, clientPort, "Lỗi ĐN: Sai tài khoản/mật khẩu");
                    log("WARN", String.format("[LOGIN_FAILED] Invalid credentials for user '%s'", username));
                    return Response.status(reqId, Protocol.STATUS_AUTH_FAILED, "Invalid username or password");
                }
            }

            case Protocol.CMD_LOGOUT -> {
                String token = request.getToken();
                String username = authService.authenticate(token);
                authService.logout(token);
                if (username != null) {
                    activityTracker.recordLogout(username, ip, clientPort);
                }
                log("INFO", String.format("[LOGOUT] User '%s' logged out (Token: %s)", username != null ? username : "unknown", token));
                return Response.ok(reqId, "Logged out successfully");
            }

            case Protocol.CMD_LIST -> {
                String username = requireAuth(request, clientAddress, clientPort);
                String folder = request.getOrDefault("folder", Protocol.FOLDER_INBOX).toUpperCase();

                List<MailItem> items = mailService.listEmails(username, folder);
                int unreadCount = mailService.countUnreadMails(username, folder);

                activityTracker.recordActivity(username, ip, clientPort, "Xem " + folder + " (" + items.size() + " thư)");

                Response res = Response.ok(reqId, "Loaded " + items.size() + " emails")
                        .put("folder", folder)
                        .put("count", String.valueOf(items.size()))
                        .put("unreadCount", String.valueOf(unreadCount));

                for (int i = 0; i < items.size(); i++) {
                    MailItem m = items.get(i);
                    res.put("mail_" + i + "_id", m.getMailId());
                    res.put("mail_" + i + "_from", m.getSender());
                    res.put("mail_" + i + "_senderIp", m.getSenderIp() != null ? m.getSenderIp() : "127.0.0.1");
                    res.put("mail_" + i + "_senderPort", String.valueOf(m.getSenderPort()));
                    res.put("mail_" + i + "_to", m.getRecipient());
                    res.put("mail_" + i + "_subject", m.getSubject());
                    res.put("mail_" + i + "_date", m.getCreatedAt());
                    res.put("mail_" + i + "_read", String.valueOf(m.isReadState()));
                    res.put("mail_" + i + "_preview", m.getPreview(80));
                }

                log("INFO", String.format("[LIST] User '%s' listed folder '%s' -> %d emails (%d unread)",
                        username, folder, items.size(), unreadCount));
                return res;
            }

            case Protocol.CMD_READ -> {
                String username = requireAuth(request, clientAddress, clientPort);
                String folder = request.getOrDefault("folder", Protocol.FOLDER_INBOX).toUpperCase();
                String mailId = request.get("mailId");

                MailItem mail = mailService.readEmail(username, folder, mailId);
                if (mail == null) {
                    activityTracker.recordActivity(username, ip, clientPort, "Lỗi đọc: Thư #" + mailId);
                    return Response.status(reqId, Protocol.STATUS_NOT_FOUND, "Email not found");
                }

                activityTracker.recordActivity(username, ip, clientPort, "Đọc: " + (mail.getSubject() != null ? mail.getSubject() : ""));
                log("INFO", String.format("[READ] User '%s' opened mail '%s' ('%s') from %s [%s:%d]",
                        username, mailId, mail.getSubject(), mail.getSender(), mail.getSenderIp(), mail.getSenderPort()));
                return Response.ok(reqId, "Email retrieved")
                        .put("mailId", mail.getMailId())
                        .put("from", mail.getSender())
                        .put("senderIp", mail.getSenderIp() != null ? mail.getSenderIp() : "127.0.0.1")
                        .put("senderPort", String.valueOf(mail.getSenderPort()))
                        .put("to", mail.getRecipient())
                        .put("subject", mail.getSubject())
                        .put("date", mail.getCreatedAt())
                        .put("read", String.valueOf(mail.isReadState()))
                        .put("folder", folder)
                        .put("body", mail.getBody());
            }

            case Protocol.CMD_SEND -> {
                String sender = requireAuth(request, clientAddress, clientPort);
                String recipient = request.get("to");
                String subject = request.get("subject");
                String body = request.get("body");
                String senderIp = ip;
                int senderPort = clientPort;

                activityTracker.recordActivity(sender, senderIp, senderPort, "Gửi: " + recipient + " - " + subject);
                log("INFO", String.format("[SEND] From: '%s' (%s:%d) -> To: '%s' | Subject: '%s'",
                        sender, senderIp, senderPort, recipient, subject));

                String mailId = mailService.sendEmail(sender, senderIp, senderPort, recipient, subject, body);
                totalSent.incrementAndGet();
                totalReceived.incrementAndGet();

                // Realtime Push notification to recipient if online
                AuthService.ClientEndpoint endpoint = authService.getEndpointForUser(recipient);
                if (endpoint != null && notificationSender != null) {
                    String notifPayload = "NOTIFY\t" + mailId + "\t" + sender + "\t" + subject;
                    notificationSender.sendNotification(endpoint.address, endpoint.port, notifPayload);
                    log("INFO", String.format("[REALTIME_PUSH] Pushed instant notification to online recipient '%s' at %s:%d",
                            recipient, endpoint.address.getHostAddress(), endpoint.port));
                }

                log("INFO", String.format("[SEND_SUCCESS] Email saved with ID: %s", mailId));
                return Response.ok(reqId, "Email sent successfully")
                        .put("mailId", mailId);
            }

            case Protocol.CMD_SAVE_DRAFT -> {
                String username = requireAuth(request, clientAddress, clientPort);
                String recipient = request.get("to");
                String subject = request.get("subject");
                String body = request.get("body");
                String existingMailId = request.get("mailId");

                activityTracker.recordActivity(username, ip, clientPort, "Lưu nháp: " + (subject != null ? subject : ""));
                String mailId = mailService.saveDraft(username, recipient, subject, body, existingMailId);
                log("INFO", String.format("[SAVE_DRAFT] User '%s' saved draft '%s'", username, mailId));
                return Response.ok(reqId, "Draft saved")
                        .put("mailId", mailId);
            }

            case Protocol.CMD_MARK_READ -> {
                String username = requireAuth(request, clientAddress, clientPort);
                String folder = request.getOrDefault("folder", Protocol.FOLDER_INBOX).toUpperCase();
                String mailId = request.get("mailId");
                boolean read = Boolean.parseBoolean(request.getOrDefault("read", "true"));

                activityTracker.recordActivity(username, ip, clientPort, "Đánh dấu: " + (read ? "Đã đọc" : "Chưa đọc"));
                boolean ok = mailService.markRead(username, folder, mailId, read);
                log("INFO", String.format("[MARK_READ] User '%s' marked mail '%s' as read=%s", username, mailId, read));
                return ok ? Response.ok(reqId, "Marked successfully") : Response.error(reqId, "Mail not found");
            }

            case Protocol.CMD_MOVE -> {
                String username = requireAuth(request, clientAddress, clientPort);
                String srcFolder = request.get("srcFolder");
                String dstFolder = request.get("dstFolder");
                String mailId = request.get("mailId");

                activityTracker.recordActivity(username, ip, clientPort, "Chuyển: " + srcFolder + " -> " + dstFolder);
                boolean ok = mailService.moveMail(username, srcFolder, dstFolder, mailId);
                log("INFO", String.format("[MOVE] User '%s' moved mail '%s' from %s to %s", username, mailId, srcFolder, dstFolder));
                return ok ? Response.ok(reqId, "Moved successfully") : Response.error(reqId, "Failed to move mail");
            }

            case Protocol.CMD_DELETE -> {
                String username = requireAuth(request, clientAddress, clientPort);
                String folder = request.getOrDefault("folder", Protocol.FOLDER_INBOX).toUpperCase();
                String mailId = request.get("mailId");

                activityTracker.recordActivity(username, ip, clientPort, "Xóa khỏi " + folder);
                boolean ok = mailService.deleteMail(username, folder, mailId);
                log("INFO", String.format("[DELETE] User '%s' deleted mail '%s' from %s", username, mailId, folder));
                return ok ? Response.ok(reqId, "Mail deleted") : Response.error(reqId, "Failed to delete mail");
            }

            case Protocol.CMD_STATS -> {
                return Response.ok(reqId, "Server Stats")
                        .put("totalRequests", String.valueOf(totalRequests.get()))
                        .put("totalSuccess", String.valueOf(totalSuccess.get()))
                        .put("totalErrors", String.valueOf(totalErrors.get()))
                        .put("totalSent", String.valueOf(totalSent.get()))
                        .put("totalReceived", String.valueOf(totalReceived.get()))
                        .put("duplicatesBlocked", String.valueOf(duplicateRequestsBlocked.get()))
                        .put("activeSessions", String.valueOf(authService.getActiveSessionCount()))
                        .put("totalAccounts", String.valueOf(accountService.countAccounts()))
                        .put("totalMails", String.valueOf(mailService.countAllMails()));
            }

            default -> {
                log("WARN", String.format("[UNKNOWN_CMD] Command '%s' not recognized", cmd));
                return Response.status(reqId, Protocol.STATUS_INVALID, "Unknown command: " + cmd);
            }
        }
    }

    private String requireAuth(Request request, InetAddress clientAddress, int clientPort) {
        String token = request.getToken();
        String username = authService.authenticate(token);
        if (username == null) {
            throw new SecurityException("Unauthorized: invalid or expired session token");
        }
        if (clientAddress != null && clientPort > 0) {
            authService.updateEndpoint(token, clientAddress, clientPort);
        }
        return username;
    }

    private void cleanOldCache() {
        long now = System.currentTimeMillis();
        responseCache.entrySet().removeIf(e -> (now - e.getValue().createdAt) > cacheTtlMillis);
    }

    private void log(String level, String msg) {
        logListener.onLog(level, msg);
    }

    // Getters for Stats
    public long getTotalRequests() { return totalRequests.get(); }
    public long getTotalSuccess() { return totalSuccess.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public long getTotalSent() { return totalSent.get(); }
    public long getTotalReceived() { return totalReceived.get(); }
    public long getDuplicatesBlocked() { return duplicateRequestsBlocked.get(); }
}
