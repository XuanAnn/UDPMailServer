package org.example.mail.server.ui;

import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogPanel extends JPanel {

    private final JTextPane textPane = new JTextPane();
    private final StyledDocument doc = textPane.getStyledDocument();
    private final JComboBox<String> levelFilter = new JComboBox<>(new String[]{"Tất cả", "INFO", "WARN", "ERROR"});
    private final JTextField searchField = new JTextField(15);
    private final JCheckBox autoScrollCheck = new JCheckBox("Tự cuộn", true);

    private static class LogEntry {
        final String timestamp;
        final String level;
        final String message;

        LogEntry(String timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }

    private final List<LogEntry> allLogs = new ArrayList<>();
    private final Style styleInfo;
    private final Style styleWarn;
    private final Style styleError;
    private final Style styleDefault;

    public LogPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(AntDesign.BG_CONTAINER);

        textPane.setEditable(false);
        textPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        textPane.setBackground(new Color(15, 23, 42)); // Slate dark console
        textPane.setForeground(new Color(226, 232, 240));

        // Setup styles
        styleDefault = textPane.addStyle("Default", null);
        StyleConstants.setForeground(styleDefault, new Color(203, 213, 225));

        styleInfo = textPane.addStyle("INFO", null);
        StyleConstants.setForeground(styleInfo, new Color(74, 222, 128)); // Emerald green

        styleWarn = textPane.addStyle("WARN", null);
        StyleConstants.setForeground(styleWarn, new Color(251, 191, 36)); // Amber gold
        StyleConstants.setBold(styleWarn, true);

        styleError = textPane.addStyle("ERROR", null);
        StyleConstants.setForeground(styleError, new Color(248, 113, 113)); // Rose red
        StyleConstants.setBold(styleError, true);

        // Control toolbar (Ant Design style)
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setBackground(AntDesign.BG_CONTAINER);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel lblLvl = new JLabel("Mức:");
        lblLvl.setFont(AntDesign.FONT_BODY_BOLD);
        lblLvl.setForeground(AntDesign.TEXT_SECONDARY);
        toolbar.add(lblLvl);

        levelFilter.setFont(AntDesign.FONT_BODY);
        levelFilter.setBackground(Color.WHITE);
        toolbar.add(levelFilter);

        JLabel lblSearch = new JLabel("🔍 Tìm:");
        lblSearch.setFont(AntDesign.FONT_BODY_BOLD);
        lblSearch.setForeground(AntDesign.TEXT_SECONDARY);
        toolbar.add(lblSearch);

        AntDesign.styleInput(searchField);
        toolbar.add(searchField);

        autoScrollCheck.setFont(AntDesign.FONT_BODY);
        autoScrollCheck.setForeground(AntDesign.TEXT_SECONDARY);
        autoScrollCheck.setOpaque(false);
        toolbar.add(autoScrollCheck);

        AntButton clearBtn = AntDesign.createDefaultButton("Xóa");
        AntButton exportBtn = AntDesign.createDefaultButton("Xuất file");

        toolbar.add(clearBtn);
        toolbar.add(exportBtn);

        add(toolbar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        setupListeners(clearBtn, exportBtn);
    }

    private void setupListeners(JButton clearBtn, JButton exportBtn) {
        levelFilter.addActionListener(e -> rebuildLogView());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { rebuildLogView(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { rebuildLogView(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildLogView(); }
        });

        clearBtn.addActionListener(e -> {
            synchronized (allLogs) {
                allLogs.clear();
            }
            textPane.setText("");
        });

        exportBtn.addActionListener(e -> exportLogs());
    }

    public void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LogEntry entry = new LogEntry(timestamp, level, message);

        synchronized (allLogs) {
            allLogs.add(entry);
        }

        // Check if matches current filter
        if (matchesFilter(entry)) {
            SwingUtilities.invokeLater(() -> appendEntry(entry));
        }

        // Also write to rotating log file
        writeToDiskLog(entry);
    }

    private boolean matchesFilter(LogEntry entry) {
        String selectedLevel = (String) levelFilter.getSelectedItem();
        if (selectedLevel != null && !selectedLevel.equals("ALL") && !selectedLevel.equals("Tất cả")) {
            if (!entry.level.equalsIgnoreCase(selectedLevel)) {
                return false;
            }
        }

        String search = searchField.getText().trim().toLowerCase();
        if (!search.isEmpty()) {
            return entry.message.toLowerCase().contains(search) || entry.level.toLowerCase().contains(search);
        }
        return true;
    }

    private void appendEntry(LogEntry entry) {
        try {
            Style style;
            switch (entry.level.toUpperCase()) {
                case "INFO" -> style = styleInfo;
                case "WARN" -> style = styleWarn;
                case "ERROR" -> style = styleError;
                default -> style = styleDefault;
            }

            String line = String.format("[%s] [%-5s] %s\n", entry.timestamp, entry.level, entry.message);
            doc.insertString(doc.getLength(), line, style);

            if (autoScrollCheck.isSelected()) {
                textPane.setCaretPosition(doc.getLength());
            }
        } catch (BadLocationException ignored) {}
    }

    private void rebuildLogView() {
        SwingUtilities.invokeLater(() -> {
            textPane.setText("");
            synchronized (allLogs) {
                for (LogEntry entry : allLogs) {
                    if (matchesFilter(entry)) {
                        appendEntry(entry);
                    }
                }
            }
        });
    }

    private void writeToDiskLog(LogEntry entry) {
        try {
            Path logDir = Paths.get("data", "logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = logDir.resolve("server-" + date + ".log");

            try (FileWriter fw = new FileWriter(logFile.toFile(), true)) {
                fw.write(String.format("[%s] [%-5s] %s%n", entry.timestamp, entry.level, entry.message));
            }
        } catch (IOException ignored) {}
    }

    private void exportLogs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("server-exported-logs.txt"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                synchronized (allLogs) {
                    for (LogEntry e : allLogs) {
                        fw.write(String.format("[%s] [%-5s] %s%n", e.timestamp, e.level, e.message));
                    }
                }
                JOptionPane.showMessageDialog(this, "Logs exported successfully!", "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to export logs: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
