package com.terrascope.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class AdminSeeder implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AdminSeeder.class
            );

    private final SecuritySettings settings;
    private final UserStore store;
    private final UserAdminService admins;

    public AdminSeeder(
            SecuritySettings settings,
            UserStore store,
            UserAdminService admins
    ) {
        this.settings = settings;
        this.store = store;
        this.admins = admins;
    }

    @Override
    public void run(
            ApplicationArguments arguments
    ) {
        seed();
    }

    void seed() {
        if (!settings.enabled()) {
            return;
        }

        if (store.count() > 0) {
            return;
        }

        if (
                settings.adminUsername().isBlank()
                || settings.adminPassword().isBlank()
        ) {
            log.warn(
                    "Security is enabled but no users exist. Set "
                            + "TERRASCOPE_SECURITY_ADMIN_USERNAME and "
                            + "TERRASCOPE_SECURITY_ADMIN_PASSWORD and restart "
                            + "to create the first administrator."
            );

            return;
        }

        try {
            admins.createInitialAdmin(
                    settings.adminUsername(),
                    settings.adminPassword()
            );

            log.info(
                    "Created the first administrator account '{}'.",
                    settings.adminUsername()
            );

        } catch (AuthException exception) {
            log.warn(
                    "Could not create the first administrator: {}",
                    exception.getMessage()
            );
        }
    }
}
