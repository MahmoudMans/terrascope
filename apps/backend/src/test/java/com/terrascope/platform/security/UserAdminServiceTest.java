package com.terrascope.platform.security;

import com.terrascope.platform.api.model.UserSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAdminServiceTest {

    private static final String PASSWORD =
            "a-long-enough-password";

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
    void createsUsersWithNormalisedNamesAndHashedPasswords() {
        UserSummary created =
                fixture.admins.create(" Dana.Smith ", "", "operator", PASSWORD);

        assertEquals("dana.smith", created.username());
        assertEquals("dana.smith", created.displayName());
        assertEquals(Role.OPERATOR, created.role());
        assertTrue(created.enabled());

        UserAccount stored =
                fixture.store.accounts.get(created.id());

        assertFalse(stored.passwordHash().contains(PASSWORD));
        assertTrue(fixture.hasher.verify(PASSWORD, stored.passwordHash()));
        assertEquals(0, stored.tokenVersion());
    }

    @Test
    void keepsACustomDisplayName() {
        UserSummary created =
                fixture.admins.create("dana", "  Dana Smith  ", "VIEWER", PASSWORD);

        assertEquals("Dana Smith", created.displayName());
    }

    @Test
    void rejectsBadInput() {
        assertEquals("INVALID_USERNAME", failure(() -> fixture.admins.create("x", "", "VIEWER", PASSWORD)).code());
        assertEquals("INVALID_USERNAME", failure(() -> fixture.admins.create("has space", "", "VIEWER", PASSWORD)).code());
        assertEquals("INVALID_USERNAME", failure(() -> fixture.admins.create(null, "", "VIEWER", PASSWORD)).code());
        assertEquals("INVALID_ROLE", failure(() -> fixture.admins.create("dana", "", "root", PASSWORD)).code());
        assertEquals("INVALID_ROLE", failure(() -> fixture.admins.create("dana", "", null, PASSWORD)).code());
        assertEquals("WEAK_PASSWORD", failure(() -> fixture.admins.create("dana", "", "VIEWER", "short")).code());
        assertEquals("WEAK_PASSWORD", failure(() -> fixture.admins.create("dana-the-admin", "", "VIEWER", "DANA-THE-ADMIN")).code());
        assertEquals("INVALID_DISPLAY_NAME", failure(() -> fixture.admins.create("dana", "bad\u0007name", "VIEWER", PASSWORD)).code());
        assertEquals("INVALID_DISPLAY_NAME", failure(() -> fixture.admins.create("dana", "n".repeat(121), "VIEWER", PASSWORD)).code());
        assertEquals(0, fixture.store.accounts.size());
    }

    @Test
    void rejectsDuplicateUsernamesIgnoringCase() {
        fixture.admins.create("dana", "", "VIEWER", PASSWORD);

        AuthException duplicate =
                failure(() -> fixture.admins.create("DANA", "", "VIEWER", PASSWORD));

        assertEquals("USERNAME_TAKEN", duplicate.code());
        assertEquals(409, duplicate.status().value());
    }

    @Test
    void createsTheInitialAdministrator() {
        UserSummary created =
                fixture.admins.createInitialAdmin("root.admin", PASSWORD);

        assertEquals(Role.ADMIN, created.role());
    }

    @Test
    void listsUsersByNameWithoutSecrets() {
        fixture.admins.create("zoe", "", "VIEWER", PASSWORD);
        fixture.admins.create("adam", "", "ADMIN", PASSWORD);

        List<UserSummary> users =
                fixture.admins.list();

        assertEquals("adam", users.get(0).username());
        assertEquals("zoe", users.get(1).username());
    }

    @Test
    void renamingDoesNotRevokeSessions() {
        UserAccount boss =
                fixture.addUser("boss", Role.ADMIN, PASSWORD);

        UserAccount dana =
                fixture.addUser("dana", Role.VIEWER, PASSWORD);

        UserSummary updated =
                fixture.admins.update(fixture.actorOf(boss), dana.id(), "Dana S.", null, null);

        assertEquals("Dana S.", updated.displayName());
        assertEquals(0, fixture.store.accounts.get(dana.id()).tokenVersion());
    }

    @Test
    void changingRoleOrDisablingRevokesSessionsButEnablingDoesNot() {
        UserAccount boss =
                fixture.addUser("boss", Role.ADMIN, PASSWORD);

        UserAccount dana =
                fixture.addUser("dana", Role.VIEWER, PASSWORD);

        fixture.admins.update(fixture.actorOf(boss), dana.id(), null, "OPERATOR", null);

        assertEquals(1, fixture.store.accounts.get(dana.id()).tokenVersion());

        fixture.admins.update(fixture.actorOf(boss), dana.id(), null, null, false);

        assertEquals(2, fixture.store.accounts.get(dana.id()).tokenVersion());

        fixture.admins.update(fixture.actorOf(boss), dana.id(), null, null, true);

        assertEquals(2, fixture.store.accounts.get(dana.id()).tokenVersion());
        assertTrue(fixture.store.accounts.get(dana.id()).enabled());
    }

    @Test
    void stopsAdministratorsChangingThemselves() {
        UserAccount boss =
                fixture.addUser("boss", Role.ADMIN, PASSWORD);

        AuthException blocked =
                failure(() -> fixture.admins.update(fixture.actorOf(boss), boss.id(), null, "VIEWER", null));

        assertEquals("SELF_CHANGE", blocked.code());

        assertEquals(
                "SELF_CHANGE",
                failure(() -> fixture.admins.update(fixture.actorOf(boss), boss.id(), null, null, false)).code()
        );

        fixture.admins.update(fixture.actorOf(boss), boss.id(), "The Boss", null, null);
    }

    @Test
    void keepsAtLeastOneEnabledAdministrator() {
        UserAccount onlyAdmin =
                fixture.addUser("boss", Role.ADMIN, PASSWORD);

        AuthenticatedUser staleActor =
                new AuthenticatedUser(UUID.randomUUID(), "ghost", "ghost", Role.ADMIN);

        AuthException blocked =
                failure(() -> fixture.admins.update(staleActor, onlyAdmin.id(), null, "VIEWER", null));

        assertEquals("LAST_ADMIN", blocked.code());
        assertEquals(1, fixture.store.lockCalls);
        assertEquals(Role.ADMIN, fixture.store.accounts.get(onlyAdmin.id()).role());
    }

    @Test
    void allowsDemotingAnAdministratorWhenAnotherRemains() {
        UserAccount boss =
                fixture.addUser("boss", Role.ADMIN, PASSWORD);

        UserAccount second =
                fixture.addUser("second", Role.ADMIN, PASSWORD);

        UserSummary updated =
                fixture.admins.update(fixture.actorOf(boss), second.id(), null, "OPERATOR", null);

        assertEquals(Role.OPERATOR, updated.role());
    }

    @Test
    void reportsUnknownUsersAndBadRoles() {
        UserAccount boss =
                fixture.addUser("boss", Role.ADMIN, PASSWORD);

        UserAccount dana =
                fixture.addUser("dana", Role.VIEWER, PASSWORD);

        assertEquals(
                "USER_NOT_FOUND",
                failure(() -> fixture.admins.update(fixture.actorOf(boss), UUID.randomUUID(), null, null, null)).code()
        );

        assertEquals(
                "INVALID_ROLE",
                failure(() -> fixture.admins.update(fixture.actorOf(boss), dana.id(), null, "king", null)).code()
        );

        assertEquals(
                "USER_NOT_FOUND",
                failure(() -> fixture.admins.resetPassword(UUID.randomUUID(), PASSWORD)).code()
        );
    }

    @Test
    void resetsPasswordsAndRevokesSessions() {
        UserAccount dana =
                fixture.addUser("dana", Role.VIEWER, PASSWORD);

        String token =
                fixture.auth.login("dana", PASSWORD, "10.0.0.1").token();

        fixture.admins.resetPassword(dana.id(), "a replacement password");

        assertFalse(fixture.auth.authenticate(token).isPresent());

        assertNotNull(fixture.auth.login("dana", "a replacement password", "10.0.0.1"));

        assertEquals(
                "WEAK_PASSWORD",
                failure(() -> fixture.admins.resetPassword(dana.id(), "short")).code()
        );
    }

    @Test
    void refusesEverythingWhenSecurityIsOff() {
        SecurityFixture off =
                new SecurityFixture(false);

        assertEquals("SECURITY_DISABLED", failure(off.admins::list).code());
        assertEquals("SECURITY_DISABLED", failure(() -> off.admins.create("dana", "", "VIEWER", PASSWORD)).code());
        assertEquals("SECURITY_DISABLED", failure(() -> off.admins.resetPassword(UUID.randomUUID(), PASSWORD)).code());
    }
}
