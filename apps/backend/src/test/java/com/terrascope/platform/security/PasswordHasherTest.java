package com.terrascope.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher =
            new PasswordHasher(1000);

    @Test
    void acceptsTheOriginalPasswordOnly() {
        String stored =
                hasher.hash("correct horse battery");

        assertTrue(
                hasher.verify("correct horse battery", stored)
        );

        assertFalse(
                hasher.verify("correct horse batterY", stored)
        );
    }

    @Test
    void saltsEveryHash() {
        String first =
                hasher.hash("same password value");

        String second =
                hasher.hash("same password value");

        assertFalse(first.equals(second));
        assertTrue(hasher.verify("same password value", first));
        assertTrue(hasher.verify("same password value", second));
    }

    @Test
    void storesTheSchemeAndIterationCount() {
        String stored =
                hasher.hash("a long enough password");

        String[] parts =
                stored.split("\\$");

        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals("1000", parts[1]);
    }

    @Test
    void verifiesHashesMadeWithAnotherIterationCount() {
        String stored =
                new PasswordHasher(2000)
                        .hash("portable password");

        assertTrue(
                hasher.verify("portable password", stored)
        );
    }

    @Test
    void rejectsMalformedStoredValues() {
        assertFalse(hasher.verify("password", null));
        assertFalse(hasher.verify("password", ""));
        assertFalse(hasher.verify("password", "plain-text"));
        assertFalse(hasher.verify("password", "pbkdf2-sha256$abc$AAAA$AAAA"));
        assertFalse(hasher.verify("password", "pbkdf2-sha256$1000$!!!$AAAA"));
        assertFalse(hasher.verify("password", "other$1000$AAAA$AAAA"));
        assertFalse(hasher.verify("password", "pbkdf2-sha256$1000$AAAA"));
    }

    @Test
    void rejectsIterationCountsOutsideTheSafeRange() {
        assertFalse(
                hasher.verify("password", "pbkdf2-sha256$10$AAAA$AAAA")
        );

        assertFalse(
                hasher.verify("password", "pbkdf2-sha256$900000000$AAAA$AAAA")
        );
    }

    @Test
    void neverAcceptsAnEmptyOrMissingPassword() {
        String stored =
                hasher.hash("a long enough password");

        assertFalse(hasher.verify("", stored));
        assertFalse(hasher.verify(null, stored));
    }

    @Test
    void dummyHashIsStableAndNeverMatchesRealPasswords() {
        String first =
                hasher.dummyHash();

        assertNotNull(first);
        assertEquals(first, hasher.dummyHash());
        assertFalse(hasher.verify("a long enough password", first));
    }
}
