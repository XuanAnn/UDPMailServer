package org.example.mail.common;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Ant Design (antd) Design System Tokens, Typography, and UI Components for Java Swing.
 * Implements Ant Design v5 color palette, button styles, tags, cards, tables, inputs, and alerts.
 */
public class AntDesign {

    // ==========================================
    // 1. Ant Design Color Tokens (v5 Palette)
    // ==========================================

    // Primary (Tech Blue)
    public static final Color PRIMARY = new Color(22, 119, 255);         // #1677ff
    public static final Color PRIMARY_HOVER = new Color(64, 150, 255);   // #4096ff
    public static final Color PRIMARY_ACTIVE = new Color(9, 88, 217);     // #0958d9
    public static final Color PRIMARY_BG = new Color(230, 244, 255);     // #e6f4ff
    public static final Color PRIMARY_BORDER = new Color(145, 202, 255); // #91caff

    // Success (Green)
    public static final Color SUCCESS = new Color(82, 196, 26);          // #52c41a
    public static final Color SUCCESS_HOVER = new Color(115, 209, 61);   // #73d13d
    public static final Color SUCCESS_ACTIVE = new Color(56, 158, 13);    // #389e0d
    public static final Color SUCCESS_BG = new Color(246, 255, 237);     // #f6ffed
    public static final Color SUCCESS_BORDER = new Color(183, 235, 143); // #b7eb8f

    // Warning (Gold / Amber)
    public static final Color WARNING = new Color(250, 173, 20);         // #faad14
    public static final Color WARNING_HOVER = new Color(255, 197, 61);   // #ffc53d
    public static final Color WARNING_BG = new Color(255, 251, 230);     // #fffbe6
    public static final Color WARNING_BORDER = new Color(255, 229, 143); // #ffe58f
    public static final Color WARNING_TEXT = new Color(212, 136, 6);     // #d48806

    // Error / Danger (Red) - using DANGER to avoid ImageObserver.ERROR conflict
    public static final Color DANGER = new Color(255, 77, 79);           // #ff4d4f
    public static final Color DANGER_HOVER = new Color(255, 120, 117);   // #ff7875
    public static final Color DANGER_ACTIVE = new Color(207, 19, 34);    // #cf1322
    public static final Color DANGER_BG = new Color(255, 242, 240);      // #fff2f0
    public static final Color DANGER_BORDER = new Color(255, 204, 199);  // #ffccc7

    // Neutral Layout & Backgrounds
    public static final Color BG_LAYOUT = new Color(245, 245, 245);      // #f5f5f5 (Ant layout body)
    public static final Color BG_CONTAINER = Color.WHITE;                // #ffffff
    public static final Color BG_ELEVATED = Color.WHITE;                 // #ffffff
    public static final Color BG_HEADER = Color.WHITE;                   // #ffffff
    public static final Color BG_DARK_HEADER = new Color(0, 21, 41);     // #001529 (Ant Design Pro Sider)

    // Text Tokens
    public static final Color TEXT_PRIMARY = new Color(31, 31, 31);      // #1f1f1f (88% opacity)
    public static final Color TEXT_SECONDARY = new Color(89, 89, 89);    // #595959 (65% opacity)
    public static final Color TEXT_TERTIARY = new Color(140, 140, 140);  // #8c8c8c (45% opacity)
    public static final Color TEXT_QUATERNARY = new Color(191, 191, 191);// #bfbfbf (25% opacity)

    // Border & Divider Tokens
    public static final Color BORDER = new Color(217, 217, 217);         // #d9d9d9
    public static final Color BORDER_SPLIT = new Color(240, 240, 240);   // #f0f0f0
    public static final Color TABLE_HEADER_BG = new Color(250, 250, 250);// #fafafa
    public static final Color ROW_HOVER_BG = new Color(250, 250, 250);   // #fafafa

    // ==========================================
    // 2. Typography
    // ==========================================

    private static final String FONT_FAMILY = detectFontFamily();

    private static String detectFontFamily() {
        String[] families = {"Segoe UI", "San Francisco", "Helvetica Neue", "Arial", "SansSerif"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] available = ge.getAvailableFontFamilyNames();
        for (String f : families) {
            for (String a : available) {
                if (a.equalsIgnoreCase(f)) {
                    return a;
                }
            }
        }
        return "SansSerif";
    }

    public static Font font(float size, int style) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    public static final Font FONT_TITLE_LARGE = font(20f, Font.BOLD);
    public static final Font FONT_TITLE = font(16f, Font.BOLD);
    public static final Font FONT_SUBTITLE = font(14f, Font.BOLD);
    public static final Font FONT_BODY = font(13f, Font.PLAIN);
    public static final Font FONT_BODY_BOLD = font(13f, Font.BOLD);
    public static final Font FONT_SMALL = font(12f, Font.PLAIN);
    public static final Font FONT_SMALL_BOLD = font(12f, Font.BOLD);
    public static final Font FONT_CODE = new Font("Consolas", Font.PLAIN, 12);

    // ==========================================
    // 3. Ant Design Buttons
    // ==========================================

