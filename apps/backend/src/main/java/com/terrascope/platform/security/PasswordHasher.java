package com.terrascope.platform.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public final class PasswordHasher {

    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String SCHEME = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int MIN_ITERATIONS = 1_000;
    private static final int MAX_ITERATIONS = 5_000_000;

    private final int iterations;

    private final SecureRandom random =
            new SecureRandom();

    private volatile String dummyHash;

    public PasswordHasher(
            int iterations
    ) {
        this.iterations =
                Math.min(
                        MAX_ITERATIONS,
                        Math.max(
                                MIN_ITERATIONS,
                                iterations
                        )
                );
    }

    public String hash(
            String password
    ) {
        byte[] salt =
                new byte[SALT_BYTES];

        random.nextBytes(salt);

        byte[] derived =
                derive(
                        password,
                        salt,
                        iterations
                );

        return SCHEME
                + "$"
                + iterations
                + "$"
                + encode(salt)
                + "$"
                + encode(derived);
    }

    public boolean verify(
            String password,
            String stored
    ) {
        if (
                password == null
                || password.isEmpty()
                || stored == null
        ) {
            return false;
        }

        String[] parts =
                stored.split(
                        "\\$",
                        -1
                );

        if (
                parts.length != 4
                || !SCHEME.equals(parts[0])
        ) {
            return false;
        }

        try {
            int storedIterations =
                    Integer.parseInt(parts[1]);

            if (
                    storedIterations < MIN_ITERATIONS
                    || storedIterations > MAX_ITERATIONS
            ) {
                return false;
            }

            byte[] salt =
                    Base64
                            .getDecoder()
                            .decode(parts[2]);

            byte[] expected =
                    Base64
                            .getDecoder()
                            .decode(parts[3]);

            byte[] actual =
                    derive(
                            password,
                            salt,
                            storedIterations
                    );

            return MessageDigest.isEqual(
                    expected,
                    actual
            );

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String dummyHash() {
        String current =
                dummyHash;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (dummyHash == null) {
                dummyHash =
                        hash("terrascope-timing-equaliser");
            }

            return dummyHash;
        }
    }

    private static byte[] derive(
            String password,
            byte[] salt,
            int iterations
    ) {
        PBEKeySpec specification =
                new PBEKeySpec(
                        password.toCharArray(),
                        salt,
                        iterations,
                        HASH_BITS
                );

        try {
            return SecretKeyFactory
                    .getInstance(ALGORITHM)
                    .generateSecret(specification)
                    .getEncoded();

        } catch (
                NoSuchAlgorithmException
                | InvalidKeySpecException exception
        ) {
            throw new IllegalStateException(
                    "PBKDF2 is not available on this JVM.",
                    exception
            );

        } finally {
            specification.clearPassword();
        }
    }

    private static String encode(
            byte[] value
    ) {
        return new String(
                Base64
                        .getEncoder()
                        .encode(value),
                StandardCharsets.US_ASCII
        );
    }
}
