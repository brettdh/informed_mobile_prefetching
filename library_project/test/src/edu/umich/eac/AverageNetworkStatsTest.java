package edu.umich.eac;

import android.net.ConnectivityManager;
import android.test.InstrumentationTestCase;

public class AverageNetworkStatsTest extends InstrumentationTestCase {
    AverageNetworkStats stats;
    
    protected void setUp() {
        stats = new AverageNetworkStats();
    }
    
    private NetworkStats initNetworkStats(int bw_down, int bw_up, int rtt_ms) {
        NetworkStats curStats = new NetworkStats();
        curStats.bandwidthDown = bw_down;
        curStats.bandwidthUp = bw_up;
        curStats.rttMillis = rtt_ms;
        return curStats;
    }
    
    private void assertStatsEqual(NetworkStats expected, NetworkStats actual) {
        assertEquals(expected.bandwidthDown, actual.bandwidthDown);
        assertEquals(expected.bandwidthUp, actual.bandwidthUp);
        assertEquals(expected.rttMillis, actual.rttMillis);
    }
    
    public void testSimpleAdding() {
        stats.add(ConnectivityManager.TYPE_WIFI, initNetworkStats(5, 5, 5));
        NetworkStats read = stats.get(ConnectivityManager.TYPE_WIFI);
        assertStatsEqual(initNetworkStats(5, 5, 5), read);
        
        stats.add(ConnectivityManager.TYPE_MOBILE, initNetworkStats(10, 10, 10));
        read = stats.get(ConnectivityManager.TYPE_WIFI);
        assertStatsEqual(initNetworkStats(5, 5, 5), read);
        read = stats.get(ConnectivityManager.TYPE_MOBILE);
        assertStatsEqual(initNetworkStats(10, 10, 10), read);
        
        stats.add(ConnectivityManager.TYPE_WIFI, initNetworkStats(15, 15, 15));
        read = stats.get(ConnectivityManager.TYPE_WIFI);
        assertStatsEqual(initNetworkStats(10, 10, 10), read);
        read = stats.get(ConnectivityManager.TYPE_MOBILE);
        assertStatsEqual(initNetworkStats(10, 10, 10), read);

        stats.add(ConnectivityManager.TYPE_MOBILE, initNetworkStats(0, 0, 0));
        read = stats.get(ConnectivityManager.TYPE_WIFI);
        assertStatsEqual(initNetworkStats(10, 10, 10), read);
        read = stats.get(ConnectivityManager.TYPE_MOBILE);
        assertStatsEqual(initNetworkStats(5, 5, 5), read);
    }
}
