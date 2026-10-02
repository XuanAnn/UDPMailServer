package org.example;

import org.example.mail.ClientMain;
import org.example.mail.ServerMain;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0) {
            String arg = args[0].trim().toLowerCase();
            if ("server".equals(arg)) {
                ServerMain.main(args);
                return;
            } else if ("client".equals(arg)) {
                ClientMain.main(args);
                return;
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(Main::showLauncherDialog);
    }

    private static void showLauncherDialog() {
        JFrame frame = new JFrame("Java UDP Mail - Launcher");
        frame.setSize(380, 240);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));

        JLabel title = new JLabel("Chọn ứng dụng:", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(title);

        JButton btnServer = new JButton("Mail Server Manager");
        btnServer.setFont(btnServer.getFont().deriveFont(Font.BOLD, 12.5f));
        btnServer.setBackground(new Color(240, 244, 250));

        JButton btnClient = new JButton("Mail Client");
        btnClient.setFont(btnClient.getFont().deriveFont(Font.BOLD, 12.5f));
        btnClient.setBackground(new Color(235, 248, 235));

        btnServer.addActionListener(e -> {
            frame.dispose();
            ServerMain.main(new String[]{});
        });

        btnClient.addActionListener(e -> {
            frame.dispose();
            ClientMain.main(new String[]{});
        });

        panel.add(btnServer);
        panel.add(btnClient);

        frame.add(panel);
        frame.setVisible(true);
    }
}