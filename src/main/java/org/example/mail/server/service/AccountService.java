package org.example.mail.server.service;

import org.example.mail.common.Validator;
import org.example.mail.server.storage.AccountRepository;
import org.example.mail.server.storage.MailRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AccountService {

    private final AccountRepository accountRepository;
    private final MailRepository mailRepository;

    public AccountService(AccountRepository accountRepository, MailRepository mailRepository) {
        this.accountRepository = accountRepository;
        this.mailRepository = mailRepository;
    }

    public synchronized boolean register(String username, String password) throws IOException {
        if (!Validator.isValidUsername(username)) {
            throw new IllegalArgumentException("Username must be 3-30 characters (letters, numbers, underscore)");
        }
        if (!Validator.isValidPassword(password)) {
            throw new IllegalArgumentException("Password cannot be empty (max 100 chars)");
        }

        if (accountRepository.exists(username)) {
            return false;
        }

        boolean saved = accountRepository.saveAccount(username, password);
        if (saved) {
            mailRepository.initUserMailboxes(username);
        }
        return saved;
    }

    public boolean userExists(String username) {
        return accountRepository.exists(username);
    }

    public int countAccounts() {
        return accountRepository.countAccounts();
    }

    public List<String> getAccountFiles(String username) {
        return accountRepository.listAccountFiles(username);
    }

    public List<Map<String, String>> getAllAccounts() {
        return accountRepository.getAllAccounts();
    }
}
