package org.example.mail;

import org.example.mail.common.MessageCodec;
import org.example.mail.common.Protocol;
import org.example.mail.common.Request;
import org.example.mail.common.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessageCodecTest {

    @Test
    public void testEncodeDecodeRequestWithVietnameseAndNewlines() {
        Request req = new Request(Protocol.CMD_SEND, "token-xyz-123");
        req.put("to", "bob");
        req.put("subject", "Tiêu đề thư tiếng Việt có dấu 🚀");
        req.put("body", "Dòng 1: Xin chào!\nDòng 2: Nội dung có nhiều dòng và ký tự đặc biệt | = \t & %");

        String raw = MessageCodec.encodeRequest(req);
        assertNotNull(raw);

        Request decoded = MessageCodec.decodeRequest(raw);
        assertEquals(req.getVersion(), decoded.getVersion());
        assertEquals(req.getRequestId(), decoded.getRequestId());
        assertEquals(req.getCommand(), decoded.getCommand());
        assertEquals(req.getToken(), decoded.getToken());
        assertEquals("bob", decoded.get("to"));
        assertEquals("Tiêu đề thư tiếng Việt có dấu 🚀", decoded.get("subject"));
        assertEquals("Dòng 1: Xin chào!\nDòng 2: Nội dung có nhiều dòng và ký tự đặc biệt | = \t & %", decoded.get("body"));
    }

    @Test
    public void testEncodeDecodeResponse() {
        Response resp = Response.ok("req-456", "Thành công");
        resp.put("count", "2");
        resp.put("mail_0_id", "id-1");
        resp.put("mail_1_id", "id-2");

        String raw = MessageCodec.encodeResponse(resp);
        assertNotNull(raw);

        Response decoded = MessageCodec.decodeResponse(raw);
        assertEquals("req-456", decoded.getRequestId());
        assertEquals(Protocol.STATUS_OK, decoded.getStatus());
        assertEquals("Thành công", decoded.getMessage());
        assertEquals("2", decoded.get("count"));
        assertEquals("id-1", decoded.get("mail_0_id"));
        assertEquals("id-2", decoded.get("mail_1_id"));
    }
}
