import time
import os
import uuid
from functools import total_ordering

@total_ordering
class UUID7:
    """
    A simple and lightweight implementation of UUIDv7 in Python.

    UUIDv7 is a time-based UUID version that is lexicographically sortable and
    is designed to be used as a database key.

    The structure is as follows:
    - 48-bit Unix timestamp in milliseconds.
    - 4-bit version, set to 7.
    - 12 bits of random data.
    - 2-bit variant, set to '10'.
    - 62 bits of random data.
    """

    def __init__(self, bytes_val):
        """
        Private constructor to create a UUID7 from a 16-byte value.
        :param bytes_val: The 16 bytes of the UUID.
        """
        if len(bytes_val) != 16:
            raise ValueError("UUID7 must be constructed with a 16-byte value.")
        self._bytes = bytes_val

    @classmethod
    def generate(cls):
        """
        Generates a new UUIDv7.
        :return: A new UUID7 instance.
        """
        # Get the current time in milliseconds since the Unix epoch
        timestamp_ms = int(time.time() * 1000)

        # Generate 10 random bytes (80 bits)
        random_bytes = os.urandom(10)

        # Create a 16-byte array
        uuid_bytes = bytearray(16)

        # Set the 48-bit timestamp
        uuid_bytes[0:6] = timestamp_ms.to_bytes(6, 'big')

        # Set the 4-bit version (0111) and the first 4 random bits
        uuid_bytes[6] = 0x70 | (random_bytes[0] & 0x0F)

        # Set the rest of the first random part
        uuid_bytes[7] = random_bytes[1]

        # Set the 2-bit variant (10) and the first 6 random bits of the second part
        uuid_bytes[8] = 0x80 | (random_bytes[2] & 0x3F)

        # Set the remaining random bytes
        uuid_bytes[9:16] = random_bytes[3:]

        return cls(bytes(uuid_bytes))

    def get_timestamp(self):
        """
        Extracts the timestamp from the UUID.
        :return: The Unix timestamp in milliseconds.
        """
        return int.from_bytes(self._bytes[0:6], 'big')

    def to_uuid(self):
        """
        Converts this UUID7 to a standard uuid.UUID object.
        :return: A uuid.UUID instance.
        """
        return uuid.UUID(bytes=self._bytes)

    def __str__(self):
        """
        Formats the UUID into the standard string representation.
        :return: The UUID string.
        """
        return str(self.to_uuid())

    def __repr__(self):
        return f"UUID7('{str(self)}')"

    def __eq__(self, other):
        if not isinstance(other, UUID7):
            return NotImplemented
        return self._bytes == other._bytes

    def __lt__(self, other):
        if not isinstance(other, UUID7):
            return NotImplemented
        return self._bytes < other._bytes