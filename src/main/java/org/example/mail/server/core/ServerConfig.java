package org.example.mail.server.core;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ServerConfig {
    private String bindAddress = "0.0.0.0";
    private int port = 5000;
    private String dataPath = "data";
    private int timeoutMs = 5000;
    private boolean autoStart = false;
    private boolean emailReportEnabled = true;

    private static final String CONFIG_FILE = "data/config/server.properties";

    public ServerConfig() {
        load();
    }

    public synchronized void load() {
        Path path = Paths.get(CONFIG_FILE);
        if (!Files.exists(path)) {
            save(); // Create with defaults
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            this.bindAddress = props.getProperty("bindAddress", "0.0.0.0").trim();
            this.port = Integer.parseInt(props.getProperty("port", "5000").trim());
            this.dataPath = props.getProperty("dataPath", "data").trim();
            this.timeoutMs = Integer.parseInt(props.getProperty("timeoutMs", "5000").trim());
            this.autoStart = Boolean.parseBoolean(props.getProperty("autoStart", "false").trim());
            this.emailReportEnabled = Boolean.parseBoolean(props.getProperty("emailReportEnabled", "true").trim());
        } catch (Exception e) {
            System.err.println("Could not load server config, using defaults: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Path path = Paths.get(CONFIG_FILE);
            Files.createDirectories(path.getParent());

            Properties props = new Properties();
            props.setProperty("bindAddress", bindAddress);
            props.setProperty("port", String.valueOf(port));
            props.setProperty("dataPath", dataPath);
            props.setProperty("timeoutMs", String.valueOf(timeoutMs));
            props.setProperty("autoStart", String.valueOf(autoStart));
            props.setProperty("emailReportEnabled", String.valueOf(emailReportEnabled));

            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "UDP Mail Server Configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to save server config: " + e.getMessage());
        }
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isEmailReportEnabled() {
        return emailReportEnabled;
    }

    public void setEmailReportEnabled(boolean emailReportEnabled) {
        this.emailReportEnabled = emailReportEnabled;
    }
}
