package org.arl.fjage;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

/**
 * A simple and lightweight implementation of UUIDv7 that is compatible with Java 8.
 *
 * <p>UUIDv7 is a time-based UUID version that is lexicographically sortable and contains a
 * timestamp.
 *
 * <p>The structure of a UUIDv7 is as follows:
 *
 * <pre>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           unix_ts_ms                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          unix_ts_ms           |  ver  |      rand_a           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var|                        rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <ul>
 * <li><b>unix_ts_ms (48 bits):</b> The Unix timestamp in milliseconds.
 * <li><b>ver (4 bits):</b> The version number, which is 7.
 * <li><b>rand_a (12 bits):</b> Random bits.
 * <li><b>var (2 bits):</b> The variant, which is '10'.
 * <li><b>rand_b (62 bits):</b> More random bits.
 * </ul>
 */
public final class UUID7 implements Comparable<UUID7>, Serializable {

  private static final long serialVersionUID = -1234567890123456789L;
  private final long mostSigBits;
  private final long leastSigBits;

  private static final class Holder {
    static final SecureRandom numberGenerator = new SecureRandom();
  }

  /**
   * Private constructor to create a UUID7 from the most and least significant bits.
   *
   * @param mostSigBits The most significant 64 bits of the UUID.
   * @param leastSigBits The least significant 64 bits of the UUID.
   */
  private UUID7(long mostSigBits, long leastSigBits) {
    this.mostSigBits = mostSigBits;
    this.leastSigBits = leastSigBits;
  }

  /**
   * Generates a new UUIDv7.
   *
   * @return A new UUIDv7 instance.
   */
  public static UUID7 generate() {
    final long timestamp = System.currentTimeMillis();
    final byte[] randomBytes = new byte[10]; // 74 bits of random data
    Holder.numberGenerator.nextBytes(randomBytes);

    // 48-bit timestamp
    long mostSigBits = timestamp << 16;

    // 4-bit version and 12-bit rand_a
    mostSigBits |= 0x7000; // Version 7
    mostSigBits |= ((long) (randomBytes[0] & 0xFF)) << 4 | ((long) (randomBytes[1] & 0xF0) >> 4);


    // 2-bit variant and 62-bit rand_b
    long leastSigBits = 0x8000000000000000L; // Variant '10'
    leastSigBits |= ((long) (randomBytes[1] & 0x0F)) << 60;
    leastSigBits |= ((long) (randomBytes[2] & 0xFF)) << 52;
    leastSigBits |= ((long) (randomBytes[3] & 0xFF)) << 44;
    leastSigBits |= ((long) (randomBytes[4] & 0xFF)) << 36;
    leastSigBits |= ((long) (randomBytes[5] & 0xFF)) << 28;
    leastSigBits |= ((long) (randomBytes[6] & 0xFF)) << 20;
    leastSigBits |= ((long) (randomBytes[7] & 0xFF)) << 12;
    leastSigBits |= ((long) (randomBytes[8] & 0xFF)) << 4;
    leastSigBits |= ((long) (randomBytes[9] & 0xFF)) >> 4;


    return new UUID7(mostSigBits, leastSigBits);
  }

  /**
   * Creates a UUID7 from a standard java.util.UUID.
   *
   * @param uuid The UUID to convert.
   * @return A new UUID7 instance.
   */
  public static UUID7 fromUUID(UUID uuid) {
    return new UUID7(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
  }

  /**
   * Returns the timestamp embedded in this UUIDv7.
   *
   * @return The timestamp in milliseconds since the Unix epoch.
   */
  public long getTimestamp() {
    return mostSigBits >> 16;
  }

  /**
   * Converts this UUIDv7 to a standard java.util.UUID.
   *
   * @return A java.util.UUID representation of this UUIDv7.
   */
  public UUID toUUID() {
    return new UUID(this.mostSigBits, this.leastSigBits);
  }

  @Override
  public int compareTo(UUID7 o) {
    int result = Long.compare(this.mostSigBits, o.mostSigBits);
    if (result == 0) {
      result = Long.compare(this.leastSigBits, o.leastSigBits);
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UUID7 uuid7 = (UUID7) o;
    return mostSigBits == uuid7.mostSigBits && leastSigBits == uuid7.leastSigBits;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mostSigBits, leastSigBits);
  }

  @Override
  public String toString() {
    return toUUID().toString();
  }
}