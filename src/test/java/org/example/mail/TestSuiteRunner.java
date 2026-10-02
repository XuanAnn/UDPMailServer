package org.example.mail;

import org.example.mail.client.model.MailItem;
import org.example.mail.client.network.MailClient;
import org.example.mail.common.*;
import org.example.mail.server.core.MailServer;
import org.example.mail.server.core.ServerConfig;
import org.example.mail.server.core.UserActivityRecord;
import org.example.mail.server.core.UserActivityTracker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TestSuiteRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   JAVA UDP MAIL SYSTEM - AUTOMATED TEST SUITE   ");
        System.out.println("=================================================");

        testMessageCodec();
        testValidation();
        testUserActivityTracker();
        testEndToEndUDPServer();

        System.out.println("=================================================");
        System.out.println(String.format("TEST RESULTS: %d PASSED, %d FAILED", passed, failed));
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + testName);
        } else {
            failed++;
            System.err.println("  [FAIL] " + testName);
        }
    }

    private static void assertEquals(Object expected, Object actual, String testName) {
        boolean eq = (expected == null && actual == null) || (expected != null && expected.equals(actual));
        if (eq) {
            passed++;
            System.out.println("  [PASS] " + testName);
        } else {
            failed++;
            System.err.println("  [FAIL] " + testName + " -> Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void testMessageCodec() {
        System.out.println("\n--- 1. Testing MessageCodec ---");

        Request req = new Request(Protocol.CMD_SEND, "sess-token-123");
        req.put("to", "bob");
        req.put("subject", "Tiêu đề thư tiếng Việt có dấu 🚀");
        req.put("body", "Dòng 1: Xin chào!\nDòng 2: Ký tự đặc biệt | = \t & %");

        String raw = MessageCodec.encodeRequest(req);
        Request decoded = MessageCodec.decodeRequest(raw);

        assertEquals(req.getVersion(), decoded.getVersion(), "Codec Request Version");
        assertEquals(req.getRequestId(), decoded.getRequestId(), "Codec Request ID");
        assertEquals(req.getCommand(), decoded.getCommand(), "Codec Command");
        assertEquals(req.getToken(), decoded.getToken(), "Codec Session Token");
        assertEquals("bob", decoded.get("to"), "Codec Payload 'to'");
        assertEquals("Tiêu đề thư tiếng Việt có dấu 🚀", decoded.get("subject"), "Codec UTF-8 Subject");
        assertEquals("Dòng 1: Xin chào!\nDòng 2: Ký tự đặc biệt | = \t & %", decoded.get("body"), "Codec Multiline Body");

        Response resp = Response.ok("req-999", "Đã gửi thành công");
        resp.put("mailId", "mail-uuid-888");
        String rawResp = MessageCodec.encodeResponse(resp);
        Response decodedResp = MessageCodec.decodeResponse(rawResp);

        assertEquals(Protocol.STATUS_OK, decodedResp.getStatus(), "Codec Response Status");
        assertEquals("Đã gửi thành công", decodedResp.getMessage(), "Codec Response Message");
        assertEquals("mail-uuid-888", decodedResp.get("mailId"), "Codec Response mailId");
    }

    private static void testValidation() {
        System.out.println("\n--- 2. Testing Validator ---");

        assertTrue(Validator.isValidUsername("alice"), "Valid username 'alice'");
        assertTrue(Validator.isValidUsername("bob_99"), "Valid username 'bob_99'");
        assertTrue(!Validator.isValidUsername("ab"), "Reject username < 3 chars");
        assertTrue(!Validator.isValidUsername("../../evil"), "Reject path traversal username");
        assertTrue(!Validator.isValidUsername("user name"), "Reject username with space");

        assertTrue(Validator.isSafeFileName("email_test.mail"), "Safe file name");
        assertTrue(!Validator.isSafeFileName("../../etc/shadow"), "Reject traversal file name");

        assertTrue(Validator.isValidPort(5000), "Valid port 5000");
        assertTrue(!Validator.isValidPort(80), "Reject privileged port 80");
        assertTrue(!Validator.isValidPort(70000), "Reject port > 65535");

        // NetworkUtils LAN IP detection
        String detectedLanIp = NetworkUtils.getLocalIPv4Address();
        assertTrue(detectedLanIp != null && !detectedLanIp.isEmpty(), "Detected LAN IPv4 not null: " + detectedLanIp);
        assertTrue(detectedLanIp.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"), "Detected IP is valid IPv4: " + detectedLanIp);
    }

    private static void testUserActivityTracker() {
        System.out.println("\n--- 3. Testing User Activity Tracker (6 Status Fields) ---");

        UserActivityTracker tracker = new UserActivityTracker();

        // 1. Initializing from stored accounts
        Map<String, String> accAlice = new HashMap<>();
        accAlice.put("username", "alice");
        accAlice.put("createdat", "2026-10-01 22:00:00");
        tracker.initUsers(List.of(accAlice));

        UserActivityRecord recAlice = tracker.getRecord("alice");
        assertTrue(recAlice != null, "Alice record created on init");
        assertTrue(recAlice.isRegistered(), "Field 2: đã đăng kí = true");
        assertTrue(!recAlice.isConnected(), "Field 1: đã kết nối = false initially");
        assertTrue(!recAlice.isLoggedIn(), "Field 3: đã đăng nhập = false initially");
        assertEquals("-", recAlice.getLoginTime(), "Field 4: giờ vào initially '-'");
        assertEquals("-", recAlice.getLogoutTime(), "Field 5: giờ ra initially '-'");

        // 2. New registration
        tracker.recordRegister("bob", "192.168.1.50", 52100);
        UserActivityRecord recBob = tracker.getRecord("bob");
        assertTrue(recBob != null, "Bob record created on register");
        assertTrue(recBob.isRegistered(), "Bob đã đăng kí = true");
        assertTrue(recBob.isConnected(), "Bob đã kết nối = true after register");
        assertTrue(recBob.getCurrentActivity().contains("[192.168.1.50:52100]"), "Field 6: đang làm gì contains IP & Port");

        // 3. Login
        tracker.recordLogin("bob", "192.168.1.50", 52100);
        assertTrue(recBob.isLoggedIn(), "Field 3: đã đăng nhập = true");
        assertTrue(!recBob.getLoginTime().equals("-"), "Field 4: giờ vào is recorded with timestamp");
        assertTrue(recBob.getLogoutTime().contains("Đang hoạt động"), "Field 5: giờ ra indicates active status while logged in");

        // 4. Live Activity with IP & Port
        tracker.recordActivity("bob", "192.168.1.50", 52100, "Đang gửi email tới 'alice'");
        assertTrue(recBob.getCurrentActivity().contains("[192.168.1.50:52100]"), "Field 6: Activity has IP & Port");
        assertTrue(recBob.getCurrentActivity().contains("Đang gửi email"), "Field 6: Activity description present");

        // 5. Logout
        tracker.recordLogout("bob", "192.168.1.50", 52100);
        assertTrue(!recBob.isLoggedIn(), "Field 3: đã đăng nhập = false after logout");
        assertTrue(!recBob.getLogoutTime().equals("-"), "Field 5: giờ ra is recorded with timestamp on logout");

        // 6. Disconnect
        tracker.recordDisconnect("bob");
        assertTrue(!recBob.isConnected(), "Field 1: đã kết nối = false after disconnect");

        // 7. Verify all records retrieved sorted
        List<UserActivityRecord> all = tracker.getAllRecords();
        assertEquals(2, all.size(), "Tracker maintains all records (Alice + Bob)");

        // 8. Anonymous probe test (ensure no phantom user is created)
        tracker.recordConnection(null, "127.0.0.1", 62103, "Ping probe");
        tracker.recordActivity(null, "127.0.0.1", 62103, "Ping probe");
        assertEquals(2, tracker.getAllRecords().size(), "Anonymous connection/activity does NOT create phantom user in table");
    }

    private static void testEndToEndUDPServer() {
        System.out.println("\n--- 4. Testing End-to-End UDP Server & Client ---");

        int testPort = 5088;
        Path testDir = Path.of("target", "test-run-" + UUID.randomUUID());

        ServerConfig config = new ServerConfig();
        config.setDataPath(testDir.toString());
        config.setPort(testPort);
        config.setBindAddress("127.0.0.1");

        MailServer server = new MailServer(config, (lvl, msg) -> {
            // Server log callback
        });

        try {
            server.start(testPort);
            Thread.sleep(150);

            MailClient client = new MailClient("127.0.0.1", testPort);

            // A. Ping
            Response pingResp = client.ping();
            assertEquals(Protocol.STATUS_PONG, pingResp.getStatus(), "Server PING -> PONG");

            // B. Register Alice and Bob
            Response regAlice = client.register("alice", "passAlice123");
            assertTrue(regAlice.isOk(), "Register Alice");

            Response regBob = client.register("bob", "passBob456");
            assertTrue(regBob.isOk(), "Register Bob");

            Response regAliceDup = client.register("alice", "passAlice123");
            assertEquals(Protocol.STATUS_USER_EXISTS, regAliceDup.getStatus(), "Prevent duplicate registration");

            // C. Login Alice
            Response failLogin = client.login("alice", "wrong_password");
            assertEquals(Protocol.STATUS_AUTH_FAILED, failLogin.getStatus(), "Reject wrong password");

            Response loginAlice = client.login("alice", "passAlice123");
            assertTrue(loginAlice.isOk(), "Login Alice");
            String tokenAlice = loginAlice.get("token");
            assertTrue(tokenAlice != null && !tokenAlice.isEmpty(), "Alice session token generated");

            Response loginBob = client.login("bob", "passBob456");
            assertTrue(loginBob.isOk(), "Login Bob");
            String tokenBob = loginBob.get("token");

            // D. Send Email Alice -> Bob with Idempotency test
            String subject = "Họp dự án Java UDP Mail";
            String body = "Chào Bob,\nNhớ kiểm tra báo cáo và slide thuyết trình nhé!\nThân,\nAlice";

            Request sendReq = new Request(Protocol.CMD_SEND, tokenAlice)
                    .put("to", "bob")
                    .put("subject", subject)
                    .put("body", body);

            Response sendResp1 = client.getTransport().send(sendReq, "127.0.0.1", testPort);
            assertTrue(sendResp1.isOk(), "Send email from Alice to Bob");
            String mailId1 = sendResp1.get("mailId");
            assertTrue(mailId1 != null, "Mail ID generated");
            assertEquals("email_001", mailId1, "Mail ID follows email_001 sequential format");

            // Send SAME request again (simulating network retry on dropped ACK)
            Response sendResp2 = client.getTransport().send(sendReq, "127.0.0.1", testPort);
            assertTrue(sendResp2.isOk(), "Duplicate send request handled");
            assertEquals(mailId1, sendResp2.get("mailId"), "Idempotency: Same mailId returned");

            // E. Bob checks Inbox -> must contain 2 emails: welcome email (new_email.txt) + Alice email (no duplicates!)
            Response listBob = client.list(tokenBob, Protocol.FOLDER_INBOX);
            assertTrue(listBob.isOk(), "Bob lists Inbox");
            List<MailItem> bobMails = MailClient.parseMailList(listBob);
            assertEquals(2, bobMails.size(), "Inbox contains welcome email + Alice email (2 total, no duplicate created)");

            MailItem mail = bobMails.stream().filter(m -> mailId1.equals(m.getMailId())).findFirst().orElse(null);
            assertTrue(mail != null, "Alice's email is present in Bob's Inbox");
            assertEquals("alice", mail.getSender(), "Email sender is Alice");
            assertEquals(subject, mail.getSubject(), "Email subject matches");
            assertTrue(!mail.isReadState(), "Email initially unread");
            assertTrue(mail.getSenderIp() != null && !mail.getSenderIp().isEmpty(), "Sender IP captured: " + mail.getSenderIp());
            assertTrue(mail.getSenderPort() > 0, "Sender Port captured: " + mail.getSenderPort());

            // Storage check: Verify welcome email and sent mail files in accounts/bob/
            Path expectedWelcomeInbox = testDir.resolve("accounts").resolve("bob").resolve("inbox").resolve("new_email.txt");
            assertTrue(Files.exists(expectedWelcomeInbox), "Welcome file exists in inbox at accounts/bob/inbox/new_email.txt");
            Path rootNewEmail = testDir.resolve("accounts").resolve("bob").resolve("new_email.txt");
            assertTrue(!Files.exists(rootNewEmail), "new_email.txt is not created directly in account root folder");

            Path expectedBobInboxFile = testDir.resolve("accounts").resolve("bob").resolve("inbox").resolve(mailId1 + ".txt");
            assertTrue(Files.exists(expectedBobInboxFile), "Mail saved on disk at accounts/bob/inbox/" + mailId1 + ".txt");

            Path expectedBobRootFile = testDir.resolve("accounts").resolve("bob").resolve(mailId1 + ".txt");
            assertTrue(Files.exists(expectedBobRootFile), "Mail saved on disk at accounts/bob/" + mailId1 + ".txt");

            Path expectedAliceSentFile = testDir.resolve("accounts").resolve("alice").resolve("sent").resolve(mailId1 + ".txt");
            assertTrue(Files.exists(expectedAliceSentFile), "Mail saved on disk at accounts/alice/sent/" + mailId1 + ".txt");

            // Verify second email gets email_002
            Request sendReq2 = new Request(Protocol.CMD_SEND, tokenAlice)
                    .put("to", "bob")
                    .put("subject", "Second email")
                    .put("body", "Second body");
            Response sendRespEmail2 = client.getTransport().send(sendReq2, "127.0.0.1", testPort);
            assertTrue(sendRespEmail2.isOk(), "Send second email from Alice to Bob");
            String mailId2 = sendRespEmail2.get("mailId");
            assertEquals("email_002", mailId2, "Second Mail ID incremented sequentially to email_002");
            Path expectedBobEmail2 = testDir.resolve("accounts").resolve("bob").resolve("inbox").resolve("email_002.txt");
            assertTrue(Files.exists(expectedBobEmail2), "Second mail saved on disk at accounts/bob/inbox/email_002.txt");

            // F. Bob reads email
            Response readResp = client.read(tokenBob, Protocol.FOLDER_INBOX, mailId1);
            assertTrue(readResp.isOk(), "Bob reads email content");
            assertEquals(body, readResp.get("body"), "Email body content matches exactly");

            // Re-check read state -> now true
            Response listBobRead = client.list(tokenBob, Protocol.FOLDER_INBOX);
            List<MailItem> bobMailsRead = MailClient.parseMailList(listBobRead);
            MailItem readItem = bobMailsRead.stream().filter(m -> mailId1.equals(m.getMailId())).findFirst().orElse(null);
            assertTrue(readItem != null && readItem.isReadState(), "Email automatically marked read after reading");

            // G. Alice Sent box contains sent emails
            Response listAliceSent = client.list(tokenAlice, Protocol.FOLDER_SENT);
            List<MailItem> aliceSent = MailClient.parseMailList(listAliceSent);
            assertEquals(2, aliceSent.size(), "Alice Sent folder contains 2 mails");
            assertEquals("bob", aliceSent.get(0).getRecipient(), "Alice Sent mail recipient is Bob");

            // H. Move to Trash & Permanent Delete
            Response moveTrash = client.moveMail(tokenBob, Protocol.FOLDER_INBOX, Protocol.FOLDER_TRASH, mailId1);
            assertTrue(moveTrash.isOk(), "Move email to Trash");

            Response listTrash = client.list(tokenBob, Protocol.FOLDER_TRASH);
            assertEquals(1, MailClient.parseMailList(listTrash).size(), "Trash contains moved email");

            Response permDelete = client.deleteMail(tokenBob, Protocol.FOLDER_TRASH, mailId1);
            assertTrue(permDelete.isOk(), "Permanently delete email from Trash");

            Response listTrashEmpty = client.list(tokenBob, Protocol.FOLDER_TRASH);
            assertEquals(0, MailClient.parseMailList(listTrashEmpty).size(), "Trash is now empty");

            // I. Logout & Token Revocation
            Response logoutAlice = client.logout(tokenAlice);
            assertTrue(logoutAlice.isOk(), "Alice logs out");

            Response listAfterLogout = client.list(tokenAlice, Protocol.FOLDER_INBOX);
            assertTrue(!listAfterLogout.isOk(), "Revoked session token rejected");

            // J. Server Persistence Test: Stop server, create new server instance with same data directory
            server.stop();
            Thread.sleep(200);

            MailServer server2 = new MailServer(config, (lvl, msg) -> {});
            server2.start(testPort);
            Thread.sleep(150);

            // Verify Alice and Bob still exist on disk after server restart!
            Response loginBob2 = client.login("bob", "passBob456");
            assertTrue(loginBob2.isOk(), "Persistence: Bob can log in after server restart");

            Response regAliceAfterRestart = client.register("alice", "passAlice123");
            assertEquals(Protocol.STATUS_USER_EXISTS, regAliceAfterRestart.getStatus(), "Persistence: Alice account persisted across restart");

            server2.stop();

        } catch (Exception ex) {
            failed++;
            System.err.println("  [FAIL] End-to-end UDP server test error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            server.stop();
            // Clean up test files
            try {
                if (Files.exists(testDir)) {
                    Files.walk(testDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            } catch (IOException ignored) {}
        }
    }
}
