package com.entain.betsafe.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for SHA-256 hashing — used for player_id hashing and row_hash computation.
 */
public final class HashUtil {

  private static final String SHA_256 = "SHA-256";

  private HashUtil() {
  }

  public static String sha256(String input) {
    if (input == null) {
      throw new IllegalArgumentException("Input must not be null");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance(SHA_256);
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Computes row_hash as SHA-256(event_id || event_type || player_id_hash || payload).
   */
  public static String computeRowHash(String eventId, String eventType,
      String playerIdHash, String payload) {
    String combined = eventId + eventType + playerIdHash + payload;
    return sha256(combined);
  }
}
