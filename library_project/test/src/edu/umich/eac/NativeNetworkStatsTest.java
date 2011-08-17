package edu.umich.eac;

import android.test.InstrumentationTestCase;

public class NativeNetworkStatsTest extends InstrumentationTestCase {
    public void testGetNetworkStats() {
        NetworkStats stats = NetworkStats.getNetworkStats();
        assertNotNull(stats);
        assertTrue(true);
    }
    
    static {
        System.loadLibrary("eac_native");
        System.loadLibrary("eac_native_tests");
    }
}
