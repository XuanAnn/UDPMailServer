package org.example.mail.client.ui;

import org.example.mail.client.model.MailItem;
import org.example.mail.client.network.MailClient;
import org.example.mail.client.service.AuthClientService;
import org.example.mail.client.service.MailClientService;
import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.common.NetworkUtils;
import org.example.mail.common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public class MainFrame extends JFrame {

    private final MailClient client;
    private final AuthClientService authService;
    private final MailClientService mailService;
    private final Runnable onLogoutCallback;
    private final String localIPv4 = NetworkUtils.getLocalIPv4Address();

    // Sub-panels
    private SidebarPanel sidebarPanel;
    private MessageListPanel messageListPanel;
    private ReadingPanel readingPanel;

    // Header Components
    private final AntTag tagConnStatus = AntDesign.createTag("Online", TagColor.SUCCESS);
    private final AntTag tagRealtimeBadge = AntDesign.createTag("Realtime", TagColor.SUCCESS);
    private final JLabel lblAlertBanner = new JLabel("");
    private final AntButton btnRetryConn = AntDesign.createDefaultButton("Thử lại");
    private final AntButton btnRefresh = AntDesign.createDefaultButton("Làm mới");
    private final AntButton btnLogout = AntDesign.createDangerButton("Đăng xuất");

    private String activeFolder = Protocol.FOLDER_INBOX;
    private Timer heartbeatTimer;
    private Timer autoSyncTimer;
    private Timer alertDismissTimer;

    public MainFrame(MailClient client,
                     AuthClientService authService,
                     MailClientService mailService,
                     Runnable onLogoutCallback) {
        this.client = client;
        this.authService = authService;
        this.mailService = mailService;
        this.onLogoutCallback = onLogoutCallback;

        setTitle("Mail Client - " + authService.getCurrentSession().getUsername());
        setSize(1160, 760);
        setMinimumSize(new Dimension(900, 540));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        initUI();
        setupHeartbeat();
        setupRealtimeSystem();
        refreshCurrentFolder();
    }

    private void initUI() {
        getContentPane().setBackground(AntDesign.BG_LAYOUT);
        setLayout(new BorderLayout());

        // 1. Top Header Bar (Ant Design Pro Navbar)
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(AntDesign.BG_CONTAINER);

        JPanel headerBar = new JPanel(new BorderLayout(10, 0));
        headerBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(8, 16, 8, 16)
        ));
        headerBar.setBackground(AntDesign.BG_CONTAINER);

        // Brand & LAN Indicators
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandPanel.setOpaque(false);
        JLabel logo = new JLabel("📬");
        logo.setFont(AntDesign.font(20f, Font.BOLD));
        JLabel title = new JLabel("Mail Client");
        title.setFont(AntDesign.FONT_SUBTITLE);
        title.setForeground(AntDesign.PRIMARY);
        brandPanel.add(logo);
        brandPanel.add(title);

        brandPanel.add(btnRefresh);

        // LAN IPv4 Badge for Client
        AntTag myIpTag = AntDesign.createTag("IP: " + localIPv4, TagColor.PROCESSING);
        brandPanel.add(myIpTag);

        // Server Host Indicator
        AntTag serverTag = AntDesign.createTag("Server: " + client.getHost() + ":" + client.getPort(), TagColor.CYAN);
        brandPanel.add(serverTag);

        tagRealtimeBadge.setToolTipText("Thời gian thực (UDP)");
        brandPanel.add(tagRealtimeBadge);

        headerBar.add(brandPanel, BorderLayout.WEST);

        // Network & User status (Right)
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        userPanel.setOpaque(false);

        userPanel.add(tagConnStatus);

        btnRetryConn.setVisible(false);
        userPanel.add(btnRetryConn);

        AntTag userTag = AntDesign.createTag(authService.getCurrentSession().getUsername(), TagColor.PURPLE);
        userPanel.add(userTag);

        userPanel.add(btnLogout);
        headerBar.add(userPanel, BorderLayout.EAST);

        topContainer.add(headerBar, BorderLayout.NORTH);

        // Realtime notification banner (Ant Design Alert style)
        JPanel bannerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        bannerPanel.setBackground(AntDesign.SUCCESS_BG);
        bannerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.SUCCESS_BORDER));
        lblAlertBanner.setFont(AntDesign.FONT_BODY_BOLD);
        lblAlertBanner.setForeground(AntDesign.SUCCESS);
        bannerPanel.add(lblAlertBanner);
        bannerPanel.setVisible(false);
        topContainer.add(bannerPanel, BorderLayout.SOUTH);

        add(topContainer, BorderLayout.NORTH);

        // 2. Main Content Panes
        sidebarPanel = new SidebarPanel();
        messageListPanel = new MessageListPanel();
        readingPanel = new ReadingPanel(mailService, this::refreshCurrentFolder);

        // Split between Message List and Reading Pane
        JSplitPane mailSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                messageListPanel,
                readingPanel
        );
        mailSplitPane.setDividerLocation(360);
        mailSplitPane.setContinuousLayout(true);
        mailSplitPane.setBorder(null);

        // Split between Sidebar and (List + Reading Pane)
        JSplitPane rootSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                sidebarPanel,
                mailSplitPane
        );
        rootSplitPane.setDividerLocation(200);
        rootSplitPane.setContinuousLayout(true);
        rootSplitPane.setBorder(null);

        add(rootSplitPane, BorderLayout.CENTER);

        // 3. Bottom Status Bar (Full details for LAN monitoring)
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(AntDesign.BG_CONTAINER);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(5, 16, 5, 16)
        ));

        int clientPort = client.getTransport() != null ? client.getTransport().getLocalPort() : 0;
        JLabel lblBottomLeft = new JLabel("💻 Client Host IPv4: " + localIPv4 + (clientPort > 0 ? " | UDP Socket: " + clientPort : ""));
        lblBottomLeft.setFont(AntDesign.FONT_SMALL_BOLD);
        lblBottomLeft.setForeground(AntDesign.TEXT_PRIMARY);
        bottomBar.add(lblBottomLeft, BorderLayout.WEST);

        JLabel lblBottomCenter = new JLabel("🌐 Server: " + client.getHost() + ":" + client.getPort());
        lblBottomCenter.setFont(AntDesign.FONT_SMALL);
        lblBottomCenter.setForeground(AntDesign.TEXT_SECONDARY);
        bottomBar.add(lblBottomCenter, BorderLayout.CENTER);

        JLabel lblBottomRight = new JLabel("⚡ Realtime UDP Delivery: Active (Push & Background Sync)");
        lblBottomRight.setFont(AntDesign.FONT_SMALL_BOLD);
        lblBottomRight.setForeground(AntDesign.SUCCESS);
        bottomBar.add(lblBottomRight, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);

        // 4. Connect Callbacks
        sidebarPanel.setOnFolderSelected(folder -> {
            this.activeFolder = folder;
            messageListPanel.setFolderTitle(capitalize(folder));
            readingPanel.clear();
            loadFolderEmails(folder, false);
        });

        sidebarPanel.setOnComposeClick(this::openComposeDialog);

        messageListPanel.setOnSelectMailCallback(item -> {
            if (item != null) {
                // Fetch full email content
                mailService.fetchMail(activeFolder, item.getMailId(), (mail, err) -> {
                    if (mail != null) {
                        readingPanel.setMail(mail, activeFolder);
                        // Update unread count badge
                        if (Protocol.FOLDER_INBOX.equalsIgnoreCase(activeFolder)) {
                            updateInboxBadge();
                        }
                    } else if (err != null) {
                        JOptionPane.showMessageDialog(this, "Không thể mở email: " + err.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });

        btnRefresh.addActionListener(e -> refreshCurrentFolder());
        btnRetryConn.addActionListener(e -> checkConnection());
        btnLogout.addActionListener(e -> doLogout());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doLogout();
            }
        });
    }

    private void openComposeDialog() {
        ComposeDialog dlg = new ComposeDialog(this, mailService, this::refreshCurrentFolder);
        dlg.setVisible(true);
    }

    private void refreshCurrentFolder() {
        btnRefresh.setEnabled(false);
        loadFolderEmails(activeFolder, false);
        updateInboxBadge();
    }

    private void silentSyncFolder() {
        if (!authService.isLoggedIn()) return;
        loadFolderEmails(activeFolder, true);
        updateInboxBadge();
    }

    private void loadFolderEmails(String folder, boolean silent) {
        if (!silent) {
            btnRefresh.setEnabled(false);
        }

        mailService.fetchFolder(folder, (items, err) -> {
            if (!silent) {
                btnRefresh.setEnabled(true);
            }
            if (items != null) {
                setOnlineStatus(true);
                messageListPanel.setMailItems(items);
            } else if (err != null && !silent) {
                setOnlineStatus(false);
                JOptionPane.showMessageDialog(this, "Không thể tải danh sách email: " + err.getMessage(), "Cảnh báo mạng", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private void updateInboxBadge() {
        if (!authService.isLoggedIn()) return;
        mailService.fetchFolder(Protocol.FOLDER_INBOX, (items, err) -> {
            if (items != null) {
                int unread = (int) items.stream().filter(m -> !m.isReadState()).count();
                sidebarPanel.setUnreadCount(Protocol.FOLDER_INBOX, unread);
            }
        });
    }

    private void setupRealtimeSystem() {
        // 1. Instant UDP Push Notification from Server
        mailService.addNotificationListener(raw -> {
            SwingUtilities.invokeLater(() -> {
                String[] parts = raw.split("\t");
                String from = (parts.length > 2) ? parts[2] : "Someone";
                String sub = (parts.length > 3) ? parts[3] : "New message";

                // Instantly update folder without clicking refresh!
                silentSyncFolder();
                updateInboxBadge();

                showAlertBanner("Thư mới: " + from + " - " + sub);
            });
        });

        // 2. Automated background sync every 2.5 seconds (ensures real-time even if UDP push packet is dropped)
        autoSyncTimer = new Timer(2500, e -> silentSyncFolder());
        autoSyncTimer.start();
    }

    private void showAlertBanner(String message) {
        lblAlertBanner.setText(message);
        Container parent = lblAlertBanner.getParent();
        if (parent != null) {
            parent.setVisible(true);
        }

        if (alertDismissTimer != null && alertDismissTimer.isRunning()) {
            alertDismissTimer.stop();
        }
        alertDismissTimer = new Timer(5000, e -> {
            if (parent != null) {
                parent.setVisible(false);
            }
        });
        alertDismissTimer.setRepeats(false);
        alertDismissTimer.start();
    }

    private void setupHeartbeat() {
        heartbeatTimer = new Timer(10000, e -> checkConnection());
        heartbeatTimer.start();
    }

    private void checkConnection() {
        mailService.checkConnection((ok, err) -> {
            setOnlineStatus(Boolean.TRUE.equals(ok));
        });
    }

    private void setOnlineStatus(boolean online) {
        if (online) {
            tagConnStatus.setText("Online");
            btnRetryConn.setVisible(false);
        } else {
            tagConnStatus.setText("Offline");
            btnRetryConn.setVisible(true);
        }
    }

    private void doLogout() {
        int opt = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn đăng xuất?",
                "Đăng xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (opt != JOptionPane.YES_OPTION) return;

        if (heartbeatTimer != null) heartbeatTimer.stop();
        if (autoSyncTimer != null) autoSyncTimer.stop();
        if (alertDismissTimer != null) alertDismissTimer.stop();

        authService.logout();
        dispose();

        if (onLogoutCallback != null) {
            onLogoutCallback.run();
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}
