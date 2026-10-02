package org.example.mail;

import org.example.mail.client.model.MailItem;
import org.example.mail.client.network.MailClient;
import org.example.mail.common.Protocol;
import org.example.mail.common.Request;
import org.example.mail.common.Response;
import org.example.mail.server.core.MailServer;
import org.example.mail.server.core.ServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ServerIntegrationTest {

    private static final int TEST_PORT = 5059;
    private static MailServer server;
    private static MailClient client;
    private static Path tempTestDataDir;

    @BeforeAll
    public static void setUp() throws Exception {
        tempTestDataDir = Path.of("target", "test-data-" + UUID.randomUUID());
        Files.createDirectories(tempTestDataDir);

        ServerConfig config = new ServerConfig();
        config.setDataPath(tempTestDataDir.toString());
        config.setPort(TEST_PORT);
        config.setBindAddress("127.0.0.1");

        server = new MailServer(config, (lvl, msg) -> System.out.println("[TEST-LOG][" + lvl + "] " + msg));
        server.start(TEST_PORT);

        // Allow socket to bind
        Thread.sleep(200);

        client = new MailClient("127.0.0.1", TEST_PORT);
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
        // Delete test data
        if (tempTestDataDir != null && Files.exists(tempTestDataDir)) {
            try {
                Files.walk(tempTestDataDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException ignored) {}
        }
    }

    @Test
    public void testPing() throws Exception {
        Response resp = client.ping();
        assertNotNull(resp);
        assertEquals(Protocol.STATUS_PONG, resp.getStatus());
    }

    @Test
    public void testFullMailWorkflowAndIdempotency() throws Exception {
        // 1. Register Alice and Bob
        Response regAlice = client.register("alice", "pass123");
        assertTrue(regAlice.isOk(), "Alice registration should succeed");

        Response regBob = client.register("bob", "secret456");
        assertTrue(regBob.isOk(), "Bob registration should succeed");

        // Duplicate registration check
        Response regAliceDup = client.register("alice", "pass123");
        assertEquals(Protocol.STATUS_USER_EXISTS, regAliceDup.getStatus());

        // 2. Login
        Response loginFail = client.login("alice", "wrongpass");
        assertEquals(Protocol.STATUS_AUTH_FAILED, loginFail.getStatus());

        Response loginAlice = client.login("alice", "pass123");
        assertTrue(loginAlice.isOk());
        String tokenAlice = loginAlice.get("token");
        assertNotNull(tokenAlice);

        Response loginBob = client.login("bob", "secret456");
        assertTrue(loginBob.isOk());
        String tokenBob = loginBob.get("token");
        assertNotNull(tokenBob);

        // 3. Send email from Alice to Bob
        String subject = "Urgent: Project Meeting";
        String body = "Hi Bob,\nPlease check the documentation.\nRegards,\nAlice";

        // Create explicit request to test idempotency
        Request sendReq = new Request(Protocol.CMD_SEND, tokenAlice)
                .put("to", "bob")
                .put("subject", subject)
                .put("body", body);

        Response sendResp1 = client.getTransport().send(sendReq, "127.0.0.1", TEST_PORT);
        assertTrue(sendResp1.isOk());
        String mailId1 = sendResp1.get("mailId");
        assertNotNull(mailId1);

        // Send duplicate request with SAME requestId (testing UDP retry idempotency)
        Response sendResp2 = client.getTransport().send(sendReq, "127.0.0.1", TEST_PORT);
        assertTrue(sendResp2.isOk());
        assertEquals(mailId1, sendResp2.get("mailId"), "Duplicate request must return cached mailId");

        // 4. Bob checks inbox - must have EXACTLY 1 email, not 2!
        Response listBob = client.list(tokenBob, Protocol.FOLDER_INBOX);
        assertTrue(listBob.isOk());
        List<MailItem> bobMails = MailClient.parseMailList(listBob);
        assertEquals(2, bobMails.size(), "Bob should have 2 emails (welcome email + Alice email)");

        MailItem received = bobMails.stream().filter(m -> mailId1.equals(m.getMailId())).findFirst().orElse(null);
        assertNotNull(received);
        assertEquals("alice", received.getSender());
        assertEquals(subject, received.getSubject());
        assertFalse(received.isReadState());

        // 5. Bob reads the email
        Response readResp = client.read(tokenBob, Protocol.FOLDER_INBOX, mailId1);
        assertTrue(readResp.isOk());
        assertEquals(body, readResp.get("body"));

        // 6. Check that Bob's email is now marked as read
        Response listBobAfterRead = client.list(tokenBob, Protocol.FOLDER_INBOX);
        List<MailItem> bobMailsAfterRead = MailClient.parseMailList(listBobAfterRead);
        MailItem readMail = bobMailsAfterRead.stream().filter(m -> mailId1.equals(m.getMailId())).findFirst().orElse(null);
        assertNotNull(readMail);
        assertTrue(readMail.isReadState());

        // 7. Check Alice's Sent box
        Response listAliceSent = client.list(tokenAlice, Protocol.FOLDER_SENT);
        assertTrue(listAliceSent.isOk());
        List<MailItem> aliceSentMails = MailClient.parseMailList(listAliceSent);
        assertEquals(1, aliceSentMails.size());
        assertEquals("bob", aliceSentMails.get(0).getRecipient());

        // 8. Move email to Trash & Delete
        Response moveResp = client.moveMail(tokenBob, Protocol.FOLDER_INBOX, Protocol.FOLDER_TRASH, mailId1);
        assertTrue(moveResp.isOk());

        Response listBobInboxEmpty = client.list(tokenBob, Protocol.FOLDER_INBOX);
        assertEquals(1, MailClient.parseMailList(listBobInboxEmpty).size(), "Bob inbox still has welcome email");

        Response listBobTrash = client.list(tokenBob, Protocol.FOLDER_TRASH);
        assertEquals(1, MailClient.parseMailList(listBobTrash).size());

        // Permanent delete from trash
        Response delResp = client.deleteMail(tokenBob, Protocol.FOLDER_TRASH, mailId1);
        assertTrue(delResp.isOk());

        Response listBobTrashEmpty = client.list(tokenBob, Protocol.FOLDER_TRASH);
        assertEquals(0, MailClient.parseMailList(listBobTrashEmpty).size());

        // 9. Logout
        Response logoutAlice = client.logout(tokenAlice);
        assertTrue(logoutAlice.isOk());

        // Attempt using revoked token
        Response listRevoked = client.list(tokenAlice, Protocol.FOLDER_INBOX);
        assertFalse(listRevoked.isOk(), "Access with revoked token must fail");
    }
}
