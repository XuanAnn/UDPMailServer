package org.example.mail.server.ui;

import org.example.mail.client.model.MailItem;
import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.server.core.MailServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mail Management Panel for UDP Mail Server.
 * Displays User List, Folder List (with mail count tags), and Emails per User
 * including File Names, Snippets, and Raw File Contents.
 */
public class MailManagementPanel extends JPanel {

    private final MailServer server;

    // 1. User List
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userListModel);
    private final AntTag lblUserCountTag = AntDesign.createTag("0", TagColor.PROCESSING);

    // 3. Email Table (includes Tệp, IP Gửi & Nội dung columns)
    private final DefaultTableModel mailTableModel = new DefaultTableModel(
            new String[]{"Mã", "Tệp", "Gửi", "IP Gửi", "Nhận", "Tiêu đề", "Nội dung", "Thời gian", "Trạng thái"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable mailTable = new JTable(mailTableModel);
    private final List<MailItem> currentMailItems = new ArrayList<>();

    // 4. Preview Components (Formatted Body + Raw File Text)
    private final JLabel lblCurrentPath = new JLabel("Chưa chọn người dùng");
    private final JLabel lblPreviewSubject = new JLabel("Chưa chọn thư");
    private final JLabel lblPreviewMeta = new JLabel(" ");
    private final JLabel lblPreviewFilePath = new JLabel(" ");
    private final JTextArea txtPreviewBody = new JTextArea();
    private final JTextArea txtPreviewRawFile = new JTextArea();

    public MailManagementPanel(MailServer server) {
        this.server = server;
        setLayout(new BorderLayout(8, 8));
        setBackground(AntDesign.BG_LAYOUT);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        initUI();
        setupListeners();
        refreshUsers();
    }

    private void initUI() {
        // --- LEFT PANEL: Users List ---
        JPanel userPanel = createUserPanel();
        userPanel.setPreferredSize(new Dimension(240, 0));

        // --- RIGHT PANEL: Email List & Preview ---
        JPanel rightPanel = createRightPanel();

        // Main Horizontal Split: Left (Users) | Right (Emails + Preview)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, userPanel, rightPanel);
        mainSplit.setDividerLocation(240);
        mainSplit.setResizeWeight(0.22);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createUserPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(AntDesign.BG_CONTAINER);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(8, 8, 8, 8)
        ));

        // Header
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(2, 4, 6, 4));

        JLabel title = new JLabel("Người dùng");
        title.setFont(AntDesign.FONT_SUBTITLE);
        title.setForeground(AntDesign.TEXT_PRIMARY);
        header.add(title, BorderLayout.WEST);
        header.add(lblUserCountTag, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        // List
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFont(AntDesign.FONT_BODY);
        userList.setBackground(AntDesign.BG_CONTAINER);
        userList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel item = new JPanel(new BorderLayout(6, 0));
            item.setBorder(new EmptyBorder(7, 10, 7, 10));
            if (isSelected) {
                item.setBackground(AntDesign.PRIMARY_BG);
            } else {
                item.setBackground(index % 2 == 0 ? AntDesign.BG_CONTAINER : AntDesign.ROW_HOVER_BG);
            }

            JLabel lbl = new JLabel("👤  " + value);
            lbl.setFont(isSelected ? AntDesign.FONT_BODY_BOLD : AntDesign.FONT_BODY);
            lbl.setForeground(isSelected ? AntDesign.PRIMARY : AntDesign.TEXT_PRIMARY);
            item.add(lbl, BorderLayout.WEST);

            if (server != null && server.getUserActivityTracker() != null) {
                var rec = server.getUserActivityTracker().getRecord(value);
                if (rec != null && rec.getClientIp() != null && !rec.getClientIp().equals("-")) {
                    String ipText = rec.getClientIp() + (rec.getClientPort() > 0 ? (":" + rec.getClientPort()) : "");
                    JLabel lblIp = new JLabel(ipText);
                    lblIp.setFont(AntDesign.FONT_SMALL);
                    lblIp.setForeground(rec.isLoggedIn() ? AntDesign.SUCCESS : AntDesign.TEXT_TERTIARY);
                    item.add(lblIp, BorderLayout.EAST);
                }
            }

            return item;
        });

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private JPanel createRightPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(AntDesign.BG_CONTAINER);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(8, 8, 8, 8)
        ));

        // Top Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(2, 4, 6, 4));

        lblCurrentPath.setFont(AntDesign.FONT_SUBTITLE);
        lblCurrentPath.setForeground(AntDesign.PRIMARY);
        toolbar.add(lblCurrentPath, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        AntButton btnOpenTxt = AntDesign.createDefaultButton("Mở file TXT");
        btnOpenTxt.addActionListener(e -> openSelectedMailTxtExternal());
        actions.add(btnOpenTxt);

        AntButton btnRefresh = AntDesign.createPrimaryButton("Làm mới");
        btnRefresh.addActionListener(e -> refreshCurrentMailView());
        actions.add(btnRefresh);

        AntButton btnToggleRead = AntDesign.createDefaultButton("Đổi trạng thái");
        btnToggleRead.addActionListener(e -> toggleSelectedMailRead());
        actions.add(btnToggleRead);

        AntButton btnDelete = AntDesign.createDangerButton("Xóa");
        btnDelete.addActionListener(e -> deleteSelectedMail());
        actions.add(btnDelete);

        toolbar.add(actions, BorderLayout.EAST);
        card.add(toolbar, BorderLayout.NORTH);

        // Mail Table configuration
        AntDesign.styleTable(mailTable);
        mailTable.getColumnModel().getColumn(0).setPreferredWidth(70);  // Mã
        mailTable.getColumnModel().getColumn(1).setPreferredWidth(95);  // Tệp
        mailTable.getColumnModel().getColumn(2).setPreferredWidth(75);  // Gửi
        mailTable.getColumnModel().getColumn(3).setPreferredWidth(125); // IP Gửi
        mailTable.getColumnModel().getColumn(4).setPreferredWidth(75);  // Nhận
        mailTable.getColumnModel().getColumn(5).setPreferredWidth(140); // Tiêu đề
        mailTable.getColumnModel().getColumn(6).setPreferredWidth(190); // Nội dung tóm tắt
        mailTable.getColumnModel().getColumn(7).setPreferredWidth(125); // Thời gian
        mailTable.getColumnModel().getColumn(8).setPreferredWidth(80);  // Trạng thái

        mailTable.getColumnModel().getColumn(8).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            boolean isRead = "Đã đọc".equals(value);
            return AntDesign.createTag(isRead ? "Đã đọc" : "Chưa đọc",
                    isRead ? TagColor.SUCCESS : TagColor.WARNING);
        });

        JScrollPane tableScroll = new JScrollPane(mailTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));

        // Preview Panel
        JPanel previewCard = createPreviewPanel();

        // Vertical Split: Top = Table, Bottom = Preview
        JSplitPane vSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, previewCard);
        vSplit.setDividerLocation(180);
        vSplit.setResizeWeight(0.42);
        vSplit.setContinuousLayout(true);
        vSplit.setBorder(null);

        card.add(vSplit, BorderLayout.CENTER);
        return card;
    }

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(AntDesign.BG_LAYOUT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Header with Subject, Metadata, and Raw File Path
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        lblPreviewSubject.setFont(AntDesign.FONT_SUBTITLE);
        lblPreviewSubject.setForeground(AntDesign.TEXT_PRIMARY);
        header.add(lblPreviewSubject, BorderLayout.NORTH);

        JPanel subHeader = new JPanel(new GridLayout(2, 1, 0, 2));
        subHeader.setOpaque(false);

        lblPreviewMeta.setFont(AntDesign.FONT_SMALL);
        lblPreviewMeta.setForeground(AntDesign.TEXT_SECONDARY);
        subHeader.add(lblPreviewMeta);

        lblPreviewFilePath.setFont(AntDesign.FONT_SMALL_BOLD);
        lblPreviewFilePath.setForeground(AntDesign.PRIMARY);
        subHeader.add(lblPreviewFilePath);

        header.add(subHeader, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);

        // Body Preview Tabs: Tab 1 = Formatted body, Tab 2 = Raw file content (.txt)
        JTabbedPane previewTabs = new JTabbedPane();
        previewTabs.setFont(AntDesign.FONT_SMALL_BOLD);
        previewTabs.setBackground(AntDesign.BG_CONTAINER);

        // Tab 1: Formatted Body
        txtPreviewBody.setEditable(false);
        txtPreviewBody.setLineWrap(true);
        txtPreviewBody.setWrapStyleWord(true);
        txtPreviewBody.setFont(AntDesign.FONT_BODY);
        txtPreviewBody.setBackground(AntDesign.BG_CONTAINER);
        txtPreviewBody.setForeground(AntDesign.TEXT_PRIMARY);
        txtPreviewBody.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane bodyScroll = new JScrollPane(txtPreviewBody);
        bodyScroll.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));
        previewTabs.addTab("Nội dung thư", bodyScroll);

        // Tab 2: Raw File on Disk (.txt)
        txtPreviewRawFile.setEditable(false);
        txtPreviewRawFile.setLineWrap(true);
        txtPreviewRawFile.setWrapStyleWord(true);
        txtPreviewRawFile.setFont(AntDesign.FONT_CODE);
        txtPreviewRawFile.setBackground(new Color(250, 250, 250));
        txtPreviewRawFile.setForeground(new Color(38, 38, 38));
        txtPreviewRawFile.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane rawScroll = new JScrollPane(txtPreviewRawFile);
        rawScroll.setBorder(BorderFactory.createLineBorder(AntDesign.BORDER_SPLIT, 1));
        previewTabs.addTab("Tệp thô (.txt)", rawScroll);

        panel.add(previewTabs, BorderLayout.CENTER);

        return panel;
    }

    private void setupListeners() {
        // User selection change -> directly refresh mails for user
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedUser = userList.getSelectedValue();
                if (selectedUser != null) {
                    refreshCurrentMailView();
                } else {
                    clearMailView();
                }
            }
        });

        // Email row selection
        mailTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = mailTable.getSelectedRow();
                if (row >= 0 && row < currentMailItems.size()) {
                    showMailPreview(currentMailItems.get(row));
                } else {
                    clearPreview();
                }
            }
        });

        // Double click on mail row to open TXT file externally
        mailTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedMailTxtExternal();
                }
            }
        });
    }

    public synchronized void refreshUsers() {
        String prevUser = userList.getSelectedValue();
        userListModel.clear();

        List<Map<String, String>> accounts = server.getAccountService().getAllAccounts();
        for (Map<String, String> acc : accounts) {
            String u = acc.get("username");
            if (u != null && !u.isEmpty()) {
                userListModel.addElement(u);
            }
        }

        lblUserCountTag.setText(String.valueOf(userListModel.size()));

        if (!userListModel.isEmpty()) {
            if (prevUser != null && userListModel.contains(prevUser)) {
                userList.setSelectedValue(prevUser, true);
            } else {
                userList.setSelectedIndex(0);
            }
        } else {
            clearMailView();
        }
    }

    public synchronized void refreshCurrentMailView() {
        String username = userList.getSelectedValue();

        if (username == null) {
            clearMailView();
            return;
        }

        lblCurrentPath.setText("👤 " + username + "  (Thư mục tài khoản: accounts/" + username + ")");

        // Load emails
        mailTableModel.setRowCount(0);
        currentMailItems.clear();

        List<MailItem> items = server.getMailService().listAccountRootMails(username);

        if (items != null) {
            currentMailItems.addAll(items);
            for (MailItem m : items) {
                String body = m.getBody() != null ? m.getBody() : "";
                String bodySnippet = body.replace('\r', ' ').replace('\n', ' ').trim();
                if (bodySnippet.length() > 50) {
                    bodySnippet = bodySnippet.substring(0, 50) + "...";
                }
                String fileName = m.getMailId().endsWith(".txt") ? m.getMailId() : m.getMailId() + ".txt";
                String sIp = m.getSenderIp();
                if (sIp == null || sIp.isEmpty() || sIp.equals("127.0.0.1") || sIp.startsWith("127.")) {
                    String lan = org.example.mail.common.NetworkUtils.getLocalIPv4Address();
                    sIp = (lan != null && !lan.isEmpty() && !lan.equals("127.0.0.1")) ? lan : "127.0.0.1";
                }
                String senderIp = sIp + (m.getSenderPort() > 0 ? (":" + m.getSenderPort()) : "");

                mailTableModel.addRow(new Object[]{
                        m.getMailId(),
                        fileName,
                        m.getSender() != null ? m.getSender() : "",
                        senderIp,
                        m.getRecipient() != null ? m.getRecipient() : "",
                        m.getSubject() != null ? m.getSubject() : "",
                        bodySnippet,
                        m.getCreatedAt() != null ? m.getCreatedAt() : "",
                        m.isReadState() ? "Đã đọc" : "Chưa đọc"
                });
            }
        }

        if (!currentMailItems.isEmpty()) {
            mailTable.setRowSelectionInterval(0, 0);
            showMailPreview(currentMailItems.get(0));
        } else {
            clearPreview();
        }
    }

    private void showMailPreview(MailItem item) {
        lblPreviewSubject.setText(item.getSubject() != null && !item.getSubject().isEmpty() ? item.getSubject() : "(Không có tiêu đề)");
        String sIp = item.getSenderIp();
        if (sIp == null || sIp.isEmpty() || sIp.equals("127.0.0.1") || sIp.startsWith("127.")) {
            String lan = org.example.mail.common.NetworkUtils.getLocalIPv4Address();
            sIp = (lan != null && !lan.isEmpty() && !lan.equals("127.0.0.1")) ? lan : "127.0.0.1";
        }
        String ipStr = sIp + (item.getSenderPort() > 0 ? (":" + item.getSenderPort()) : "");
        String meta = String.format("Mã: %s  |  Từ: %s  |  IP: %s  |  Đến: %s  |  Thời gian: %s",
                item.getMailId(),
                item.getSender() != null ? item.getSender() : "-",
                ipStr,
                item.getRecipient() != null ? item.getRecipient() : "-",
                item.getCreatedAt() != null ? item.getCreatedAt() : "-"
        );
        lblPreviewMeta.setText(meta);
        txtPreviewBody.setText(item.getBody() != null ? item.getBody() : "");
        txtPreviewBody.setCaretPosition(0);

        // Load raw file on disk
        String username = userList.getSelectedValue();
        if (username != null) {
            String baseId = item.getMailId().endsWith(".txt")
                    ? item.getMailId().substring(0, item.getMailId().length() - 4)
                    : item.getMailId();

            Path userDir = server.getMailService().getMailRepository().getUserMailboxDir(username);
            Path filePath = userDir.resolve(baseId + ".txt");
            if (!Files.exists(filePath)) {
                filePath = userDir.resolve(baseId + ".mail");
            }

            if (Files.exists(filePath)) {
                try {
                    String raw = Files.readString(filePath, StandardCharsets.UTF_8);
                    long size = Files.size(filePath);
                    lblPreviewFilePath.setText("📁 Tệp đĩa: " + filePath + " (" + size + " bytes)");
                    txtPreviewRawFile.setText(raw);
                    txtPreviewRawFile.setCaretPosition(0);
                } catch (IOException e) {
                    lblPreviewFilePath.setText("📁 Tệp đĩa: " + filePath + " (Lỗi đọc)");
                    txtPreviewRawFile.setText("(Không thể đọc nội dung file: " + e.getMessage() + ")");
                }
            } else {
                lblPreviewFilePath.setText("📁 Tệp đĩa: " + filePath + " (Không tồn tại)");
                txtPreviewRawFile.setText("(File không tồn tại trên đĩa)");
            }
        }
    }

    private void clearPreview() {
        lblPreviewSubject.setText("Chưa chọn thư");
        lblPreviewMeta.setText(" ");
        lblPreviewFilePath.setText(" ");
        txtPreviewBody.setText("");
        txtPreviewRawFile.setText("");
    }

    private void clearMailView() {
        lblCurrentPath.setText("Chưa chọn người dùng");
        mailTableModel.setRowCount(0);
        currentMailItems.clear();
        clearPreview();
    }

    private void toggleSelectedMailRead() {
        int row = mailTable.getSelectedRow();
        String username = userList.getSelectedValue();

        if (row < 0 || row >= currentMailItems.size() || username == null) {
            return;
        }

        MailItem item = currentMailItems.get(row);
        boolean newRead = !item.isReadState();
        server.getMailService().markRead(username, "ALL", item.getMailId(), newRead);
        refreshCurrentMailView();
    }

    private void deleteSelectedMail() {
        int row = mailTable.getSelectedRow();
        String username = userList.getSelectedValue();

        if (row < 0 || row >= currentMailItems.size() || username == null) {
            return;
        }

        MailItem item = currentMailItems.get(row);
        int opt = JOptionPane.showConfirmDialog(
                this,
                "Xác nhận xóa tệp thư '" + item.getMailId() + ".txt' vĩnh viễn?",
                "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (opt == JOptionPane.YES_OPTION) {
            server.getMailService().deleteAccountRootMail(username, item.getMailId());
            refreshCurrentMailView();
        }
    }

    private Path getSelectedMailFilePath() {
        int row = mailTable.getSelectedRow();
        String username = userList.getSelectedValue();
        if (row < 0 || row >= currentMailItems.size() || username == null) {
            return null;
        }

        MailItem item = currentMailItems.get(row);
        String baseId = item.getMailId().endsWith(".txt")
                ? item.getMailId().substring(0, item.getMailId().length() - 4)
                : item.getMailId();

        Path userDir = server.getMailService().getMailRepository().getUserMailboxDir(username);
        Path filePath = userDir.resolve(baseId + ".txt");
        if (!Files.exists(filePath)) {
            filePath = userDir.resolve(baseId + ".mail");
        }
        return filePath;
    }

    private void openSelectedMailTxtExternal() {
        Path filePath = getSelectedMailFilePath();
        if (filePath == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn thư cần mở tệp .txt!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!Files.exists(filePath)) {
            JOptionPane.showMessageDialog(this, "Tệp không tồn tại trên đĩa: " + filePath, "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(filePath.toFile());
            } else {
                openFileWithSystemProcess(filePath);
            }
        } catch (Exception ex) {
            openFileWithSystemProcess(filePath);
        }
    }

    private void openFileWithSystemProcess(Path filePath) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("notepad.exe", filePath.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", filePath.toAbsolutePath().toString()).start();
            } else {
                new ProcessBuilder("xdg-open", filePath.toAbsolutePath().toString()).start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể mở tệp: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
