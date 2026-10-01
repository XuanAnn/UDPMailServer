package org.example.mail.server.ui;

import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.common.NetworkUtils;
import org.example.mail.common.Validator;
import org.example.mail.server.core.ServerConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ServerConfigDialog extends JDialog {

    private final JTextField txtBindAddress = new JTextField();
    private final JTextField txtPort = new JTextField();
    private final JTextField txtDataPath = new JTextField();
    private final JTextField txtTimeout = new JTextField();
    private final JCheckBox chkAutoStart = new JCheckBox("Tự chạy khi mở");
    private final JCheckBox chkReportEnabled = new JCheckBox("Bật thống kê");

    private final ServerConfig config;
    private boolean saved = false;

    public ServerConfigDialog(Frame owner, ServerConfig config, boolean isServerRunning) {
        super(owner, "Cấu hình máy chủ", true);
        this.config = config;

        setSize(500, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AntDesign.BG_CONTAINER);

        // Header Modal Title
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setBackground(AntDesign.BG_CONTAINER);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(14, 20, 14, 20)
        ));
        JLabel titleLbl = new JLabel("Cấu hình máy chủ");
        titleLbl.setFont(AntDesign.FONT_TITLE);
        titleLbl.setForeground(AntDesign.TEXT_PRIMARY);
        headerPanel.add(titleLbl, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Form Body
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(AntDesign.BG_CONTAINER);
        formPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Style Inputs
        AntDesign.styleInput(txtBindAddress);
        AntDesign.styleInput(txtPort);
        AntDesign.styleInput(txtDataPath);
        AntDesign.styleInput(txtTimeout);

        txtBindAddress.setText(config.getBindAddress());
        txtPort.setText(String.valueOf(config.getPort()));
        if (isServerRunning) {
            txtPort.setEnabled(false);
            txtPort.setToolTipText("Không thể đổi port khi server đang chạy");
        }
        txtDataPath.setText(config.getDataPath());
        txtTimeout.setText(String.valueOf(config.getTimeoutMs()));

        chkAutoStart.setSelected(config.isAutoStart());
        chkAutoStart.setFont(AntDesign.FONT_BODY);
        chkAutoStart.setOpaque(false);

        chkReportEnabled.setSelected(config.isEmailReportEnabled());
        chkReportEnabled.setFont(AntDesign.FONT_BODY);
        chkReportEnabled.setOpaque(false);

        addFormField(formPanel, gbc, 0, "Địa chỉ IP:", txtBindAddress);
        addFormField(formPanel, gbc, 1, "Cổng UDP:", txtPort);
        addFormField(formPanel, gbc, 2, "Thư mục dữ liệu:", txtDataPath);
        addFormField(formPanel, gbc, 3, "Timeout (ms):", txtTimeout);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        formPanel.add(chkAutoStart, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        formPanel.add(chkReportEnabled, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Bottom Footer
        JPanel bottomArea = new JPanel(new BorderLayout());
        bottomArea.setBackground(AntDesign.BG_LAYOUT);
        bottomArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(12, 20, 12, 20)
        ));

        String localLanIp = NetworkUtils.getLocalIPv4Address();
        AntTag lanTag = AntDesign.createTag("IP: " + localLanIp, TagColor.PROCESSING);
        bottomArea.add(lanTag, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        AntButton btnCancel = AntDesign.createDefaultButton("Hủy");
        AntButton btnSave = AntDesign.createPrimaryButton("Lưu");

        btnPanel.add(btnCancel);
        btnPanel.add(btnSave);
        bottomArea.add(btnPanel, BorderLayout.EAST);

        add(bottomArea, BorderLayout.SOUTH);

        btnSave.addActionListener(e -> saveConfig(isServerRunning));
        btnCancel.addActionListener(e -> dispose());
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0.35;
        JLabel lbl = new JLabel(label);
        lbl.setFont(AntDesign.FONT_BODY_BOLD);
        lbl.setForeground(AntDesign.TEXT_PRIMARY);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0.65;
        panel.add(field, gbc);
    }

    private void saveConfig(boolean isServerRunning) {
        String bindAddr = txtBindAddress.getText().trim();
        String portStr = txtPort.getText().trim();
        String dataPath = txtDataPath.getText().trim();
        String timeoutStr = txtTimeout.getText().trim();

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (!Validator.isValidPort(port)) {
                JOptionPane.showMessageDialog(this, "Port must be between 1024 and 65535", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port number format", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int timeout;
        try {
            timeout = Integer.parseInt(timeoutStr);
            if (timeout < 500) {
                JOptionPane.showMessageDialog(this, "Timeout must be at least 500ms", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid timeout value", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Test directory write
        try {
            File dir = new File(dataPath);
            if (!dir.exists() && !dir.mkdirs()) {
                JOptionPane.showMessageDialog(this, "Cannot create data directory: " + dataPath, "Directory Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!dir.canWrite()) {
                JOptionPane.showMessageDialog(this, "Data directory is not writable: " + dataPath, "Permission Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Data path error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Test port availability if server not currently running
        if (!isServerRunning) {
            try (DatagramSocket testSocket = new DatagramSocket(null)) {
                testSocket.setReuseAddress(true);
                testSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddr), port));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Port " + port + " is in use or cannot be bound: " + ex.getMessage(),
                        "Port Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        config.setBindAddress(bindAddr.isEmpty() ? "0.0.0.0" : bindAddr);
        config.setPort(port);
        config.setDataPath(dataPath.isEmpty() ? "data" : dataPath);
        config.setTimeoutMs(timeout);
        config.setAutoStart(chkAutoStart.isSelected());
        config.setEmailReportEnabled(chkReportEnabled.isSelected());
        config.save();

        saved = true;
        JOptionPane.showMessageDialog(this, "Configuration saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }
}
