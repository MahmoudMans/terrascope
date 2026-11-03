CREATE TABLE app_users (
    id UUID PRIMARY KEY,

    username VARCHAR(64) NOT NULL,

    display_name VARCHAR(120) NOT NULL,

    role VARCHAR(16) NOT NULL,

    password_hash VARCHAR(255) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    token_version INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    last_login_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_app_users_username
        UNIQUE (
            username
        ),

    CONSTRAINT ck_app_users_username
        CHECK (
            username = LOWER(username)
            AND CHAR_LENGTH(username) BETWEEN 3 AND 64
        ),

    CONSTRAINT ck_app_users_display_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(display_name)
            ) BETWEEN 1 AND 120
        ),

    CONSTRAINT ck_app_users_role
        CHECK (
            role IN (
                'VIEWER',
                'OPERATOR',
                'ADMIN'
            )
        )
);

CREATE INDEX idx_app_users_role_enabled
    ON app_users (
        role,
        enabled
    );
