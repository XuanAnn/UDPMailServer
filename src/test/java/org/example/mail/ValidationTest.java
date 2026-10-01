package org.example.mail;

import org.example.mail.common.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationTest {

    @Test
    public void testUsernameValidation() {
        assertTrue(Validator.isValidUsername("alice"));
        assertTrue(Validator.isValidUsername("bob_123"));
        assertTrue(Validator.isValidUsername("user_valid"));

        assertFalse(Validator.isValidUsername("ab")); // too short
        assertFalse(Validator.isValidUsername("user@domain.com")); // special characters not allowed in local username
        assertFalse(Validator.isValidUsername("user name")); // spaces
        assertFalse(Validator.isValidUsername("../../evil")); // path traversal attempt
        assertFalse(Validator.isValidUsername(null));
    }

    @Test
    public void testSafeFileName() {
        assertTrue(Validator.isSafeFileName("email_123.mail"));
        assertFalse(Validator.isSafeFileName("../../etc/passwd"));
        assertFalse(Validator.isSafeFileName("..\\windows\\system32"));
        assertFalse(Validator.isSafeFileName("c:/autoexec.bat"));
    }

    @Test
    public void testPortValidation() {
        assertTrue(Validator.isValidPort(5000));
        assertTrue(Validator.isValidPort(1024));
        assertTrue(Validator.isValidPort(65535));

        assertFalse(Validator.isValidPort(80)); // privileged
        assertFalse(Validator.isValidPort(70000)); // out of range
    }
}