    public enum ButtonType {
        PRIMARY, DEFAULT, DANGER_PRIMARY, DANGER_DEFAULT, SUCCESS_PRIMARY, SUCCESS_DEFAULT, TEXT, LINK
    }

    public static class AntButton extends JButton {
        private final ButtonType type;
        private boolean isHovered = false;
        private boolean isPressed = false;
        private int cornerRadius = 6;

        public AntButton(String text, ButtonType type) {
            super(text);
            this.type = type != null ? type : ButtonType.DEFAULT;
            init();
        }

        private void init() {
            setFont(FONT_BODY_BOLD);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 14, 6, 14));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        isHovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) {
                        isPressed = true;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    isPressed = false;
                    repaint();
                }
            });
        }

        public void setCornerRadius(int radius) {
            this.cornerRadius = radius;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            Color bgColor;
            Color borderColor;
            Color textColor;

            if (!isEnabled()) {
                bgColor = new Color(245, 245, 245);
                borderColor = BORDER;
                textColor = TEXT_QUATERNARY;
            } else {
                switch (type) {
                    case PRIMARY -> {
                        bgColor = isPressed ? PRIMARY_ACTIVE : (isHovered ? PRIMARY_HOVER : PRIMARY);
                        borderColor = bgColor;
                        textColor = Color.WHITE;
                    }
                    case DANGER_PRIMARY -> {
                        bgColor = isPressed ? DANGER_ACTIVE : (isHovered ? DANGER_HOVER : DANGER);
                        borderColor = bgColor;
                        textColor = Color.WHITE;
                    }
                    case SUCCESS_PRIMARY -> {
                        bgColor = isPressed ? SUCCESS_ACTIVE : (isHovered ? SUCCESS_HOVER : SUCCESS);
                        borderColor = bgColor;
                        textColor = Color.WHITE;
                    }
                    case DANGER_DEFAULT -> {
                        bgColor = isHovered ? DANGER_BG : Color.WHITE;
                        borderColor = isHovered ? DANGER_HOVER : DANGER;
                        textColor = isHovered ? DANGER_HOVER : DANGER;
                    }
                    case SUCCESS_DEFAULT -> {
                        bgColor = isHovered ? SUCCESS_BG : Color.WHITE;
                        borderColor = isHovered ? SUCCESS_HOVER : SUCCESS;
                        textColor = isHovered ? SUCCESS_HOVER : SUCCESS;
                    }
                    case TEXT, LINK -> {
                        bgColor = isHovered ? new Color(240, 240, 240) : new Color(0, 0, 0, 0);
                        borderColor = new Color(0, 0, 0, 0);
                        textColor = (type == ButtonType.LINK) ? (isHovered ? PRIMARY_HOVER : PRIMARY) : TEXT_PRIMARY;
                    }
                    default -> { // DEFAULT
                        bgColor = Color.WHITE;
                        borderColor = isHovered ? PRIMARY : BORDER;
                        textColor = isHovered ? PRIMARY : TEXT_PRIMARY;
                    }
                }
            }

            // Draw background
            if (bgColor.getAlpha() > 0) {
                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, width, height, cornerRadius, cornerRadius));
            }

            // Draw border
            if (borderColor.getAlpha() > 0 && type != ButtonType.TEXT && type != ButtonType.LINK) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, cornerRadius, cornerRadius));
            }

            // Draw text
            g2.setColor(textColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int stringWidth = fm.stringWidth(getText());
            int stringHeight = fm.getAscent();
            int x = (width - stringWidth) / 2;
            int y = (height + stringHeight) / 2 - 2;

            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    public static AntButton createPrimaryButton(String text) {
        return new AntButton(text, ButtonType.PRIMARY);
    }

    public static AntButton createDefaultButton(String text) {
        return new AntButton(text, ButtonType.DEFAULT);
    }

    public static AntButton createDangerButton(String text) {
        return new AntButton(text, ButtonType.DANGER_PRIMARY);
    }

    public static AntButton createSuccessButton(String text) {
        return new AntButton(text, ButtonType.SUCCESS_PRIMARY);
    }

    public static AntButton createTextButton(String text) {
        return new AntButton(text, ButtonType.TEXT);
    }

    // ==========================================
    // 4. Ant Design Tags (Badges)
    // ==========================================

    public enum TagColor {
        PROCESSING, SUCCESS, WARNING, ERROR, DEFAULT, PURPLE, CYAN
    }

    public static class AntTag extends JLabel {
        private final TagColor tagColor;

        public AntTag(String text, TagColor tagColor) {
            super(text, SwingConstants.CENTER);
            this.tagColor = tagColor != null ? tagColor : TagColor.DEFAULT;
            init();
        }

        private void init() {
            setFont(FONT_SMALL_BOLD);
            setOpaque(false);
            setBorder(new EmptyBorder(2, 8, 2, 8));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg;
            Color border;
            Color text;

            switch (tagColor) {
                case PROCESSING -> { bg = PRIMARY_BG; border = PRIMARY_BORDER; text = PRIMARY; }
                case SUCCESS -> { bg = SUCCESS_BG; border = SUCCESS_BORDER; text = SUCCESS; }
                case WARNING -> { bg = WARNING_BG; border = WARNING_BORDER; text = WARNING_TEXT; }
                case ERROR -> { bg = DANGER_BG; border = DANGER_BORDER; text = DANGER; }
                case PURPLE -> { bg = new Color(249, 240, 255); border = new Color(211, 173, 247); text = new Color(114, 46, 209); }
                case CYAN -> { bg = new Color(230, 255, 251); border = new Color(135, 232, 222); text = new Color(19, 194, 194); }
                default -> { bg = new Color(250, 250, 250); border = BORDER; text = TEXT_SECONDARY; }
            }

            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 4, 4));

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 4, 4));

            setForeground(text);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    public static AntTag createTag(String text, TagColor color) {
        return new AntTag(text, color);
    }

    // ==========================================
    // 5. Ant Design Cards
    // ==========================================

    public static JPanel createCard(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_CONTAINER);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SPLIT, 1),
                new EmptyBorder(0, 0, 0, 0)
        ));

        if (title != null && !title.isEmpty()) {
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(BG_CONTAINER);
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SPLIT),
                    new EmptyBorder(12, 16, 12, 16)
            ));

            JLabel lblTitle = new JLabel(title);
            lblTitle.setFont(FONT_SUBTITLE);
            lblTitle.setForeground(TEXT_PRIMARY);
            header.add(lblTitle, BorderLayout.WEST);
            card.add(header, BorderLayout.NORTH);
        }

        if (content != null) {
            content.setBackground(BG_CONTAINER);
            card.add(content, BorderLayout.CENTER);
        }

        return card;
    }

    public static JPanel createStatisticCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 6));
        card.setBackground(BG_CONTAINER);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SPLIT, 1),
                new EmptyBorder(14, 18, 14, 18)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(FONT_SMALL);
        lblTitle.setForeground(TEXT_SECONDARY);

        valueLabel.setFont(font(26f, Font.BOLD));
        valueLabel.setForeground(accentColor != null ? accentColor : TEXT_PRIMARY);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // ==========================================
    // 6. Ant Design Alert
    // ==========================================

    public static JPanel createAlert(String message, String description, TagColor type) {
        JPanel alert = new JPanel(new BorderLayout(8, 4));
        alert.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        type == TagColor.SUCCESS ? SUCCESS_BORDER :
                        type == TagColor.ERROR ? DANGER_BORDER :
                        type == TagColor.WARNING ? WARNING_BORDER : PRIMARY_BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        Color bg = type == TagColor.SUCCESS ? SUCCESS_BG :
                   type == TagColor.ERROR ? DANGER_BG :
                   type == TagColor.WARNING ? WARNING_BG : PRIMARY_BG;
        alert.setBackground(bg);

        String iconStr = type == TagColor.SUCCESS ? "✔" :
                         type == TagColor.ERROR ? "✖" :
                         type == TagColor.WARNING ? "⚠" : "ℹ";

        JLabel iconLbl = new JLabel(iconStr);
        iconLbl.setFont(font(14f, Font.BOLD));
        iconLbl.setForeground(
                type == TagColor.SUCCESS ? SUCCESS :
                type == TagColor.ERROR ? DANGER :
                type == TagColor.WARNING ? WARNING_TEXT : PRIMARY
        );
        alert.add(iconLbl, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new GridLayout(description != null && !description.isEmpty() ? 2 : 1, 1, 0, 2));
        textPanel.setOpaque(false);

        JLabel msgLbl = new JLabel(message);
        msgLbl.setFont(FONT_BODY_BOLD);
        msgLbl.setForeground(TEXT_PRIMARY);
        textPanel.add(msgLbl);

        if (description != null && !description.isEmpty()) {
            JLabel descLbl = new JLabel(description);
            descLbl.setFont(FONT_SMALL);
            descLbl.setForeground(TEXT_SECONDARY);
            textPanel.add(descLbl);
        }

        alert.add(textPanel, BorderLayout.CENTER);
        return alert;
    }

    // ==========================================
    // 7. Ant Design Form Inputs & Table Styler
    // ==========================================

    public static void styleInput(JComponent field) {
        field.setFont(FONT_BODY);
        field.setBackground(BG_CONTAINER);
        field.setForeground(TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(6, 11, 6, 11)
        ));
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(BORDER_SPLIT);
        table.setBackground(BG_CONTAINER);
        table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(PRIMARY_BG);
        table.setSelectionForeground(PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BODY_BOLD);
        header.setBackground(TABLE_HEADER_BG);
        header.setForeground(TEXT_PRIMARY);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SPLIT));
        header.setPreferredSize(new Dimension(header.getWidth(), 38));

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setBackground(TABLE_HEADER_BG);
                l.setForeground(TEXT_PRIMARY);
                l.setFont(FONT_BODY_BOLD);
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SPLIT),
                        new EmptyBorder(0, 10, 0, 10)
                ));
                return l;
            }
        };
        table.getTableHeader().setDefaultRenderer(headerRenderer);
    }
}
