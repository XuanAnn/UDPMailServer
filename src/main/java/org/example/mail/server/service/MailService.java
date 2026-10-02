package org.example.mail.server.service;

import org.example.mail.client.model.MailFolder;
import org.example.mail.client.model.MailItem;
import org.example.mail.common.Protocol;
import org.example.mail.common.Validator;
import org.example.mail.server.storage.AccountRepository;
import org.example.mail.server.storage.MailRepository;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class MailService {

    private final MailRepository mailRepository;
    private final AccountRepository accountRepository;

    public MailService(MailRepository mailRepository, AccountRepository accountRepository) {
        this.mailRepository = mailRepository;
        this.accountRepository = accountRepository;
    }

    public synchronized String sendEmail(String sender, String recipient, String subject, String body) throws IOException {
        return sendEmail(sender, "127.0.0.1", 0, recipient, subject, body);
    }

    public synchronized String sendEmail(String sender, String senderIp, int senderPort, String recipient, String subject, String body) throws IOException {
        if (!Validator.isValidUsername(sender)) {
            throw new IllegalArgumentException("Invalid sender username");
        }
        if (!Validator.isValidUsername(recipient)) {
            throw new IllegalArgumentException("Invalid recipient username format");
        }
        if (!accountRepository.exists(recipient)) {
            throw new IllegalArgumentException("Recipient '" + recipient + "' does not exist");
        }
        if (!Validator.isValidSubject(subject)) {
            throw new IllegalArgumentException("Subject is required (max 200 characters)");
        }
        if (!Validator.isValidBody(body)) {
            throw new IllegalArgumentException("Email body exceeds allowed limit");
        }

        String mailId = mailRepository.getNextMailId();

        // 1. Deliver to recipient INBOX
        MailItem inboxItem = new MailItem(mailId, sender, recipient, subject, body);
        inboxItem.setSenderIp(senderIp != null ? senderIp : "127.0.0.1");
        inboxItem.setSenderPort(senderPort);
        inboxItem.setFolder(MailFolder.INBOX);
        inboxItem.setReadState(false);
        mailRepository.saveMail(recipient, Protocol.FOLDER_INBOX, inboxItem);

        // Save email content directly in recipient account directory (accounts/<recipient>/email_xxx.txt)
        mailRepository.saveMailToAccountRoot(recipient, mailId, body);

        // 2. Save copy to sender SENT
        MailItem sentItem = new MailItem(mailId, sender, recipient, subject, body);
        sentItem.setSenderIp(senderIp != null ? senderIp : "127.0.0.1");
        sentItem.setSenderPort(senderPort);
        sentItem.setFolder(MailFolder.SENT);
        sentItem.setReadState(true);
        mailRepository.saveMail(sender, Protocol.FOLDER_SENT, sentItem);

        return mailId;
    }

    public synchronized String saveDraft(String username, String recipient, String subject, String body, String existingMailId) throws IOException {
        if (!Validator.isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid username");
        }
        String mailId = (existingMailId != null && !existingMailId.trim().isEmpty())
                ? existingMailId.trim()
                : mailRepository.getNextMailId();

        MailItem draft = new MailItem(mailId, username, recipient, subject, body);
        draft.setFolder(MailFolder.DRAFTS);
        draft.setReadState(true);
        mailRepository.saveMail(username, Protocol.FOLDER_DRAFTS, draft);
        return mailId;
    }

    public synchronized List<MailItem> listEmails(String username, String folder) {
        if (!Validator.isValidUsername(username) || !Validator.isValidFolder(folder)) {
            return List.of();
        }
        return mailRepository.listMails(username, folder);
    }

    public synchronized MailItem readEmail(String username, String folder, String mailId) {
        if (!Validator.isValidUsername(username) || !Validator.isValidFolder(folder) || !Validator.isValidMailId(mailId)) {
            return null;
        }

        MailItem mail = mailRepository.getMail(username, folder, mailId);
        if (mail != null && !mail.isReadState() && Protocol.FOLDER_INBOX.equalsIgnoreCase(folder)) {
            mailRepository.markAsRead(username, folder, mailId, true);
            mail.setReadState(true);
        }
        return mail;
    }

    public synchronized boolean markRead(String username, String folder, String mailId, boolean read) {
        if (!Validator.isValidUsername(username) || !Validator.isValidFolder(folder) || !Validator.isValidMailId(mailId)) {
            return false;
        }
        return mailRepository.markAsRead(username, folder, mailId, read);
    }

    public synchronized boolean moveMail(String username, String srcFolder, String dstFolder, String mailId) {
        if (!Validator.isValidUsername(username) || !Validator.isValidFolder(srcFolder) || !Validator.isValidFolder(dstFolder) || !Validator.isValidMailId(mailId)) {
            return false;
        }
        return mailRepository.moveMail(username, srcFolder, dstFolder, mailId);
    }

    public synchronized boolean deleteMail(String username, String folder, String mailId) {
        if (!Validator.isValidUsername(username) || !Validator.isValidFolder(folder) || !Validator.isValidMailId(mailId)) {
            return false;
        }

        if (Protocol.FOLDER_TRASH.equalsIgnoreCase(folder)) {
            return mailRepository.deletePermanently(username, folder, mailId);
        } else {
            return mailRepository.moveMail(username, folder, Protocol.FOLDER_TRASH, mailId);
        }
    }

    public synchronized int countUnreadMails(String username, String folder) {
        if (!Validator.isValidUsername(username) || !Validator.isValidFolder(folder)) {
            return 0;
        }
        return mailRepository.countUnreadMails(username, folder);
    }

    public synchronized int countAllMails() {
        return mailRepository.countAllMails();
    }

    public MailRepository getMailRepository() {
        return mailRepository;
    }

    public synchronized List<MailItem> listAccountRootMails(String username) {
        if (!Validator.isValidUsername(username)) {
            return List.of();
        }
        return mailRepository.listAccountRootMails(username);
    }

    public synchronized boolean deleteAccountRootMail(String username, String mailId) {
        if (!Validator.isValidUsername(username) || !Validator.isValidMailId(mailId)) {
            return false;
        }
        return mailRepository.deleteAccountRootMail(username, mailId);
    }
}
