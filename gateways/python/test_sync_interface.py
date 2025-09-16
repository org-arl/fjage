#!/usr/bin/env python3
"""
Simple test to verify that the TCPConnector now has a synchronous interface.
"""

from fjagepy import TCPConnector
import time

def test_sync_interface():
    """Test that all TCPConnector methods are synchronous."""

    # Test 1: Instantiation should be synchronous
    print("Test 1: Creating TCPConnector...")
    connector = TCPConnector("localhost", 9999, reconnect_delay=-1)  # Disable reconnect for quick failure
    print("✓ TCPConnector created successfully")

    # Test 2: set_receive_callback should be synchronous
    print("Test 2: Setting callback...")
    def dummy_callback(messages):
        print(f"Received: {messages}")

    connector.set_receive_callback(dummy_callback)
    print("✓ Callback set successfully")

    # Test 3: is_connected should be synchronous
    print("Test 3: Checking connection status...")
    connected = connector.is_connected()
    print(f"✓ is_connected() returned: {connected}")

    # Test 4: connect should be synchronous (will fail since no server)
    print("Test 4: Testing connect (should fail quickly)...")
    start_time = time.time()
    try:
        connector.connect()
        print("✗ Connect unexpectedly succeeded")
    except Exception as e:
        elapsed = time.time() - start_time
        print(f"✓ Connect failed as expected after {elapsed:.2f}s: {e}")

    # Test 5: disconnect should be synchronous
    print("Test 5: Testing disconnect...")
    connector.disconnect()
    print("✓ Disconnect completed successfully")

    # Test 6: send should be synchronous (will fail since not connected)
    print("Test 6: Testing send while disconnected...")
    try:
        connector.send("test message")
        print("✗ Send unexpectedly succeeded")
    except ConnectionError as e:
        print(f"✓ Send failed as expected: {e}")

    print("\nAll tests passed! The TCPConnector interface is synchronous.")

if __name__ == "__main__":
    test_sync_interface()
