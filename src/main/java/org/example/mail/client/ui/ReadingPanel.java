package org.example.mail.client.ui;

import org.example.mail.client.model.MailItem;
import org.example.mail.client.service.MailClientService;
import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadingPanel extends JPanel {

    private final JLabel lblSubject = new JLabel("Chưa chọn thư");
    private final JLabel lblFrom = new JLabel("-");
    private final JLabel lblTo = new JLabel("-");
    private final JLabel lblDate = new JLabel("-");
    private final JLabel lblFolder = new JLabel("");

    // Mode 1: App View Body
    private final JTextArea txtBody = new JTextArea();

    // Mode 2: In-App Raw TXT View
    private final JTextArea txtRawView = new JTextArea();
    private final JTabbedPane viewModeTabs = new JTabbedPane();

    private final AntButton btnReply = AntDesign.createDefaultButton("Trả lời");
    private final AntButton btnForward = AntDesign.createDefaultButton("Chuyển tiếp");
    private final AntButton btnDelete = AntDesign.createDangerButton("Xóa");
    private final AntButton btnToggleRead = AntDesign.createDefaultButton("Chưa đọc");
    private final AntButton btnOpenTxtExternal = AntDesign.createDefaultButton("Mở file TXT");

    private final MailClientService mailService;
    private final Runnable onMailChangedCallback;

    private MailItem currentMail;
    private String currentFolder = Protocol.FOLDER_INBOX;

    public ReadingPanel(MailClientService mailService, Runnable onMailChangedCallback) {
        this.mailService = mailService;
        this.onMailChangedCallback = onMailChangedCallback;

        setLayout(new BorderLayout());
        setBackground(AntDesign.BG_CONTAINER);
        initUI();
        clear();
    }

    private void initUI() {
        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setBorder(new EmptyBorder(14, 18, 10, 18));
        headerPanel.setBackground(AntDesign.BG_CONTAINER);

        // Subject & Toolbar
        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);

        lblSubject.setFont(AntDesign.FONT_TITLE_LARGE);
        lblSubject.setForeground(AntDesign.TEXT_PRIMARY);
        topRow.add(lblSubject, BorderLayout.CENTER);

        // Action Buttons Row (Ant Design Buttons)
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        toolbar.setOpaque(false);

        toolbar.add(btnReply);
        toolbar.add(btnForward);
        toolbar.add(btnToggleRead);
        toolbar.add(btnOpenTxtExternal);
        toolbar.add(btnDelete);

        topRow.add(toolbar, BorderLayout.EAST);
        headerPanel.add(topRow, BorderLayout.NORTH);

        // Metadata rows
        JPanel metaPanel = new JPanel(new GridLayout(3, 1, 3, 3));
        metaPanel.setOpaque(false);
        metaPanel.setBorder(new EmptyBorder(6, 0, 0, 0));

        JPanel fromRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fromRow.setOpaque(false);
        JLabel fLbl = new JLabel("Từ:");
        fLbl.setFont(AntDesign.FONT_BODY_BOLD);
        fLbl.setForeground(AntDesign.TEXT_SECONDARY);
        fromRow.add(fLbl);

        lblFrom.setFont(AntDesign.FONT_BODY_BOLD);
        lblFrom.setForeground(AntDesign.PRIMARY);
        fromRow.add(lblFrom);
        fromRow.add(Box.createHorizontalStrut(12));
        fromRow.add(lblFolder);
        metaPanel.add(fromRow);

        JPanel toRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toRow.setOpaque(false);
        JLabel tLbl = new JLabel("Đến:");
        tLbl.setFont(AntDesign.FONT_BODY_BOLD);
        tLbl.setForeground(AntDesign.TEXT_SECONDARY);
        toRow.add(tLbl);

        lblTo.setFont(AntDesign.FONT_BODY);
        lblTo.setForeground(AntDesign.TEXT_PRIMARY);
        toRow.add(lblTo);
        metaPanel.add(toRow);

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        JLabel dLbl = new JLabel("Thời gian:");
        dLbl.setFont(AntDesign.FONT_BODY_BOLD);
        dLbl.setForeground(AntDesign.TEXT_SECONDARY);
        dateRow.add(dLbl);

        lblDate.setFont(AntDesign.FONT_BODY);
        lblDate.setForeground(AntDesign.TEXT_TERTIARY);
        dateRow.add(lblDate);
        metaPanel.add(dateRow);

        headerPanel.add(metaPanel, BorderLayout.SOUTH);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(14, 18, 10, 18)
        ));
        add(headerPanel, BorderLayout.NORTH);

        // Center: Dual Mode Tabs (Ant Design style)
        // Mode 1: Directly on App
        txtBody.setEditable(false);
        txtBody.setFont(AntDesign.FONT_BODY);
        txtBody.setForeground(AntDesign.TEXT_PRIMARY);
        txtBody.setLineWrap(true);
        txtBody.setWrapStyleWord(true);
        txtBody.setMargin(new Insets(16, 20, 16, 20));
        txtBody.setBackground(Color.WHITE);
        JScrollPane scrollAppMode = new JScrollPane(txtBody);
        scrollAppMode.setBorder(null);

        // Mode 2: TXT Format View
        txtRawView.setEditable(false);
        txtRawView.setFont(AntDesign.FONT_CODE);
        txtRawView.setForeground(new Color(30, 41, 59));
        txtRawView.setBackground(new Color(248, 250, 252));
        txtRawView.setMargin(new Insets(16, 20, 16, 20));
        JScrollPane scrollTxtMode = new JScrollPane(txtRawView);
        scrollTxtMode.setBorder(null);

        viewModeTabs.setFont(AntDesign.FONT_BODY_BOLD);
        viewModeTabs.setBackground(AntDesign.BG_CONTAINER);
        viewModeTabs.addTab("Giao diện", scrollAppMode);
        viewModeTabs.addTab("File TXT", scrollTxtMode);

        add(viewModeTabs, BorderLayout.CENTER);

        // Listeners
        btnReply.addActionListener(e -> onReply());
        btnForward.addActionListener(e -> onForward());
        btnToggleRead.addActionListener(e -> onToggleRead());
        btnOpenTxtExternal.addActionListener(e -> openMailAsTxtExternal());
        btnDelete.addActionListener(e -> onDelete());
    }

    public void setMail(MailItem mail, String folder) {
        this.currentMail = mail;
        this.currentFolder = folder;

        if (mail == null) {
            clear();
            return;
        }

        lblSubject.setText(mail.getSubject() != null && !mail.getSubject().isEmpty() ? mail.getSubject() : "(Không có tiêu đề)");

        // Format: sender + [IP: ... , Port: ...]
        String senderStr = (mail.getSender() != null ? mail.getSender() : "Unknown");
        String senderIp = (mail.getSenderIp() != null && !mail.getSenderIp().isEmpty()) ? mail.getSenderIp() : "127.0.0.1";
        int senderPort = mail.getSenderPort();
        if (senderPort > 0) {
            senderStr += "  [IP: " + senderIp + ", Port: " + senderPort + "]";
        } else {
            senderStr += "  [IP: " + senderIp + "]";
        }

        lblFrom.setText(senderStr);
        lblTo.setText(mail.getRecipient() != null ? mail.getRecipient() : "Me");
        lblDate.setText(mail.getCreatedAt() != null ? mail.getCreatedAt() : "-");
        lblFolder.setText("[" + folder + "]");

        // Populate Mode 1: App View
        txtBody.setText(mail.getBody() != null ? mail.getBody() : "");
        txtBody.setCaretPosition(0);

        // Populate Mode 2: Formatted TXT View
        String txtFormat = generateTxtContent(mail, folder, senderStr);
        txtRawView.setText(txtFormat);
        txtRawView.setCaretPosition(0);

        btnToggleRead.setText(mail.isReadState() ? "✉ Đánh dấu chưa đọc" : "✉ Đánh dấu đã đọc");

        boolean isTrash = Protocol.FOLDER_TRASH.equalsIgnoreCase(folder);
        btnDelete.setText(isTrash ? "❌ Xóa vĩnh viễn" : "🗑 Chuyển vào thùng rác");

        btnReply.setEnabled(!isTrash);
        btnForward.setEnabled(true);
        btnDelete.setEnabled(true);
        btnToggleRead.setEnabled(true);
        btnOpenTxtExternal.setEnabled(true);
    }

    public void clear() {
        this.currentMail = null;
        lblSubject.setText("Chọn thư để xem chi tiết");
        lblFrom.setText("-");
        lblTo.setText("-");
        lblDate.setText("-");
        lblFolder.setText("");
        txtBody.setText("");
        txtRawView.setText("");

        btnReply.setEnabled(false);
        btnForward.setEnabled(false);
        btnDelete.setEnabled(false);
        btnToggleRead.setEnabled(false);
        btnOpenTxtExternal.setEnabled(false);
    }

    private String generateTxtContent(MailItem mail, String folder, String senderInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("======================================================================\n");
        sb.append("                       EMAIL MESSAGE FILE (.TXT)                      \n");
        sb.append("======================================================================\n");
        sb.append(String.format("Mail ID     : %s\n", mail.getMailId()));
        sb.append(String.format("From        : %s\n", senderInfo));
        sb.append(String.format("To          : %s\n", mail.getRecipient() != null ? mail.getRecipient() : ""));
        sb.append(String.format("Subject     : %s\n", mail.getSubject() != null ? mail.getSubject() : ""));
        sb.append(String.format("Date        : %s\n", mail.getCreatedAt() != null ? mail.getCreatedAt() : ""));
        sb.append(String.format("Folder      : %s\n", folder));
        sb.append(String.format("Read State  : %s\n", mail.isReadState() ? "READ" : "UNREAD"));
        sb.append("======================================================================\n");
        sb.append("NỘI DUNG THƯ (CONTENT):\n");
        sb.append("======================================================================\n\n");
        sb.append(mail.getBody() != null ? mail.getBody() : "");
        sb.append("\n\n======================================================================\n");
        return sb.toString();
    }

    private void openMailAsTxtExternal() {
        if (currentMail == null) return;
        try {
            String senderStr = (currentMail.getSender() != null ? currentMail.getSender() : "Unknown");
            String senderIp = (currentMail.getSenderIp() != null) ? currentMail.getSenderIp() : "127.0.0.1";
            int senderPort = currentMail.getSenderPort();
            if (senderPort > 0) {
                senderStr += "  [IP: " + senderIp + ", Port: " + senderPort + "]";
            } else {
                senderStr += "  [IP: " + senderIp + "]";
            }

            String content = generateTxtContent(currentMail, currentFolder, senderStr);
            String safeId = currentMail.getMailId() != null ? currentMail.getMailId().replace(":", "_").replace("/", "_") : "email";
            if (!safeId.endsWith(".txt")) safeId += ".txt";

            Path tempFile = Files.createTempFile("email_view_" + safeId + "_", ".txt");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            // Open in external editor (Notepad / default text editor)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(tempFile.toFile());
            } else {
                // Windows fallback
                new ProcessBuilder("notepad.exe", tempFile.toAbsolutePath().toString()).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở tệp TXT bên ngoài: " + ex.getMessage(),
                    "Lỗi mở tệp", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onReply() {
        if (currentMail == null) return;
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        ComposeDialog dlg = new ComposeDialog(parent, mailService, onMailChangedCallback);
        dlg.populateReply(currentMail);
        dlg.setVisible(true);
    }

    private void onForward() {
        if (currentMail == null) return;
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        ComposeDialog dlg = new ComposeDialog(parent, mailService, onMailChangedCallback);
        dlg.populateForward(currentMail);
        dlg.setVisible(true);
    }

    private void onToggleRead() {
        if (currentMail == null) return;
        boolean newRead = !currentMail.isReadState();
        mailService.markRead(currentMail.getMailId(), currentFolder, newRead, (ok, err) -> {
            if (Boolean.TRUE.equals(ok)) {
                currentMail.setReadState(newRead);
                btnToggleRead.setText(newRead ? "Chưa đọc" : "Đã đọc");
                if (onMailChangedCallback != null) onMailChangedCallback.run();
            } else {
                JOptionPane.showMessageDialog(this, "Thao tác thất bại: " + (err != null ? err.getMessage() : "Timeout"));
            }
        });
    }

    private void onDelete() {
        if (currentMail == null) return;

        boolean isTrash = Protocol.FOLDER_TRASH.equalsIgnoreCase(currentFolder);
        String prompt = isTrash
                ? "Bạn có chắc chắn muốn xóa vĩnh viễn thư này?"
                : "Bạn có muốn chuyển thư này vào Thùng rác (Trash)?";

        int opt = JOptionPane.showConfirmDialog(this, prompt, "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (opt != JOptionPane.YES_OPTION) return;

        if (isTrash) {
            mailService.deleteMail(currentMail.getMailId(), currentFolder, (ok, err) -> {
                if (Boolean.TRUE.equals(ok)) {
                    clear();
                    if (onMailChangedCallback != null) onMailChangedCallback.run();
                } else {
                    JOptionPane.showMessageDialog(this, "Xóa thất bại: " + (err != null ? err.getMessage() : "Timeout"));
                }
            });
        } else {
            mailService.moveMail(currentMail.getMailId(), currentFolder, Protocol.FOLDER_TRASH, (ok, err) -> {
                if (Boolean.TRUE.equals(ok)) {
                    clear();
                    if (onMailChangedCallback != null) onMailChangedCallback.run();
                } else {
                    JOptionPane.showMessageDialog(this, "Chuyển vào thùng rác thất bại: " + (err != null ? err.getMessage() : "Timeout"));
                }
            });
        }
    }
}
