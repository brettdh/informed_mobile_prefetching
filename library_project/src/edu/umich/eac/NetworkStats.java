package edu.umich.eac;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

class NetworkStats {
    // TODO: get real network stats.
    public int bandwidthDown = 1250000;
    public int bandwidthUp = 1250000;
    public int rttMillis = 1;
    
    public void updateAsAverage(NetworkStats newStats, int numPreviousStats) {
        bandwidthDown = ((bandwidthDown * numPreviousStats) 
                         + newStats.bandwidthDown) / (numPreviousStats + 1);
        bandwidthUp = ((bandwidthUp * numPreviousStats)
                       + newStats.bandwidthUp) / (numPreviousStats + 1);
        rttMillis = ((rttMillis * numPreviousStats)
                     + newStats.rttMillis) / (numPreviousStats + 1);
    }
    
    public static native NetworkStats getBestNetworkStats();
    
    public static Map<Integer, NetworkStats> getAllNetworkStats(Context context) {
        Map<Integer, NetworkStats> stats = getAllNetworkStatsByIp();
        
        Map<Integer, NetworkStats> netStatsByNetType = new HashMap<Integer, NetworkStats>();
        for (Integer ipAddr : stats.keySet()) {
            Integer netType = getNetType(context, ipAddr);
            if (netType != null && statsValid(stats.get(ipAddr))) {
                netStatsByNetType.put(netType, stats.get(ipAddr));
            }
        }
        return netStatsByNetType;
    }
    
    private static boolean statsValid(NetworkStats stats) {
        if (stats.bandwidthDown == 1250000 && 
            stats.bandwidthUp == 1250000 &&
            stats.rttMillis == 1) {
            // scout sets these when it fails to look up the wifi stats.
            //  apparently this is caused by a race between receiving a
            //  connectivity broadcast intent and calling getConnectionInfo()
            //  from the WifiManager; you can lose the wifi in between,
            //  in which case the essid/bssid lookup fails, the breadcrumbs db
            //  lookup fails, and the scout creates the network with bogus stats.
            // Rather than trying to fix the race, I'll just cope with invalid estimates
            //  by not adding them to the set of current network stats.
            return false;
        } else {
            return true;
        }
    }

    private static Integer getNetType(Context context, int ipAddr) {
        WifiManager wifi = 
            (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            return ConnectivityManager.TYPE_MOBILE;
        }
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        if (wifiInfo == null) {
            return ConnectivityManager.TYPE_MOBILE;
        }
        if (wifiInfo.getIpAddress() == ipAddr) {
            return ConnectivityManager.TYPE_WIFI;
        } else {
            return ConnectivityManager.TYPE_MOBILE;
        }
    }
    
    // returns (IP, stats) mapping of all available networks.
    static native Map<Integer, NetworkStats> getAllNetworkStatsByIp();
    
    static {
        System.loadLibrary("eac_support");
    }
}
