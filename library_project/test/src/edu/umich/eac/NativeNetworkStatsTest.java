package edu.umich.eac;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class NativeNetworkStatsTest extends InstrumentationTestCase {
    public void testGetBestNetworkStats() {
        NetworkStats stats = NetworkStats.getBestNetworkStats();
        assertNotNull(stats);
        assertTrue(true);
    }
    
    public void testGetAllNetworkStats() throws SocketException, UnknownHostException {
        Map<Integer, NetworkStats> nets = NetworkStats.getAllNetworkStatsByIp();
        assertNotNull(nets);
        assertTrue(nets.size() > 0);
        
        ArrayList<NetworkInterface> systemNets = 
            Collections.list(NetworkInterface.getNetworkInterfaces());
        for (Integer ipAddr : nets.keySet()) {
            byte[] addrArray = new byte[4];
            addrArray[0] = (byte)((ipAddr >> 0  ) & 0xFF);
            addrArray[1] = (byte)((ipAddr >> 8  ) & 0xFF);
            addrArray[2] = (byte)((ipAddr >> 16 ) & 0xFF);
            addrArray[3] = (byte)((ipAddr >> 24 ) & 0xFF);
            
            InetAddress ip = InetAddress.getByAddress(addrArray);
            NetworkInterface iface = 
                NetworkInterface.getByInetAddress(ip);
            assertNotNull(iface);
            assertTrue(systemNets.contains(iface));
            
            NetworkStats stats = nets.get(ipAddr);
            assertNotNull(stats);
        }
    }
    
    public void testGetAllNetworkStatsByType() throws InterruptedException {
        Context context = getInstrumentation().getContext();
        Map<Integer, NetworkStats> nets = NetworkStats.getAllNetworkStats(context);
        assertNotNull(nets);
        assertTrue(nets.size() > 0);
    }
    
    static {
        System.loadLibrary("eac_native");
        System.loadLibrary("eac_native_tests");
    }
}
