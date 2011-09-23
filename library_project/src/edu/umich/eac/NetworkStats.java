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
            if (netType != null) {
                netStatsByNetType.put(netType, stats.get(ipAddr));
            }
        }
        return netStatsByNetType;
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
}
