////// common utilities

// generate random ID with length 4*len characters
/**
 *
 * @private
 * @param {number} len
 */
export function _guid(len) {
  const s4 = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
  return Array.from({ length: len }, s4).join('');
}


/**
 * A simple and lightweight implementation of UUIDv7.
 *
 * UUIDv7 is a time-based UUID version that is lexicographically sortable and
 * is designed to be used as a database key.
 *
 * The structure is as follows:
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
 *
 * - unix_ts_ms (48 bits): Unix timestamp in milliseconds.
 * - ver (4 bits): Version, set to 7.
 * - rand_a (12 bits): Random data.
 * - var (2 bits): Variant, set to '10'.
 * - rand_b (62 bits): Random data.
 */
export class UUID7 {
    /**
     * Private constructor to create a UUID7 from a byte array.
     * @param {Uint8Array} bytes The 16 bytes of the UUID.
     */
    constructor(bytes) {
        if (bytes.length !== 16) {
            throw new Error('UUID7 must be constructed with a 16-byte array.');
        }
        this.bytes = bytes;
    }

    /**
     * Generates a new UUIDv7.
     * @returns {UUID7} A new UUIDv7 instance.
     */
    static generate() {
        const bytes = new Uint8Array(16);
        const randomBytes = crypto.getRandomValues(new Uint8Array(10));
        const timestamp = Date.now();

        // Set the 48-bit timestamp
        // JavaScript numbers are 64-bit floats, but bitwise operations treat them
        // as 32-bit signed integers. We need to handle the 48-bit timestamp carefully.
        const timestampHi = Math.floor(timestamp / 2 ** 16);
        const timestampLo = timestamp % 2 ** 16;

        bytes[0] = (timestampHi >> 24) & 0xff;
        bytes[1] = (timestampHi >> 16) & 0xff;
        bytes[2] = (timestampHi >> 8) & 0xff;
        bytes[3] = timestampHi & 0xff;
        bytes[4] = (timestampLo >> 8) & 0xff;
        bytes[5] = timestampLo & 0xff;

        // Copy the 10 random bytes
        bytes.set(randomBytes, 6);

        // Set the 4-bit version (0111) in byte 6
        bytes[6] = (bytes[6] & 0x0f) | 0x70;

        // Set the 2-bit variant (10) in byte 8
        bytes[8] = (bytes[8] & 0x3f) | 0x80;

        return new UUID7(bytes);
    }

    /**
     * Extracts the timestamp from the UUID.
     * @returns {number} The Unix timestamp in milliseconds.
     */
    getTimestamp() {
        let timestamp = 0;
        timestamp = this.bytes[0] * 2 ** 40;
        timestamp += this.bytes[1] * 2 ** 32;
        timestamp += this.bytes[2] * 2 ** 24;
        timestamp += this.bytes[3] * 2 ** 16;
        timestamp += this.bytes[4] * 2 ** 8;
        timestamp += this.bytes[5];
        return timestamp;
    }

    /**
     * Formats the UUID into the standard string representation.
     * @returns {string} The UUID string.
     */
    toString() {
        let result = '';
        for (let i = 0; i < 16; i++) {
            result += this.bytes[i].toString(16).padStart(2, '0');
            if (i === 3 || i === 5 || i === 7 || i === 9) {
                result += '-';
            }
        }
        return result;
    }
}