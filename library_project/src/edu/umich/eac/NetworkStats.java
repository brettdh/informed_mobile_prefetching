package edu.umich.eac;

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
    
    static native NetworkStats getNetworkStats();
}
