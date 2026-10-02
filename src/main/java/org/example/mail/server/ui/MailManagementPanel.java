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

    public static class FolderEntry {
        public final String code;
        public final String displayName;
        public final String icon;
        public int count;

        public FolderEntry(String code, String displayName, String icon) {
            this.code = code;
            this.displayName = displayName;
            this.icon = icon;
            this.count = 0;
        }

        @Override
        public String toString() {
            return icon + " " + displayName;
        }
    }

    private final MailServer server;

    // 1. User List
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userListModel);
    private final AntTag lblUserCountTag = AntDesign.createTag("0", TagColor.PROCESSING);

    // 2. Folder List
    private final DefaultListModel<FolderEntry> folderListModel = new DefaultListModel<>();
    private final JList<FolderEntry> folderList = new JList<>(folderListModel);

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
        initDefaultFolders();
        setupListeners();
        refreshUsers();
    }

    private void initDefaultFolders() {
        folderListModel.clear();
        folderListModel.addElement(new FolderEntry("INBOX", "Hộp thư đến", "📥"));
        folderListModel.addElement(new FolderEntry("SENT", "Đã gửi", "📤"));
        folderListModel.addElement(new FolderEntry("DRAFTS", "Bản nháp", "📝"));
        folderListModel.addElement(new FolderEntry("TRASH", "Thùng rác", "🗑️"));
        folderListModel.addElement(new FolderEntry("ROOT", "Tệp tài khoản", "📁"));
    }

    private void initUI() {
        // --- LEFT SPLIT: Users & Folders ---
        JPanel userPanel = createUserPanel();
        JPanel folderPanel = createFolderPanel();

        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, userPanel, folderPanel);
        leftSplit.setDividerLocation(180);
        leftSplit.setResizeWeight(0.5);
        leftSplit.setContinuousLayout(true);
        leftSplit.setBorder(null);
        leftSplit.setPreferredSize(new Dimension(380, 0));

        // --- RIGHT PANEL: Email List & Preview ---
        JPanel rightPanel = createRightPanel();

        // Main Horizontal Split: Left (Users + Folders) | Right (Emails + Preview)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightPanel);
        mainSplit.setDividerLocation(380);
        mainSplit.setResizeWeight(0.30);
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

    private JPanel createFolderPanel() {
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

        JLabel title = new JLabel("Thư mục");
        title.setFont(AntDesign.FONT_SUBTITLE);
        title.setForeground(AntDesign.TEXT_PRIMARY);
        header.add(title, BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        // List
        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        folderList.setFont(AntDesign.FONT_BODY);
        folderList.setBackground(AntDesign.BG_CONTAINER);
        folderList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel item = new JPanel(new BorderLayout(4, 0));
            item.setBorder(new EmptyBorder(7, 8, 7, 8));
            if (isSelected) {
                item.setBackground(AntDesign.PRIMARY_BG);
            } else {
                item.setBackground(index % 2 == 0 ? AntDesign.BG_CONTAINER : AntDesign.ROW_HOVER_BG);
            }

            JLabel lblName = new JLabel(value.icon + " " + value.displayName);
            lblName.setFont(isSelected ? AntDesign.FONT_BODY_BOLD : AntDesign.FONT_BODY);
            lblName.setForeground(isSelected ? AntDesign.PRIMARY : AntDesign.TEXT_PRIMARY);
            item.add(lblName, BorderLayout.WEST);

            TagColor tagColor = TagColor.DEFAULT;
            if (value.count > 0) {
                tagColor = value.code.equals("INBOX") ? TagColor.PROCESSING : TagColor.SUCCESS;
            }
            AntTag badge = AntDesign.createTag(String.valueOf(value.count), tagColor);
            item.add(badge, BorderLayout.EAST);

            return item;
        });

        JScrollPane scroll = new JScrollPane(folderList);
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
        // User selection change
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedUser = userList.getSelectedValue();
                if (selectedUser != null) {
                    updateFolderCounts(selectedUser);
                    if (folderList.getSelectedIndex() < 0) {
                        folderList.setSelectedIndex(0);
                    } else {
                        refreshCurrentMailView();
                    }
                } else {
                    clearMailView();
                }
            }
        });

        // Folder selection change
        folderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshCurrentMailView();
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

    private void updateFolderCounts(String username) {
        for (int i = 0; i < folderListModel.size(); i++) {
            FolderEntry entry = folderListModel.get(i);
            int count = 0;
            try {
                if ("ROOT".equalsIgnoreCase(entry.code)) {
                    count = server.getMailService().listAccountRootMails(username).size();
                } else {
                    count = server.getMailService().listEmails(username, entry.code).size();
                }
            } catch (Exception ignored) {}
            entry.count = count;
        }
        folderList.repaint();
    }

    public synchronized void refreshCurrentMailView() {
        String username = userList.getSelectedValue();
        FolderEntry folder = folderList.getSelectedValue();

        if (username == null || folder == null) {
            clearMailView();
            return;
        }

        lblCurrentPath.setText("👤 " + username + "  /  " + folder.icon + " " + folder.displayName);

        // Update counts
        updateFolderCounts(username);

        // Load emails
        mailTableModel.setRowCount(0);
        currentMailItems.clear();

        List<MailItem> items;
        if ("ROOT".equalsIgnoreCase(folder.code)) {
            items = server.getMailService().listAccountRootMails(username);
        } else {
            items = server.getMailService().listEmails(username, folder.code);
        }

        if (items != null) {
            currentMailItems.addAll(items);
            for (MailItem m : items) {
                String body = m.getBody() != null ? m.getBody() : "";
                String bodySnippet = body.replace('\r', ' ').replace('\n', ' ').trim();
                if (bodySnippet.length() > 50) {
                    bodySnippet = bodySnippet.substring(0, 50) + "...";
                }
                String fileName = m.getMailId().endsWith(".txt") ? m.getMailId() : m.getMailId() + ".txt";
                String senderIp = (m.getSenderIp() != null && !m.getSenderIp().isEmpty() ? m.getSenderIp() : "127.0.0.1")
                        + (m.getSenderPort() > 0 ? (":" + m.getSenderPort()) : "");

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
        String ipStr = (item.getSenderIp() != null && !item.getSenderIp().isEmpty() ? item.getSenderIp() : "127.0.0.1")
                + (item.getSenderPort() > 0 ? (":" + item.getSenderPort()) : "");
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
        FolderEntry folder = folderList.getSelectedValue();
        if (username != null && folder != null) {
            String baseId = item.getMailId().endsWith(".txt")
                    ? item.getMailId().substring(0, item.getMailId().length() - 4)
                    : item.getMailId();

            Path filePath;
            if ("ROOT".equalsIgnoreCase(folder.code)) {
                filePath = server.getMailService().getMailRepository().getUserMailboxDir(username).resolve(baseId + ".txt");
            } else {
                filePath = server.getMailService().getMailRepository().getUserMailboxDir(username).resolve(folder.code.toLowerCase()).resolve(baseId + ".txt");
                if (!Files.exists(filePath)) {
                    filePath = server.getMailService().getMailRepository().getUserMailboxDir(username).resolve(folder.code.toLowerCase()).resolve(baseId + ".mail");
                }
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
        FolderEntry folder = folderList.getSelectedValue();

        if (row < 0 || row >= currentMailItems.size() || username == null || folder == null) {
            return;
        }

        if ("ROOT".equalsIgnoreCase(folder.code)) {
            JOptionPane.showMessageDialog(this, "Tệp gốc không hỗ trợ đổi trạng thái đọc", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        MailItem item = currentMailItems.get(row);
        boolean newRead = !item.isReadState();
        server.getMailService().markRead(username, folder.code, item.getMailId(), newRead);
        refreshCurrentMailView();
    }

    private void deleteSelectedMail() {
        int row = mailTable.getSelectedRow();
        String username = userList.getSelectedValue();
        FolderEntry folder = folderList.getSelectedValue();

        if (row < 0 || row >= currentMailItems.size() || username == null || folder == null) {
            return;
        }

        MailItem item = currentMailItems.get(row);
        int opt = JOptionPane.showConfirmDialog(
                this,
                "Xác nhận xóa thư '" + item.getMailId() + "'?",
                "Xác nhận",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (opt == JOptionPane.YES_OPTION) {
            if ("ROOT".equalsIgnoreCase(folder.code)) {
                server.getMailService().deleteAccountRootMail(username, item.getMailId());
            } else {
                server.getMailService().deleteMail(username, folder.code, item.getMailId());
            }
            refreshCurrentMailView();
        }
    }
}
