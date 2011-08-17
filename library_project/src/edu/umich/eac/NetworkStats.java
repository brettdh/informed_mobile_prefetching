package edu.umich.eac;

class NetworkStats {
    // TODO: get real network stats.
    public int bandwidthDown = 1250000;
    public int bandwidthUp = 1250000;
    public int rttMillis = 1;
    
    static native NetworkStats getNetworkStats();
}
