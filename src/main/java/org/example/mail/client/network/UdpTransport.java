package org.example.mail.client.network;

import org.example.mail.common.MessageCodec;
import org.example.mail.common.Protocol;
import org.example.mail.common.Request;
import org.example.mail.common.Response;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class UdpTransport {

    @FunctionalInterface
    public interface PushNotificationListener {
        void onNotification(String message);
    }

    private int timeoutMs;
    private int maxRetries;

    private DatagramSocket socket;
    private Thread receiverThread;
    private volatile boolean running = true;

    // In-flight requests awaiting response: requestId -> CompletableFuture
    private final Map<String, CompletableFuture<Response>> pendingRequests = new ConcurrentHashMap<>();
    private final List<PushNotificationListener> notificationListeners = new CopyOnWriteArrayList<>();

    public UdpTransport() {
        this(Protocol.DEFAULT_TIMEOUT_MS, Protocol.MAX_RETRIES);
    }

    public UdpTransport(int timeoutMs, int maxRetries) {
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        initSocket();
    }

    private synchronized void initSocket() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
                running = true;
                receiverThread = new Thread(this::listenLoop, "UDP-Client-Receiver");
                receiverThread.setDaemon(true);
                receiverThread.start();
            }
        } catch (SocketException e) {
            System.err.println("Failed to initialize client DatagramSocket: " + e.getMessage());
        }
    }

    public void addNotificationListener(PushNotificationListener listener) {
        if (listener != null) {
            notificationListeners.add(listener);
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[Protocol.MAX_PACKET_SIZE];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);

                if (raw.startsWith("NOTIFY")) {
                    // Real-time Push Notification from server!
                    for (PushNotificationListener l : notificationListeners) {
                        try {
                            l.onNotification(raw);
                        } catch (Exception ex) {
                            System.err.println("Error in notification listener: " + ex.getMessage());
                        }
                    }
                } else if (raw.startsWith("RESP")) {
                    try {
                        Response resp = MessageCodec.decodeResponse(raw);
                        CompletableFuture<Response> future = pendingRequests.remove(resp.getRequestId());
                        if (future != null) {
                            future.complete(resp);
                        }
                    } catch (Exception ex) {
                        System.err.println("Error decoding incoming response: " + ex.getMessage());
                    }
                }
            } catch (SocketException se) {
                if (!running) break;
            } catch (Exception e) {
                if (running) {
                    System.err.println("Error in client receive loop: " + e.getMessage());
                }
            }
        }
    }

    public Response send(Request request, String host, int port) throws IOException {
        initSocket();

        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            request.setRequestId(java.util.UUID.randomUUID().toString());
        }

        String rawReq = MessageCodec.encodeRequest(request);
        byte[] reqData = rawReq.getBytes(StandardCharsets.UTF_8);

        if (reqData.length > Protocol.MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Request payload size exceeds maximum UDP packet limit");
        }

        InetAddress targetAddress = InetAddress.getByName(host);
        DatagramPacket outPacket = new DatagramPacket(reqData, reqData.length, targetAddress, port);

        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            attempts++;
            CompletableFuture<Response> future = new CompletableFuture<>();
            pendingRequests.put(request.getRequestId(), future);

            try {
                synchronized (this) {
                    if (socket != null && !socket.isClosed()) {
                        socket.send(outPacket);
                    }
                }

                // Wait for response matching requestId
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);

            } catch (TimeoutException te) {
                lastException = te;
                // Will retry with same requestId (server is idempotent!)
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", ie);
            } catch (ExecutionException ee) {
                throw new IOException("Error processing response: " + ee.getCause().getMessage(), ee);
            } finally {
                pendingRequests.remove(request.getRequestId());
            }
        }

        throw new SocketTimeoutException("Request timed out after " + maxRetries + " attempts (" + host + ":" + port + ")");
    }

    public int getLocalPort() {
        return socket != null ? socket.getLocalPort() : 0;
    }

    public synchronized void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();
        }
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
