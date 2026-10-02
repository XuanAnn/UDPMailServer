package org.example.mail.client.ui;

import org.example.mail.client.model.MailItem;
import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntTag;
import org.example.mail.common.AntDesign.TagColor;
import org.example.mail.common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MessageListPanel extends JPanel {

    private final DefaultListModel<MailItem> listModel = new DefaultListModel<>();
    private final JList<MailItem> mailJList = new JList<>(listModel);
    private final JLabel lblFolderTitle = new JLabel("Hộp thư đến");
    private final JLabel lblCount = new JLabel("0 thư");
    private String currentFolder = Protocol.FOLDER_INBOX;
    private String currentUsername = "";
    private final JTextField searchField = new JTextField() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !hasFocus()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(AntDesign.TEXT_QUATERNARY);
                g2.setFont(AntDesign.FONT_SMALL);
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString("Tìm kiếm...", 8, y);
                g2.dispose();
            }
        }
    };

    private List<MailItem> rawItems = new ArrayList<>();
    private Consumer<MailItem> onSelectMailCallback;

    public MessageListPanel() {
        setLayout(new BorderLayout());
        setBackground(AntDesign.BG_CONTAINER);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AntDesign.BORDER_SPLIT));
        initUI();
    }

    private void initUI() {
        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBorder(new EmptyBorder(12, 14, 10, 14));
        topPanel.setBackground(AntDesign.BG_CONTAINER);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        lblFolderTitle.setFont(AntDesign.FONT_SUBTITLE);
        lblFolderTitle.setForeground(AntDesign.TEXT_PRIMARY);
        titleRow.add(lblFolderTitle, BorderLayout.WEST);

        lblCount.setFont(AntDesign.FONT_SMALL);
        lblCount.setForeground(AntDesign.TEXT_SECONDARY);
        titleRow.add(lblCount, BorderLayout.EAST);
        topPanel.add(titleRow, BorderLayout.NORTH);

        // Search Bar (Ant Design Search Input)
        JPanel searchBox = new JPanel(new BorderLayout(6, 0));
        searchBox.setBackground(Color.WHITE);
        searchBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER, 1),
                new EmptyBorder(4, 10, 4, 10)
        ));
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setForeground(AntDesign.TEXT_TERTIARY);
        searchBox.add(searchIcon, BorderLayout.WEST);

        searchField.setBorder(null);
        searchField.setBackground(Color.WHITE);
        searchField.setFont(AntDesign.FONT_BODY);
        searchField.setForeground(AntDesign.TEXT_PRIMARY);
        searchBox.add(searchField, BorderLayout.CENTER);
        topPanel.add(searchBox, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // Mail List with Custom Ant Design Renderer
        mailJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mailJList.setCellRenderer(new MailCellRenderer());
        mailJList.setBackground(AntDesign.BG_CONTAINER);

        JScrollPane scrollPane = new JScrollPane(mailJList);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AntDesign.BORDER_SPLIT));
        add(scrollPane, BorderLayout.CENTER);

        // Search filtering listener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        // Selection listener
        mailJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                MailItem selected = mailJList.getSelectedValue();
                if (selected != null && onSelectMailCallback != null) {
                    onSelectMailCallback.accept(selected);
                }
            }
        });
    }

    public void setOnSelectMailCallback(Consumer<MailItem> callback) {
        this.onSelectMailCallback = callback;
    }

    public void setFolderTitle(String title) {
        lblFolderTitle.setText(title);
    }

    public void setMailItems(List<MailItem> items) {
        this.rawItems = (items != null) ? items : new ArrayList<>();
        applyFilter();
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        listModel.clear();

        for (MailItem m : rawItems) {
            if (query.isEmpty() || matches(m, query)) {
                listModel.addElement(m);
            }
        }

        lblCount.setText(listModel.getSize() + " thư");
    }

    private boolean matches(MailItem m, String query) {
        if (m.getSender() != null && m.getSender().toLowerCase().contains(query)) return true;
        if (m.getRecipient() != null && m.getRecipient().toLowerCase().contains(query)) return true;
        if (m.getSubject() != null && m.getSubject().toLowerCase().contains(query)) return true;
        if (m.getBody() != null && m.getBody().toLowerCase().contains(query)) return true;
        return false;
    }

    public void setCurrentContext(String folder, String username) {
        this.currentFolder = folder != null ? folder.toUpperCase() : Protocol.FOLDER_INBOX;
        this.currentUsername = username != null ? username : "";
        mailJList.repaint();
    }

    public MailItem getSelectedMail() {
        return mailJList.getSelectedValue();
    }

    public void selectFirstIfAvailable() {
        if (!listModel.isEmpty()) {
            mailJList.setSelectedIndex(0);
        }
    }

    // Custom Cell Renderer for Clean Ant Design List Items
    private class MailCellRenderer extends JPanel implements ListCellRenderer<MailItem> {
        private final JLabel lblSender = new JLabel();
        private final JLabel lblDate = new JLabel();
        private final JLabel lblSubject = new JLabel();
        private final JLabel lblPreview = new JLabel();
        private final JLabel lblUnreadDot = new JLabel("●");

        public MailCellRenderer() {
            setLayout(new BorderLayout(6, 3));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.BORDER_SPLIT),
                    new EmptyBorder(10, 12, 10, 12)
            ));

            // Top row: Unread Dot + Sender + Date
            JPanel top = new JPanel(new BorderLayout(4, 0));
            top.setOpaque(false);

            JPanel senderBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            senderBox.setOpaque(false);
            lblUnreadDot.setForeground(AntDesign.PRIMARY);
            lblUnreadDot.setFont(AntDesign.font(12f, Font.BOLD));
            senderBox.add(lblUnreadDot);

            lblSender.setFont(AntDesign.FONT_BODY_BOLD);
            lblSender.setForeground(AntDesign.TEXT_PRIMARY);
            senderBox.add(lblSender);
            top.add(senderBox, BorderLayout.WEST);

            lblDate.setFont(AntDesign.FONT_SMALL);
            lblDate.setForeground(AntDesign.TEXT_TERTIARY);
            top.add(lblDate, BorderLayout.EAST);

            // Middle row: Subject
            lblSubject.setFont(AntDesign.FONT_BODY);
            lblSubject.setForeground(AntDesign.TEXT_PRIMARY);

            // Bottom row: Preview snippet
            lblPreview.setFont(AntDesign.FONT_SMALL);
            lblPreview.setForeground(AntDesign.TEXT_SECONDARY);

            JPanel center = new JPanel(new GridLayout(2, 1, 2, 2));
            center.setOpaque(false);
            center.add(lblSubject);
            center.add(lblPreview);

            add(top, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MailItem> list,
                                                      MailItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            if (value != null) {
                boolean isSent = Protocol.FOLDER_SENT.equalsIgnoreCase(currentFolder);
                boolean isDraft = Protocol.FOLDER_DRAFTS.equalsIgnoreCase(currentFolder);
                boolean isOutgoingTrash = Protocol.FOLDER_TRASH.equalsIgnoreCase(currentFolder)
                        && currentUsername != null && !currentUsername.isEmpty()
                        && currentUsername.equalsIgnoreCase(value.getSender());

                if (isSent || isDraft || isOutgoingTrash) {
                    String recipient = value.getRecipient();
                    lblSender.setText("Đến: " + (recipient != null && !recipient.isEmpty() ? recipient : "(Chưa có)"));
                } else {
                    lblSender.setText(value.getSender() != null ? value.getSender() : "Unknown");
                }

                lblDate.setText(value.getCreatedAt() != null ? value.getCreatedAt() : "");
                String fileName = value.getMailId() != null
                        ? (value.getMailId().endsWith(".txt") ? value.getMailId() : value.getMailId() + ".txt")
                        : "mail.txt";
                String baseSubject = (value.getSubject() != null && !value.getSubject().isEmpty())
                        ? value.getSubject() : "(Không tiêu đề)";
                lblSubject.setText("📄 [" + fileName + "] " + baseSubject);
                lblPreview.setText(value.getPreview(65));

                boolean unread = !value.isReadState();
                lblUnreadDot.setVisible(unread);
                if (unread) {
                    lblSubject.setFont(AntDesign.FONT_BODY_BOLD);
                    lblSender.setFont(AntDesign.FONT_BODY_BOLD);
                } else {
                    lblSubject.setFont(AntDesign.FONT_BODY);
                    lblSender.setFont(AntDesign.FONT_BODY);
                }
            }

            if (isSelected) {
                setBackground(AntDesign.PRIMARY_BG);
                lblSubject.setForeground(AntDesign.PRIMARY);
            } else {
                setBackground(Color.WHITE);
                lblSubject.setForeground(AntDesign.TEXT_PRIMARY);
            }

            return this;
        }
    }
}
