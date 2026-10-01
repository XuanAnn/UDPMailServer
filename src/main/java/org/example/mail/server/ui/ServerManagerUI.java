package org.example.mail.server.ui;

import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.common.NetworkUtils;
import org.example.mail.common.Protocol;
import org.example.mail.server.core.MailServer;
import org.example.mail.server.core.ServerConfig;
import org.example.mail.server.core.UserActivityRecord;
import org.example.mail.server.core.UserActivityTracker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;

public class ServerManagerUI extends JFrame {

    private final ServerConfig config = new ServerConfig();
    private final LogPanel logPanel = new LogPanel();
    private final String localIPv4 = NetworkUtils.getLocalIPv4Address();
    private MailServer server;

    // UI Components - Ant Design Control Buttons
    private final AntButton btnStart = AntDesign.createSuccessButton("Khởi động");
    private final AntButton btnStop = AntDesign.createDangerButton("Dừng");
    private final AntButton btnRestart = AntDesign.createPrimaryButton("Khởi động lại");
    private final AntButton btnConfig = AntDesign.createDefaultButton("Cấu hình");
    private final AntButton btnAbout = AntDesign.createDefaultButton("Thông tin");
    private final JCheckBox chkAutoStart = new JCheckBox("Tự chạy");
    private final JCheckBox chkEmailReport = new JCheckBox("Báo cáo", true);

    // Status Badges (AntTag based)
    private final JLabel lblSocketStatus = new JLabel("STOPPED");
    private final JLabel lblAccountService = new JLabel("STOPPED");
    private final JLabel lblAuthService = new JLabel("STOPPED");
    private final JLabel lblMailService = new JLabel("STOPPED");
    private final JLabel lblPortInfo = new JLabel("Port: " + config.getPort());

    // Report / Statistics Labels
    private final JLabel lblStatSent = new JLabel("0");
    private final JLabel lblStatReceived = new JLabel("0");
    private final JLabel lblStatAccounts = new JLabel("0");
    private final JLabel lblStatSessions = new JLabel("0");
    private final JLabel lblStatErrors = new JLabel("0");
    private final JLabel lblStatDuplicates = new JLabel("0");

