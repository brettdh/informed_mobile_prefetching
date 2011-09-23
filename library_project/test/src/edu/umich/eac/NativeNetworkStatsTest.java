package edu.umich.eac;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.test.InstrumentationTestCase;

public class NativeNetworkStatsTest extends InstrumentationTestCase {
    public void testGetBestNetworkStats() {
        NetworkStats stats = NetworkStats.getBestNetworkStats();
        assertNotNull(stats);
        assertTrue(true);
    }
    
    public void testGetAllNetworkStats() throws SocketException, UnknownHostException {
        Map<String, NetworkStats> nets = NetworkStats.getAllNetworkStats();
        assertTrue(nets.size() > 0);
        
        ArrayList<NetworkInterface> systemNets = 
            Collections.list(NetworkInterface.getNetworkInterfaces());
        assertEquals(systemNets.size(), nets.size());
        for (String ipAddr : nets.keySet()) {
            NetworkInterface iface = 
                NetworkInterface.getByInetAddress(InetAddress.getByName(ipAddr));
            assertTrue(systemNets.contains(iface));
            
            NetworkStats stats = nets.get(ipAddr);
            assertNotNull(stats);
        }
    }
    
    static {
        System.loadLibrary("eac_native");
        System.loadLibrary("eac_native_tests");
    }
}
