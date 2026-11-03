package com.terrascope.platform.security;

import com.terrascope.platform.api.model.LoginResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final String PASSWORD =
            "alice-long-password!";

    private final SecurityFixture fixture =
            new SecurityFixture(true);

    private AuthException failure(
            Runnable action
    ) {
        return assertThrows(
                AuthException.class,
                action::run
        );
    }

    @Test
    void logsInAndIssuesAUsableToken() {
        UserAccount alice =
                fixture.addUser("alice", Role.OPERATOR, PASSWORD);

        LoginResponse response =
                fixture.auth.login(" Alice ", PASSWORD, "10.0.0.1");

        assertEquals("alice", response.user().username());
        assertEquals(Role.OPERATOR, response.user().role());
        assertNotNull(response.expiresAt());

        Optional<AuthenticatedUser> user =
                fixture.auth.authenticate(response.token());

        assertTrue(user.isPresent());
        assertEquals(alice.id(), user.get().id());
        assertEquals(Role.OPERATOR, user.get().role());
        assertNotNull(fixture.store.accounts.get(alice.id()).lastLoginAt());
    }

    @Test
    void treatsWrongPasswordsAndUnknownUsersTheSame() {
        fixture.addUser("alice", Role.VIEWER, PASSWORD);

        AuthException wrong =
                failure(() -> fixture.auth.login("alice", "not the password", "10.0.0.1"));

        AuthException unknown =
                failure(() -> fixture.auth.login("nobody", PASSWORD, "10.0.0.1"));

        AuthException malformed =
                failure(() -> fixture.auth.login("a b", PASSWORD, "10.0.0.1"));

        AuthException blank =
                failure(() -> fixture.auth.login("alice", "", "10.0.0.1"));

        assertEquals("INVALID_CREDENTIALS", wrong.code());
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertEquals(wrong.getMessage(), malformed.getMessage());
        assertEquals(wrong.getMessage(), blank.getMessage());
        assertEquals(401, wrong.status().value());
    }

    @Test
    void refusesDisabledAccountsEvenWithTheRightPassword() {
        UserAccount alice =
                fixture.addUser("alice", Role.VIEWER, PASSWORD);

        fixture.store.updateProfile(alice.id(), "alice", Role.VIEWER, false, true);

        assertEquals(
                "INVALID_CREDENTIALS",
                failure(() -> fixture.auth.login("alice", PASSWORD, "10.0.0.1")).code()
        );
    }

    @Test
    void locksAnAccountAfterFiveFailures() {
        fixture.addUser("alice", Role.VIEWER, PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(
                    "INVALID_CREDENTIALS",
                    failure(() -> fixture.auth.login("alice", "wrong password", "10.0.0.1")).code()
            );
        }

        AuthException locked =
                failure(() -> fixture.auth.login("alice", PASSWORD, "10.0.0.1"));

        assertEquals("TOO_MANY_ATTEMPTS", locked.code());
        assertEquals(429, locked.status().value());
        assertTrue(locked.getMessage().contains("15 minutes"));
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() {
        fixture.addUser("alice", Role.VIEWER, PASSWORD);

        for (int round = 0; round < 2; round++) {
            for (int attempt = 0; attempt < 4; attempt++) {
                failure(() -> fixture.auth.login("alice", "wrong password", "10.0.0.1"));
            }

            assertNotNull(fixture.auth.login("alice", PASSWORD, "10.0.0.1"));
        }
    }

    @Test
    void limitsFailuresPerClientAcrossUsernames() {
        AuthService limited =
                new AuthService(
                        fixture.settings,
                        fixture.store,
                        fixture.directory,
                        fixture.hasher,
                        fixture.tokens,
                        new LoginThrottle(100, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                        new LoginThrottle(2, Duration.ofMinutes(15), Duration.ofMinutes(15))
                );

        fixture.addUser("alice", Role.VIEWER, PASSWORD);

        failure(() -> limited.login("one", "wrong password", "10.0.0.9"));
        failure(() -> limited.login("two", "wrong password", "10.0.0.9"));

        assertEquals(
                "TOO_MANY_ATTEMPTS",
                failure(() -> limited.login("alice", PASSWORD, "10.0.0.9")).code()
        );

        assertNotNull(limited.login("alice", PASSWORD, "10.0.0.10"));
    }

    @Test
    void rejectsTokensAfterARoleChangeOrDisable() {
        UserAccount alice =
                fixture.addUser("alice", Role.VIEWER, PASSWORD);

        UserAccount boss =
                fixture.addUser("boss", Role.ADMIN, "boss-long-password!");

        String token =
                fixture.auth.login("alice", PASSWORD, "10.0.0.1").token();

        assertTrue(fixture.auth.authenticate(token).isPresent());

        fixture.admins.update(fixture.actorOf(boss), alice.id(), null, "OPERATOR", null);

        assertFalse(fixture.auth.authenticate(token).isPresent());

        String fresh =
                fixture.auth.login("alice", PASSWORD, "10.0.0.1").token();

        assertEquals(
                Role.OPERATOR,
                fixture.auth.authenticate(fresh).get().role()
        );

        fixture.admins.update(fixture.actorOf(boss), alice.id(), null, null, false);

        assertFalse(fixture.auth.authenticate(fresh).isPresent());
    }

    @Test
    void rejectsTokensForUsersThatNoLongerExist() {
        UserAccount alice =
                fixture.addUser("alice", Role.VIEWER, PASSWORD);

        String token =
                fixture.auth.login("alice", PASSWORD, "10.0.0.1").token();

        fixture.store.accounts.remove(alice.id());
        fixture.directory.invalidate();

        assertFalse(fixture.auth.authenticate(token).isPresent());
        assertFalse(fixture.auth.authenticate("garbage").isPresent());
        assertFalse(fixture.auth.authenticate(null).isPresent());
    }

    @Test
    void servesRepeatedAuthenticationFromTheDirectoryCache() {
        fixture.addUser("alice", Role.VIEWER, PASSWORD);

        String token =
                fixture.auth.login("alice", PASSWORD, "10.0.0.1").token();

        fixture.store.findByIdCalls = 0;

        for (int index = 0; index < 5; index++) {
            assertTrue(fixture.auth.authenticate(token).isPresent());
        }

        assertEquals(1, fixture.store.findByIdCalls);
    }

    @Test
    void changesThePasswordAndRevokesOldTokens() {
        fixture.addUser("alice", Role.VIEWER, PASSWORD);

        String oldToken =
                fixture.auth.login("alice", PASSWORD, "10.0.0.1").token();

        AuthenticatedUser actor =
                fixture.auth.authenticate(oldToken).get();

        LoginResponse changed =
                fixture.auth.changePassword(actor, PASSWORD, "a brand new password");

        assertFalse(fixture.auth.authenticate(oldToken).isPresent());
        assertTrue(fixture.auth.authenticate(changed.token()).isPresent());

        assertEquals(
                "INVALID_CREDENTIALS",
                failure(() -> fixture.auth.login("alice", PASSWORD, "10.0.0.1")).code()
        );

        assertNotNull(fixture.auth.login("alice", "a brand new password", "10.0.0.1"));
    }

    @Test
    void rejectsAWrongCurrentPasswordAndLocksAfterRepeatedGuesses() {
        UserAccount alice =
                fixture.addUser("alice", Role.VIEWER, PASSWORD);

        AuthenticatedUser actor =
                fixture.actorOf(alice);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(
                    "WRONG_PASSWORD",
                    failure(() -> fixture.auth.changePassword(actor, "nope", "a brand new password")).code()
            );
        }

        assertEquals(
                "TOO_MANY_ATTEMPTS",
                failure(() -> fixture.auth.changePassword(actor, PASSWORD, "a brand new password")).code()
        );
    }

    @Test
    void rejectsWeakOrUnchangedNewPasswords() {
        UserAccount alice =
                fixture.addUser("alice", Role.VIEWER, PASSWORD);

        AuthenticatedUser actor =
                fixture.actorOf(alice);

        assertEquals(
                "WEAK_PASSWORD",
                failure(() -> fixture.auth.changePassword(actor, PASSWORD, "short")).code()
        );

        assertEquals(
                "WEAK_PASSWORD",
                failure(() -> fixture.auth.changePassword(actor, PASSWORD, PASSWORD)).code()
        );
    }

    @Test
    void describesTheCurrentUser() {
        UserAccount alice =
                fixture.addUser("alice", Role.OPERATOR, PASSWORD);

        assertEquals(
                "alice",
                fixture.auth.currentUser(fixture.actorOf(alice)).username()
        );

        fixture.store.accounts.remove(alice.id());

        assertEquals(
                401,
                failure(() -> fixture.auth.currentUser(fixture.actorOf(alice))).status().value()
        );
    }

    @Test
    void doesNothingWhenSecurityIsDisabled() {
        SecurityFixture off =
                new SecurityFixture(false);

        off.addUser("alice", Role.VIEWER, PASSWORD);

        assertFalse(off.auth.enabled());

        assertEquals(
                "SECURITY_DISABLED",
                failure(() -> off.auth.login("alice", PASSWORD, "10.0.0.1")).code()
        );

        String token =
                off.tokens.issue(java.util.UUID.randomUUID(), 0).token();

        assertFalse(off.auth.authenticate(token).isPresent());
    }

    @Test
    void refusesToStartWithAWeakSecretWhenEnabled() {
        assertThrows(
                IllegalStateException.class,
                () -> new SecuritySettings(true, "too-short", "PT8H", "", "")
        );

        new SecuritySettings(false, "", "PT8H", "", "");
    }

    @Test
    void clampsTheTokenLifetime() {
        assertEquals(
                Duration.ofHours(8),
                new SecuritySettings(false, "", "garbage", "", "").tokenTtl()
        );

        assertEquals(
                Duration.ofMinutes(5),
                new SecuritySettings(false, "", "PT1S", "", "").tokenTtl()
        );

        assertEquals(
                Duration.ofDays(30),
                new SecuritySettings(false, "", "P365D", "", "").tokenTtl()
        );
    }
}
