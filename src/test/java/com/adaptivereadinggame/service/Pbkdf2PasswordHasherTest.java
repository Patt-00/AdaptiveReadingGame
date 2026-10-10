package com.adaptivereadinggame.service;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Pbkdf2PasswordHasherTest {
    private static final Pbkdf2PasswordHasher HASHER = new Pbkdf2PasswordHasher();
    private static String verifier;

    @BeforeAll static void encodeOnce() {
        verifier = HASHER.encode("  correct password  ".toCharArray());
    }

    @Test void correctAndWrongPasswordsAreCheckedWithoutChangingInput() {
        char[] password = "  correct password  ".toCharArray();
        char[] original = password.clone();
        assertTrue(HASHER.matches(password, verifier));
        assertFalse(HASHER.matches("correct password".toCharArray(), verifier));
        assertArrayEquals(original, password);
        assertFalse(verifier.contains("correct password"));
        assertTrue(verifier.startsWith("pbkdf2-sha256$v1$600000$"));
        String[] parts = verifier.split("\\$");
        assertEquals(16, Base64.getDecoder().decode(parts[3]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[4]).length);
    }

    @Test void eachEncodeGetsAnIndependentRandomSaltAndPreservesPassword() {
        char[] password = "  correct password  ".toCharArray();
        char[] original = password.clone();
        String second = HASHER.encode(password);
        assertNotEquals(verifier, second);
        assertNotEquals(verifier.split("\\$")[3], second.split("\\$")[3]);
        assertArrayEquals(original, password);
    }

    @Test void malformedUnsupportedAndUnboundedVerifiersAreRejected() {
        String salt = verifier.split("\\$")[3];
        String hash = verifier.split("\\$")[4];
        for (String malformed : List.of("", "plaintext", "pbkdf2-sha256$v2$600000$" + salt + "$" + hash,
                "pbkdf2-sha256$v1$1$" + salt + "$" + hash,
                "pbkdf2-sha256$v1$1000001$" + salt + "$" + hash,
                "pbkdf2-sha256$v1$2147483647$" + salt + "$" + hash,
                "pbkdf2-sha256$v1$0600000$" + salt + "$" + hash,
                "pbkdf2-sha256$v1$-600000$" + salt + "$" + hash,
                "pbkdf2-sha256$v1$600000$invalid!$" + hash,
                "pbkdf2-sha256$v1$600000$" + salt + "$short",
                "pbkdf2-sha256$v1$600000$" + salt.replace("=", "") + "$" + hash,
                verifier + "$extra", "x".repeat(1000))) {
            assertFalse(HASHER.matches("password".toCharArray(), malformed), malformed);
        }
        assertFalse(HASHER.matches(null, verifier));
        assertFalse(HASHER.matches(new char[0], verifier));
        assertFalse(HASHER.matches("password".toCharArray(), null));
    }

    @Test void emptyOrNullPasswordCannotBeEncoded() {
        assertThrows(IllegalArgumentException.class, () -> HASHER.encode(null));
        assertThrows(IllegalArgumentException.class, () -> HASHER.encode(new char[0]));
    }
}
