package com.terrascope.platform.repository;

import com.terrascope.platform.security.Role;
import com.terrascope.platform.security.UserAccount;
import com.terrascope.platform.security.UserStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository implements UserStore {

    private static final String COLUMNS = """
            id,
            username,
            display_name,
            role,
            password_hash,
            enabled,
            token_version,
            created_at,
            last_login_at
            """;

    private final JdbcClient jdbcClient;

    private final RowMapper<UserAccount> rowMapper =
            this::mapRow;

    public UserRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<UserAccount> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM app_users
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        id
                )
                .query(
                        rowMapper
                )
                .optional();
    }

    @Override
    public Optional<UserAccount> findByUsername(
            String username
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM app_users
                        WHERE username = :username
                        """)
                .param(
                        "username",
                        username
                )
                .query(
                        rowMapper
                )
                .optional();
    }

    @Override
    public List<UserAccount> findAll() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM app_users
                        ORDER BY
                            enabled DESC,
                            username ASC
                        """)
                .query(
                        rowMapper
                )
                .list();
    }

    @Override
    public long count() {
        return jdbcClient
                .sql("""
                        SELECT COUNT(*) AS total
                        FROM app_users
                        """)
                .query(
                        (resultSet, rowNumber) ->
                                resultSet.getLong("total")
                )
                .single();
    }

    @Override
    public long countEnabledAdminsExcluding(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT COUNT(*) AS total
                        FROM app_users
                        WHERE role = 'ADMIN'
                          AND enabled = TRUE
                          AND id <> :id
                        """)
                .param(
                        "id",
                        id
                )
                .query(
                        (resultSet, rowNumber) ->
                                resultSet.getLong("total")
                )
                .single();
    }

    @Override
    public void lockEnabledAdmins() {
        jdbcClient
                .sql("""
                        SELECT id
                        FROM app_users
                        WHERE role = 'ADMIN'
                          AND enabled = TRUE
                        FOR UPDATE
                        """)
                .query(
                        (resultSet, rowNumber) ->
                                resultSet.getObject(
                                        "id",
                                        UUID.class
                                )
                )
                .list();
    }

    @Override
    public UserAccount insert(
            UserAccount account
    ) {
        return jdbcClient
                .sql("""
                        INSERT INTO app_users (
                            id,
                            username,
                            display_name,
                            role,
                            password_hash,
                            enabled,
                            token_version,
                            created_at
                        )
                        VALUES (
                            :id,
                            :username,
                            :displayName,
                            :role,
                            :passwordHash,
                            :enabled,
                            :tokenVersion,
                            :createdAt
                        )
                        RETURNING
                        """
                        + COLUMNS)
                .param(
                        "id",
                        account.id()
                )
                .param(
                        "username",
                        account.username()
                )
                .param(
                        "displayName",
                        account.displayName()
                )
                .param(
                        "role",
                        account.role().name()
                )
                .param(
                        "passwordHash",
                        account.passwordHash()
                )
                .param(
                        "enabled",
                        account.enabled()
                )
                .param(
                        "tokenVersion",
                        account.tokenVersion()
                )
                .param(
                        "createdAt",
                        account
                                .createdAt()
                                .atOffset(
                                        ZoneOffset.UTC
                                )
                )
                .query(
                        rowMapper
                )
                .single();
    }

    @Override
    public Optional<UserAccount> updateProfile(
            UUID id,
            String displayName,
            Role role,
            boolean enabled,
            boolean revokeTokens
    ) {
        return jdbcClient
                .sql("""
                        UPDATE app_users
                        SET
                            display_name = :displayName,
                            role = :role,
                            enabled = :enabled,
                            token_version =
                                token_version
                                + CASE WHEN :revoke THEN 1 ELSE 0 END
                        WHERE id = :id
                        RETURNING
                        """
                        + COLUMNS)
                .param(
                        "displayName",
                        displayName
                )
                .param(
                        "role",
                        role.name()
                )
                .param(
                        "enabled",
                        enabled
                )
                .param(
                        "revoke",
                        revokeTokens
                )
                .param(
                        "id",
                        id
                )
                .query(
                        rowMapper
                )
                .optional();
    }

    @Override
    public Optional<UserAccount> updatePassword(
            UUID id,
            String passwordHash
    ) {
        return jdbcClient
                .sql("""
                        UPDATE app_users
                        SET
                            password_hash = :passwordHash,
                            token_version = token_version + 1
                        WHERE id = :id
                        RETURNING
                        """
                        + COLUMNS)
                .param(
                        "passwordHash",
                        passwordHash
                )
                .param(
                        "id",
                        id
                )
                .query(
                        rowMapper
                )
                .optional();
    }

    @Override
    public void recordLogin(
            UUID id,
            Instant at
    ) {
        jdbcClient
                .sql("""
                        UPDATE app_users
                        SET last_login_at = :at
                        WHERE id = :id
                        """)
                .param(
                        "at",
                        at.atOffset(
                                ZoneOffset.UTC
                        )
                )
                .param(
                        "id",
                        id
                )
                .update();
    }

    private UserAccount mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime createdAt =
                resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                );

        OffsetDateTime lastLoginAt =
                resultSet.getObject(
                        "last_login_at",
                        OffsetDateTime.class
                );

        return new UserAccount(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getString(
                        "username"
                ),
                resultSet.getString(
                        "display_name"
                ),
                Role.valueOf(
                        resultSet.getString(
                                "role"
                        )
                ),
                resultSet.getString(
                        "password_hash"
                ),
                resultSet.getBoolean(
                        "enabled"
                ),
                resultSet.getInt(
                        "token_version"
                ),
                createdAt.toInstant(),
                lastLoginAt == null
                        ? null
                        : lastLoginAt.toInstant()
        );
    }
}
