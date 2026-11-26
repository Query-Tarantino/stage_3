package org.ulpgc.tarantino.common.datalake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A document's {@code sha256} (SPEC §4.6): the SHA-256, in lowercase hexadecimal, of the UTF-8 bytes of its header
 * followed by those of its body. The crawler records it and every copy read or received is checked against it.
 */
public final class DocumentDigest {

    private static final String ALGORITHM = "SHA-256";

    private DocumentDigest() {
    }

    public static String of(String header, String body) {
        MessageDigest digest = sha256();
        digest.update(header.getBytes(StandardCharsets.UTF_8));
        digest.update(body.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has " + ALGORITHM, e);
        }
    }
}
