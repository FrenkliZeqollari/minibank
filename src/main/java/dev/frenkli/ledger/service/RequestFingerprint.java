package dev.frenkli.ledger.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A SHA-256 hash of what a money-moving request asks for. Stored with the transaction, so a
 * retry with the same Idempotency-Key can be compared against the original request.
 *
 * <p>Built from the request's fields, not from the raw JSON: the same request can be written as
 * different JSON (field order, spaces), and must still get the same fingerprint.
 */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(String operation, Object... fields) {
        StringBuilder canonical = new StringBuilder(operation);
        for (Object field : fields) {
            canonical.append('|');
            if (field == null) {
                canonical.append('~');
            } else {
                // Length prefix: without it, ("a|b", "c") and ("a", "b|c") would produce the same
                // text, and null must differ from the string "~".
                String value = field.toString();
                canonical.append(value.length()).append(':').append(value);
            }
        }
        return sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
