package com.terrascope.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSeederTest {

    private static AdminSeeder seeder(
            SecurityFixture fixture
    ) {
        return new AdminSeeder(
                fixture.settings,
                fixture.store,
                fixture.admins
        );
    }

    @Test
    void createsTheFirstAdministratorFromTheEnvironment() {
        SecurityFixture fixture =
                new SecurityFixture(true, "Root.Admin", "a-long-enough-password");

        seeder(fixture).seed();

        assertEquals(1, fixture.store.accounts.size());

        UserAccount created =
                fixture.store.accounts.values().iterator().next();

        assertEquals("root.admin", created.username());
        assertEquals(Role.ADMIN, created.role());
        assertTrue(fixture.hasher.verify("a-long-enough-password", created.passwordHash()));
    }

    @Test
    void leavesExistingUsersAlone() {
        SecurityFixture fixture =
                new SecurityFixture(true, "root.admin", "a-long-enough-password");

        fixture.addUser("someone", Role.VIEWER, "another-long-password");

        seeder(fixture).seed();

        assertEquals(1, fixture.store.accounts.size());
    }

    @Test
    void doesNothingWhenSecurityIsOff() {
        SecurityFixture fixture =
                new SecurityFixture(false, "root.admin", "a-long-enough-password");

        seeder(fixture).seed();

        assertEquals(0, fixture.store.accounts.size());
    }

    @Test
    void doesNothingWithoutCredentials() {
        SecurityFixture fixture =
                new SecurityFixture(true, "", "");

        seeder(fixture).seed();

        assertEquals(0, fixture.store.accounts.size());
    }

    @Test
    void survivesAWeakSeedPassword() {
        SecurityFixture fixture =
                new SecurityFixture(true, "root.admin", "short");

        seeder(fixture).seed();

        assertEquals(0, fixture.store.accounts.size());
    }
}
