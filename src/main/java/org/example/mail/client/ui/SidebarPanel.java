package org.example.mail.client.ui;

import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SidebarPanel extends JPanel {

    private final AntButton btnCompose = AntDesign.createPrimaryButton("✏ Soạn thư (Compose)");
    private final DefaultListModel<FolderItem> folderModel = new DefaultListModel<>();
    private final JList<FolderItem> folderList = new JList<>(folderModel);

    public static class FolderItem {
        final String folderCode;
        final String name;
        final String icon;
        int unreadCount;

        FolderItem(String folderCode, String name, String icon) {
            this.folderCode = folderCode;
            this.name = name;
            this.icon = icon;
            this.unreadCount = 0;
        }

        @Override
        public String toString() {
            return icon + "  " + name;
        }
    }

    private final Map<String, FolderItem> folderMap = new HashMap<>();
    private Consumer<String> onFolderSelectedCallback;
    private Runnable onComposeClickCallback;

    public SidebarPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, AntDesign.BORDER_SPLIT),
                new EmptyBorder(12, 10, 12, 10)
        ));
        setBackground(AntDesign.BG_CONTAINER);

        initUI();
    }

    private void initUI() {
        // Compose Button at Top (Ant Design Primary Button)
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 0, 12, 0));

        btnCompose.setPreferredSize(new Dimension(170, 38));
        btnCompose.setFont(AntDesign.FONT_BODY_BOLD);
        top.add(btnCompose, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // Folders List (Ant Design Navigation Menu style)
        addFolder(Protocol.FOLDER_INBOX, "Hộp thư đến (Inbox)", "📥");
        addFolder(Protocol.FOLDER_SENT, "Đã gửi (Sent)", "📤");
        addFolder(Protocol.FOLDER_DRAFTS, "Thư nháp (Drafts)", "📝");
        addFolder(Protocol.FOLDER_TRASH, "Thùng rác (Trash)", "🗑");

        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        folderList.setSelectedIndex(0);
        folderList.setBackground(AntDesign.BG_CONTAINER);
        folderList.setBorder(null);

        folderList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel itemPanel = new JPanel(new BorderLayout(8, 0)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (isSelected) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(AntDesign.PRIMARY_BG);
                            g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 6, 6));
                            g2.dispose();
                        }
                    }
                };
                itemPanel.setOpaque(false);
                itemPanel.setBorder(new EmptyBorder(8, 12, 8, 10));

                if (value instanceof FolderItem item) {
                    JLabel lblName = new JLabel(item.icon + "  " + item.name);
                    lblName.setFont(isSelected ? AntDesign.FONT_BODY_BOLD : AntDesign.FONT_BODY);
                    lblName.setForeground(isSelected ? AntDesign.PRIMARY : AntDesign.TEXT_PRIMARY);
                    itemPanel.add(lblName, BorderLayout.WEST);

                    if (item.unreadCount > 0) {
                        JLabel lblBadge = new JLabel(String.valueOf(item.unreadCount), SwingConstants.CENTER) {
                            @Override
                            protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                g2.setColor(AntDesign.DANGER);
                                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                                g2.dispose();
                                super.paintComponent(g);
                            }
                        };
                        lblBadge.setFont(AntDesign.font(11f, Font.BOLD));
                        lblBadge.setForeground(Color.WHITE);
                        lblBadge.setBorder(new EmptyBorder(2, 6, 2, 6));
                        itemPanel.add(lblBadge, BorderLayout.EAST);
                    }
                }

                return itemPanel;
            }
        });

        folderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                FolderItem item = folderList.getSelectedValue();
                if (item != null && onFolderSelectedCallback != null) {
                    onFolderSelectedCallback.accept(item.folderCode);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(folderList);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        btnCompose.addActionListener(e -> {
            if (onComposeClickCallback != null) {
                onComposeClickCallback.run();
            }
        });
    }

    private void addFolder(String code, String name, String icon) {
        FolderItem item = new FolderItem(code, name, icon);
        folderModel.addElement(item);
        folderMap.put(code, item);
    }

    public void setUnreadCount(String folderCode, int count) {
        FolderItem item = folderMap.get(folderCode);
        if (item != null) {
            item.unreadCount = count;
            folderList.repaint();
        }
    }

    public void setOnFolderSelected(Consumer<String> callback) {
        this.onFolderSelectedCallback = callback;
    }

    public void setOnComposeClick(Runnable callback) {
        this.onComposeClickCallback = callback;
    }

    public String getSelectedFolder() {
        FolderItem item = folderList.getSelectedValue();
        return item != null ? item.folderCode : Protocol.FOLDER_INBOX;
    }
}
