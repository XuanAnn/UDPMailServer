package org.example.mail.client.model;

public enum MailFolder {
    INBOX("Inbox"),
    SENT("Sent"),
    DRAFTS("Drafts"),
    TRASH("Trash");

    private final String displayName;

    MailFolder(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MailFolder fromString(String name) {
        if (name == null) return INBOX;
        for (MailFolder folder : values()) {
            if (folder.name().equalsIgnoreCase(name.trim())) {
                return folder;
            }
        }
        return INBOX;
    }
}
