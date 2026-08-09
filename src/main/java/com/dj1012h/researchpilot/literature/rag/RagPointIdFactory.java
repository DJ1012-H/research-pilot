package com.dj1012h.researchpilot.literature.rag;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/** Derives deterministic RFC 4122 UUIDv5 identifiers for projected paper segments. */
public final class RagPointIdFactory {

    public static final UUID NAMESPACE = UUID.fromString("74fbcd22-6592-5cd8-a606-29d5ad4e5e9f");

    private RagPointIdFactory() { }

    public static UUID create(long paperId, String embeddingVersion, RagSegmentType segmentType, int segmentIndex) {
        if (paperId < 1) {
            throw new IllegalArgumentException("paperId must be positive");
        }
        String version = canonicalField(embeddingVersion, "embeddingVersion");
        String type = canonicalField(
                Objects.requireNonNull(segmentType, "segmentType must not be null").name(),
                "segmentType");
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must not be negative");
        }
        String canonicalName = paperId + "|" + version + "|" + type + "|" + segmentIndex;
        return uuidV5(NAMESPACE, canonicalName.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalField(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be non-blank canonical text");
        }
        if (value.indexOf('|') >= 0) {
            throw new IllegalArgumentException(field + " must not contain the point-name separator");
        }
        return value;
    }

    private static UUID uuidV5(UUID namespace, byte[] name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer namespaceBytes = ByteBuffer.allocate(16)
                    .putLong(namespace.getMostSignificantBits())
                    .putLong(namespace.getLeastSignificantBits());
            sha1.update(namespaceBytes.array());
            byte[] hash = sha1.digest(name);
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer uuidBytes = ByteBuffer.wrap(hash);
            return new UUID(uuidBytes.getLong(), uuidBytes.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }
}
