package org.example.mail.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class MessageCodec {

    private MessageCodec() {}

    public static String encodeRequest(Request request) {
        StringBuilder sb = new StringBuilder();
        sb.append("REQ\t")
          .append(sanitizeToken(request.getVersion())).append("\t")
          .append(sanitizeToken(request.getRequestId())).append("\t")
          .append(sanitizeToken(request.getCommand())).append("\t")
          .append(request.getToken() != null ? sanitizeToken(request.getToken()) : "")
          .append("\n");

        for (Map.Entry<String, String> entry : request.getPayload().entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";
            sb.append(key).append("=").append(base64Encode(val)).append("\n");
        }
        return sb.toString();
    }

    public static Request decodeRequest(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty request data");
        }

        String[] lines = raw.split("\r?\n");
        if (lines.length == 0) {
            throw new IllegalArgumentException("No header line found");
        }

        String header = lines[0];
        String[] parts = header.split("\t", -1);
        if (parts.length < 4 || !"REQ".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid REQ header: " + header);
        }

        Request request = new Request();
        request.setVersion(parts[1]);
        request.setRequestId(parts[2]);
        request.setCommand(parts[3]);
        if (parts.length > 4 && !parts[4].isEmpty()) {
            request.setToken(parts[4]);
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;
            int eqIdx = line.indexOf('=');
            if (eqIdx > 0) {
                String key = line.substring(0, eqIdx);
                String valEncoded = line.substring(eqIdx + 1);
                request.put(key, base64Decode(valEncoded));
            }
        }

        return request;
    }

    public static String encodeResponse(Response response) {
        StringBuilder sb = new StringBuilder();
        String safeMsg = response.getMessage() != null ? response.getMessage() : "";
        sb.append("RESP\t")
          .append(sanitizeToken(response.getRequestId())).append("\t")
          .append(sanitizeToken(response.getStatus())).append("\t")
          .append(base64Encode(safeMsg))
          .append("\n");

        for (Map.Entry<String, String> entry : response.getPayload().entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";
            sb.append(key).append("=").append(base64Encode(val)).append("\n");
        }
        return sb.toString();
    }

    public static Response decodeResponse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty response data");
        }

        String[] lines = raw.split("\r?\n");
        if (lines.length == 0) {
            throw new IllegalArgumentException("No header line found");
        }

        String header = lines[0];
        String[] parts = header.split("\t", -1);
        if (parts.length < 4 || !"RESP".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid RESP header: " + header);
        }

        Response response = new Response();
        response.setRequestId(parts[1]);
        response.setStatus(parts[2]);
        response.setMessage(base64Decode(parts[3]));

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;
            int eqIdx = line.indexOf('=');
            if (eqIdx > 0) {
                String key = line.substring(0, eqIdx);
                String valEncoded = line.substring(eqIdx + 1);
                response.put(key, base64Decode(valEncoded));
            }
        }

        return response;
    }

    public static String base64Encode(String text) {
        if (text == null) return "";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return encoded; // Fallback if not valid base64
        }
    }

    private static String sanitizeToken(String val) {
        if (val == null) return "";
        return val.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
