package org.example.mail.server.core;

import org.example.mail.common.*;
import org.example.mail.server.service.AccountService;
import org.example.mail.server.service.AuthService;
import org.example.mail.server.service.MailService;
import org.example.mail.server.storage.AccountRepository;
import org.example.mail.server.storage.MailRepository;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MailServer {

    public enum Status {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    public interface StatusListener {
        void onStatusChanged(Status status, String message);
    }

    private final ServerConfig config;
    private final AccountRepository accountRepository;
    private final MailRepository mailRepository;
    private final AuthService authService;
    private final AccountService accountService;
    private final MailService mailService;
    private final RequestDispatcher dispatcher;

    private DatagramSocket socket;
    private volatile Status status = Status.STOPPED;
    private Thread serverThread;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "UDP-Worker");
        t.setDaemon(true);
        return t;
    });
    private final List<StatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final ServerLogListener logListener;

    public MailServer(ServerConfig config, ServerLogListener logListener) {
        this.config = config != null ? config : new ServerConfig();
        this.logListener = logListener != null ? logListener : (level, msg) -> {};

        this.accountRepository = new AccountRepository(this.config.getDataPath());
        this.mailRepository = new MailRepository(this.config.getDataPath());
        this.authService = new AuthService(this.accountRepository);
        this.accountService = new AccountService(this.accountRepository, this.mailRepository);
        this.mailService = new MailService(this.mailRepository, this.accountRepository);

        this.dispatcher = new RequestDispatcher(authService, accountService, mailService, this.logListener);
        this.dispatcher.setNotificationSender((addr, port, msg) -> {
            try {
                byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                DatagramPacket p = new DatagramPacket(bytes, bytes.length, addr, port);
                synchronized (this) {
                    if (socket != null && !socket.isClosed()) {
                        socket.send(p);
                    }
                }
            } catch (Exception ex) {
                this.logListener.onLog("WARN", "Failed to push UDP notification to " + addr + ":" + port + " - " + ex.getMessage());
            }
        });
    }

    public void addStatusListener(StatusListener listener) {
        if (listener != null) {
            statusListeners.add(listener);
        }
    }

    private void updateStatus(Status newStatus, String message) {
        this.status = newStatus;
        for (StatusListener l : statusListeners) {
            try {
                l.onStatusChanged(newStatus, message);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void start(int port) throws IOException {
        if (status == Status.RUNNING || status == Status.STARTING) {
            logListener.onLog("WARN", "Server is already running or starting.");
            return;
        }

        updateStatus(Status.STARTING, "Binding socket on port " + port + "...");
        logListener.onLog("INFO", "Starting UDP Mail Server on port " + port + "...");

        try {
            // Check bind address
            SocketAddress endpoint;
            if (config.getBindAddress() == null || "0.0.0.0".equals(config.getBindAddress().trim())) {
                endpoint = new InetSocketAddress(port);
            } else {
                endpoint = new InetSocketAddress(InetAddress.getByName(config.getBindAddress()), port);
            }

            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(endpoint);

            updateStatus(Status.RUNNING, "Server listening on port " + port);
            logListener.onLog("INFO", "Server successfully bound to " + endpoint + " [RUNNING]");

            dispatcher.getActivityTracker().initUsers(accountService.getAllAccounts());

            serverThread = new Thread(this::listenLoop, "UDP-Server-Receiver");
            serverThread.setDaemon(true);
            serverThread.start();

        } catch (SocketException | UnknownHostException e) {
            updateStatus(Status.ERROR, "Port " + port + " error: " + e.getMessage());
            logListener.onLog("ERROR", "Failed to bind port " + port + ": " + e.getMessage());
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            throw e;
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[Protocol.MAX_PACKET_SIZE];

        while (status == Status.RUNNING && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Clone data so worker thread can process safely
                int length = packet.getLength();
                byte[] data = new byte[length];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, length);
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                workerPool.submit(() -> handlePacket(data, clientAddr, clientPort));

            } catch (SocketException se) {
                if (status != Status.RUNNING) {
                    break; // Normal close
                }
                logListener.onLog("WARN", "Socket exception in listen loop: " + se.getMessage());
            } catch (Exception e) {
                if (status == Status.RUNNING) {
                    logListener.onLog("ERROR", "Receive error: " + e.getMessage());
                }
            }
        }
    }

    private void handlePacket(byte[] data, InetAddress clientAddr, int clientPort) {
        String rawRequest = new String(data, StandardCharsets.UTF_8);
        Response response;

        try {
            Request request = MessageCodec.decodeRequest(rawRequest);
            response = dispatcher.dispatch(request, clientAddr, clientPort);
        } catch (Exception ex) {
            logListener.onLog("WARN", "Malformed packet from " + clientAddr + ":" + clientPort + " - " + ex.getMessage());
            response = Response.error("UNKNOWN", "Malformed request: " + ex.getMessage());
        }

        try {
            String rawResponse = MessageCodec.encodeResponse(response);
            byte[] responseBytes = rawResponse.getBytes(StandardCharsets.UTF_8);

            if (responseBytes.length > Protocol.MAX_PACKET_SIZE) {
                response = Response.status(response.getRequestId(), Protocol.STATUS_PAYLOAD_TOO_LARGE, "Response payload too large");
                rawResponse = MessageCodec.encodeResponse(response);
                responseBytes = rawResponse.getBytes(StandardCharsets.UTF_8);
            }

            DatagramPacket reply = new DatagramPacket(
                    responseBytes,
                    responseBytes.length,
                    clientAddr,
                    clientPort
            );

            synchronized (this) {
                if (socket != null && !socket.isClosed()) {
                    socket.send(reply);
                }
            }
        } catch (Exception e) {
            logListener.onLog("ERROR", "Failed to send response to " + clientAddr + ":" + clientPort + " - " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (status == Status.STOPPED) return;

        logListener.onLog("INFO", "Stopping UDP Mail Server...");
        updateStatus(Status.STOPPED, "Server stopped");

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }

        logListener.onLog("INFO", "UDP Mail Server stopped successfully.");
    }

    public synchronized void restart(int port) throws IOException {
        stop();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        start(port);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public RequestDispatcher getDispatcher() {
        return dispatcher;
    }

    public AccountService getAccountService() {
        return accountService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public MailService getMailService() {
        return mailService;
    }

    public UserActivityTracker getUserActivityTracker() {
        return dispatcher.getActivityTracker();
    }

    public ServerConfig getConfig() {
        return config;
    }
}
