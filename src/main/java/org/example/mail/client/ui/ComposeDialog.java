package org.example.mail.client.ui;

import org.example.mail.client.model.MailItem;
import org.example.mail.client.service.MailClientService;
import org.example.mail.common.AntDesign;
import org.example.mail.common.AntDesign.AntButton;
import org.example.mail.common.Validator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ComposeDialog extends JDialog {

    private final JTextField txtTo = new JTextField();
    private final JTextField txtSubject = new JTextField();
    private final JTextArea txtBody = new JTextArea();
    private final AntButton btnSend = AntDesign.createPrimaryButton("Gửi");
    private final AntButton btnSaveDraft = AntDesign.createDefaultButton("Lưu nháp");
    private final AntButton btnDiscard = AntDesign.createDefaultButton("Hủy");
    private final JLabel lblStatus = new JLabel(" ");

    private final MailClientService mailService;
    private final Runnable onSuccessCallback;
    private String existingMailId;
    private boolean sentOrSaved = false;

    public ComposeDialog(Frame owner, MailClientService mailService, Runnable onSuccessCallback) {
        super(owner, "Soạn thư", true);
        this.mailService = mailService;
        this.onSuccessCallback = onSuccessCallback;

        initUI();
    }

    private void initUI() {
        setSize(660, 520);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());
        getContentPane().setBackground(AntDesign.BG_CONTAINER);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Header Modal Bar
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(AntDesign.BG_CONTAINER);
        headerBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(12, 18, 12, 18)
        ));
        JLabel titleLbl = new JLabel("Soạn thư");
        titleLbl.setFont(AntDesign.FONT_TITLE);
        titleLbl.setForeground(AntDesign.TEXT_PRIMARY);
        headerBar.add(titleLbl, BorderLayout.WEST);
        add(headerBar, BorderLayout.NORTH);

        // Form Fields (To, Subject)
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(AntDesign.BG_CONTAINER);
        formPanel.setBorder(new EmptyBorder(14, 18, 10, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 4, 5, 4);

        // To field
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblTo = new JLabel("Người nhận:");
        lblTo.setFont(AntDesign.FONT_BODY_BOLD);
        lblTo.setForeground(AntDesign.TEXT_PRIMARY);
        formPanel.add(lblTo, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        AntDesign.styleInput(txtTo);
        formPanel.add(txtTo, gbc);

        // Subject field
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel lblSub = new JLabel("Tiêu đề:");
        lblSub.setFont(AntDesign.FONT_BODY_BOLD);
        lblSub.setForeground(AntDesign.TEXT_PRIMARY);
        formPanel.add(lblSub, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        AntDesign.styleInput(txtSubject);
        formPanel.add(txtSubject, gbc);

        // Center: Body textarea inside clean card
        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));
        centerPanel.setBackground(AntDesign.BG_CONTAINER);
        centerPanel.setBorder(new EmptyBorder(0, 18, 10, 18));

        centerPanel.add(formPanel, BorderLayout.NORTH);

        txtBody.setFont(AntDesign.FONT_BODY);
        txtBody.setLineWrap(true);
        txtBody.setWrapStyleWord(true);
        txtBody.setMargin(new Insets(10, 12, 10, 12));
        JScrollPane scrollPane = new JScrollPane(txtBody);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AntDesign.BORDER, 1),
                new EmptyBorder(0, 0, 0, 0)
        ));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Bottom Actions
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(AntDesign.BG_LAYOUT);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AntDesign.BORDER_SPLIT),
                new EmptyBorder(10, 18, 10, 18)
        ));

        lblStatus.setFont(AntDesign.FONT_SMALL);
        lblStatus.setForeground(AntDesign.PRIMARY);
        bottomPanel.add(lblStatus, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);

        btnPanel.add(btnSaveDraft);
        btnPanel.add(btnDiscard);
        btnPanel.add(btnSend);
        bottomPanel.add(btnPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        btnSend.addActionListener(e -> sendEmail());
        btnSaveDraft.addActionListener(e -> saveDraft());
        btnDiscard.addActionListener(e -> handleClose());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClose();
            }
        });
    }

    public void populateReply(MailItem mail) {
        if (mail != null) {
            prepareReply(mail.getSender(), mail.getSubject(), mail.getBody(), mail.getSender(), mail.getCreatedAt());
        }
    }

    public void populateForward(MailItem mail) {
        if (mail != null) {
            prepareForward(mail.getSubject(), mail.getBody(), mail.getSender(), mail.getCreatedAt());
        }
    }

    public void prepareReply(String recipient, String originalSubject, String originalBody, String originalSender, String date) {
        setTitle("Trả lời (Reply): " + originalSubject);
        txtTo.setText(recipient);
        txtSubject.setText(originalSubject != null && originalSubject.startsWith("Re:") ? originalSubject : "Re: " + originalSubject);
        txtBody.setText("\n\n--- Thư gốc từ " + originalSender + " lúc " + date + " ---\n" + originalBody);
        txtBody.setCaretPosition(0);
    }

    public void prepareForward(String originalSubject, String originalBody, String originalSender, String date) {
        setTitle("Chuyển tiếp (Forward): " + originalSubject);
        txtTo.setText("");
        txtSubject.setText(originalSubject != null && originalSubject.startsWith("Fwd:") ? originalSubject : "Fwd: " + originalSubject);
        txtBody.setText("\n\n---------- Chuyển tiếp thư ---------\n" +
                "Người gửi: " + originalSender + "\n" +
                "Ngày gửi: " + date + "\n" +
                "Tiêu đề: " + originalSubject + "\n\n" + originalBody);
        txtBody.setCaretPosition(0);
    }

    public void prepareEditDraft(String mailId, String to, String subject, String body) {
        this.existingMailId = mailId;
        setTitle("Chỉnh sửa bản nháp: " + subject);
        txtTo.setText(to);
        txtSubject.setText(subject);
        txtBody.setText(body);
    }

    private void sendEmail() {
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtBody.getText();

        if (!Validator.isValidUsername(to)) {
            JOptionPane.showMessageDialog(this, "Tên người nhận phải từ 3-30 ký tự hợp lệ.", "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            txtTo.requestFocus();
            return;
        }

        if (subject.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tiêu đề email.", "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            txtSubject.requestFocus();
            return;
        }

        setBusy(true, "Đang gửi email qua giao thức UDP...");

        mailService.sendMail(to, subject, body, (mailId, error) -> {
            setBusy(false, "");
            if (error != null) {
                JOptionPane.showMessageDialog(this, "Không thể gửi thư:\n" + error.getMessage(), "Lỗi gửi thư", JOptionPane.ERROR_MESSAGE);
            } else {
                sentOrSaved = true;
                JOptionPane.showMessageDialog(this, "Thư đã được gửi thành công!", "Đã gửi", JOptionPane.INFORMATION_MESSAGE);
                if (onSuccessCallback != null) onSuccessCallback.run();
                dispose();
            }
        });
    }

    private void saveDraft() {
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtBody.getText();

        if (subject.isEmpty() && body.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nội dung thư nháp trống.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true, "Đang lưu bản nháp...");

        mailService.saveDraft(to, subject.isEmpty() ? "(Không có tiêu đề)" : subject, body, existingMailId, (mailId, error) -> {
            setBusy(false, "");
            if (error != null) {
                JOptionPane.showMessageDialog(this, "Không thể lưu bản nháp:\n" + error.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            } else {
                sentOrSaved = true;
                this.existingMailId = mailId;
                JOptionPane.showMessageDialog(this, "Đã lưu bản nháp thành công.", "Bản nháp", JOptionPane.INFORMATION_MESSAGE);
                if (onSuccessCallback != null) onSuccessCallback.run();
                dispose();
            }
        });
    }

    private void handleClose() {
        if (sentOrSaved) {
            dispose();
            return;
        }

        boolean hasContent = !txtTo.getText().trim().isEmpty() ||
                             !txtSubject.getText().trim().isEmpty() ||
                             !txtBody.getText().trim().isEmpty();

        if (hasContent) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Bạn có nội dung thư chưa lưu.\nBạn có muốn lưu thư này vào mục Thư nháp (Draft) trước khi đóng?",
                    "Lưu bản nháp?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (opt == JOptionPane.YES_OPTION) {
                saveDraft();
            } else if (opt == JOptionPane.NO_OPTION) {
                dispose();
            }
        } else {
            dispose();
        }
    }

    private void setBusy(boolean busy, String status) {
        lblStatus.setText(status);
        btnSend.setEnabled(!busy);
        btnSaveDraft.setEnabled(!busy);
        btnDiscard.setEnabled(!busy);
        txtTo.setEnabled(!busy);
        txtSubject.setEnabled(!busy);
        txtBody.setEnabled(!busy);
    }
}
