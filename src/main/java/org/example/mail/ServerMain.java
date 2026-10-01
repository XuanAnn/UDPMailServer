package org.example.mail;

import org.example.mail.server.ui.ServerManagerUI;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ServerMain {
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