    // 1. User Status & Activity Table (Status Tổng Thể)
    private final DefaultTableModel userStatusTableModel = new DefaultTableModel(
            new String[]{
                    "Tài khoản",
                    "Kết nối",
                    "Đăng ký",
                    "Đăng nhập",
                    "Giờ vào",
                    "Giờ ra",
                    "Hoạt động",
                    "Thời gian"
            }, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable userStatusTable = new JTable(userStatusTableModel);
    private final JLabel lblOverallSummary = new JLabel(" (0 tài khoản)");

    // 2. Account list table for live demo
    private final DefaultTableModel accountTableModel = new DefaultTableModel(
            new String[]{"Tài khoản", "Mật khẩu", "Ngày tạo", "Trạng thái"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable accountTable = new JTable(accountTableModel);

    private Timer statsTimer;

    public ServerManagerUI() {
        setTitle("UDP Mail Server Manager - v" + Protocol.VERSION);
        setSize(1260, 800);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        initServer();
        initUI();
        setupListeners();

        refreshAccountsTable();
        refreshUserStatusTable();

        // Start stats and user activity refresh timer (every 1.5 seconds)
        statsTimer = new Timer(1500, e -> {
            updateStatistics();
            refreshUserStatusTable();
        });
        statsTimer.start();

        // Auto start if configured
        if (config.isAutoStart()) {
            SwingUtilities.invokeLater(this::startServer);
        }
    }

    private void initServer() {
        server = new MailServer(config, logPanel::log);
        server.addStatusListener((status, msg) -> SwingUtilities.invokeLater(() -> onServerStatusChanged(status, msg)));
        server.getUserActivityTracker().addChangeListener(() -> SwingUtilities.invokeLater(this::refreshUserStatusTable));
        server.getUserActivityTracker().initUsers(server.getAccountService().getAllAccounts());
    }

    private void initUI() {
        getContentPane().setBackground(AntDesign.BG_LAYOUT);
        setLayout(new BorderLayout(0, 0));

        // Top Header & Control Panel Card
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBackground(AntDesign.BG_LAYOUT);
        topPanel.setBorder(new EmptyBorder(10, 12, 6, 12));

        // 1. Ant Design Control Bar
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBackground(AntDesign.BG_CONTAINER);
        controlPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(8, 14, 8, 14)
        ));

        // Left action buttons
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBtns.setOpaque(false);

        btnStop.setEnabled(false);
        btnRestart.setEnabled(false);

        leftBtns.add(btnStart);
        leftBtns.add(btnStop);
        leftBtns.add(btnRestart);
        leftBtns.add(btnConfig);

        controlPanel.add(leftBtns, BorderLayout.WEST);

        // Right toggles & about
        JPanel rightOpts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightOpts.setOpaque(false);

        chkAutoStart.setSelected(config.isAutoStart());
        chkAutoStart.setFont(AntDesign.FONT_BODY);
        chkAutoStart.setOpaque(false);
        rightOpts.add(chkAutoStart);

        chkEmailReport.setSelected(config.isEmailReportEnabled());
        chkEmailReport.setFont(AntDesign.FONT_BODY);
        chkEmailReport.setOpaque(false);
        rightOpts.add(chkEmailReport);

        rightOpts.add(btnAbout);
        controlPanel.add(rightOpts, BorderLayout.EAST);

        // 2. LAN IPv4 Banner (Styled as Ant Design Alert)
        JPanel lanAlert = new JPanel(new BorderLayout(10, 0));
        lanAlert.setBackground(AntDesign.PRIMARY_BG);
        lanAlert.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.PRIMARY_BORDER, 1),
                new EmptyBorder(6, 14, 6, 14)
        ));

        JPanel lanLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        lanLeft.setOpaque(false);

        JLabel lblInfoIcon = new JLabel("🌐");
        lblInfoIcon.setFont(AntDesign.font(15f, Font.BOLD));
        lanLeft.add(lblInfoIcon);

        JLabel lblLanTag = new JLabel("Server IP:");
        lblLanTag.setFont(AntDesign.FONT_BODY_BOLD);
        lblLanTag.setForeground(AntDesign.PRIMARY);
        lanLeft.add(lblLanTag);

        JTextField txtLan = new JTextField(localIPv4, 12);
        txtLan.setEditable(false);
        txtLan.setFont(AntDesign.FONT_CODE);
        txtLan.setBackground(Color.WHITE);
        txtLan.setForeground(AntDesign.TEXT_PRIMARY);
        AntDesign.styleInput(txtLan);
        lanLeft.add(txtLan);

        AntButton btnCopyLan = AntDesign.createDefaultButton("Sao chép");
        btnCopyLan.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(localIPv4), null);
            JOptionPane.showMessageDialog(this, "Đã sao chép IP: " + localIPv4, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });
        lanLeft.add(btnCopyLan);

        lanAlert.add(lanLeft, BorderLayout.WEST);

        AntTag portBadge = AntDesign.createTag("Port: " + config.getPort(), TagColor.PROCESSING);
        lanAlert.add(portBadge, BorderLayout.EAST);

        JPanel topBars = new JPanel(new GridLayout(2, 1, 0, 8));
        topBars.setOpaque(false);
        topBars.add(controlPanel);
        topBars.add(lanAlert);
        topPanel.add(topBars, BorderLayout.NORTH);

        // 3. Status Cards Row (Ant Design Cards)
        lblPortInfo.setText("Port: " + config.getPort());
        JPanel statusCardsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        statusCardsPanel.setOpaque(false);

        statusCardsPanel.add(createStatusCard("Socket UDP", lblSocketStatus, lblPortInfo));
        statusCardsPanel.add(createStatusCard("Tài khoản", lblAccountService, new JLabel("accounts/")));
        statusCardsPanel.add(createStatusCard("Xác thực", lblAuthService, new JLabel("Session Token")));
        statusCardsPanel.add(createStatusCard("Hộp thư", lblMailService, new JLabel("accounts/<user>/")));

        topPanel.add(statusCardsPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Center: JSplitPane with Overall User Status Dashboard on Top, and Compact Terminal Console on Bottom
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(AntDesign.FONT_BODY_BOLD);
        tabbedPane.setBackground(AntDesign.BG_LAYOUT);

        // Tab 1: Live User Status & Activity (Status Tổng Thể)
        tabbedPane.addTab("Người dùng", createOverallStatusPanel());

        // Tab 2: Accounts & Credentials
        JPanel accountPanel = new JPanel(new BorderLayout(5, 5));
        accountPanel.setBackground(AntDesign.BG_CONTAINER);
        accountPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel accTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        accTop.setOpaque(false);
        AntButton btnRefreshAcc = AntDesign.createPrimaryButton("Làm mới");
        accTop.add(btnRefreshAcc);
        btnRefreshAcc.addActionListener(e -> refreshAccountsTable());
        accountPanel.add(accTop, BorderLayout.NORTH);

        AntDesign.styleTable(accountTable);
        accountTable.getColumnModel().getColumn(3).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                AntDesign.createTag(String.valueOf(value), TagColor.SUCCESS));

        JScrollPane accScroll = new JScrollPane(accountTable);
        accScroll.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));
        accountPanel.add(accScroll, BorderLayout.CENTER);
        tabbedPane.addTab("Tài khoản", accountPanel);

        // Tab 3: Detailed Email Report & Metrics
        JPanel reportPanel = createReportPanel();
        tabbedPane.addTab("Thống kê", reportPanel);

        // Compact Terminal Panel (Thu nhỏ terminal console bên dưới)
        JPanel compactTerminal = createCompactTerminalPanel();

        // Vertical Split Pane: Top = Status Tổng Thể, Bottom = Terminal thu nhỏ
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, compactTerminal);
        centerSplit.setDividerLocation(360);
        centerSplit.setResizeWeight(0.68);
        centerSplit.setContinuousLayout(true);
        centerSplit.setBorder(new EmptyBorder(0, 12, 0, 12));
        centerSplit.setBackground(AntDesign.BG_LAYOUT);

        add(centerSplit, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(AntDesign.BG_CONTAINER);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(6, 16, 6, 16)
        ));
        JLabel lblLeft = new JLabel("IP: " + localIPv4 + " | Port: " + config.getPort());
        lblLeft.setFont(AntDesign.FONT_SMALL_BOLD);
        lblLeft.setForeground(AntDesign.TEXT_PRIMARY);
        statusBar.add(lblLeft, BorderLayout.WEST);

        JLabel lblCopy = new JLabel("UDP Mail Server v" + Protocol.VERSION);
        lblCopy.setForeground(AntDesign.TEXT_TERTIARY);
        lblCopy.setFont(AntDesign.FONT_SMALL);
        statusBar.add(lblCopy, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        updateStatusBadges(MailServer.Status.STOPPED);
    }

    private JPanel createStatusCard(String title, JLabel statusLabel, JLabel subLabel) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(AntDesign.BG_CONTAINER);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(AntDesign.FONT_BODY_BOLD);
        titleLbl.setForeground(AntDesign.TEXT_SECONDARY);

        statusLabel.setFont(AntDesign.font(14f, Font.BOLD));
        statusLabel.setForeground(AntDesign.DANGER);

        subLabel.setFont(AntDesign.FONT_SMALL);
        subLabel.setForeground(AntDesign.TEXT_TERTIARY);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(statusLabel, BorderLayout.CENTER);
        card.add(subLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createReportPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 14, 14));
        panel.setBackground(AntDesign.BG_LAYOUT);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        panel.add(AntDesign.createStatisticCard("Thư đã gửi", lblStatSent, AntDesign.SUCCESS));
        panel.add(AntDesign.createStatisticCard("Thư đã nhận", lblStatReceived, AntDesign.PRIMARY));

        panel.add(AntDesign.createStatisticCard("Phiên online", lblStatSessions, new Color(19, 194, 194)));
        panel.add(AntDesign.createStatisticCard("Chặn trùng", lblStatDuplicates, AntDesign.WARNING_TEXT));
        panel.add(AntDesign.createStatisticCard("Lỗi / Từ chối", lblStatErrors, AntDesign.DANGER));

        return panel;
    }

    private JPanel createOverallStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(AntDesign.BG_CONTAINER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header bar with status summary & refresh
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        JPanel leftInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftInfo.setOpaque(false);
        JLabel lblTitle = new JLabel("Trạng thái người dùng");
        lblTitle.setFont(AntDesign.FONT_SUBTITLE);
        lblTitle.setForeground(AntDesign.TEXT_PRIMARY);
        leftInfo.add(lblTitle);

        lblOverallSummary.setFont(AntDesign.FONT_BODY_BOLD);
        lblOverallSummary.setForeground(AntDesign.PRIMARY);
        leftInfo.add(lblOverallSummary);
        topBar.add(leftInfo, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);
        AntButton btnRefreshStatus = AntDesign.createPrimaryButton("Làm mới");
        btnRefreshStatus.addActionListener(e -> refreshUserStatusTable());
        rightControls.add(btnRefreshStatus);
        topBar.add(rightControls, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);

        // Configure userStatusTable with Ant Design Table style
        AntDesign.styleTable(userStatusTable);

        // Column widths - make "Đang làm gì (Kèm IP & Port)" significantly wider and auto-expand
        if (userStatusTable.getColumnModel().getColumnCount() >= 8) {
            // Col 0: Username
            userStatusTable.getColumnModel().getColumn(0).setPreferredWidth(95);
            userStatusTable.getColumnModel().getColumn(0).setMinWidth(80);
            userStatusTable.getColumnModel().getColumn(0).setMaxWidth(130);

            // Col 1: Connected
            userStatusTable.getColumnModel().getColumn(1).setPreferredWidth(105);
            userStatusTable.getColumnModel().getColumn(1).setMinWidth(90);
            userStatusTable.getColumnModel().getColumn(1).setMaxWidth(125);

            // Col 2: Registered
            userStatusTable.getColumnModel().getColumn(2).setPreferredWidth(105);
            userStatusTable.getColumnModel().getColumn(2).setMinWidth(90);
            userStatusTable.getColumnModel().getColumn(2).setMaxWidth(125);

            // Col 3: Logged in
            userStatusTable.getColumnModel().getColumn(3).setPreferredWidth(115);
            userStatusTable.getColumnModel().getColumn(3).setMinWidth(100);
            userStatusTable.getColumnModel().getColumn(3).setMaxWidth(135);

            // Col 4: Giờ vào (Login)
            userStatusTable.getColumnModel().getColumn(4).setPreferredWidth(140);
            userStatusTable.getColumnModel().getColumn(4).setMinWidth(120);
            userStatusTable.getColumnModel().getColumn(4).setMaxWidth(165);

            // Col 5: Giờ ra (Logout)
            userStatusTable.getColumnModel().getColumn(5).setPreferredWidth(140);
            userStatusTable.getColumnModel().getColumn(5).setMinWidth(120);
            userStatusTable.getColumnModel().getColumn(5).setMaxWidth(165);

            // Col 6: Đang làm gì (Kèm IP & Port) -> Expanded to 550px+ with no max-width to absorb all free table width
            userStatusTable.getColumnModel().getColumn(6).setPreferredWidth(550);
            userStatusTable.getColumnModel().getColumn(6).setMinWidth(380);

            // Col 7: Cập nhật cuối
            userStatusTable.getColumnModel().getColumn(7).setPreferredWidth(95);
            userStatusTable.getColumnModel().getColumn(7).setMinWidth(80);
            userStatusTable.getColumnModel().getColumn(7).setMaxWidth(120);
        }

        // Custom Cell Renderers for Ant Design Tag badges
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        DefaultTableCellRenderer boldRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setFont(AntDesign.FONT_BODY_BOLD);
                c.setForeground(AntDesign.TEXT_PRIMARY);
                return c;
            }
        };

        DefaultTableCellRenderer tagRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String text = value != null ? value.toString() : "";
                TagColor color = TagColor.DEFAULT;
                if (text.contains("Đã kết nối") || text.contains("Đã đăng nhập") || text.contains("Online")) {
                    color = TagColor.SUCCESS;
                } else if (text.contains("Đã đăng ký") || text.contains("Đang hoạt động")) {
                    color = TagColor.PROCESSING;
                } else if (text.contains("Chưa") || text.contains("Offline") || text.contains("Đã ngắt")) {
                    color = TagColor.DEFAULT;
                }
                return AntDesign.createTag(text, color);
            }
        };

        DefaultTableCellRenderer activityRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setFont(AntDesign.FONT_BODY);
                c.setForeground(AntDesign.TEXT_PRIMARY);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                    if (value != null) {
                        ((JComponent) c).setToolTipText(value.toString());
                    }
                }
                return c;
            }
        };

        userStatusTable.getColumnModel().getColumn(0).setCellRenderer(boldRenderer);
        userStatusTable.getColumnModel().getColumn(1).setCellRenderer(tagRenderer);
        userStatusTable.getColumnModel().getColumn(2).setCellRenderer(tagRenderer);
        userStatusTable.getColumnModel().getColumn(3).setCellRenderer(tagRenderer);
        userStatusTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        userStatusTable.getColumnModel().getColumn(5).setCellRenderer(tagRenderer);
        userStatusTable.getColumnModel().getColumn(6).setCellRenderer(activityRenderer);
        userStatusTable.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(userStatusTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCompactTerminalPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(800, 180));
        panel.setMinimumSize(new Dimension(500, 120));
        panel.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));

        JPanel headerBar = new JPanel(new BorderLayout(6, 0));
        headerBar.setBackground(AntDesign.BG_DARK_HEADER); // #001529 Ant Design Dark Sider color
        headerBar.setBorder(new EmptyBorder(6, 12, 6, 12));

        JLabel lblTitle = new JLabel("Nhật ký máy chủ (Console)");
        lblTitle.setFont(AntDesign.FONT_SMALL_BOLD);
        lblTitle.setForeground(Color.WHITE);
        headerBar.add(lblTitle, BorderLayout.WEST);

        JLabel lblDesc = new JLabel("UDP Realtime");
        lblDesc.setFont(AntDesign.FONT_SMALL);
        lblDesc.setForeground(new Color(148, 163, 184));
        headerBar.add(lblDesc, BorderLayout.EAST);

        panel.add(headerBar, BorderLayout.NORTH);
        panel.add(logPanel, BorderLayout.CENTER);

        return panel;
    }

    private void setupListeners() {
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        btnRestart.addActionListener(e -> restartServer());

        btnConfig.addActionListener(e -> {
            ServerConfigDialog dlg = new ServerConfigDialog(this, config, server.isRunning());
            dlg.setVisible(true);
            if (dlg.isSaved()) {
                lblPortInfo.setText("Port: " + config.getPort());
                chkAutoStart.setSelected(config.isAutoStart());
                chkEmailReport.setSelected(config.isEmailReportEnabled());
            }
        });

        chkAutoStart.addActionListener(e -> {
            config.setAutoStart(chkAutoStart.isSelected());
            config.save();
        });

        chkEmailReport.addActionListener(e -> {
            config.setEmailReportEnabled(chkEmailReport.isSelected());
            config.save();
        });

        btnAbout.addActionListener(e -> showAboutDialog());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });
    }

    private void startServer() {
        btnStart.setEnabled(false);
        try {
            server.start(config.getPort());
            refreshAccountsTable();
            refreshUserStatusTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage(),
                    "Bind Error", JOptionPane.ERROR_MESSAGE);
            btnStart.setEnabled(true);
        }
    }

    private void stopServer() {
        server.stop();
    }

    private void restartServer() {
        btnRestart.setEnabled(false);
        btnStop.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                server.restart(config.getPort());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ServerManagerUI.this,
                            "Failed to restart server: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onServerStatusChanged(MailServer.Status status, String msg) {
        updateStatusBadges(status);
        boolean running = (status == MailServer.Status.RUNNING);
        btnStart.setEnabled(!running);
        btnStop.setEnabled(running);
        btnRestart.setEnabled(running);
    }

    private void updateStatusBadges(MailServer.Status status) {
        String text = status.name();
        Color color = switch (status) {
            case RUNNING -> AntDesign.SUCCESS;
            case STARTING -> AntDesign.WARNING_TEXT;
            case ERROR -> AntDesign.DANGER;
            default -> AntDesign.TEXT_TERTIARY;
        };

        lblSocketStatus.setText(text);
        lblSocketStatus.setForeground(color);

        lblAccountService.setText(text);
        lblAccountService.setForeground(color);

        lblAuthService.setText(text);
        lblAuthService.setForeground(color);

        lblMailService.setText(text);
        lblMailService.setForeground(color);
    }

    private void updateStatistics() {
        if (!chkEmailReport.isSelected() || server == null) return;

        lblStatSent.setText(String.valueOf(server.getDispatcher().getTotalSent()));
        lblStatReceived.setText(String.valueOf(server.getDispatcher().getTotalReceived()));
        lblStatAccounts.setText(String.valueOf(server.getAccountService().countAccounts()));
        lblStatSessions.setText(String.valueOf(server.getAuthService().getActiveSessionCount()));
        lblStatErrors.setText(String.valueOf(server.getDispatcher().getTotalErrors()));
        lblStatDuplicates.setText(String.valueOf(server.getDispatcher().getDuplicatesBlocked()));
    }

    private void refreshAccountsTable() {
        accountTableModel.setRowCount(0);
        List<Map<String, String>> accounts = server.getAccountService().getAllAccounts();
        for (Map<String, String> acc : accounts) {
            accountTableModel.addRow(new Object[]{
                    acc.getOrDefault("username", ""),
                    acc.getOrDefault("password", ""),
                    acc.getOrDefault("createdat", ""),
                    acc.getOrDefault("status", "ACTIVE")
            });
        }
    }

    private void refreshUserStatusTable() {
        if (server == null || server.getUserActivityTracker() == null) {
            return;
        }

        List<UserActivityRecord> records = server.getUserActivityTracker().getAllRecords();
        int totalAccounts = records.size();
        long connectedCount = records.stream().filter(UserActivityRecord::isConnected).count();
        long loggedInCount = records.stream().filter(UserActivityRecord::isLoggedIn).count();

        lblOverallSummary.setText(String.format(" (%d tài khoản | %d kết nối | %d online)",
                totalAccounts, connectedCount, loggedInCount));

        userStatusTableModel.setRowCount(0);
        for (UserActivityRecord rec : records) {
            String connStr = rec.isConnected() ? "● Đã kết nối" : "○ Chưa kết nối";
            String regStr = rec.isRegistered() ? "● Đã đăng ký" : "○ Chưa";
            String loginStr = rec.isLoggedIn() ? "● Online" : "○ Offline";
            String inTime = (rec.getLoginTime() != null && !rec.getLoginTime().isEmpty()) ? rec.getLoginTime() : "-";
            String outTime;
            if (rec.isLoggedIn()) {
                outTime = "● Đang hoạt động";
            } else if (rec.getLogoutTime() != null && !rec.getLogoutTime().isEmpty() && !"-".equals(rec.getLogoutTime())) {
                outTime = rec.getLogoutTime();
            } else {
                outTime = "-";
            }
            String activity = rec.getCurrentActivity() != null ? rec.getCurrentActivity() : "-";
            String lastActive = rec.getLastActiveTime() != null ? rec.getLastActiveTime() : "-";

            userStatusTableModel.addRow(new Object[]{
                    rec.getUsername(),
                    connStr,
                    regStr,
                    loginStr,
                    inTime,
                    outTime,
                    activity,
                    lastActive
            });
        }
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "Java UDP Mail Server & Desktop Client\n" +
                "Design System: Ant Design (antd) v5 Template\n" +
                "Protocol Version: " + Protocol.VERSION + "\n\n" +
                "Features:\n" +
                "• Ant Design Component Library: AntButton, AntTag, StatisticCard, AntAlert\n" +
                "• Reliable UDP Datagram Protocol (ACK, Timeout, Retry, RequestId, Idempotent)\n" +
                "• Persistent Account & Mailbox storage on disk\n" +
                "• Live User Activity & Status Monitor (6 tracked fields)\n" +
                "• Real-time logging, status monitoring and reports\n",
                "About Java UDP Mail Server",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void confirmExit() {
        if (server != null && server.isRunning()) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "The Mail Server is currently running.\nDo you want to stop the server and exit?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) {
                return;
            }
            server.stop();
        }
        if (statsTimer != null) statsTimer.stop();
        dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerManagerUI ui = new ServerManagerUI();
            ui.setVisible(true);
        });
    }
}
