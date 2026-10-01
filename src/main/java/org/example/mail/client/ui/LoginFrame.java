package org.example.mail.client.ui;

import org.example.mail.client.network.MailClient;
import org.example.mail.client.service.AuthClientService;
import org.example.mail.client.service.MailClientService;
import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.common.NetworkUtils;
import org.example.mail.common.Protocol;
import org.example.mail.common.Response;
import org.example.mail.common.Validator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginFrame extends JFrame {

    private final String localIPv4 = NetworkUtils.getLocalIPv4Address();
    private final JTextField txtHost = new JTextField("127.0.0.1", 11);
    private final JTextField txtPort = new JTextField(String.valueOf(Protocol.DEFAULT_PORT), 4);

    // Login Form Fields
    private final JTextField txtLoginUser = new JTextField();
    private final JPasswordField txtLoginPass = new JPasswordField();
    private final AntButton btnLogin = AntDesign.createPrimaryButton("Đăng nhập (Sign In)");

    // Register Form Fields
    private final JTextField txtRegUser = new JTextField();
    private final JPasswordField txtRegPass = new JPasswordField();
    private final JPasswordField txtRegPassConfirm = new JPasswordField();
    private final AntButton btnRegister = AntDesign.createSuccessButton("Tạo tài khoản (Create Account)");

    private final JLabel lblStatus = new JLabel(" ", SwingConstants.CENTER);

    private MailClient client;
    private AuthClientService authService;
    private MailClientService mailService;

    public LoginFrame() {
        setTitle("Java Desktop Mail Client - Ant Design Edition");
        setSize(480, 600);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        initServices();
        initUI();
    }

    private void initServices() {
        String host = txtHost.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        client = new MailClient(host, port);
        authService = new AuthClientService(client);
        mailService = new MailClientService(client, authService);
    }

    private void initUI() {
        getContentPane().setBackground(AntDesign.BG_LAYOUT);
        setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(AntDesign.BG_LAYOUT);
        root.setBorder(new EmptyBorder(16, 24, 16, 24));

        // Header / Logo & Local LAN IPv4 badge
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        brandRow.setOpaque(false);
        JLabel logo = new JLabel("📬");
        logo.setFont(AntDesign.font(24f, Font.BOLD));
        JLabel title = new JLabel("Java Mail Client");
        title.setFont(AntDesign.FONT_TITLE_LARGE);
        title.setForeground(AntDesign.PRIMARY);
        brandRow.add(logo);
        brandRow.add(title);
        header.add(brandRow, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Ant Design Template • Reliable UDP Protocol", SwingConstants.CENTER);
        subtitle.setFont(AntDesign.FONT_BODY);
        subtitle.setForeground(AntDesign.TEXT_SECONDARY);
        header.add(subtitle, BorderLayout.CENTER);

        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        tagRow.setOpaque(false);
        AntTag myIpTag = AntDesign.createTag("💻 My LAN IPv4: " + localIPv4, TagColor.PROCESSING);
        tagRow.add(myIpTag);
        header.add(tagRow, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);

        // Center: Ant Design Card Container with Tabs (Login / Register)
        JPanel cardContainer = new JPanel(new BorderLayout());
        cardContainer.setBackground(AntDesign.BG_CONTAINER);
        cardContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(AntDesign.FONT_BODY_BOLD);
        tabs.setBackground(AntDesign.BG_CONTAINER);
        tabs.addTab("Đăng nhập (Sign In)", createLoginPanel());
        tabs.addTab("Đăng ký (Register)", createRegisterPanel());
        cardContainer.add(tabs, BorderLayout.CENTER);

        root.add(cardContainer, BorderLayout.CENTER);

        // Bottom: Server config & Status
        JPanel bottom = new JPanel(new BorderLayout(4, 6));
        bottom.setOpaque(false);

        JPanel serverBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        serverBar.setBackground(AntDesign.BG_CONTAINER);
        serverBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel lblSrv = new JLabel("Server:");
        lblSrv.setFont(AntDesign.FONT_SMALL_BOLD);
        lblSrv.setForeground(AntDesign.TEXT_SECONDARY);
        serverBar.add(lblSrv);

        AntDesign.styleInput(txtHost);
        serverBar.add(txtHost);

        JLabel lblColon = new JLabel(":");
        lblColon.setFont(AntDesign.FONT_BODY_BOLD);
        serverBar.add(lblColon);

        AntDesign.styleInput(txtPort);
        serverBar.add(txtPort);

        AntButton btnTestPing = AntDesign.createDefaultButton("⚡ Ping");
        serverBar.add(btnTestPing);

        AntButton btnUseLanIp = AntDesign.createDefaultButton("Use My IP");
        btnUseLanIp.setToolTipText("Fill Server Host with this machine's LAN IPv4 (" + localIPv4 + ")");
        btnUseLanIp.addActionListener(e -> txtHost.setText(localIPv4));
        serverBar.add(btnUseLanIp);

        bottom.add(serverBar, BorderLayout.NORTH);

        lblStatus.setFont(AntDesign.FONT_SMALL_BOLD);
        lblStatus.setForeground(AntDesign.TEXT_SECONDARY);
        bottom.add(lblStatus, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);

        // Style Form Fields
        AntDesign.styleInput(txtLoginUser);
        AntDesign.styleInput(txtLoginPass);
        AntDesign.styleInput(txtRegUser);
        AntDesign.styleInput(txtRegPass);
        AntDesign.styleInput(txtRegPassConfirm);

        // Action Listeners
        btnLogin.addActionListener(e -> doLogin());
        btnRegister.addActionListener(e -> doRegister());
        btnTestPing.addActionListener(e -> doPing());
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AntDesign.BG_CONTAINER);
        panel.setBorder(new EmptyBorder(12, 10, 12, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 4, 5, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        JLabel lblU = new JLabel("Tên tài khoản (Username):");
        lblU.setFont(AntDesign.FONT_BODY_BOLD);
        lblU.setForeground(AntDesign.TEXT_PRIMARY);
        panel.add(lblU, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(txtLoginUser, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblP = new JLabel("Mật khẩu (Password):");
        lblP.setFont(AntDesign.FONT_BODY_BOLD);
        lblP.setForeground(AntDesign.TEXT_PRIMARY);
        panel.add(lblP, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(txtLoginPass, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        gbc.insets = new Insets(14, 4, 5, 4);
        btnLogin.setPreferredSize(new Dimension(180, 36));
        panel.add(btnLogin, gbc);

        // Press Enter to submit
        txtLoginPass.addActionListener(e -> doLogin());
        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AntDesign.BG_CONTAINER);
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        JLabel lblU = new JLabel("Tên đăng ký (3-30 ký tự chữ/số):");
        lblU.setFont(AntDesign.FONT_BODY_BOLD);
        lblU.setForeground(AntDesign.TEXT_PRIMARY);
        panel.add(lblU, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(txtRegUser, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblP = new JLabel("Mật khẩu:");
        lblP.setFont(AntDesign.FONT_BODY_BOLD);
        lblP.setForeground(AntDesign.TEXT_PRIMARY);
        panel.add(lblP, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(txtRegPass, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        JLabel lblPc = new JLabel("Xác nhận mật khẩu:");
        lblPc.setFont(AntDesign.FONT_BODY_BOLD);
        lblPc.setForeground(AntDesign.TEXT_PRIMARY);
        panel.add(lblPc, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(txtRegPassConfirm, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        gbc.insets = new Insets(10, 4, 4, 4);
        btnRegister.setPreferredSize(new Dimension(180, 36));
        panel.add(btnRegister, gbc);

        txtRegPassConfirm.addActionListener(e -> doRegister());
        return panel;
    }

    private void updateClientAddress() {
        try {
            String host = txtHost.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            client.setHost(host);
            client.setPort(port);
        } catch (Exception ex) {
            lblStatus.setText("Cấu hình Host hoặc Port không hợp lệ");
            lblStatus.setForeground(AntDesign.DANGER);
        }
    }

    private void doPing() {
        updateClientAddress();
        lblStatus.setText("Đang kiểm tra kết nối tới Server...");
        lblStatus.setForeground(AntDesign.PRIMARY);

        mailService.checkConnection((ok, err) -> {
            if (Boolean.TRUE.equals(ok)) {
                lblStatus.setText("🟢 Server đang hoạt động tốt (Online)");
                lblStatus.setForeground(AntDesign.SUCCESS);
            } else {
                lblStatus.setText("🔴 Không thể kết nối tới server: " + (err != null ? err.getMessage() : "Timeout"));
                lblStatus.setForeground(AntDesign.DANGER);
            }
        });
    }

    private void doLogin() {
        updateClientAddress();
        String user = txtLoginUser.getText().trim();
        String pass = new String(txtLoginPass.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            lblStatus.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu");
            lblStatus.setForeground(AntDesign.DANGER);
            return;
        }

        setBusy(true, "Đang xác thực thông tin đăng nhập...");

        SwingWorker<Response, Void> worker = new SwingWorker<>() {
            @Override
            protected Response doInBackground() throws Exception {
                return authService.login(user, pass);
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    Response res = get();
                    if (res != null && res.isOk()) {
                        lblStatus.setText("Đăng nhập thành công! Đang mở hòm thư...");
                        lblStatus.setForeground(AntDesign.SUCCESS);
                        openMainFrame();
                    } else {
                        String msg = (res != null) ? res.getMessage() : "Lỗi không xác định";
                        lblStatus.setText("Đăng nhập thất bại: " + msg);
                        lblStatus.setForeground(AntDesign.DANGER);
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Lỗi mạng: " + ex.getMessage());
                    lblStatus.setForeground(AntDesign.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void doRegister() {
        updateClientAddress();
        String user = txtRegUser.getText().trim();
        String pass = new String(txtRegPass.getPassword());
        String pass2 = new String(txtRegPassConfirm.getPassword());

        if (!Validator.isValidUsername(user)) {
            lblStatus.setText("Tên đăng nhập không hợp lệ (3-30 ký tự a-z, 0-9, _)");
            lblStatus.setForeground(AntDesign.DANGER);
            return;
        }

        if (pass.length() < 3) {
            lblStatus.setText("Mật khẩu phải có ít nhất 3 ký tự");
            lblStatus.setForeground(AntDesign.DANGER);
            return;
        }

        if (!pass.equals(pass2)) {
            lblStatus.setText("Mật khẩu xác nhận không khớp");
            lblStatus.setForeground(AntDesign.DANGER);
            return;
        }

        setBusy(true, "Đang khởi tạo tài khoản mới trên server...");

        SwingWorker<Response, Void> worker = new SwingWorker<>() {
            @Override
            protected Response doInBackground() throws Exception {
                return authService.register(user, pass);
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    Response res = get();
                    if (res != null && res.isOk()) {
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "Tạo tài khoản '" + user + "' thành công!\n" +
                                "Server đã tạo thư mục và thư chào mừng (new_email.txt).\n" +
                                "Bây giờ bạn có thể đăng nhập ngay.",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        txtLoginUser.setText(user);
                        txtLoginPass.setText(pass);
                        txtRegUser.setText("");
                        txtRegPass.setText("");
                        txtRegPassConfirm.setText("");
                    } else {
                        String msg = (res != null) ? res.getMessage() : "Lỗi đăng ký";
                        lblStatus.setText("Không thể tạo tài khoản: " + msg);
                        lblStatus.setForeground(AntDesign.DANGER);
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Lỗi mạng: " + ex.getMessage());
                    lblStatus.setForeground(AntDesign.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void setBusy(boolean busy, String msg) {
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
        if (busy) {
            lblStatus.setText(msg);
            lblStatus.setForeground(AntDesign.PRIMARY);
        }
    }

    private void openMainFrame() {
        SwingUtilities.invokeLater(() -> {
            MainFrame main = new MainFrame(client, authService, mailService, () -> {
                // Logout callback
                SwingUtilities.invokeLater(() -> {
                    txtLoginPass.setText("");
                    lblStatus.setText("Đã đăng xuất khỏi tài khoản");
                    lblStatus.setForeground(AntDesign.TEXT_SECONDARY);
                    setVisible(true);
                });
            });
            main.setVisible(true);
            dispose();
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            LoginFrame frame = new LoginFrame();
            frame.setVisible(true);
        });
    }
}
