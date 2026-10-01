package org.example.mail.server.storage;

import org.example.mail.common.Validator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class AccountRepository {

    private final Path rootPath;

    public AccountRepository(String dataPath) {
        this.rootPath = Paths.get(dataPath, "accounts");
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize accounts directory: " + rootPath, e);
        }
    }

    private Path getUserDir(String username) {
        if (!Validator.isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid username format: " + username);
        }
        return rootPath.resolve(username);
    }

    private Path getAccountFile(String username) {
        return getUserDir(username).resolve("account.txt");
    }

    public synchronized boolean exists(String username) {
        if (!Validator.isValidUsername(username)) return false;
        Path file = getAccountFile(username);
        return Files.isRegularFile(file);
    }

    public synchronized boolean saveAccount(String username, String password) throws IOException {
        if (!Validator.isValidUsername(username) || !Validator.isValidPassword(password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        Path userDir = getUserDir(username);
        if (Files.exists(userDir)) {
            return false; // Account already exists
        }

        Files.createDirectories(userDir);

        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content = "Username: " + username + "\n" +
                         "Password: " + password + "\n" +
                         "CreatedAt: " + createdAt + "\n" +
                         "Status: ACTIVE\n";

        Path targetFile = getAccountFile(username);
        Path tmpFile = userDir.resolve("account.tmp");

        Files.writeString(tmpFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // Create new_email.txt in the user's folder
        Path welcomeFile = userDir.resolve("new_email.txt");
        String welcomeMsg = "Thank you for using this service. we hope that you will feel comfortabl........";
        Files.writeString(welcomeFile, welcomeMsg, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return true;
    }

    public synchronized List<String> listAccountFiles(String username) {
        List<String> fileNames = new ArrayList<>();
        if (!exists(username)) return fileNames;
        try {
            Path userDir = getUserDir(username);
            try (var s = Files.list(userDir)) {
                s.filter(Files::isRegularFile)
                 .map(p -> p.getFileName().toString())
                 .filter(n -> !n.equals("account.txt") && !n.endsWith(".tmp"))
                 .forEach(fileNames::add);
            }
            Path inboxDir = userDir.resolve("inbox");
            if (Files.exists(inboxDir)) {
                try (var s = Files.list(inboxDir)) {
                    s.filter(Files::isRegularFile)
                     .map(p -> p.getFileName().toString())
                     .filter(n -> !n.endsWith(".tmp") && !fileNames.contains(n))
                     .forEach(fileNames::add);
                }
            }
        } catch (IOException ignored) {}
        Collections.sort(fileNames);
        return fileNames;
    }

    public synchronized boolean verifyPassword(String username, String password) {
        if (!exists(username)) return false;
        try {
            Path file = getAccountFile(username);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("Password: ")) {
                    String stored = line.substring("Password: ".length()).trim();
                    return stored.equals(password);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading account file for " + username + ": " + e.getMessage());
        }
        return false;
    }

    public synchronized String getPassword(String username) {
        if (!exists(username)) return null;
        try {
            Path file = getAccountFile(username);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("Password: ")) {
                    return line.substring("Password: ".length()).trim();
                }
            }
        } catch (IOException e) {
            System.err.println("Error getting password for " + username + ": " + e.getMessage());
        }
        return null;
    }

    public synchronized List<Map<String, String>> getAllAccounts() {
        List<Map<String, String>> accounts = new ArrayList<>();
        if (!Files.exists(rootPath)) return accounts;

        try (var stream = Files.list(rootPath)) {
            List<Path> userDirs = stream.filter(Files::isDirectory).collect(Collectors.toList());
            for (Path dir : userDirs) {
                Path accFile = dir.resolve("account.txt");
                if (Files.exists(accFile)) {
                    Map<String, String> map = new HashMap<>();
                    map.put("username", dir.getFileName().toString());
                    List<String> lines = Files.readAllLines(accFile, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            String k = line.substring(0, colon).trim().toLowerCase();
                            String v = line.substring(colon + 1).trim();
                            map.put(k, v);
                        }
                    }
                    accounts.add(map);
                }
            }
        } catch (IOException e) {
            System.err.println("Error listing accounts: " + e.getMessage());
        }
        return accounts;
    }

    public synchronized int countAccounts() {
        if (!Files.exists(rootPath)) return 0;
        try (var stream = Files.list(rootPath)) {
            return (int) stream.filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("account.txt")))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
}
